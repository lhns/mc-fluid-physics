package de.lolhens.minecraft.fluidphysics.config

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod
import de.lolhens.minecraft.fluidphysics.config.Config.Commented
import de.lolhens.minecraft.fluidphysics.config.FluidPhysicsConfig.{RainRefillConfig, SpringConfig, registryGet}
import io.circe.Codec
import net.minecraft.block.Block
import net.minecraft.fluid.{Fluid, Fluids}
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.registry.Registry
import net.minecraft.world.World
import net.minecraft.world.biome.Biome

case class FluidPhysicsConfig(
                               updateConfig: Commented[Boolean] = true ->
                                 "Automatically update config when the structure changes in new versions",

                               fluidWhitelist: Commented[Seq[Identifier]] = Seq(Fluids.WATER, Fluids.LAVA).map(Registry.FLUID.getId) ->
                                 "Fluids that are affected by this mod",

                               findSourceMaxIterations: Commented[Int] = 255 ->
                                 "Maximum iterations to find the fluid source block",

                               findSourceMaxCheckedBlocks: Commented[Option[Int]] = Some(4095) ->
                                 "Maximum number of blocks to check when finding the fluid source block",

                               biomeDependentFluidInfinity: Commented[Boolean] = false ->
                                 "Infinite fluid sources will be enabled in river and ocean biomes",

                               flowOverSources: Commented[Boolean] = true ->
                                 "Fluids will flow over source blocks",

                               debugFluidState: Commented[Boolean] = false ->
                                 "Water will be colored depending on its fluid state",

                               spring: Option[SpringConfig] = Some(SpringConfig()),

                               rainRefill: Option[RainRefillConfig] = Some(RainRefillConfig())
                             ) {
  lazy val getFluidWhitelist: Seq[Fluid] = fluidWhitelist.value.map(registryGet(Registry.FLUID, _))

  def getFlowOverSources: Boolean = flowOverSources.value

  def getDebugFluidState: Boolean = debugFluidState.value

  def enabledFor(fluid: Fluid): Boolean = getFluidWhitelist.exists(_.matchesType(fluid))
}

object FluidPhysicsConfig extends Config[FluidPhysicsConfig] {
  override val default: FluidPhysicsConfig = FluidPhysicsConfig()

  override def updateConfig(config: FluidPhysicsConfig): Boolean = config.updateConfig.value

  override protected def codec: Codec[FluidPhysicsConfig] = makeCodec

  private def registryGet[A](registry: Registry[A], id: Identifier): A =
    registry.getOrEmpty(id)
      .orElseThrow(() => new IllegalArgumentException("Registry does not contain identifier: " + id))

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

                               fluidWhitelist: Commented[Seq[Identifier]] = Seq(Fluids.WATER).map(Registry.FLUID.getId) ->
                                 "These fluids will be refilled when it is raining",

                               biomeDependent: Commented[Boolean] = true ->
                                 "Fluids will only be refilled in biomes where it can rain"
                             ) {
    lazy val getFluidWhitelist: Seq[Fluid] = fluidWhitelist.value.map(registryGet(Registry.FLUID, _))

    def canRefillFluid(fluid: Fluid): Boolean = getFluidWhitelist.exists(_.matchesType(fluid))

    def canRainAt(world: World, pos: BlockPos): Boolean =
      !biomeDependent.value || {
        val biome = world.getBiome(pos)
        biome.getPrecipitation == Biome.Precipitation.RAIN && biome.getTemperature(pos) >= 0.15F
      }
  }

}
