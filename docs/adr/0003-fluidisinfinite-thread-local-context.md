# ADR-0003 — `ThreadLocal` plumbing for `FluidIsInfinite` context

**Status:** Accepted — only viable approach without forking
`FlowingFluid.getNewLiquid` outright.

## Context

Pre-1.20.5, `WaterFluid.isInfinite()` was a no-arg method. The mod's
predecessor mixed in at HEAD, looked at the surrounding world via
`Platform.instance` (a global) plus a separately-stashed BlockPos, and
returned a per-position decision.

1.20.5 changed the signature to `WaterFluid.canConvertToSource(Level)`
— the `Level` is now passed in but the `BlockPos` is not. 1.21.1 keeps
that same shape (1.21.2 takes `ServerLevel` instead, but we're not
there).

The mod needs the position for two reasons:

1. **Biome lookup.** `isInfiniteInBiome(fluid, level, pos)` reads
   `level.getBiome(pos)` and consults the
   `biomeDependentFluidInfinityWhitelist` config — without the pos,
   no per-biome decision possible.
2. **Spring adjacency.** `nextToSpring` checks the six neighbors of
   `pos` for the `fluidphysics:spring` block — also pos-dependent.

The vanilla call site doesn't pass pos. There's no mixin parameter to
intercept; the parameter list is `(Level)`.

The candidate alternatives were:

- **Redirect the entire `canConvertToSource` call** with `@Redirect` /
  `@WrapOperation`, replacing the call with one that has the pos in
  scope. Brittle against vanilla bytecode changes; conflicts with any
  other mod that wants to wrap the same call.
- **Re-implement `getNewLiquid` entirely** via `@Overwrite`. Massive
  surface, hostile to other mods.
- **Plumb pos through a side channel.**

## Decision

Use a `ThreadLocal` to side-channel the `(Level, BlockPos)` from the
HEAD of `FlowableFluidMixin.getNewLiquid` to the HEAD of
`WaterFluidMixin.canConvertToSource`. The mod call sequence is:

1. Vanilla calls `FlowingFluid.getNewLiquid(level, pos, state)`.
2. Mod's `FlowableFluidMixin.getNewLiquid (HEAD)` runs first → sets
   `FluidIsInfinite.localWorld = level; localPos = pos`.
3. Vanilla's body runs; somewhere in there it calls
   `WaterFluid.canConvertToSource(level)` for the same position.
4. Mod's `WaterFluidMixin.canConvertToSource (HEAD, cancellable)`
   runs → reads `FluidIsInfinite.world` and `.pos` from the
   `ThreadLocal` → decides → potentially overrides return.

Implementation:

`FluidIsInfinite.scala:11–22`:
```scala
private val localWorld: ThreadLocal[LevelReader] = new ThreadLocal()
private val localPos: ThreadLocal[BlockPos] = new ThreadLocal()
def set(world: LevelReader, pos: BlockPos): Unit = {
    localWorld.set(world)
    localPos.set(pos)
}
def world: LevelReader = localWorld.get()
def pos: BlockPos = localPos.get()
```

`FlowableFluidMixin.java:116–119`:
```java
@Inject(at = @At("HEAD"), method = "getNewLiquid")
protected void fluidphysics$getNewLiquid(Level level, BlockPos pos, ...) {
    FluidIsInfinite.set(level, pos);
}
```

`WaterFluidMixin.java:16–21`:
```java
@Inject(at = @At("HEAD"), method = "canConvertToSource(Lnet/minecraft/world/level/Level;)Z", cancellable = true)
protected void fluidphysics$canConvertToSource(Level level, CallbackInfoReturnable<Boolean> info) {
    if (!FluidIsInfinite.isInfinite(Fluids.WATER)) {
        info.setReturnValue(false);
    }
}
```

## Consequences

**Positive:**
- Minimal surface area: two HEAD injects, one ThreadLocal pair.
- Doesn't conflict with other mods that want to redirect or wrap
  `canConvertToSource` — they see the regular vanilla call shape.
- Same-thread invariant: server worldgen runs `getNewLiquid` and
  `canConvertToSource` on the chunk-tick thread for the same
  `(level, pos)` pair, so the stash-then-read sequence is reliable.

**Negative:**
- **Coupling assumption.** The pattern only works because vanilla's
  `getNewLiquid` calls `canConvertToSource` *for the same position* in
  the same call. If a future MC version moves `canConvertToSource` out
  of `getNewLiquid`'s flow (e.g. into a worldgen-time pass on a
  different thread, or into a delayed task), the stash will be stale
  or unset when the read happens. The failure mode is *wrong infinite-
  source decisions*, not a crash — water either becomes infinite
  everywhere or nowhere depending on the leftover `ThreadLocal` value.
  This will silently regress until someone looks at the algorithm.
- **`ThreadLocal` leak risk.** The values aren't cleared after read.
  A long-lived chunk-tick thread will hold references to the last
  `(Level, BlockPos)` until the next `getNewLiquid` overwrites them.
  On dimension unload the `Level` reference would prevent GC of the
  unloaded dimension. In practice the chunk-tick thread tends to keep
  ticking other dimensions and overwriting, so the leak window is
  short-lived; not currently considered worth a `try/finally` clear.

**Neutral:**
- Approach matches the broader "intercept at vanilla seams" philosophy
  of the mod rather than carving out a parallel implementation.

## Out-of-scope follow-ups

- Adding a `try/finally` clear in `getNewLiquid`'s tail to bound the
  leak window. Would require a TAIL inject that doesn't currently
  exist; trivial change if the leak ever bites.
- Moving the stash to `tick(ServerLevel, BlockPos, FluidState)` HEAD
  (one level up the call chain) for a wider scope, in case some
  future MC version inlines `getNewLiquid`.
