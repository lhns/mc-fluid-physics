package de.lolhens.minecraft.fluidphysics.config

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod
import de.lolhens.minecraft.fluidphysics.config.Config.Commented
import de.lolhens.minecraft.fluidphysics.config.FluidPhysicsConfig.{RainRefillConfig, SpringConfig, registryGet}
import io.circe.Codec
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.level.Level
import net.minecraft.world.level.biome.Biome
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.material.{Fluid, Fluids}
import net.minecraftforge.registries.{ForgeRegistries, IForgeRegistry, IForgeRegistryEntry}

import scala.jdk.CollectionConverters._

case class FluidPhysicsConfig(
                               updateConfig: Commented[Boolean] = true ->
                                 "Automatically update config when the structure changes in new versions",

                               fluidWhitelist: Commented[Seq[ResourceLocation]] = Seq(Fluids.WATER, Fluids.LAVA).map(ForgeRegistries.FLUIDS.getKey) ->
                                 "Fluids that are affected by this mod",

                               findSourceMaxIterations: Commented[Int] = 255 ->
                                 "Maximum iterations to find the fluid source block",

                               findSourceMaxCheckedBlocks: Commented[Option[Int]] = Some(4095) ->
                                 "Maximum number of blocks to check when finding the fluid source block",

                               biomeDependentFluidInfinity: Commented[Boolean] = false ->
                                 "Infinite fluid sources will be enabled in the specified biomes",

                               biomeDependentFluidInfinityWhitelist: Commented[Seq[ResourceLocation]] = FluidPhysicsConfig.defaultBiomes.map(_._1) ->
                                 "Infinite fluid sources will be enabled in these biomes (river and ocean biomes by default)",

                               flowOverSources: Commented[Boolean] = true ->
                                 "Fluids will flow over source blocks",

                               debugFluidState: Commented[Boolean] = false ->
                                 "Water will be colored depending on its fluid state",

                               spring: Option[SpringConfig] = Some(SpringConfig()),

                               rainRefill: Option[RainRefillConfig] = Some(RainRefillConfig())
                             ) {
  lazy val getFluidWhitelist: Seq[Fluid] = fluidWhitelist.value.map(registryGet(ForgeRegistries.FLUIDS, _))

  lazy val getFluidInfinityBiomes: Option[Set[ResourceLocation]] = Option.when(biomeDependentFluidInfinity.value)(
    biomeDependentFluidInfinityWhitelist.value.toSet
  )

  def getFlowOverSources: Boolean = flowOverSources.value

  def getDebugFluidState: Boolean = debugFluidState.value

  def enabledFor(fluid: Fluid): Boolean = getFluidWhitelist.exists(_.isSame(fluid))
}

object FluidPhysicsConfig extends Config[FluidPhysicsConfig] {
  override val default: FluidPhysicsConfig = FluidPhysicsConfig()

  private lazy val defaultBiomes: Seq[(ResourceLocation, Biome)] =
    ForgeRegistries.BIOMES.getEntries.iterator.asScala.map(e => (e.getKey.location, e.getValue))
      .filter {
        case (_, biome) =>
          val category = biome.getBiomeCategory
          category == Biome.BiomeCategory.OCEAN || category == Biome.BiomeCategory.RIVER
      }
      .toSeq

  override def updateConfig(config: FluidPhysicsConfig): Boolean = config.updateConfig.value

  override protected def codec: Codec[FluidPhysicsConfig] = makeCodec

  private def registryGet[A <: IForgeRegistryEntry[A]](registry: IForgeRegistry[A], id: ResourceLocation): A = {
    require(registry.containsKey(id), "Registry does not contain identifier: " + id)
    registry.getValue(id)
  }

  case class SpringConfig(
                           block: Commented[ResourceLocation] = FluidPhysicsMod.SPRING_BLOCK_ID ->
                             "Sets the block name which will act as a spring block. Fluid source blocks that are adjacent to spring blocks will behave like in vanilla",

                           updateBlocksInWorld: Commented[Boolean] = false ->
                             "If you changed the spring block name from the default to another block you can use this option to replace all blocks in your world with the new spring block. Be careful because you cannot convert them back!",

                           allowInfiniteWater: Commented[Boolean] = true ->
                             "Infinite water sources are possible next to spring blocks"
                         ) {
    lazy val getBlock: Block = registryGet(ForgeRegistries.BLOCKS, block.value)

    def shouldUpdateBlocksInWorld: Boolean =
      block.value != FluidPhysicsMod.SPRING_BLOCK_ID && updateBlocksInWorld.value
  }

  case class RainRefillConfig(
                               probability: Commented[Double] = 0.2 ->
                                 "When it is raining, each tick one block for every chunk is selected and replaced with a source block at this probability",

                               fluidWhitelist: Commented[Seq[ResourceLocation]] = Seq(Fluids.WATER).map(ForgeRegistries.FLUIDS.getKey) ->
                                 "These fluids will be refilled when it is raining",

                               biomeDependent: Commented[Boolean] = true ->
                                 "Fluids will only be refilled in biomes where it can rain"
                             ) {
    lazy val getFluidWhitelist: Seq[Fluid] = fluidWhitelist.value.map(registryGet(ForgeRegistries.FLUIDS, _))

    def canRefillFluid(fluid: Fluid): Boolean = getFluidWhitelist.exists(_.isSame(fluid))

    def canRainAt(world: Level, pos: BlockPos): Boolean =
      !biomeDependent.value || {
        val biome = world.getBiome(pos)
        biome.getPrecipitation == Biome.Precipitation.RAIN && biome.getTemperature(pos) >= 0.15F
      }
  }

}
