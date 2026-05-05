# ADR-0001 — Find-and-move source vs. spawn-and-equalize

**Status:** Accepted — load-bearing decision behind the mod's
"physics-y water" feel; in place since the mod's first release. This
ADR is a reverse-engineered record of an existing decision.

## Context

Vanilla Minecraft's `FlowingFluid.tick` spreads water by *spawning*
new flowing cells: when a source ticks, it calls `spreadTo(pos.below)`
and each horizontal neighbor reachable via `canSpreadTo`. The new cell
gets a level computed from its neighbors. Two source neighbors → it
*becomes* a source. Net effect: water is infinite, levels equalize,
pouring a bucket of water down a slope creates an ever-spreading film
that never depletes.

The mod wants water to behave finitely — pour it, and the source
*flows with* it. Cells of "drop" off the source aren't durable
artifacts; the source itself is the artifact, and movement of the
source is the spread.

## Decision

Intercept `spreadTo` at HEAD (cancellable). Before vanilla's body
runs, walk the connected fluid graph upstream from the spread
destination and look for the cell that's actually a *source*. If
found, drain that source and place it at the destination, then cancel
vanilla's `spreadTo` so the original level/falling write doesn't
re-execute.

Implementation in `FlowableFluidMixin.java:121–151`:

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

The find-and-move helpers live in `FluidSourceFinder.scala`:
`findSourceInternal` walks upstream, `moveSource` performs the drain
+ place.

## Consequences

**Positive:**
- Water is finite and pourable. Players can build canals, plumbing,
  drainage features — all with the intuitive "the water you place is
  the water you have" mental model.
- Compatible with vanilla level/falling state machine for everything
  *except* spread, so vanilla rendering, fluid-tick scheduling, and
  block-update listeners all work unchanged.

**Negative:**
- Loss of vanilla "two source neighbors becomes a source" everywhere
  the mod is enabled, *unless* explicitly granted back via
  `biomeDependentFluidInfinityWhitelist` (ADR-0006) or the spring
  block (ADR-0004). Players relying on the classic 2x2 infinite pond
  trick need to be in a whitelisted biome or place a spring.
- `FluidSourceFinder` recursion has a runtime cost on every spread
  attempt — bounded by `findSourceMaxIterations` and
  `findSourceMaxCheckedBlocks` config knobs (255 / 4095 by default).
  In pathological topologies (e.g. a 100-block-long flowing canal)
  this can hit the cap; the spread is then suppressed rather than
  performed, which the player sees as "water stopped advancing".
- Mod compatibility: any other mod that intercepts `spreadTo` after
  this one needs to be aware that vanilla's body may not run.

**Neutral:**
- Save-format compatible: still uses vanilla's `LiquidBlock` + level
  property. Disabling the mod and reloading produces vanilla water
  behavior again, no broken state.
