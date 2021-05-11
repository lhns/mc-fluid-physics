package de.lolhens.minecraft.fluidphysics.block

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod
import net.minecraft.block.{AbstractBlock, Block}

class SpringBlock(properties: AbstractBlock.Properties) extends Block(properties) {
  FluidPhysicsMod.config.spring.filter(_.shouldUpdateBlocksInWorld).foreach { spring =>
    registerDefaultState(spring.getBlock.defaultBlockState)
  }
}
