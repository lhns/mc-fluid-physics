# ADR-0002 — Direction-gate `canPassThroughWall` interception to DOWN-only

**Status:** Accepted — landed during the 1.18→1.21 port. Without it
the mod silently breaks horizontal water spread on 1.21.1.

## Context

In MC 1.18.2 (and earlier), `FlowingFluid` had a method
`canFlowDownInto(BlockGetter, BlockPos, BlockState, BlockPos, BlockState)`
that was hardcoded to the DOWN direction — the name reflected the
intent. This mod's predecessor injected at its RETURN to add two
behaviors: (a) trapdoor pass-through for water, (b) when
`getFlowOverSources` is on, block downward spread *into* an existing
source so seas can't be drained from above.

In 1.21.1, the equivalent method was renamed and generalized to
`canPassThroughWall(Direction direction, BlockGetter, BlockPos,
BlockState, BlockPos, BlockState)`. Vanilla now reuses the same method
for *horizontal* source-neighbor counting inside `getNewLiquid`: when
recomputing a flowing cell's level, the loop over horizontal neighbors
calls `canPassThroughWall(neighborDirection, ...)` to determine which
neighbors count toward the level computation.

If the mod's "block flow into a source" rule fires unconditionally,
horizontal source neighbors are also vetoed → `getNewLiquid` returns
EMPTY for any cell next to a source → flowing water cannot exist
adjacent to a source → horizontal spread is dead globally. Water flows
*down* fine but disappears the moment it tries to creep along a
horizontal plane next to its supply.

This was observed empirically when the mod was first ported to 1.21.1
without the gate.

## Decision

Gate the entire body of the `canPassThroughWall` `@Inject` on
`direction == Direction.DOWN`. For any other direction, return
immediately and let vanilla's original return value stand.

Implementation in `FlowableFluidMixin.java:64–85`:

```java
@Inject(at = @At("RETURN"), method = "canPassThroughWall", cancellable = true)
private void fluidphysics$canPassThroughWall(Direction direction, ...) {
    if (direction != Direction.DOWN) return;
    // ... trapdoor pass-through + flowOverSources veto ...
}
```

The inline comment at lines 58–63 documents the trap explicitly. The
gate restores the 1.18.2 semantics where the original method was
DOWN-only by signature.

## Consequences

**Positive:**
- Horizontal water spread works again on 1.21.1.
- The mod's two intended behaviors (trapdoor pass-through,
  drain-protection) still fire correctly because both are conceptually
  about the DOWN direction.

**Negative:**
- The gate is implicit knowledge: a future maintainer who looks at
  `canPassThroughWall` without reading this ADR or the inline comment
  might see the `if (direction != Direction.DOWN) return` and try to
  remove it as dead code. The fail mode is silent — water flows down
  fine but won't spread sideways — and only becomes obvious in
  testing.
- If a future MC version adds a third use site for `canPassThroughWall`
  with yet another direction-dependent semantics, the gate may need to
  be revisited.

**Neutral:**
- The DOWN-only semantics matches the natural reading of "this fluid
  is *passing through* a block on its way *down*", which is what the
  trapdoor and drain-protection rules both encode.
