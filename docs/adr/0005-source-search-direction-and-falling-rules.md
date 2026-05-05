# ADR-0005 — Source-search direction selection and falling-rules

**Status:** Accepted — the heuristic that makes
`FluidSourceFinder.findSourceInternal` actually find the source rather
than wandering the connected fluid graph forever.

## Context

The conservation rule (ADR-0001) is "find the upstream source and move
it". The implementation is a graph walk over connected same-fluid
cells starting from `pos.above()` of the spread destination. Naively,
this is unbounded — water cells form arbitrary networks, and a graph
walk that doesn't pick directions purposefully can:

- Visit O(N²) cells in a flat puddle before finding the source (or
  giving up against the visited-set budget).
- Wander downstream from the entry point and return a *downstream*
  source, completely subverting the intended semantics.
- Get stuck in cycles around split-flow geometries (waterfalls
  branching around a pillar, etc.) without convergence guarantees.

The walk needs three properties:

1. **Bounded.** Both worst-case and typical case fast.
2. **Upstream-biased.** Sources upstream of the entry should be
   found before sources downstream (ideally downstream sources should
   never be returned, but in some topologies that's impossible without
   global knowledge).
3. **No backtracking.** Don't re-enter the cell we just came from on
   the next recursion level.

## Decision

Three selection rules govern recursion in
`FluidSourceFinder.findSourceInternal`
(`FluidSourceFinder.scala:80–145`):

### Rule A — Climb-first

```scala
if (direction != Direction.DOWN) {
    val up: BlockPos = blockPos.above()
    val upFluidState = world.getFluidState(up)
    if (!upFluidState.isEmpty && fluid.isSame(upFluidState.getType)) {
        val sourcePos = findSourceInternal(world, up, upFluidState, fluid,
                                            Direction.UP, ignoreBlocks, ...)
        if (sourcePos.isDefined) return sourcePos
    }
}
```

Any non-DOWN entry tries the cell above first. Captures "the source
is at the top of a column". Skipped when the recursion arrived from
above (`direction == Direction.DOWN`) — that cell is already known
and contributed nothing.

### Rule B — Upstream filter on horizontals

```scala
val falling = fluidState.getValue(FlowingFluid.FALLING)

while (i < horizontal.length) {
    val nextDirection = horizontal(i)
    if (nextDirection != oppositeDirection) {
        val level = fluidState.getAmount
        val nextFluidState = world.getFluidState(...)
        val nextLevel = nextFluidState.getAmount
        val nextFalling = nextFluidState.getValue(FlowingFluid.FALLING)
        if (nextLevel > level || (falling && !nextFalling) || ignoreLevel) {
            // recurse
        }
    }
    i += 1
}
```

Three conditions for horizontal recursion:

- `nextLevel > level` — the neighbor has more fluid, i.e. it's *up
  the level gradient*. Sources have level 8; flowing cells have lower
  levels by distance from the source. Following increasing level
  generally heads toward the source.
- `falling && !nextFalling` — the current cell is flagged as falling
  (the cell of a waterfall column) and the neighbor is not. A
  waterfall column is at level 8 falling; its non-falling horizontal
  neighbor is the cell *feeding* the waterfall — i.e. one block
  upstream of the lip. Without this rule, the climb-first heuristic
  would walk *up the waterfall* and miss the source entirely (level
  is the same all the way up a waterfall column).
- `ignoreLevel` — caller-requested escape hatch. Used by external
  callers (rare; not by the tick path). Lets the search run as a
  pure same-fluid flood-fill if the caller knows what they're doing.

### Rule C — No backtrack

```scala
val oppositeDirection = direction.getOpposite
// ... rule B exclusion ...
if (nextDirection != oppositeDirection) { ... }
```

Never recurse in the direction opposite to the one we just came from.
Prevents oscillation between two adjacent cells. Combined with the
visited-set (`ignoreBlocks`), guarantees termination.

### Budgets

```scala
if (iteration > maxIterations ||
    FluidPhysicsMod.config.findSourceMaxCheckedBlocks.value.exists(ignoreBlocks.size >= _))
    return None
```

Two configurable bounds:

- `findSourceMaxIterations` — cap on recursion depth (default 255).
- `findSourceMaxCheckedBlocks` — cap on visited-set size (default
  `Some(4095)`; `None` = unbounded).

Either limit triggers `None` return → spread is suppressed (since
`spreadTo`'s inject treats no-source-found-and-no-source-here as
"don't spread"). Player-visible: water "stops advancing" when it gets
too far from its source.

## Consequences

**Positive:**
- Bounded runtime. The visited-set guarantees no cell is revisited;
  the iteration cap guarantees at-most-N recursion frames; the
  no-backtrack rule means each frame eliminates at least one direction
  from consideration.
- Upstream-biased in the common cases:
  - Single source, flowing puddle: finds source in O(distance) calls.
  - Single source, waterfall: climb-first walks up the column;
    `falling && !nextFalling` jumps from the lip to the feeding cell.
- Recursion terminates on disconnected components — the visited-set
  fills, the function returns `None`.

**Negative:**
- **Adversarial topologies can defeat the heuristic.** Two sources
  connected through a non-monotone level field (e.g. via a dip and
  rise) can confuse the level-gradient filter, leading to either a
  longer search or `None` return. Player-visible as "the water won't
  spread even though there's clearly a source upstream".
- **Hits the cap on long horizontal channels.** A 200-block-long
  flowing canal exceeds the default 255 iteration cap. Configurable
  workaround: bump `findSourceMaxIterations`. The default tradeoff is
  conservative — the cost of a higher cap is per-spread CPU on every
  fluid tick.
- **Direction-from-recursion encoding is implicit.** The `direction`
  parameter passed to recursion is the direction the recursion *came
  in*, not the direction the current cell is heading. A reader of
  the code has to keep that invariant in mind.

**Neutral:**
- The `falling && !nextFalling` rule is a small, code-only
  acknowledgment that vanilla's `FALLING` boolean state encodes
  semantically useful information about flow direction (not just
  rendering). Other mods rarely look at `FALLING` outside of rendering;
  this mod uses it as a routing hint.
