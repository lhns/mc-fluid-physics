package de.lolhens.minecraft.fluidphysics.block

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod
import net.minecraft.block.Block

class SpringBlock(properties: Block.Properties) extends Block(properties) {
  FluidPhysicsMod.config.spring.filter(_.shouldUpdateBlocksInWorld).foreach { spring =>
    setDefaultState(spring.getBlock.getDefaultState)
  }
}
