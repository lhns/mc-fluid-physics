# fabric-1.20.1 build notes

The Scala / Zinc setup in `fabric-1.20.1/build.gradle` looks gratuitous. It isn't. Every line pins a precise constraint, and dropping any one of them breaks the build at a different layer. Read this before "cleaning up" the build file.

## TL;DR — four interlocking constraints

| # | Line in build.gradle | Why it has to be exactly this |
|---|---|---|
| 1 | `force scala-compiler/library/reflect:2.13.12` | scala-compiler ≤ 2.13.8 has [scala/bug#12783](https://github.com/scala/bug/issues/12783) — its `MethodParameters` attribute parser crashes with `bad constant pool index: 0` when reading 1.20.1 / Fabric API class files. Fixed in 2.13.9+. |
| 2 | `dependencies { zinc 'org.scala-sbt:zinc_2.12:1.3.5' }` | Gradle 7.3.1's scala plugin hardcodes `DEFAULT_SCALA_ZINC_VERSION = "2.12"` and registers an `afterResolve` callback inside `defaultDependencies` that throws if scala-library doesn't `startsWith("2.12")`. `defaultDependencies` only fires when the `zinc` config is empty, so providing an explicit zinc dep bypasses both the safeguard and the default. `scala.zincVersion = '…'` does **not** silence the safeguard — the check never re-reads that property. |
| 3 | Explicit zinc pinned to **1.3.5**, not newer | Zinc 1.4.0 migrated `AnalyzingCompiler.compileSources(Iterable, File, Iterable, …)` to use `java.nio.file.Path` instead of `java.io.File`. Gradle 7.3.1's compiled scala plugin still calls the old `File` overload — any Zinc ≥ 1.4 → `NoSuchMethodError` at compile time. 1.3.5 is the highest version with the old API. |
| 4 | Force is scoped via `configurations.matching { it.name != 'zinc' }`, not `configurations.all` | `zinc_2.12:1.3.5` is itself a Scala 2.12 program. If the global force downgrades-or-upgrades **its** scala-library to 2.13.12, Zinc's own bytecode crashes with `NoClassDefFoundError: scala/Serializable` — the trait existed in 2.12 and was removed in 2.13. Force user-code configurations to 2.13.12; leave Zinc's runtime classpath on whatever 2.12.x it brings. |

Each constraint exists because the previous one is non-negotiable. The chain isn't optional layers — it's a single load-bearing structure.

## What NOT to do

- **Don't bump the root Gradle wrapper to 7.5+ to dodge constraint #2.** ForgeGradle 5.1 (used by every `forge-1.*` module) calls `AbstractArtifactRepository.<init>(ObjectFactory)`, a Gradle internal removed between 7.3 and 7.6. The wrapper must stay at 7.3.1 for the forge modules to even configure. We tried this; CI broke immediately.
- **Don't bump `fabric-language-scala`.** The newest version on `maven.fabricmc.net` is `1.1.0+scala.2.13.6` (Sep 2021). No version with Scala ≥ 2.13.9 exists; the artifact is abandoned. Plausibly-named candidates like `1.7.8+scala.2.13.10` are fictional.
- **Don't drop the explicit zinc dep "for cleanliness".** It's the keystone — without it the `startsWith("2.12")` safeguard re-engages and rejects scala-library 2.13.12.
- **Don't widen the force back to `configurations.all`.** Zinc's runtime breaks (constraint #4).
- **Don't fall for `scala { zincVersion = '…' }`.** It looks like the natural fix and even appears to compile, but it cannot silence the safeguard — Gradle 7.3/7.4's check is hardcoded against the *scala* binary version constant, not the zinc property.

## What success looks like

```
> Task :fabric-1.20.1:compileScala
[Warn] : 2 feature warnings; …
> Task :fabric-1.20.1:processResources
> Task :fabric-1.20.1:classes
> Task :fabric-1.20.1:jar
> Task :fabric-1.20.1:remapJar
```

No errors mentioning "default Zinc version", `compileSources`, or `scala/Serializable`.

## When this stack can be simplified

If any of these change upstream, the workarounds collapse and most of the build.gradle complexity can come out:

- **Someone publishes `fabric-language-scala:>=…+scala.2.13.9` on `maven.fabricmc.net`.** Then drop everything in this stack: just bump the dependency. The simplest path to make this happen is forking upstream and publishing — the upstream POM is trivial.
- **The `forge-1.*` modules get retired or migrated off ForgeGradle 5.1.** Then the root wrapper can move to Gradle 7.5+, the hardcoded safeguard becomes `startsWith("2.13")`, the explicit zinc dep is no longer needed, and only constraint #1 remains.
- **Minecraft 1.20.1 stops being a target.** Then the whole module goes.

Until one of these happens, leave the four lines alone.

## Cross-references

- `gradle/wrapper/gradle-wrapper.properties` — pinned to 7.3.1, see "What NOT to do" above for why
- Gradle scala plugin source: `subprojects/scala/src/main/java/org/gradle/api/plugins/scala/ScalaBasePlugin.java` at tag `v7.3.1`, lines ~139–160 — the `defaultDependencies` / `afterResolve` block this build bypasses
- Zinc source: `internal/zinc-compile-core/src/main/scala/sbt/internal/inc/AnalyzingCompiler.scala` — `compileSources` signature changed in the 1.4.0 release (File → Path migration)
