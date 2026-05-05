# ADR-0006 — Biome-gated config knobs

**Status:** Accepted — every behavioral delta of the mod is gated
through one of these biome-aware config functions.

## Context

The mod's conservation rule (ADR-0001) breaks vanilla expectations:

- Players accustomed to "scoop two source neighbors → free third
  source" lose that mechanic.
- Aquatic biomes (oceans, rivers) become drainable from above by
  finite finite-water flowing in.
- Desert oases — which in vanilla aren't a thing, but which players
  might build via vanilla two-neighbor source conversion — become
  unintuitive: water poured into a hot dry biome should arguably *not*
  persist forever as a vanilla puddle would.

Three separate knobs are needed:

1. **Per-fluid + per-biome master switch.** Some biomes might want
   vanilla water entirely (oceans, swamps with their unique water
   feel), so the mod stays out of their way.
2. **Infinite-source allowed.** Some biomes are large bodies of water
   that need vanilla two-neighbor conversion to keep filling
   themselves naturally as the world generates and ticks.
3. **Unfillable-at-sea-level.** Desert and similar biomes where a
   block at `sea_level - 1` *should* drain rather than hold water.
   The vanilla "underground reservoir" feel.

Naive implementation: hard-code "ocean" / "river" / "desert" in the
mod source. Fragile (data-pack biomes break it) and forks vanilla's
biome system. Better: expose three configuration functions on
`FluidPhysicsConfig` that take `(fluid, level, pos)` and return a
boolean, with biome-set whitelists/blacklists in the user's
`fluidphysics.toml`.

## Decision

Three biome-aware config functions
(`FluidPhysicsConfig.scala:118–149`):

```scala
def isEnabledFor(fluid: Fluid, world: Level, pos: BlockPos): Boolean = {
    if (!isEnabledFor(fluid)) return false
    val whitelist = WorldContext(world).getBiomeWhitelist
    if (whitelist.isEmpty) return false
    val biome: Holder[Biome] = world.getBiome(pos)
    whitelist.get(Some(fluid))
        .orElse(whitelist.get(None))
        .exists(_.contains(biome.value))
}

def isInfiniteInBiome(fluid: Fluid, world: Level, pos: BlockPos): Boolean = ...

def isUnfillableInBiome(fluid: Fluid, world: Level, pos: BlockPos): Boolean = ...
```

Each consults a per-world biome registry, a user-configurable
whitelist (via `Some(fluid) → biomes`, falling back to `None → biomes`
for fluid-agnostic rules), and looks up the biome at `pos`.

The three rules wire into the algorithm at:

- `isEnabledFor(fluid, level, pos)` — gates every mod intercept.
  `FlowableFluidMixin`'s `canSpreadTo`, `canPassThroughWall`, and
  `spreadTo` injects all return early if false → vanilla behavior
  resumes for that cell.
- `isInfiniteInBiome` — read by `FluidIsInfinite.isInfinite`
  (ADR-0003) → controls `WaterFluid.canConvertToSource` veto.
  Effectively: "in these biomes, vanilla two-neighbor source
  conversion stays on".
- `isUnfillableInBiome` — read by `FlowableFluidMixin.canSpreadTo`
  (line 104). When the destination is at `level.getSeaLevel() - 1`
  in an unfillable biome, source-state targets are also passable —
  i.e. water voids itself there.

The user's `fluidphysics.toml` exposes:

- `biomeWhitelist` / `biomeBlacklist` (master switch — defaults to
  whitelist=None which ships as "no biomes" → mod entirely off until
  configured).
- `biomeDependentFluidInfinityWhitelist` (infinite-source biomes —
  defaults to empty; user typically populates with rivers + oceans).
- `unfillableBiomeWhitelist` / `unfillableBiomeBlacklist` (unfillable
  biomes — defaults to whitelist=Some(empty) → no biomes are
  unfillable until configured).

The defaults err on "do nothing"; users opt in to per-biome behavior.

The `WorldContext(world)` cache (`FluidPhysicsConfig.scala:82–116`)
keys the biome-set lookup on the dimension's biome registry,
weak-referencing the `Level`. Avoids re-resolving biome IDs per
fluid-tick.

## Consequences

**Positive:**
- Server operators can tune behavior per biome without modifying the
  mod source. Data-pack biomes are first-class — anyone can add
  custom biomes and place them on the appropriate lists.
- The three rules compose orthogonally:
  - `isEnabledFor=false` → mod off entirely for that biome.
  - `isEnabledFor=true, isInfiniteInBiome=true` → mod's spread rule
    runs, but vanilla source conversion still works (oceans, rivers).
  - `isEnabledFor=true, isUnfillableInBiome=true` → mod's spread
    rule runs, plus desert-style void-at-sea-level.
- The whitelist falls back to fluid-agnostic rules (`None →
  biomes`), so a config of "rule applies to all fluids in these
  biomes" is one keystroke shorter than spelling out each fluid.

**Negative:**
- The defaults ship as "do nothing" (whitelists are empty by
  default), so the mod is *invisible* until configured. New users
  who don't read the docs may install the mod, see vanilla water,
  and conclude the mod is broken. Mitigated by the README and config
  comments; not eliminated.
- Three separate biome lists in the config file is a lot of cognitive
  load. Some users will conflate them.
- Per-tick biome lookup is cheap but non-zero (cached `WorldContext`
  + `Level.getBiome(pos)`). A pathological case (every fluid tick is
  enabled, biome boundary right next to a flowing cell) does
  per-tick `getBiome` calls. Not currently a measured issue.

**Neutral:**
- The `Level → WorldContext` `WeakHashMap` is an implementation
  detail of the cache. In practice the `Level` is referenced for the
  dimension's lifetime, so the weak reference doesn't help much; it
  exists for cleanliness, not memory pressure.
