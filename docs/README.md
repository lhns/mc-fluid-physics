# docs/

Notes that don't fit in `build.gradle` comments — gnarly toolchain workarounds, integration traps, and other context where the "why" is needed to keep someone from naively cleaning up the "what".

| Doc | Subject |
|---|---|
| [fabric-1.20.1-build.md](fabric-1.20.1-build.md) | The four-constraint Scala/Zinc setup in `fabric-1.20.1/build.gradle` and why each line is load-bearing |
| [neoforge-1.21.1-cats-integration.md](neoforge-1.21.1-cats-integration.md) | Why `neoforge-1.21.1` shades cats into a private namespace via shadow plugin instead of using SCF's bundled cats; JPMS module-layer traps. Applies to the SCF-based `multi-1.21.1` branch — superseded on `mcdp-1.21.1` |
| [mcdp-port.md](mcdp-port.md) | The `mcdp-1.21.1` branch: replacing SCF with mcdp (MC Dependency Provider) on both Fabric and NeoForge. Build wiring, per-loader changes, CI |
| [water-flow-algorithm.md](water-flow-algorithm.md) | The water-flow / fluid-physics algorithm: vanilla MC 1.21.1 recap, the four mixin injects in `FlowableFluidMixin`, `FluidSourceFinder` deep-dive, infinite-source plumbing, config quick-reference |
| [adr/](adr/README.md) | Architecture decision records for the six load-bearing choices in the water-flow algorithm (find-and-move conservation, direction gating, ThreadLocal context, spring-as-marker, source-search rules, biome-gated config) |
