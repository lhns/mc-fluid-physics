# ADR-0004 — Spring block as non-drainable infinite-source marker

**Status:** Accepted — the only intentional way to create permanent
finite-water sources in non-ocean biomes.

## Context

ADR-0001's conservation rule says: when water spreads, move the
upstream source. Combined with ADR-0006's biome-gated infinite-source
rule (oceans + rivers), this means water in *any other biome* is
strictly finite. Pour a bucket on a desert, watch it drain to a single
cell, then watch that cell drain off the slope until it stops.

Players need a way to designate "this is a permanent water source"
without enabling infinite water everywhere. Vanilla had no such
concept; the obvious candidate (a custom block whose presence flags
adjacent fluid as infinite) requires two pieces of integration:

1. The infinite-source decision (`FluidIsInfinite.isInfinite`) must
   return true near the block.
2. The conservation rule (`FluidSourceFinder.findSourceInternal`) must
   *not* return the spring-adjacent source as the result of an
   upstream search — otherwise `moveSource` would drain it on the
   first downstream tick, defeating the whole point.

Both integrations are needed. Either alone is broken: integration 1
without 2 means the spring fills its source, the source drains away,
and the spring fills it again on the next tick — a flicker. Integration
2 without 1 means the source can't be drained but never appears in the
first place, since vanilla's two-neighbor source conversion was vetoed
by ADR-0001's gating.

## Decision

Introduce a custom block `fluidphysics:spring`, configurable via
`SpringConfig` in `FluidPhysicsConfig`. When `spring.allowInfiniteWater`
is on, integrate at both points:

**Integration 1 — infinite-source eligibility**
(`FluidIsInfinite.scala:26–33`):

```scala
def isNextToSpring = FluidPhysicsMod.config.spring match {
    case Some(spring) if spring.allowInfiniteWater.value =>
        (Direction.DOWN +: horizontal).exists { direction =>
            world.getBlockState(pos.relative(direction)).is(spring.getBlock)
        }
    case _ => false
}
```

A flowing-cell adjacent to a spring (DOWN or any horizontal) is
eligible for source conversion via `WaterFluidMixin.canConvertToSource`
(see ADR-0003 for the plumbing).

**Integration 2 — exempt-from-drain**
(`FluidSourceFinder.scala:109–122`):

```scala
if (!ignoreFirst && fluidState.isSource) {
    val nextToSpring = FluidPhysicsMod.config.spring.map(_.getBlock) match {
        case Some(springBlock) =>
            (Direction.DOWN +: horizontal).filterNot(_ == oppositeDirection).exists { direction =>
                world.getBlockState(blockPos.relative(direction)).is(springBlock)
            }
        case None => false
    }
    if (!nextToSpring) {
        return Some(blockPos)
    }
}
```

When the source-finder reaches a source cell, it returns *unless* that
cell is adjacent to a spring — in which case the search continues past
it as if the cell weren't a source at all. Spring-fed sources are
invisible to `findSourceOrNull`, so `moveSource` never targets them.

The neighbor check excludes `oppositeDirection` (the direction the
recursion came from) — small optimization, since we know the previous
cell isn't a spring (or we wouldn't have recursed from it).

Worldgen support: `SpringBlockFeature` places the block during chunk
generation in qualifying biomes. The full chain
spring-block → biome-feature → infinite-source is wired through the
mod's data-pack feature config; details in
`SpringBlockFeature.scala`.

## Consequences

**Positive:**
- Players in non-whitelisted biomes can build durable water features
  by placing a spring under or beside a source cell. Caves can have
  permanent pools; deserts can have oases.
- The marker is a real block (visible, breakable, non-stackable
  semantics under player control), not an invisible NBT tag. Easier
  to debug, easier to design around in survival/creative.
- The integration is symmetric: spring presence is *both* what allows
  source conversion and what prevents drain, so adding/removing a
  spring is a single-action toggle.

**Negative:**
- The neighbor scan runs once per source-conversion check and once
  per source-find return. Six block lookups per call; cheap but
  measurable in heavy fluid-tick scenes. Bounded by the fluid-tick
  rate, not pathological.
- Two separate code paths (`FluidIsInfinite` for conversion eligibility,
  `FluidSourceFinder` for drain exemption) need to stay in sync. The
  shared concept is "spring adjacency" but they're spelled out
  independently; a future refactor could extract a common
  `nextToSpring(world, pos)` helper.
- Players unfamiliar with the mod won't know the spring block exists
  unless they consult the mod's tutorial / wiki. There's no in-game
  affordance pointing at it.

**Neutral:**
- The `allowInfiniteWater` toggle on `SpringConfig` lets server
  operators disable infinite-water-near-springs while keeping the
  block (e.g. for purely decorative purposes). The current default
  is on.
