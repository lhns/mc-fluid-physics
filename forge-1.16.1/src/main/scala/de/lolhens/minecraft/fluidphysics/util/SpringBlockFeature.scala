package de.lolhens.minecraft.fluidphysics.util

import de.lolhens.minecraft.fluidphysics.{FluidPhysicsMod, horizontal}
import net.minecraft.util.Direction
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IWorld
import net.minecraft.world.gen.feature.LiquidsConfig

object SpringBlockFeature {
  def generate(world: IWorld,
               blockPos: BlockPos,
               springFeatureConfig: LiquidsConfig): Unit =
    FluidPhysicsMod.config.spring.map(_.getBlock) match {
      case Some(springBlock) =>
        def isAir(direction: Direction): Boolean =
          world.isAirBlock(blockPos.offset(direction))

        def isValidBlock(direction: Direction): Boolean =
          springFeatureConfig.acceptedBlocks.contains(world.getBlockState(blockPos.offset(direction)).getBlock)

        val preferredDirections =
          horizontal.toSeq.filter(isAir).map(_.getOpposite).filter(isValidBlock).sortBy { direction =>
            (if (isValidBlock(direction.rotateY())) 1 else 0) +
              (if (isValidBlock(direction.rotateYCCW())) 1 else 0)
          }.reverse ++
            Seq(Direction.DOWN).filter(isValidBlock)

        val direction = preferredDirections.headOption.getOrElse(horizontal.find(isValidBlock).get)

        world.setBlockState(blockPos.offset(direction), springBlock.getDefaultState, 2)

      case _ =>
    }
}
