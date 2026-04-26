# mcdp port (1.21.1)

How the `mcdp-1.21.1` branch replaces *Scalable Cat's Force* on both Fabric and NeoForge with a single language provider — [mcdp](https://gitea.lhns.de/lhns/mc-dependency-provider), the MC Dependency Provider.

## Why

The 1.21.1 multi-loader port shipped with two different Scala 3 language providers, both maintained by Kotori316:

- **Fabric:** SCF Fabric (`com.kotori316:scalable-cats-force-fabric:3.1.8`), modid `kotori_scala`. Bundled (`include`) into the mod jar.
- **NeoForge:** SCF with the `:with-library` classifier (`com.kotori316:scalablecatsforce-neoforge:3.5.0-build-1:with-library`). Required because NeoForge's JPMS module layer rejects `cats-kernel`'s keyword sub-packages (`byte`, `int`, …). SCF's workaround is a forked cats with renamed packages — which then breaks compatibility with circe (compiled against vanilla cats), forcing us to shade vanilla `cats-core_3:2.13.0` into a private namespace inside our own mod jar with eight `cats.kernel.instances.{byte,char,…}` → `…shaded.cats.kernel.instances._{byte,_char,…}` relocations.

The whole stack is documented in [neoforge-1.21.1-cats-integration.md](neoforge-1.21.1-cats-integration.md) — eight relocations, one shadowJar, two parallel cats copies in memory at runtime.

mcdp solves both problems with one mechanism: each opted-in mod gets its own `URLClassLoader`. Maven deps stay in that loader's *unnamed* JPMS module (so the keyword check never fires), and each mod sees its own copy of every dep (so version conflicts can't happen). No bytecode rewriting, no library forks, no relocations. One language provider for both loaders.

## Build wiring

mcdp lives at `C:/Users/pierr/Documents/git/mc-scala` (sibling checkout, repo `gitea.lhns.de/lhns/mc-dependency-provider`). The 1.21.1 build pulls it in via composite include:

- **`settings-1.21.1.gradle`** — `pluginManagement { includeBuild('../mc-scala') }` so the Gradle plugin id `de.lhns.mcdp` resolves without a publish.
- **`fabric-1.21.1/build.gradle` & `neoforge-1.21.1/build.gradle`** — `mavenLocal()` is added to the per-subproject repositories. The plugin is composite-built, but the *runtime* artifacts (`de.lhns.mcdp:mcdp-fabric`, `de.lhns.mcdp:mcdp-neoforge`) are shadow jars and have to be published to mavenLocal explicitly. Workflow before any rebuild:

  ```
  ../mc-scala/gradlew :fabric:publishToMavenLocal :neoforge:publishToMavenLocal
  ```

  (mc-scala's ADR-0012 explains why composite substitution can't replace the shadow jar — Fabric's `ClasspathModCandidateFinder` rejects the per-subproject classes-dir output.)

## Per-loader changes

### Fabric (`fabric-1.21.1/build.gradle`, `fabric-1.21.1/src/main/resources/fabric.mod.json`)

- Drop `com.kotori316:scalable-cats-force-fabric` (both `modImplementation` and `include`).
- Drop the `Kotori316 Maven` repository entry.
- Drop the entire `shadow` configuration that bundled circe into the mod jar (and the matching `jar { from { configurations.shadow.collect ... } }` block).
- Add `id 'de.lhns.mcdp'` in the `plugins {}` block (composite-resolved).
- Add `modImplementation 'de.lhns.mcdp:mcdp-fabric:0.1.0-SNAPSHOT'`.
- Move `scala3-library_3`, `circe-*`, and `cats-core_3` from `compileOnly` / `shadow` to `mcdepImplementation` — that's mcdp's opt-in bucket. The Gradle plugin walks its transitive closure and emits each library into the manifest.
- Add `mcdepprovider { lang.set('scala') }`.
- `fabric.mod.json`: `"adapter": "scala"` → `"adapter": "mcdepprovider"`, and depends `"kotori_scala"` → `"mcdepprovider"`.

### NeoForge (`neoforge-1.21.1/build.gradle`, `neoforge-1.21.1/src/main/resources/META-INF/neoforge.mods.toml`)

- Drop `com.kotori316:scalablecatsforce-neoforge:*:with-library`.
- Drop the `com.gradleup.shadow` plugin entirely along with the entire `shadowJar` block (eight keyword-package relocations + catch-all `cats` → shaded + scala excludes + service merging).
- Drop the `tasks.named('jar') { enabled = false }` line — the plain `jar` task is the artifact again.
- Drop the `additionalRuntimeClasspath` glue that fed shaded cats/circe into MDG dev runs.
- Drop the `Kotori316 Maven` repository entry.
- Add `id 'de.lhns.mcdp'` to the `plugins {}` block.
- Add `implementation 'de.lhns.mcdp:mcdp-neoforge:0.1.0-SNAPSHOT'`.
- Move `scala3-library_3`, `circe-*`, and `cats-core_3` from `compileOnly` / `shadow` to `mcdepImplementation`. No `excludeGroup` / platform-enumeration bookkeeping needed — the plugin auto-subtracts platform-provided artifacts.
- Add `mcdepprovider { lang.set('scala') }`.
- `neoforge.mods.toml`: `modLoader = "kotori_scala"` → `modLoader = "mcdepprovider"`. **No** `[modproperties]` block — mcdp rediscovers `@Mod`-annotated classes via `ModFileScanData.getAnnotatedBy(Mod.class)` exactly like vanilla javafml.

`FluidPhysicsNeoForge.scala` is unchanged: mcdp's NeoForge loader matches FML's default `(IEventBus)` constructor shape directly.

## Runtime contract

mcdp is a separate mod, not bundled. Drop **both** jars into `mods/`:

- Fabric: `fluidphysics-…+fabric-1.21.1.jar` + `mcdp-fabric-0.1.0-SNAPSHOT.jar` (built from mc-scala via `:fabric:shadowJar`).
- NeoForge: `fluidphysics-…+neoforge-1.21.1.jar` + `mcdp-neoforge-0.1.0-SNAPSHOT.jar` (built from mc-scala via `:neoforge:shadowJar`).

On first launch, mcdp downloads each `mcdepImplementation` artifact (Scala stdlib, cats, circe, transitive deps) from Maven Central into `~/.cache/mc-lib-provider/libs/<sha>.jar`. Net access is required only for the first run; subsequent runs hit the cache.

A built fluidphysics jar contains `META-INF/mcdepprovider.toml` listing each declared dependency's coords, URL, and SHA-256. mcdp reads that manifest at boot, verifies SHAs against the cache, and serves each lib through the per-mod `URLClassLoader`. The keyword `cats.kernel.instances.byte` package never touches JPMS validation.

## CI

`.github/workflows/build.yml` (modern job) clones mc-dependency-provider into a sibling directory and runs `:fabric:publishToMavenLocal :neoforge:publishToMavenLocal` before `./gradlew-1.21.1 build`. If the gitea.lhns.de host is down, CI fails — same as how the legacy `multi-1.21.1` branch fails when `maven.kotori316.com` is unreachable.

## Files touched on this branch

```
.github/workflows/build.yml                     clone+publish mcdp first
common-1.21.1/build.gradle                       cats version sourced from props
common-1.21.1/gradle.properties                  cats_version
docs/mcdp-port.md                                this file
docs/README.md                                   index update
fabric-1.21.1/build.gradle                       drop SCF, add mcdp + mcdepImplementation
fabric-1.21.1/gradle.properties                  drop slp_fabric_version, add mcdp_version + cats_version
fabric-1.21.1/src/main/resources/fabric.mod.json adapter + depends
neoforge-1.21.1/build.gradle                     drop SCF + shadow, add mcdp + mcdepImplementation
neoforge-1.21.1/gradle.properties                drop scf_neoforge_version, add mcdp_version + cats_version
neoforge-1.21.1/src/main/resources/META-INF/neoforge.mods.toml   modLoader
settings-1.21.1.gradle                           includeBuild('../mc-scala')
```

## Out of scope

- A non-snapshot mcdp release. v0.1.0 is not on Maven Central yet; composite + mavenLocal is the only resolution path.
- Removing `docs/neoforge-1.21.1-cats-integration.md`. That doc still describes the SCF setup on `multi-1.21.1`, which is the active mainline branch until mcdp graduates. Once mcdp lands on master, that doc can go.
- Legacy 1.15.2–1.18.2 modules and `fabric-1.20.1`. Those use Scala 2.13 + the legacy Gradle 7.3 wrapper and are unrelated to mcdp.
