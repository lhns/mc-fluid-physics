package de.lolhens.minecraft.fluidphysics.config

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod
import de.lolhens.minecraft.fluidphysics.config.Config.Commented
import de.lolhens.minecraft.fluidphysics.config.Config.Implicits._
import de.lolhens.minecraft.fluidphysics.config.FluidPhysicsConfig.{FluidId, FluidRuleConfig, RainRefillConfig, SpringConfig, registryGetOption}
import io.circe.`export`.Exported
import io.circe.syntax._
import io.circe.{Codec, Decoder, Encoder}
import net.minecraft.block.Block
import net.minecraft.fluid.{Fluid, Fluids}
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.registry.{BuiltinRegistries, Registry}
import net.minecraft.world.World
import net.minecraft.world.biome.Biome

import scala.collection.mutable
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

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

                               biomeWhitelist: Commented[Option[Seq[FluidRuleConfig[Identifier]]]] = None ->
                                 "Biomes in which fluids are affected by this mod",

                               biomeBlacklist: Commented[Seq[FluidRuleConfig[Identifier]]] = Seq.empty ->
                                 "Biomes in which fluids are not affected by this mod",

                               biomeDependentFluidInfinity: Commented[Boolean] = true ->
                                 "This option is deprecated",

                               biomeDependentFluidInfinityWhitelist: Commented[Seq[FluidRuleConfig[Identifier]]] = Seq.empty -> {
                                 val exampleBiomes = FluidPhysicsConfig.waterBiomes.map(_._1).asJson.hoconString
                                 s"Infinite fluid sources will be enabled in these biomes\nExample (river and ocean biomes):\n$exampleBiomes"
                               },

                               unfillableBiomeWhitelist: Commented[Option[Seq[FluidRuleConfig[Identifier]]]] = Some(Seq.empty) ->
                                 "Biomes which will void fluid at sea level and therefore can't be filled",

                               unfillableBiomeBlacklist: Commented[Seq[FluidRuleConfig[Identifier]]] = Seq.empty ->
                                 "Biomes which will not void fluid at sea level and therefore can be filled",

                               flowOverSources: Commented[Boolean] = true ->
                                 "Fluids will flow over source blocks",

                               debugFluidState: Commented[Boolean] = false ->
                                 "Water will be colored depending on its fluid state",

                               spring: Option[SpringConfig] = Some(SpringConfig()),

                               rainRefill: Option[RainRefillConfig] = Some(RainRefillConfig())
                             ) {
  lazy val getFluidWhitelist: Set[Fluid] = {
    val fluidGroupBlacklist = fluidBlacklist.value.map(_.fluidGroup)
    fluidWhitelist.value
      .getOrElse(FluidId.list)
      .map(_.fluidGroup)
      .filterNot(fluidGroupBlacklist.contains)
      .flatMap(_.fluids)
      .toSet
  }

  case class WorldContext(biomesRegistry: Registry[Biome]) {
    lazy val getBiomeWhitelist: Map[Option[Fluid], Set[Biome]] =
      FluidRuleConfig.toMap(
        biomeWhitelist.value,
        biomeBlacklist.value,
        biomesRegistry.getIds.iterator.asScala,
        registryGetOption[Biome](biomesRegistry, _)
      )

    lazy val getBiomeDependentFluidInfinityWhitelist: Map[Option[Fluid], Set[Biome]] =
      FluidRuleConfig.toMap(
        Some(biomeDependentFluidInfinityWhitelist.value),
        Seq.empty,
        Seq.empty,
        registryGetOption[Biome](biomesRegistry, _)
      )

    lazy val getUnfillableBiomeWhitelist: Map[Option[Fluid], Set[Biome]] =
      FluidRuleConfig.toMap(
        unfillableBiomeWhitelist.value,
        unfillableBiomeBlacklist.value,
        biomesRegistry.getIds.iterator.asScala,
        registryGetOption[Biome](biomesRegistry, _)
      )
  }

  object WorldContext {
    private val weakMap = mutable.WeakHashMap.empty[World, WorldContext]

    def apply(world: World): WorldContext = weakMap.getOrElseUpdate(
      world,
      new WorldContext(world.getRegistryManager.get(Registry.BIOME_KEY))
    )
  }

  def getFlowOverSources: Boolean = flowOverSources.value

  def getDebugFluidState: Boolean = debugFluidState.value

  def isEnabledFor(fluid: Fluid): Boolean = getFluidWhitelist.contains(fluid)

  def isEnabledFor(fluid: Fluid, world: World, pos: BlockPos): Boolean = {
    if (!isEnabledFor(fluid)) return false
    val whitelist = WorldContext(world).getBiomeWhitelist
    if (whitelist.isEmpty) return false
    val biome = world.getBiome(pos)
    whitelist.get(Some(fluid))
      .orElse(whitelist.get(None))
      .exists(_.contains(biome))
  }

  def isInfiniteInBiome(fluid: Fluid, world: World, pos: BlockPos): Boolean = {
    val whitelist = WorldContext(world).getBiomeDependentFluidInfinityWhitelist
    if (whitelist.isEmpty) return false
    val biome = world.getBiome(pos)
    whitelist.get(Some(fluid))
      .orElse(whitelist.get(None))
      .exists(_.contains(biome))
  }

  def isUnfillableInBiome(fluid: Fluid, world: World, pos: BlockPos): Boolean = {
    val whitelist = WorldContext(world).getUnfillableBiomeWhitelist
    if (whitelist.isEmpty) return false
    val biome = world.getBiome(pos)
    whitelist.get(Some(fluid))
      .orElse(whitelist.get(None))
      .exists(_.contains(biome))
  }
}

object FluidPhysicsConfig extends Config[FluidPhysicsConfig] {
  override lazy val default: FluidPhysicsConfig = FluidPhysicsConfig()

  private def waterBiomes: Seq[(Identifier, Biome)] =
    BuiltinRegistries.BIOME.getEntries.iterator.asScala.map(e => (e.getKey.getValue, e.getValue))
      .filter {
        case (_, biome) =>
          val category = biome.getCategory
          category == Biome.Category.OCEAN || category == Biome.Category.RIVER
      }
      .toSeq

  override def shouldUpdateConfig(config: FluidPhysicsConfig): Boolean = config.updateConfig.value

  override def migrateConfig(config: FluidPhysicsConfig): FluidPhysicsConfig = {
    if (!config.biomeDependentFluidInfinity.value)
      config.copy(
        biomeDependentFluidInfinity = config.biomeDependentFluidInfinity.withValue(true),
        biomeDependentFluidInfinityWhitelist = config.biomeDependentFluidInfinityWhitelist.withValue(Seq.empty)
      )
    else
      config
  }

  override protected def codec: Codec[FluidPhysicsConfig] = makeCodec

  private def registryGetOption[A](registry: Registry[A], id: Identifier): Option[A] =
    registry.getOrEmpty(id).toScala

  private def registryGet[A](registry: Registry[A], id: Identifier): A =
    registryGetOption(registry, id)
      .getOrElse(throw new IllegalArgumentException("Registry does not contain identifier: " + id))

  case class FluidId(id: Identifier) {
    lazy val fluidGroup: FluidGroup = FluidGroup.byFluid(registryGet(Registry.FLUID, id))
  }

  object FluidId {
    def byFluid(fluid: Fluid): FluidId =
      FluidId(Registry.FLUID.getId(fluid))

    def list: Seq[FluidId] =
      Registry.FLUID.getIds.iterator.asScala.map(FluidId(_)).toSeq

    implicit val codec: Codec[FluidId] = Codec.from(
      Decoder[Identifier].map(FluidId(_)),
      Encoder[Identifier].contramap(_.id)
    )
  }

  case class FluidGroup(fluids: Set[Fluid])

  object FluidGroup {
    def byFluid(fluid: Fluid): FluidGroup = groups(fluid)

    lazy val groups: Map[Fluid, FluidGroup] = {
      val fluids = Registry.FLUID.getEntries.iterator.asScala.map(_.getValue).toSeq
      fluids
        .groupBy(fluid => fluids.find(fluid.matchesType).get)
        .map(e => FluidGroup(e._2.toSet))
        .flatMap(group => group.fluids.map(_ -> group))
        .toMap
    }
  }

  case class FluidRuleConfig[A](fluid: Option[FluidId], value: A) {
    def map[B](f: A => B): FluidRuleConfig[B] = FluidRuleConfig[B](fluid, f(value))

    lazy val rule: FluidRule[A] = FluidRule(fluid.map(_.fluidGroup), value)
  }

  object FluidRuleConfig {
    implicit def codec[A](implicit decoder: Decoder[A], encoder: Encoder[A]): Codec[FluidRuleConfig[A]] = {
      implicit val rawDecoder: Decoder[FluidRuleConfig[A]] = implicitly[Exported[Decoder[FluidRuleConfig[A]]]].instance
      implicit val rawEncoder: Encoder[FluidRuleConfig[A]] = implicitly[Exported[Encoder[FluidRuleConfig[A]]]].instance
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
                           block: Commented[Identifier] = FluidPhysicsMod.SPRING_BLOCK_ID ->
                             "Sets the block name which will act as a spring block. Fluid source blocks that are adjacent to spring blocks will behave like in vanilla",

                           updateBlocksInWorld: Commented[Boolean] = false ->
                             "If you changed the spring block name from the default to another block you can use this option to replace all blocks in your world with the new spring block. Be careful because you cannot convert them back!",

                           allowInfiniteWater: Commented[Boolean] = true ->
                             "Infinite water sources are possible next to spring blocks"
                         ) {
    lazy val getBlock: Block = registryGet(Registry.BLOCK, block.value)

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
                             ) {
    private lazy val getFluidWhitelist: Set[Fluid] = fluidWhitelist.value.map(_.fluidGroup).flatMap(_.fluids).toSet

    def canRefillFluid(fluid: Fluid): Boolean = getFluidWhitelist.contains(fluid)

    def canRainAt(world: World, pos: BlockPos): Boolean =
      !biomeDependent.value || {
        val biome = world.getBiome(pos)
        biome.getPrecipitation == Biome.Precipitation.RAIN && biome.doesNotSnow(pos)
      }
  }

}
