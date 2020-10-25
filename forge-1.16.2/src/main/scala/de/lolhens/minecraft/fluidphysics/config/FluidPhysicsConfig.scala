package de.lolhens.minecraft.fluidphysics.config

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod
import de.lolhens.minecraft.fluidphysics.config.FluidPhysicsConfig.{RainRefillConfig, SpringConfig, registryGet}
import io.circe.Codec
import net.minecraft.block.Block
import net.minecraft.fluid.{Fluid, Fluids}
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraft.world.biome.Biome
import net.minecraftforge.registries.{ForgeRegistries, IForgeRegistry, IForgeRegistryEntry}

case class FluidPhysicsConfig(fluidWhitelist: Seq[ResourceLocation] = Seq(Fluids.WATER, Fluids.LAVA).map(ForgeRegistries.FLUIDS.getKey),
                              findSourceMaxIterations: Int = 255,
                              findSourceMaxCheckedBlocks: Option[Int] = Some(4095),
                              biomeDependentFluidInfinity: Boolean = false,
                              flowOverSources: Boolean = true,
                              debugFluidState: Boolean = false,
                              spring: Option[SpringConfig] = Some(SpringConfig()),
                              rainRefill: Option[RainRefillConfig] = Some(RainRefillConfig())) {
  lazy val getFluidWhitelist: Seq[Fluid] = fluidWhitelist.map(registryGet(ForgeRegistries.FLUIDS, _))

  def enabledFor(fluid: Fluid): Boolean = getFluidWhitelist.exists(_.isEquivalentTo(fluid))
}

object FluidPhysicsConfig extends Config[FluidPhysicsConfig] {
  override val default: FluidPhysicsConfig = FluidPhysicsConfig()

  override protected def codec: Codec[FluidPhysicsConfig] = makeCodec

  private def registryGet[A <: IForgeRegistryEntry[A]](registry: IForgeRegistry[A], id: ResourceLocation): A = {
    require(registry.containsKey(id), "Registry does not contain identifier: " + id)
    registry.getValue(id)
  }

  case class SpringConfig(block: ResourceLocation = FluidPhysicsMod.SPRING_BLOCK_ID,
                          updateBlocksInWorld: Boolean = false,
                          allowInfiniteWater: Boolean = true) {
    lazy val getBlock: Block = registryGet(ForgeRegistries.BLOCKS, block)

    def shouldUpdateBlocksInWorld: Boolean =
      block != FluidPhysicsMod.SPRING_BLOCK_ID && updateBlocksInWorld
  }

  case class RainRefillConfig(probability: Double = 0.2,
                              fluidWhitelist: Seq[ResourceLocation] = Seq(Fluids.WATER).map(ForgeRegistries.FLUIDS.getKey),
                              biomeDependent: Boolean = true) {
    lazy val getFluidWhitelist: Seq[Fluid] = fluidWhitelist.map(registryGet(ForgeRegistries.FLUIDS, _))

    def canRefillFluid(fluid: Fluid): Boolean = getFluidWhitelist.exists(_.isEquivalentTo(fluid))

    def canRainAt(world: World, pos: BlockPos): Boolean =
      !biomeDependent || {
        val biome = world.getBiome(pos)
        biome.getPrecipitation == Biome.RainType.RAIN && biome.getTemperature(pos) >= 0.15F
      }
  }

}
