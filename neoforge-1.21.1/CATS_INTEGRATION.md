# Cats integration notes — NeoForge 1.21.1 build

This module shades `cats.*` into a mod-private namespace (`de.lolhens.fluidphysics.shaded.cats.*`) via the Gradle Shadow plugin. This document explains why — because the rationale isn't obvious from the build.gradle alone and any refactor of the cats handling needs to understand both the JPMS rules at play and the non-trivial shape of Kotori's cats fork that SCF ships.

## TL;DR

- Our mod depends on circe for config parsing. Circe depends on cats.
- At runtime, ScalableCatsForce (SCF) is the canonical NeoForge-1.21.1 Scala language provider and bundles its own cats — but a **forked** cats with JPMS-driven renames that don't match what circe's bytecode calls.
- Bundling vanilla cats in the mod jar hits two JPMS errors in NeoForge's module loader (`ResolutionException`, `Invalid package name: 'byte'`).
- The fix: bundle vanilla cats under a project-private package, and let shadow rewrite every `cats.*` reference in circe's bytecode (and our own) to that private path. SCF's cats stays untouched for other mods; we're self-contained.

## Why SCF's cats won't satisfy circe

SCF's `scalablecatsforce-neoforge:3.5.0-build-1:with-library` jar has a cats that's been patched in two ways to make it JPMS-loadable inside NeoForge's module layers:

1. **`cats.instances.package$*` object renames.** Vanilla cats has lowercase objects inside the `cats.instances` package: `package$either$`, `package$list$`, `package$int$`, … Kotori renamed them to PascalCase with an `I` suffix: `package$EitherI$`, `package$ListI$`, `package$IntI$`, … — about 45 objects. Not universal (`package$all$` stayed lowercase).

2. **`cats.kernel.instances.*` sub-package flattening.** Vanilla cats-kernel has sub-packages literally named `byte`, `char`, `short`, `int`, `long`, `float`, `double`, `boolean` — those are Java keywords, and JPMS refuses to form a module descriptor for any jar that declares them as packages (`Invalid package name: 'byte' is not a Java identifier`). Kotori dissolved these sub-packages entirely — their flat `cats.kernel.instances` has class names like `BooleanBounded`, `BigDecimalOrder`, etc. at the package root. Structural reorganisation, not a pure rename.

Circe, being a standard library on Maven Central, was compiled against **vanilla** cats. Its bytecode hardcodes references like `cats/instances/package$either$` and whatever it calls in `cats.kernel.instances.byte.*` — **none of which exist in SCF's cats**. Running circe against SCF's cats directly produces runtime `NoClassDefFoundError`s.

## Why the obvious workarounds fail

Tried in order during the investigation; each is a dead end:

| Attempt | What happens |
|---|---|
| Pin circe to an older version (`0.14.10` instead of transitively-bumped `0.14.13`) | No change — both versions reference `cats.instances.package$either$` at the same bytecode offset. This is an API-surface mismatch, not a version-drift issue. |
| Bundle vanilla cats at `cats/**` alongside circe in our jar | JPMS module resolver fails at layer construction: `Module fluidphysics contains package cats.kernel.instances, module kotori_scala exports package cats.kernel.instances to fluidphysics`. NeoForge's `SecureJarHandler` enforces "one module owns one package" across the layer hierarchy. |
| JiJ vanilla cats into `META-INF/jarjar/` | Same layer-resolution crash — NeoForge treats JiJ entries as sibling JPMS modules, doesn't fix ownership. |
| Strip the 8 keyword-named sub-packages (`byte`, `char`, …) from the bundled cats before packaging | Passes the keyword check but still hits the cross-layer `cats.kernel.instances` split-package error. The container package is what's split, not just the keyword children. |
| Upgrade SCF to 3.7.x (which ships a newer cats-core with different renames) | 3.7.x calls `FMLLoader.getCurrent()`, a method not present in NeoForge 21.1.77. SCF 3.7.x targets NeoForge 21.3+ / Minecraft 1.21.3+ — incompatible with 1.21.1. |

## What works: shadow relocation

Shadow plugin rewrites bytecode references everywhere in the output jar — including our own compiled Scala code AND circe's pre-compiled classes. Relocating `cats` to a private namespace achieves:

- Circe's internal call to `cats.instances.package$either$` → rewritten to `de.lolhens.fluidphysics.shaded.cats.instances.package$either$` → served by our bundled vanilla cats → works.
- Our own `import cats.syntax.either.*` → rewritten the same way → served by our shaded copy → works.
- No module-layer conflict: our module exports `de.lolhens.fluidphysics.shaded.cats.*` (private), SCF's module exports `cats.*` (standard). Different package names, no overlap.
- No keyword package problem: the 8 offending sub-packages get additional specific relocations (`cats.kernel.instances.byte` → `cats.kernel.instances._byte`, etc.) that rename them away from Java keywords.

The matching block in `build.gradle`:

```gradle
shadowJar {
    configurations = [project.configurations.shadow]
    archiveClassifier.set('')   // replace default jar output
    exclude 'scala/**'          // SCF provides scala stdlib; duplicates = crash
    exclude 'scala3/**'

    // Specific overrides for the 8 keyword-named sub-packages. Shadow prefers
    // longer-prefix matches, so these are applied before the general `cats` rule.
    relocate 'cats.kernel.instances.byte',    'de.lolhens.fluidphysics.shaded.cats.kernel.instances._byte'
    relocate 'cats.kernel.instances.char',    'de.lolhens.fluidphysics.shaded.cats.kernel.instances._char'
    relocate 'cats.kernel.instances.short',   'de.lolhens.fluidphysics.shaded.cats.kernel.instances._short'
    relocate 'cats.kernel.instances.int',     'de.lolhens.fluidphysics.shaded.cats.kernel.instances._int'
    relocate 'cats.kernel.instances.long',    'de.lolhens.fluidphysics.shaded.cats.kernel.instances._long'
    relocate 'cats.kernel.instances.float',   'de.lolhens.fluidphysics.shaded.cats.kernel.instances._float'
    relocate 'cats.kernel.instances.double',  'de.lolhens.fluidphysics.shaded.cats.kernel.instances._double'
    relocate 'cats.kernel.instances.boolean', 'de.lolhens.fluidphysics.shaded.cats.kernel.instances._boolean'

    relocate 'cats', 'de.lolhens.fluidphysics.shaded.cats'
}

tasks.named('jar') { enabled = false }
tasks.named('assemble') { dependsOn tasks.named('shadowJar') }
```

`io/circe/**` stays at its standard path — only `cats.*` is renamed. Circe itself gets no namespace rewrite.

## Jar shape expected after a clean build

| Path | Count | Notes |
|---|---|---|
| `de/lolhens/minecraft/fluidphysics/**` | (our code) | cats refs inside rewritten to shaded path |
| `io/circe/**` | ~802 | Unchanged path; cats refs inside rewritten to shaded path |
| `de/lolhens/fluidphysics/shaded/cats/**` | ~4242 | Bundled vanilla cats-core 2.13.0 + cats-kernel 2.13.0 |
| `de/lolhens/fluidphysics/shaded/cats/kernel/instances/_byte/`, `_char/`, … | 8 × 4 = 32 | Renamed keyword packages |
| `cats/**` | 0 | Fully relocated — no top-level cats in our jar |
| `scala/**` | 0 | SCF is the sole scala provider |

Jar size: approximately **11.7 MB** (most of it is the bundled cats).

## Alternatives and when to consider them

### Drop circe entirely

Cleanest long-term fix. Replace circe with `com.typesafe:config` (pure Java, ~300 KB, no cats dependency) plus hand-written per-field decoders using a `.getOrElse(default.field)` pattern. Rough scope: 150–200 lines of rewrites in `common-1.21.1/src/main/scala/de/lolhens/minecraft/fluidphysics/config/Config.scala` and `FluidPhysicsConfig.scala`.

Benefits: no cats dependency at all, jar shrinks to ~2.7 MB, immune to any future cats / circe / SCF version-alignment incidents.

Consider when: the 11.7 MB size starts mattering, OR another integration incident comes up that would have been avoided by not having cats in the jar.

### Bytecode-rewrite circe to use Kotori's names

Let SCF serve cats; rewrite circe's bytecode to call `cats.instances.package$EitherI$` instead of `cats.instances.package$either$`. Would remove the bundled cats entirely (save ~9 MB).

Cost: ~50 line shadow `relocate` block for the `cats.instances.*` object renames, plus reverse-engineering the `cats.kernel.instances.*` sub-package flattening class by class. Fragile across cats / SCF version bumps — every upstream release requires re-verifying the mapping.

Not currently worth it; documented for completeness.

## Unrelated but nearby: dev-mode run tasks

`./gradlew-1.21.1 :neoforge-1.21.1:runData` / `runGameTestServer` / `runClient` **do not work** with the current setup. Circe ends up in a different JPMS layer than our mod in MDG's dev-mode module structure, and our named mod module can't `read` circe's classes there. Symptom during boot: `ClassNotFoundException: io.circe.Encoder` (or similar).

This is a separate problem from the cats integration — dev-mode runs fail even when the production jar loads cleanly. The production jar (dropped into a real NeoForge instance) is unaffected because NeoForge's production `SecureJarHandler` handles module layers differently from what MDG does in dev mode.

Consequences:
- GameTests in `src/main/java/.../gametest/FluidFlowGameTests.java` compile cleanly but haven't been executed live. They'd need to run in a real game instance, not dev mode.
- Datagen (`runData`) can't be used to regenerate resources locally.

Fixing dev-mode runs would likely require either writing an explicit `module-info.java` for the mod or restructuring how circe gets onto the dev classpath. Not blocking for a real release.

## Verification after future rebuilds

```bash
./gradlew-1.21.1 :neoforge-1.21.1:build --no-daemon

JAR=neoforge-1.21.1/build/libs/fluidphysics-*+neoforge-1.21.1.jar
unzip -l "$JAR" | grep -cE '^\s*[0-9]+.*\scats/'                                      # expect 0
unzip -l "$JAR" | grep -cE '^\s*[0-9]+.*\sde/lolhens/fluidphysics/shaded/cats/'       # expect ~4000
unzip -l "$JAR" | grep 'cats/instances/package\$either\$'                             # expect 1 hit under shaded
unzip -l "$JAR" | grep -cE '^\s*[0-9]+.*\scats/kernel/instances/byte/'                # expect 0 (renamed to _byte)
unzip -l "$JAR" | grep -cE '^\s*[0-9]+.*\sscala/'                                     # expect 0
```

Then drop the jar into a real NeoForge 1.21.1 instance alongside `scalablecatsforce-neoforge-3.5.0-build-1-with-library.jar` from <https://maven.kotori316.com>. The game should reach the title screen without a `ResolutionException` or `NoClassDefFoundError: cats/...` in the log.
