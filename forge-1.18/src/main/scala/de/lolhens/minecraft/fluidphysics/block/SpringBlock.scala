package de.lolhens.minecraft.fluidphysics.block

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour

class SpringBlock(properties: BlockBehaviour.Properties) extends Block(properties) {
  FluidPhysicsMod.config.spring.filter(_.shouldUpdateBlocksInWorld).foreach { spring =>
    registerDefaultState(spring.getBlock.defaultBlockState)
  }
}
