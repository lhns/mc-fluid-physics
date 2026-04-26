# docs/

Notes that don't fit in `build.gradle` comments — gnarly toolchain workarounds, integration traps, and other context where the "why" is needed to keep someone from naively cleaning up the "what".

| Doc | Subject |
|---|---|
| [fabric-1.20.1-build.md](fabric-1.20.1-build.md) | The four-constraint Scala/Zinc setup in `fabric-1.20.1/build.gradle` and why each line is load-bearing |
| [neoforge-1.21.1-cats-integration.md](neoforge-1.21.1-cats-integration.md) | Why `neoforge-1.21.1` shades cats into a private namespace via shadow plugin instead of using SCF's bundled cats; JPMS module-layer traps. Applies to the SCF-based `multi-1.21.1` branch — superseded on `mcdp-1.21.1` |
| [mcdp-port.md](mcdp-port.md) | The `mcdp-1.21.1` branch: replacing SCF with mcdp (MC Dependency Provider) on both Fabric and NeoForge. Build wiring, per-loader changes, CI |
