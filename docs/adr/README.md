# Architecture Decision Records — water-flow algorithm

These ADRs are reverse-engineered from the existing implementation on
the `mcdp-1.21.1` branch. Each one captures a load-bearing design
choice in the water-flow / fluid-physics algorithm so a future reader
can understand *why* the code looks the way it does, not just *what*
it does.

For the algorithm walkthrough that ties all six together, see
[`../water-flow-algorithm.md`](../water-flow-algorithm.md).

| ADR | Title | What's at stake |
|---|---|---|
| [0001](0001-find-and-move-source-vs-spawn-and-equalize.md) | Find-and-move source vs. spawn-and-equalize | The conservation rule. Make water finite by moving the upstream source on spread, not spawning new flowing cells. |
| [0002](0002-direction-gate-canPassThroughWall-to-DOWN-only.md) | Direction-gate `canPassThroughWall` interception to DOWN-only | A 1.18→1.21 trap: vanilla reused the method for horizontal source-neighbor counting. Without the gate, horizontal water spread breaks globally. |
| [0003](0003-fluidisinfinite-thread-local-context.md) | `ThreadLocal` plumbing for `FluidIsInfinite` context | Vanilla's `WaterFluid.canConvertToSource(Level)` doesn't pass a BlockPos. Stash `(level, pos)` from `getNewLiquid (HEAD)`, read from `canConvertToSource (HEAD)`. |
| [0004](0004-spring-block-as-non-drainable-source-marker.md) | Spring block as non-drainable infinite-source marker | Lets players designate permanent water sources outside infinite-source biomes. Two integrations: source-conversion eligibility + drain exemption. |
| [0005](0005-source-search-direction-and-falling-rules.md) | Source-search direction selection and falling-rules | The three rules (climb-first, level-gradient, falling-vs-not) that make `findSourceInternal` actually find the source rather than wandering. |
| [0006](0006-biome-gated-config-knobs.md) | Biome-gated config knobs | The three biome-aware config functions (`isEnabledFor`, `isInfiniteInBiome`, `isUnfillableInBiome`) that compose the per-biome behavior matrix. |

## Format

Each ADR follows the [Michael Nygard format](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions):

- **Status** — Accepted (we're documenting decisions already shipped).
- **Context** — the vanilla MC behavior or constraint that the
  decision had to handle.
- **Decision** — what the mod actually does, with file + line
  citations.
- **Consequences** — positive, negative, and neutral effects on
  players, modders, and future maintainers.

Numbering is in chronological order of when each decision was
*recognized* and written down, not when it was implemented in the
codebase. Most decisions predate this ADR series and were captured
post-hoc by reading the source.
