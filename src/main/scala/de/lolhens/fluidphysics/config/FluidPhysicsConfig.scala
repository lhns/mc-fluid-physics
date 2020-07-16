package de.lolhens.fluidphysics.config

import de.lolhens.fluidphysics.FluidPhysicsMod
import de.lolhens.fluidphysics.config.FluidPhysicsConfig.{RainRefillConfig, SpringConfig, registryGet}
import io.circe.Codec
import net.minecraft.block.Block
import net.minecraft.fluid.{Fluid, Fluids}
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry

case class FluidPhysicsConfig(fluidWhitelist: Seq[Identifier] = Seq(Fluids.WATER, Fluids.LAVA).map(Registry.FLUID.getId),
                              findSourceMaxIterations: Int = 255,
                              flowOverSources: Boolean = true,
                              debugFluidState: Boolean = false,
                              spring: Option[SpringConfig] = Some(SpringConfig()),
                              rainRefill: Option[RainRefillConfig] = Some(RainRefillConfig())) {
  lazy val getFluidWhitelist: Seq[Fluid] = fluidWhitelist.map(registryGet(Registry.FLUID, _))

  def enabledFor(fluid: Fluid): Boolean = getFluidWhitelist.exists(_.matchesType(fluid))
}

object FluidPhysicsConfig extends Config[FluidPhysicsConfig] {
  override val default: FluidPhysicsConfig = FluidPhysicsConfig()

  override protected def codec: Codec[FluidPhysicsConfig] = makeCodec

  private def registryGet[A](registry: Registry[A], id: Identifier): A = {
    require(registry.containsId(id), "Registry does not contain identifier: " + id)
    registry.get(id)
  }

  case class SpringConfig(block: Identifier = FluidPhysicsMod.SPRING_BLOCK_IDENTIFIER,
                          updateBlocksInWorld: Boolean = false,
                          allowInfiniteWater: Boolean = true) {
    lazy val getBlock: Block = registryGet(Registry.BLOCK, block)

    def shouldUpdateBlocksInWorld: Boolean =
      block != FluidPhysicsMod.SPRING_BLOCK_IDENTIFIER && updateBlocksInWorld
  }

  case class RainRefillConfig(probability: Double = 0.2,
                              fluidWhitelist: Seq[Identifier] = Seq(Fluids.WATER).map(Registry.FLUID.getId)) {
    lazy val getFluidWhitelist: Seq[Fluid] = fluidWhitelist.map(registryGet(Registry.FLUID, _))

    def canRefillFluid(fluid: Fluid): Boolean = getFluidWhitelist.exists(_.matchesType(fluid))
  }

}
