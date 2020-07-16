package de.lolhens.fluidphysics.block

import de.lolhens.fluidphysics.FluidPhysicsMod
import net.minecraft.block.AbstractBlock.Settings
import net.minecraft.block.Block

class SpringBlock(settings: Settings) extends Block(settings) {
  FluidPhysicsMod.config.spring.filter(_.shouldUpdateBlocksInWorld).foreach { spring =>
    setDefaultState(spring.getBlock.getDefaultState)
  }
}
