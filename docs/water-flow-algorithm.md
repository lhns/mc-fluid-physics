# Water-flow algorithm

How water (and any other whitelisted fluid) actually moves on the
`mcdp-1.21.1` branch — vanilla Minecraft 1.21.1's algorithm first, then
the four mixin injection points and three Scala helpers that turn it
into something more physics-y.

## TL;DR / mental model

- **Vanilla water** is a per-cell automaton: each tick a flowing cell
  recomputes its level from neighbors, and a new flowing cell is
  *spawned* into adjacent passable blocks. Two source neighbors → the
  cell becomes a source. Net effect: infinite water everywhere, levels
  equalize, no conservation.
- **This mod's water** keeps the same level/falling state machine but
  intercepts `spreadTo` to *move the upstream source itself* into the
  downstream cell, instead of spawning a new cell. Result: water is
  finite. Pour a bucket of water onto a slab and watch it flow off the
  edge — the source travels with it.
- Conservation is selectively turned off via three knobs: the
  `flowOverSources` / `getFlowOverSources` rule (lakes/oceans aren't
  drained from above), `isInfiniteInBiome` (oceans + rivers keep
  vanilla two-neighbor source conversion), and `fluidphysics:spring`
  (a marker block that exempts adjacent sources from being moved).
- `RainRefill` periodically tops up partial sources during rain so
  outdoor pools don't slowly evaporate (see "See also" at the end).
- A `ThreadLocal` plumbs `(Level, BlockPos)` from `getNewLiquid`'s HEAD
  into `WaterFluid.canConvertToSource`, because vanilla's API only
  hands the latter a `Level` — the BlockPos needed for biome / spring
  checks isn't passed.

## Vanilla recap (MC 1.21.1)

The vanilla flowing-fluid pipeline lives in `net.minecraft.world.level.material.FlowingFluid`:

- **`tick(ServerLevel, BlockPos, FluidState)`** — per-tick entry. For
  flowing (non-source) fluid: compute `getNewLiquid(level, pos, state)`;
  if changed, write it. For source fluid: try `spread(level, pos, state)`.
- **`getNewLiquid(level, pos, blockStateAt)`** — the level recomputer.
  Looks at the four horizontal neighbors. For each one that's the same
  fluid type and *not* on a level-falling boundary, pick the
  highest-level neighbor and emit `level - 1` (a "drop" decremented from
  the strongest upstream supply). If the cell *above* is the same fluid
  type, emit `MAX_LEVEL` with the falling flag set. Also counts adjacent
  source blocks; with two or more, the result becomes a new source —
  this is the "infinite water" rule, gated by `WaterFluid.canConvertToSource(Level)`.
- **`spreadTo(level, pos, blockState, direction, fluidState)`** —
  writes the new fluid block. Calls `beforeDestroyingBlock(level, pos, blockState)`
  on the displaced solid (drops items), then `setBlock(pos, fluidState.createLegacyBlock(), 3)`
  or routes through `LiquidBlockContainer.placeLiquid` for waterloggable
  targets.
- **`canSpreadTo(...)` / `canPassThroughWall(direction, ...)`** — gates.
  The latter returns true if a fluid can replace a block when flowing
  through it; called both for the down direction (pouring through) *and*
  for horizontal source-neighbor counting in `getNewLiquid`.
- **`WaterFluid.canConvertToSource(Level)`** — the post-1.20.5 method
  invoked by `getNewLiquid` to decide whether a flowing cell with two
  source neighbors becomes a source. Pre-1.20.5 was no-arg `isInfinite()`.
  1.21.2 takes `ServerLevel`; we're on 1.21.1, so it's still `Level`.

## Mod overrides — `FlowableFluidMixin`

Located at `common-1.21.1/src/main/scala/de/lolhens/minecraft/fluidphysics/mixin/FlowableFluidMixin.java`.
Four `@Inject`s, each annotated against `FlowingFluid`. Walking them
in tick-order:

### 1. `getNewLiquid (HEAD)` — context stash

```java
@Inject(at = @At("HEAD"), method = "getNewLiquid")
protected void fluidphysics$getNewLiquid(Level level, BlockPos pos, BlockState state, ...) {
    FluidIsInfinite.set(level, pos);
}
```

Stashes `(level, pos)` into `FluidIsInfinite`'s `ThreadLocal`s before
vanilla runs the body. Vanilla then calls `WaterFluid.canConvertToSource(Level)`
inside `getNewLiquid`'s flow — see step 4 below.

### 2. `canPassThroughWall (RETURN, direction-gated)`

```java
@Inject(at = @At("RETURN"), method = "canPassThroughWall", cancellable = true)
private void fluidphysics$canPassThroughWall(Direction direction, ...) {
    if (direction != Direction.DOWN) return;
    // ... two effects below
}
```

The direction gate (lines 64–85) is the trap captured by ADR-0002: in
1.18.2 the equivalent method was hardcoded to DOWN; in 1.21.1 vanilla
reuses the same method for horizontal source-neighbor counting in
`getNewLiquid`. Without the gate, the second effect below would
incorrectly veto neighbor counts for any cell next to a source, causing
`getNewLiquid` to emit EMPTY and killing horizontal spread entirely.

Two effects:
- **Trapdoor pass-through:** if the destination is a non-waterlogged
  `TrapDoorBlock` and either bottom-half or open, water passes through
  it. (Vanilla treats trapdoors as solid for fluid spread.)
- **Don't drain seas from above:** if the original return was true,
  the destination is a source of this fluid, the per-biome enable check
  passes, and `getFlowOverSources()` is on, override the return to
  false. Stops the conservation rule from picking up an ocean source
  every time a finite stream pours into it.

### 3. `canSpreadTo (HEAD, direction-gated)`

```java
@Inject(at = @At("HEAD"), method = "canSpreadTo", cancellable = true)
protected void fluidphysics$canSpreadTo(BlockGetter blockGetter, BlockPos fluidPos, ...) {
    if (flowDirection == Direction.DOWN && ...) {
        // ...
    }
}
```

Gated to DOWN only. Allows downward spread into existing same-fluid
cells (so a column above a sea can keep falling without vanilla bailing
because the destination "already has fluid"). Special case: if the
destination is at sea-level − 1 in an "unfillable" biome
(`isUnfillableInBiome`, e.g. desert), source-state targets are also
passable — i.e. desert oases drain rather than persist forever.

Trapdoor pass-through duplicated here for cases the wall check doesn't
cover.

### 4. `spreadTo (HEAD, cancellable)` — the conservation rule

```java
@Inject(at = @At("HEAD"), method = "spreadTo", cancellable = true)
protected void fluidphysics$spreadTo(LevelAccessor level, BlockPos pos, ...) {
    // ... eligibility checks ...
    BlockPos sourcePos = FluidSourceFinder.findSourceOrNull(level, up, still.getType());
    if (sourcePos != null) {
        FluidSourceFinder.moveSource(level, sourcePos, pos, state, fluid, still);
        info.cancel();
    } else if (isSourceBlockOfThisType(state.getFluidState())) {
        info.cancel();
    }
}
```

The headline change. When vanilla would spread fluid into a new cell,
this inject:

1. Filters: must be DOWN spread or "above is same fluid type"; per-biome
   enable check must pass; piston-up-below safe-stop (don't pour onto a
   piston that's facing UP — gives one tick of slack for pistons about
   to extend).
2. Finds an upstream source via
   `FluidSourceFinder.findSourceOrNull(level, pos.above(), still.getType())`.
   This walks the connected fluid graph from `pos.above()` looking for
   a source block; details in §Source search below.
3. If found: `moveSource(...)` drains the source and places it at `pos`,
   then cancels vanilla's `spreadTo` so the original level/falling logic
   doesn't double-write.
4. If no source found but `pos` is already a source, also cancel — that
   handles the "no upstream water; let the existing source stay put"
   case rather than letting vanilla flatten it.

## Source search — `FluidSourceFinder.findSourceInternal`

`common-1.21.1/.../util/FluidSourceFinder.scala`. The algorithm walks
the connected fluid graph along directions consistent with "upstream":

```scala
private def findSourceInternal(world, blockPos, fluidState, fluid, direction,
                               ignoreBlocks, ignoreFirst, ignoreLevel,
                               maxIterations, iteration): Option[BlockPos] = {
  if (iteration > maxIterations || ignoreBlocks.size >= maxCheckedBlocks) return None
  if (!ignoreFirst && ignoreBlocks.contains(blockPos)) return None
  ignoreBlocks.add(blockPos)

  if (sameFluidType(fluidState)) {
    // Step 1: try UP first, unless we just came from above
    if (direction != Direction.DOWN) {
      val up = blockPos.above()
      if (sameFluidType(world.getFluidState(up))) {
        recurse(up, Direction.UP, ...) // climb columns
      }
    }

    // Step 2: source hit — return unless adjacent to spring (ADR-0004)
    if (!ignoreFirst && fluidState.isSource && !nextToSpring(blockPos)) {
      return Some(blockPos)
    }

    // Step 3: walk horizontal neighbors going upstream
    for (nextDirection <- horizontal if nextDirection != direction.getOpposite) {
      val next = blockPos.relative(nextDirection)
      val nextFluidState = world.getFluidState(next)
      if (sameFluidType(nextFluidState)) {
        if (nextLevel > level || (falling && !nextFalling) || ignoreLevel) {
          recurse(next, nextDirection, ...)
        }
      }
    }
  }
  None
}
```

Three rules govern recursion:

- **Climb-first:** any non-DOWN entry tries UP before horizontals.
  Captures "the source is at the top of the column".
- **Upstream filter:** horizontal recursion only when
  `nextLevel > level` (climbing the level gradient toward the source)
  *or* `falling && !nextFalling` (the cell above a waterfall — its
  upstream is the column-feed) *or* the caller passed
  `ignoreLevel = true` (escape hatch for callers outside the tick path).
- **No-backtrack:** never recurse back the way we came
  (`oppositeDirection` exclusion).

Two budgets bound runtime:

- `findSourceMaxIterations` (default 255) caps recursion depth.
- `findSourceMaxCheckedBlocks` (default `Some(4095)`) caps the
  visited-set size. Both are config-tunable in
  `FluidPhysicsConfig.scala:36–40`.

### Worked example — single source feeding a 3×3 puddle

```
. . . . .
. F F F .          F = flowing, level 7
. F S F .          S = source (level 8)
. F F F .
. . . . .
```

Imagine vanilla wants to spread the south-west F to the cell south of
it (off-puddle). The mod's `spreadTo` HEAD inject calls
`findSourceOrNull(level, up=puddleCell.above(), water)`. Above is air,
so step 1 short-circuits. The puddle cell is non-source, so step 2 is
skipped. Step 3 picks up neighbors: each adjacent F cell has the same
level (7), so the level-gradient filter (`nextLevel > level`) blocks
them — except S, which has level 8 > 7. Recurse to S; S is a source
(step 2 hits) and not adjacent to a spring → return S. `moveSource`
then drains S and places a new source at the new spread destination.

The puddle now looks like:

```
. . . . .          new source moved one cell south,
. F F F .          previously at S is now flowing level 7
. F F F .
S F F F .
. . . . .          (sketch — actual neighbor levels recompute next tick)
```

## Source drain + place — `FluidSourceFinder.moveSource`

```scala
def moveSource(world, srcPos, dstPos, dstState, fluid, still): Unit = {
  // Drain srcPos
  srcState.getBlock match {
    case bucketPickup: BucketPickup if !bucketPickup.isInstanceOf[LiquidBlock] =>
      bucketPickup.pickupBlock(null, world, srcPos, srcState)  // waterloggable
    case _ =>
      if (!srcState.isAir) callBeforeDestroyingBlock(world, srcPos, srcState)
      val newSourceLevel = still.getAmount - 1                  // 8 → 7
      world.setBlock(srcPos, fluid.getFlowing(newSourceLevel, false).createLegacyBlock(), 3)
  }
  // Place at dstPos
  dstState.getBlock match {
    case liquidBlockContainer: LiquidBlockContainer =>
      liquidBlockContainer.placeLiquid(world, dstPos, dstState, still)
    case _ =>
      if (!dstState.isAir) callBeforeDestroyingBlock(world, dstPos, dstState)
      world.setBlock(dstPos, still.createLegacyBlock(), 3)
  }
}
```

Two important details:

- **`BucketPickup` branch:** a waterloggable block (kelp, seagrass, …)
  is drained via its own `pickupBlock` — preserves the block, removes
  the fluid. Excludes plain `LiquidBlock` to avoid recursion.
- **`callBeforeDestroyingBlock`:** the displaced solid's drop logic
  fires (cobblestone gets broken into items, etc.). Reached via the
  `FlowableFluidAccessor.callBeforeDestroyingBlock` invoker — that's
  the only reason the accessor mixin exists.

## Infinite-source decisions — `FluidIsInfinite` + `WaterFluidMixin`

The mod still wants vanilla "two source neighbors becomes a source"
behavior in some places (oceans, rivers, near a spring block). Vanilla
exposes the decision through `WaterFluid.canConvertToSource(Level)`,
which only receives `Level` — no BlockPos.

The plumbing:

1. **`FlowableFluidMixin.getNewLiquid (HEAD)`** stashes `(level, pos)`
   into `FluidIsInfinite`'s `ThreadLocal`.
2. **`WaterFluidMixin.canConvertToSource (HEAD, cancellable)`** reads
   `FluidIsInfinite.isInfinite(Fluids.WATER)`. If false, force the
   return to false (overriding vanilla's default true).
3. **`FluidIsInfinite.isInfinite`** returns true if either:
   - `isInfiniteInBiome` — biome on the user's
     `biomeDependentFluidInfinityWhitelist` (oceans + rivers by
     default), or
   - `nextToSpring` — any of `DOWN +: HORIZONTAL` adjacent to the
     stashed pos is a `fluidphysics:spring` block AND the spring's
     `allowInfiniteWater` flag is on.
   Else returns false → `canConvertToSource` is vetoed → vanilla
   conversion blocked → conservation governs.

When `isEnabledFor(fluid)` is false (i.e. the fluid isn't on the mod's
whitelist at all), `isInfinite` returns true unconditionally — vanilla
behavior preserved everywhere for that fluid.

The `ThreadLocal` is a deliberate hack documented in ADR-0003. The
guarantee that holds it together: vanilla calls `canConvertToSource`
*inside* `getNewLiquid`'s body, both run on the same thread for the
same `(level, pos)`, so the stash is set-then-read with no
intervening overwrite. If a future MC version moves
`canConvertToSource` out of the `getNewLiquid` flow, this breaks —
the breakage will surface as wrong infinite-source behavior, not a
crash.

## Config quick reference

All keys live in `FluidPhysicsConfig.scala`. Defaults shown.

| Key | Default | What it does |
|---|---|---|
| `findSourceMaxIterations` | 255 | Recursion depth cap in `findSourceInternal`. |
| `findSourceMaxCheckedBlocks` | `Some(4095)` | Visited-set cap. `None` = unbounded. |
| `flowOverSources` | true | If true, `canPassThroughWall` blocks downward spread *into* an existing source — preserves seas/lakes from being drained. |
| `fluidWhitelist` / `fluidBlacklist` | (whitelist=None means "all"; blacklist=empty) | Which fluids run through this mod at all. |
| `biomeWhitelist` / `biomeBlacklist` | (whitelist=None means "no biomes"; blacklist=empty) | Per-fluid + per-biome master switch — gates `isEnabledFor(fluid, level, pos)`. |
| `biomeDependentFluidInfinityWhitelist` | empty | Biomes where vanilla two-neighbor source conversion stays on. Typically oceans + rivers. |
| `unfillableBiomeWhitelist` / `unfillableBiomeBlacklist` | (whitelist=Some(empty); blacklist=empty) | Biomes where water disappears at sea-level − 1. Used by `canSpreadTo`'s desert-oasis special case. |
| `spring` | `Some(SpringConfig())` | Enables the `fluidphysics:spring` block. `spring.allowInfiniteWater = true` lets adjacent cells trigger `isInfinite`. |
| `rainRefill` | `Some(RainRefillConfig())` | Periodic source-cell refill in rain. Prevents outdoor pools from slowly draining. |
| `debugFluidState` | false | Color water by level (debug viz). |

## See also

- `RainRefill.scala` — every server-tick, walks loaded chunks of
  rainy biomes; with config-tunable probability, picks a random
  surface fluid cell adjacent to ≥ 2 sources and converts it to a
  source. Acts as a slow regeneration so finite outdoor pools don't
  shrink to nothing under evaporation-by-mod-spread. Tightly coupled
  to `RainRefillConfig`'s probability + biome filters; see config
  comments inline.
- `SpringBlockFeature.scala` — worldgen feature that places spring
  blocks during chunk generation in qualifying biomes, providing the
  "permanent water source" markers `FluidIsInfinite` and
  `FluidSourceFinder` consult.
- `mcdp-port.md` — explains how the cross-classloader call from
  vanilla mixin handlers (game-layer) into these Scala helpers
  (per-mod loader) is wired transparently by mcdp's auto-bridge
  codegen. The four `LOGIC_*` static fields you'd see on the
  rewritten `FlowableFluidMixin.class` are the bridge interfaces;
  bridge impls in `…fluidphysics.{fabric,neoforge}.mcdp_bridges_impl`
  forward to the per-mod-loader Scala objects.
- `docs/adr/` — design-decision records for the six load-bearing
  choices in this algorithm (find-and-move conservation, direction
  gating, ThreadLocal context plumbing, spring as non-drainable
  marker, source-search direction rules, biome-gated config).
