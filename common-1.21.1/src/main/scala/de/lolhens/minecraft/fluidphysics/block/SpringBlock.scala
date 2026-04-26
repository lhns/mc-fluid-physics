package de.lolhens.minecraft.fluidphysics.block

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour

class SpringBlock(settings: BlockBehaviour.Properties) extends Block(settings) {
  FluidPhysicsMod.config.spring.filter(_.shouldUpdateBlocksInWorld).foreach { spring =>
    registerDefaultState(spring.getBlock.defaultBlockState())
  }
}
