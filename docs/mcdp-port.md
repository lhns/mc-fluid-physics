# mcdp port (1.21.1)

How the `mcdp-1.21.1` branch replaces *Scalable Cat's Force* on both Fabric and NeoForge with a single language provider — [mcdp](https://gitea.lhns.de/lhns/mc-dependency-provider), the MC Dependency Provider.

## Why

The 1.21.1 multi-loader port shipped with two different Scala 3 language providers, both maintained by Kotori316:

- **Fabric:** SCF Fabric (`com.kotori316:scalable-cats-force-fabric:3.1.8`), modid `kotori_scala`. Bundled (`include`) into the mod jar.
- **NeoForge:** SCF with the `:with-library` classifier (`com.kotori316:scalablecatsforce-neoforge:3.5.0-build-1:with-library`). Required because NeoForge's JPMS module layer rejects `cats-kernel`'s keyword sub-packages (`byte`, `int`, …). SCF's workaround is a forked cats with renamed packages — which then breaks compatibility with circe (compiled against vanilla cats), forcing us to shade vanilla `cats-core_3:2.13.0` into a private namespace inside our own mod jar with eight `cats.kernel.instances.{byte,char,…}` → `…shaded.cats.kernel.instances._{byte,_char,…}` relocations.

The whole stack is documented in [neoforge-1.21.1-cats-integration.md](neoforge-1.21.1-cats-integration.md) — eight relocations, one shadowJar, two parallel cats copies in memory at runtime.

mcdp solves both problems with one mechanism: each opted-in mod gets its own `URLClassLoader`. Maven deps stay in that loader's *unnamed* JPMS module (so the keyword check never fires), and each mod sees its own copy of every dep (so version conflicts can't happen). No bytecode rewriting (for deps), no library forks, no relocations. One language provider for both loaders.

## Build wiring

mcdp lives at `C:/Users/pierr/Documents/git/mc-scala` (sibling checkout, repo `gitea.lhns.de/lhns/mc-dependency-provider`). The 1.21.1 build pulls it in via composite include:

- **`settings-1.21.1.gradle`** — `pluginManagement { includeBuild('../mc-scala') }` so the Gradle plugin id `de.lhns.mcdp` resolves without a publish, plus a top-level `includeBuild('../mc-scala')` so module substitution maps `de.lhns.mcdp:mcdp` to the `:mcdp` subproject (per ADR-0016).
- **`build-1.21.1.gradle`** — declares `fabric-loom` and `de.lhns.mcdp` in a shared root `plugins {}` block with `apply false`. Without this, `:common-1.21.1` and `:fabric-1.21.1` would each load loom into a separate classloader and `RemapJarTask` blows up with `cannot cast BuildSharedServiceManager$Inject to BuildSharedServiceManager`.
- **`fabric-1.21.1/build.gradle` & `neoforge-1.21.1/build.gradle`** — `mavenLocal()` is added to the per-subproject repositories. The plugin is composite-built, but the *runtime* artifact `de.lhns.mcdp:mcdp` is a shadow jar (per ADR-0012 in mc-scala/docs/) and has to be published to mavenLocal explicitly. Workflow before any rebuild:

  ```
  ../mc-scala/gradlew :mcdp:publishToMavenLocal
  ```

  ADR-0016 unified the two prior platform-specific artifacts (`mcdp-fabric`, `mcdp-neoforge`) into a single jar that runs unmodified on either loader. There's only one publishing target now.

## Per-loader changes

### Fabric (`fabric-1.21.1/build.gradle`, `fabric-1.21.1/src/main/resources/fabric.mod.json`)

- Drop `com.kotori316:scalable-cats-force-fabric` (both `modImplementation` and `include`).
- Drop the `Kotori316 Maven` repository entry.
- Drop the entire `shadow` configuration that bundled circe into the mod jar (and the matching `jar { from { configurations.shadow.collect ... } }` block).
- `apply plugin: 'de.lhns.mcdp'` (composite-resolved; declared in `build-1.21.1.gradle` with `apply false`).
- Add `modImplementation 'de.lhns.mcdp:mcdp:0.1.0-SNAPSHOT'` (unified artifact, ADR-0016).
- Move `scala3-library_3`, `circe-*`, and `cats-core_3` from `compileOnly` / `shadow` to `mcdepImplementation` — that's mcdp's opt-in bucket. The Gradle plugin walks its transitive closure and emits each library into the manifest.
- `mcdepprovider { lang.set('scala'); bridges { bridgePackage.set(...) }; sharedPackages.add(...) }` — see "Mixin auto-bridge codegen" below.
- `fabric.mod.json`: `"adapter": "scala"` → `"adapter": "mcdepprovider"`, and depends `"kotori_scala"` → `"mcdepprovider"`.

### NeoForge (`neoforge-1.21.1/build.gradle`, `neoforge-1.21.1/src/main/resources/META-INF/neoforge.mods.toml`)

- Drop `com.kotori316:scalablecatsforce-neoforge:*:with-library`.
- Drop the `com.gradleup.shadow` plugin entirely along with the entire `shadowJar` block (eight keyword-package relocations + catch-all `cats` → shaded + scala excludes + service merging).
- Drop the `tasks.named('jar') { enabled = false }` line — the plain `jar` task is the artifact again.
- Drop the `additionalRuntimeClasspath` glue that fed shaded cats/circe into MDG dev runs.
- Drop the `Kotori316 Maven` repository entry.
- Add `id 'de.lhns.mcdp'` to the `plugins {}` block.
- Add `implementation 'de.lhns.mcdp:mcdp:0.1.0-SNAPSHOT'` (unified artifact, ADR-0016).
- Move `scala3-library_3`, `circe-*`, and `cats-core_3` from `compileOnly` / `shadow` to `mcdepImplementation`. No `excludeGroup` / platform-enumeration bookkeeping needed — the plugin auto-subtracts platform-provided artifacts.
- `mcdepprovider { lang.set('scala'); bridges { bridgePackage.set(...) }; sharedPackages.add(...) }` — see "Mixin auto-bridge codegen" below.
- `neoforge.mods.toml`: `modLoader = "kotori_scala"` → `modLoader = "mcdepprovider"`. **No** `[modproperties]` block — mcdp rediscovers `@Mod`-annotated classes via `ModFileScanData.getAnnotatedBy(Mod.class)` exactly like vanilla javafml.

`FluidPhysicsNeoForge.scala` keeps a `(IEventBus)` ctor: mcdp's NeoForge loader builds a context bag of `[IEventBus, ModContainer, Dist]` with subset matching (ADR-0017), so `()`, `(IEventBus)`, `(ModContainer)`, or any subset/superset all resolve.

## Mixin auto-bridge codegen

The mod has 9 mixins (~447 LOC) that call into Scala helpers (`FluidPhysicsMod`, `FluidPhysicsConfig`, `FluidIsInfinite`, `FluidSourceFinder`, `SpringBlockFeature`). With mcdp's per-mod classloader, those classes live in a child loader that game-layer mixin handlers can't see directly — without intervention every mixin handler crashes with `NoClassDefFoundError: scala/runtime/LazyVals$`.

ADR-0018's auto-bridge codegen handles this transparently. The mixins stay as plain Sponge-Common-style code calling Scala objects directly; the gradle plugin's `:generateMcdpBridges` task scans compiled mixin bytecode, synthesizes a bridge interface + impl + manifest per cross-classloader call site, and rewrites the mixin's method bodies to dispatch through the bridge. Codegen is on by default — there is nothing to add to the build for it to work in the typical case.

Two consumer-side overrides this mod needs:

- **`bridges { bridgePackage.set(...) }`.** mcdp's default bridge package is `<group>.<projectName>.mcdp_bridges`. With Gradle project names `fabric-1.21.1` / `neoforge-1.21.1`, the dots in the MC version turn `21` and `1` into illegal package segments. Pin a valid identifier: `de.lolhens.minecraft.fluidphysics.{fabric,neoforge}.mcdp_bridges`.
- **`sharedPackages.add('de.lolhens.minecraft.fluidphysics.mixin.')`.** `FlowableFluidMixin implements FlowableFluidAccessor` — both are mixins in the same package. ADR-0018 explicitly leaves class-header references unrewritten; the cross-classloader cure is to load the mixin package parent-first (game-layer classloader). Safe here because every type in `…fluidphysics.mixin.` is itself a mixin and only ever referenced from other mixins.

No mod source changes for this — all 9 mixins remain as written. The auto-bridge codegen does the rewrite at build time.

## Runtime contract

mcdp is a separate mod, not bundled. Drop **two** jars into `mods/`:

- `fluidphysics-…+{fabric,neoforge}-1.21.1.jar`
- `mcdp-0.1.0-SNAPSHOT.jar` (the unified jar built from mc-scala via `:mcdp:shadowJar`; one jar runs unmodified on Fabric or NeoForge).

On first launch, mcdp downloads each `mcdepImplementation` artifact (Scala stdlib, cats, circe, transitive deps) from Maven Central into `~/.cache/mc-lib-provider/libs/<sha>.jar`. Net access is required only for the first run; subsequent runs hit the cache.

A built fluidphysics jar contains:
- `META-INF/mcdepprovider.toml` — each declared `mcdepImplementation` dependency's coords, URL, and SHA-256.
- `META-INF/mcdp-bridges.toml` — every auto-generated `(mixin, field, bridgeInterface, impl)` tuple from the codegen.

mcdp reads both at boot, verifies SHAs against the cache, serves each `mcdepImplementation` lib through the per-mod `URLClassLoader`, and registers the bridge entries so each rewritten mixin's `<clinit>` can resolve its `LOGIC_*` field. The keyword `cats.kernel.instances.byte` package never touches JPMS validation.

## CI

`.github/workflows/build.yml` (modern job) clones mc-dependency-provider into a sibling directory and runs `:mcdp:publishToMavenLocal` before `./gradlew-1.21.1 build`. If the gitea.lhns.de host is down, CI fails — same as how the legacy `multi-1.21.1` branch fails when `maven.kotori316.com` is unreachable.

## Files touched on this branch

```
.github/workflows/build.yml                     clone+publish mcdp first
build-1.21.1.gradle                              shared-plugin-classloader root
common-1.21.1/build.gradle                       cats version sourced from props
common-1.21.1/gradle.properties                  cats_version
docs/mcdp-port.md                                this file
docs/README.md                                   index update
fabric-1.21.1/build.gradle                       drop SCF, add mcdp + mcdepImplementation + bridges DSL
fabric-1.21.1/gradle.properties                  drop slp_fabric_version, add mcdp_version + cats_version
fabric-1.21.1/src/main/resources/fabric.mod.json adapter + depends
neoforge-1.21.1/build.gradle                     drop SCF + shadow, add mcdp + mcdepImplementation + bridges DSL
neoforge-1.21.1/gradle.properties                drop scf_neoforge_version, add mcdp_version + cats_version
neoforge-1.21.1/src/main/resources/META-INF/neoforge.mods.toml   modLoader
neoforge-1.21.1/src/main/scala/.../FluidPhysicsNeoForge.scala    drop unused ModContainer ctor param
settings-1.21.1.gradle                           includeBuild('../mc-scala') for plugin + module
```

## Out of scope

- A non-snapshot mcdp release. v0.1.0 is not on Maven Central yet; composite + mavenLocal is the only resolution path.
- Removing `docs/neoforge-1.21.1-cats-integration.md`. That doc still describes the SCF setup on `multi-1.21.1`, which is the active mainline branch until mcdp graduates. Once mcdp lands on master, that doc can go.
- Legacy 1.15.2–1.18.2 modules and `fabric-1.20.1`. Those use Scala 2.13 + the legacy Gradle 7.3 wrapper and are unrelated to mcdp.
