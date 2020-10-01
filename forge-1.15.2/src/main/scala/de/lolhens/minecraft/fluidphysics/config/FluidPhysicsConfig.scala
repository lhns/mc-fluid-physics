package de.lolhens.minecraft.fluidphysics.config

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod
import de.lolhens.minecraft.fluidphysics.config.FluidPhysicsConfig.{RainRefillConfig, SpringConfig, registryGet}
import io.circe.Codec
import net.minecraft.block.Block
import net.minecraft.fluid.{Fluid, Fluids}
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.registry.Registry
import net.minecraft.world.World
import net.minecraft.world.biome.Biome

case class FluidPhysicsConfig(fluidWhitelist: Seq[ResourceLocation] = Seq(Fluids.WATER, Fluids.LAVA).map(Registry.FLUID.getKey),
                              findSourceMaxIterations: Int = 255,
                              biomeDependentFluidInfinity: Boolean = false,
                              flowOverSources: Boolean = true,
                              debugFluidState: Boolean = false,
                              spring: Option[SpringConfig] = Some(SpringConfig()),
                              rainRefill: Option[RainRefillConfig] = Some(RainRefillConfig())) {
  lazy val getFluidWhitelist: Seq[Fluid] = fluidWhitelist.map(registryGet(Registry.FLUID, _))

  def enabledFor(fluid: Fluid): Boolean = getFluidWhitelist.exists(_.isEquivalentTo(fluid))
}

object FluidPhysicsConfig extends Config[FluidPhysicsConfig] {
  override val default: FluidPhysicsConfig = FluidPhysicsConfig()

  override protected def codec: Codec[FluidPhysicsConfig] = makeCodec

  private def registryGet[A](registry: Registry[A], id: ResourceLocation): A = {
    require(registry.containsKey(id), "Registry does not contain identifier: " + id)
    registry.getOrDefault(id)
  }

  case class SpringConfig(block: ResourceLocation = FluidPhysicsMod.SPRING_BLOCK_ID,
                          updateBlocksInWorld: Boolean = false,
                          allowInfiniteWater: Boolean = true) {
    lazy val getBlock: Block = registryGet(Registry.BLOCK, block)

    def shouldUpdateBlocksInWorld: Boolean =
      block != FluidPhysicsMod.SPRING_BLOCK_ID && updateBlocksInWorld
  }

  case class RainRefillConfig(probability: Double = 0.2,
                              fluidWhitelist: Seq[ResourceLocation] = Seq(Fluids.WATER).map(Registry.FLUID.getKey),
                              biomeDependent: Boolean = true) {
    lazy val getFluidWhitelist: Seq[Fluid] = fluidWhitelist.map(registryGet(Registry.FLUID, _))

    def canRefillFluid(fluid: Fluid): Boolean = getFluidWhitelist.exists(_.isEquivalentTo(fluid))

    def canRainAt(world: World, pos: BlockPos): Boolean =
      !biomeDependent || {
        val biome = world.getBiome(pos)
        biome.getPrecipitation == Biome.RainType.RAIN && biome.getTemperature(pos) >= 0.15F
      }
  }

}
