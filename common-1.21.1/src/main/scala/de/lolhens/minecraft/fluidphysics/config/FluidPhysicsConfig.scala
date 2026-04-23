package de.lolhens.minecraft.fluidphysics.config

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod
import de.lolhens.minecraft.fluidphysics.config.Config.Commented
import de.lolhens.minecraft.fluidphysics.config.Config.Implicits.{*, given}
import de.lolhens.minecraft.fluidphysics.config.FluidPhysicsConfig.{FluidId, FluidRuleConfig, RainRefillConfig, SpringConfig, registryGetOption}
import io.circe.derivation.ConfiguredCodec
import io.circe.syntax.*
import io.circe.{Codec, Decoder, Encoder, Json}
import net.minecraft.core.Registry
import net.minecraft.core.registries.{BuiltInRegistries, Registries}
import net.minecraft.core.{BlockPos, Holder}
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.BiomeTags
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.material.{Fluid, Fluids}

import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

case class FluidPhysicsConfig(
  updateConfig: Commented[Boolean] = true ->
    "Automatically update config when the structure changes in new versions",

  fluidWhitelist: Commented[Option[Seq[FluidId]]] = Some(Seq(Fluids.WATER, Fluids.LAVA).map(FluidId.byFluid)) -> {
    val exampleFluids = FluidId.list.asJson.hoconString
    s"Fluids that are affected by this mod\nExample:\n$exampleFluids"
  },

  fluidBlacklist: Commented[Seq[FluidId]] = Seq.empty ->
    "Fluids that are not affected by this mod",

  findSourceMaxIterations: Commented[Int] = 255 ->
    "Maximum iterations to find the fluid source block",

  findSourceMaxCheckedBlocks: Commented[Option[Int]] = Some(4095) ->
    "Maximum number of blocks to check when finding the fluid source block",

  biomeWhitelist: Commented[Option[Seq[FluidRuleConfig[ResourceLocation]]]] = None ->
    "Biomes in which fluids are affected by this mod",

  biomeBlacklist: Commented[Seq[FluidRuleConfig[ResourceLocation]]] = Seq.empty ->
    "Biomes in which fluids are not affected by this mod",

  biomeDependentFluidInfinity: Commented[Boolean] = true ->
    "This option is deprecated",

  biomeDependentFluidInfinityWhitelist: Commented[Seq[FluidRuleConfig[ResourceLocation]]] = Seq.empty -> {
    val exampleBiomes = FluidPhysicsConfig.waterBiomeExamples.asJson.hoconString
    s"Infinite fluid sources will be enabled in these biomes\nExample (river and ocean biomes):\n$exampleBiomes"
  },

  unfillableBiomeWhitelist: Commented[Option[Seq[FluidRuleConfig[ResourceLocation]]]] = Some(Seq.empty) ->
    "Biomes which will void fluid at sea level and therefore can't be filled",

  unfillableBiomeBlacklist: Commented[Seq[FluidRuleConfig[ResourceLocation]]] = Seq.empty ->
    "Biomes which will not void fluid at sea level and therefore can be filled",

  flowOverSources: Commented[Boolean] = true ->
    "Fluids will flow over source blocks",

  debugFluidState: Commented[Boolean] = false ->
    "Water will be colored depending on its fluid state",

  spring: Option[SpringConfig] = Some(SpringConfig()),

  rainRefill: Option[RainRefillConfig] = Some(RainRefillConfig())
) derives ConfiguredCodec {
  lazy val getFluidWhitelist: Set[Fluid] = {
    val fluidGroupBlacklist = fluidBlacklist.value.map(_.fluidGroup)
    fluidWhitelist.value
      .getOrElse(FluidId.list)
      .map(_.fluidGroup)
      .filterNot(fluidGroupBlacklist.contains)
      .flatMap(_.fluids)
      .toSet
  }

  /** Per-world cache keyed on biomes registry (biome set is dimension-scoped in 1.21.1). */
  case class WorldContext(biomesRegistry: Registry[Biome]) {
    lazy val getBiomeWhitelist: Map[Option[Fluid], Set[Biome]] =
      FluidRuleConfig.toMap[ResourceLocation, Biome](
        biomeWhitelist.value,
        biomeBlacklist.value,
        biomesRegistry.keySet.iterator.asScala,
        registryGetOption(biomesRegistry, _)
      )

    lazy val getBiomeDependentFluidInfinityWhitelist: Map[Option[Fluid], Set[Biome]] =
      FluidRuleConfig.toMap[ResourceLocation, Biome](
        Some(biomeDependentFluidInfinityWhitelist.value),
        Seq.empty,
        Seq.empty,
        registryGetOption(biomesRegistry, _)
      )

    lazy val getUnfillableBiomeWhitelist: Map[Option[Fluid], Set[Biome]] =
      FluidRuleConfig.toMap[ResourceLocation, Biome](
        unfillableBiomeWhitelist.value,
        unfillableBiomeBlacklist.value,
        biomesRegistry.keySet.iterator.asScala,
        registryGetOption(biomesRegistry, _)
      )
  }

  object WorldContext {
    private val weakMap = mutable.WeakHashMap.empty[Level, WorldContext]

    def apply(world: Level): WorldContext = weakMap.getOrElseUpdate(
      world,
      new WorldContext(world.registryAccess.registryOrThrow(Registries.BIOME))
    )
  }

  def getFlowOverSources: Boolean = flowOverSources.value
  def getDebugFluidState: Boolean = debugFluidState.value

  def isEnabledFor(fluid: Fluid): Boolean = getFluidWhitelist.contains(fluid)

  def isEnabledFor(fluid: Fluid, world: Level, pos: BlockPos): Boolean = {
    if (!isEnabledFor(fluid)) return false
    val whitelist = WorldContext(world).getBiomeWhitelist
    if (whitelist.isEmpty) return false
    val biome: Holder[Biome] = world.getBiome(pos)
    whitelist.get(Some(fluid))
      .orElse(whitelist.get(None))
      .exists(_.contains(biome.value))
  }

  def isInfiniteInBiome(fluid: Fluid, world: Level, pos: BlockPos): Boolean = {
    val whitelist = WorldContext(world).getBiomeDependentFluidInfinityWhitelist
    if (whitelist.isEmpty) return false
    val biome: Holder[Biome] = world.getBiome(pos)
    whitelist.get(Some(fluid))
      .orElse(whitelist.get(None))
      .exists(_.contains(biome.value))
  }

  def isUnfillableInBiome(fluid: Fluid, world: Level, pos: BlockPos): Boolean = {
    val whitelist = WorldContext(world).getUnfillableBiomeWhitelist
    if (whitelist.isEmpty) return false
    val biome: Holder[Biome] = world.getBiome(pos)
    whitelist.get(Some(fluid))
      .orElse(whitelist.get(None))
      .exists(_.contains(biome.value))
  }
}

object FluidPhysicsConfig extends Config[FluidPhysicsConfig] {
  override lazy val default: FluidPhysicsConfig = FluidPhysicsConfig()

  // Example biome names for the config comment. Previously enumerated via Biome.Category which was
  // removed in 1.18+. We use the stable vanilla IDs here — biome tags (BiomeTags.IS_OCEAN etc.)
  // drive the actual runtime matching via WorldContext.
  private def waterBiomeExamples: Seq[ResourceLocation] = Seq(
    "river", "frozen_river",
    "ocean", "deep_ocean", "cold_ocean", "deep_cold_ocean",
    "frozen_ocean", "deep_frozen_ocean",
    "lukewarm_ocean", "deep_lukewarm_ocean",
    "warm_ocean"
  ).map(ResourceLocation.withDefaultNamespace)

  override def shouldUpdateConfig(config: FluidPhysicsConfig): Boolean = config.updateConfig.value

  override def migrateConfig(config: FluidPhysicsConfig): FluidPhysicsConfig = {
    if (!config.biomeDependentFluidInfinity.value)
      config.copy(
        biomeDependentFluidInfinity = config.biomeDependentFluidInfinity.withValue(true),
        biomeDependentFluidInfinityWhitelist = config.biomeDependentFluidInfinityWhitelist.withValue(Seq.empty)
      )
    else config
  }

  override protected def codec: Codec[FluidPhysicsConfig] = makeCodec

  private def registryGetOption[A](registry: Registry[A], id: ResourceLocation): Option[A] =
    Option(registry.get(id))

  private def registryGet[A](registry: Registry[A], id: ResourceLocation): A =
    registryGetOption(registry, id)
      .getOrElse(throw new IllegalArgumentException("Registry does not contain identifier: " + id))

  case class FluidId(id: ResourceLocation) {
    lazy val fluidGroup: FluidGroup = FluidGroup.byFluid(registryGet(BuiltInRegistries.FLUID, id))
  }

  object FluidId {
    def byFluid(fluid: Fluid): FluidId = FluidId(BuiltInRegistries.FLUID.getKey(fluid))

    def list: Seq[FluidId] =
      BuiltInRegistries.FLUID.keySet.iterator.asScala.map(k => FluidId(k)).toSeq

    given codec: Codec[FluidId] = Codec.from(
      Decoder[ResourceLocation].map(FluidId(_)),
      Encoder[ResourceLocation].contramap(_.id)
    )
  }

  case class FluidGroup(fluids: Set[Fluid])

  object FluidGroup {
    def byFluid(fluid: Fluid): FluidGroup = groups(fluid)

    lazy val groups: Map[Fluid, FluidGroup] = {
      val fluids: Seq[Fluid] = BuiltInRegistries.FLUID.iterator().asScala.toSeq
      fluids
        .groupBy(fluid => fluids.find(fluid.isSame).get)
        .map { e =>
          val fluidSet: Set[Fluid] = e._2.toSet
          FluidGroup(fluidSet)
        }
        .flatMap(group => group.fluids.map(_ -> group))
        .toMap
    }
  }

  case class FluidRuleConfig[A](fluid: Option[FluidId], value: A) {
    def map[B](f: A => B): FluidRuleConfig[B] = FluidRuleConfig[B](fluid, f(value))

    lazy val rule: FluidRule[A] = FluidRule(fluid.map(_.fluidGroup), value)
  }

  object FluidRuleConfig {
    given codec[A](using decoder: Decoder[A], encoder: Encoder[A]): Codec[FluidRuleConfig[A]] = {
      val rawDecoder: Decoder[FluidRuleConfig[A]] = Decoder.instance { c =>
        for {
          fluid <- c.downField("fluid").as[Option[FluidId]]
          value <- c.downField("value").as[A]
        } yield FluidRuleConfig(fluid, value)
      }
      val rawEncoder: Encoder[FluidRuleConfig[A]] = Encoder.instance { r =>
        Json.obj(
          "fluid" -> r.fluid.asJson,
          "value" -> r.value.asJson
        )
      }
      Codec.from(
        decoder.map(FluidRuleConfig(None, _)).or(rawDecoder),
        Encoder.instance {
          case FluidRuleConfig(None, value) => encoder(value)
          case rule => rawEncoder(rule)
        }
      )
    }

    def toMap[A, B](whitelist: Option[IterableOnce[FluidRuleConfig[A]]],
                    blacklist: Seq[FluidRuleConfig[A]],
                    default: => IterableOnce[A],
                    f: A => Option[B]): Map[Option[Fluid], Set[B]] = {
      val blacklistRules = blacklist.map(_.rule)
      whitelist.fold(
        default.iterator.map(FluidRuleConfig(None, _))
      )(_.iterator)
        .map(_.rule)
        .filterNot(blacklistRules.contains)
        .flatMap(e => f(e.value).map(e.withValue))
        .toSeq
        .groupBy(_.fluidGroup)
        .flatMap {
          case (fluidGroupOption, rules) =>
            val ruleSet = rules.map(_.value).toSet
            fluidGroupOption.map(_.fluids.map(Some(_))).getOrElse(Seq(None)).map {
              _ -> ruleSet
            }
        }
    }
  }

  case class FluidRule[A](fluidGroup: Option[FluidGroup], value: A) {
    def withValue[B](value: B): FluidRule[B] = copy(value = value)
    def map[B](f: A => B): FluidRule[B] = withValue(f(value))
  }

  case class SpringConfig(
    block: Commented[ResourceLocation] = FluidPhysicsMod.SPRING_BLOCK_ID ->
      "Sets the block name which will act as a spring block. Fluid source blocks that are adjacent to spring blocks will behave like in vanilla",

    updateBlocksInWorld: Commented[Boolean] = false ->
      "If you changed the spring block name from the default to another block you can use this option to replace all blocks in your world with the new spring block. Be careful because you cannot convert them back!",

    allowInfiniteWater: Commented[Boolean] = true ->
      "Infinite water sources are possible next to spring blocks"
  ) derives ConfiguredCodec {
    lazy val getBlock: Block = registryGet(BuiltInRegistries.BLOCK, block.value)

    def shouldUpdateBlocksInWorld: Boolean =
      block.value != FluidPhysicsMod.SPRING_BLOCK_ID && updateBlocksInWorld.value
  }

  case class RainRefillConfig(
    probability: Commented[Double] = 0.2 ->
      "When it is raining, each tick one block for every chunk is selected and replaced with a source block at this probability",

    fluidWhitelist: Commented[Seq[FluidId]] = Seq(Fluids.WATER).map(FluidId.byFluid) ->
      "These fluids will be refilled when it is raining",

    biomeDependent: Commented[Boolean] = true ->
      "Fluids will only be refilled in biomes where it can rain"
  ) derives ConfiguredCodec {
    private lazy val getFluidWhitelist: Set[Fluid] = fluidWhitelist.value.map(_.fluidGroup).flatMap(_.fluids).toSet

    def canRefillFluid(fluid: Fluid): Boolean = getFluidWhitelist.contains(fluid)

    def canRainAt(world: Level, pos: BlockPos): Boolean =
      !biomeDependent.value || {
        val biome: Holder[Biome] = world.getBiome(pos)
        biome.value.hasPrecipitation && biome.value.warmEnoughToRain(pos)
      }
  }
}
