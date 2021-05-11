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
        def isAir(direction: Direction, offset: Int = 1): Boolean =
          world.isEmptyBlock(blockPos.relative(direction, offset))

        def isValidBlock(direction: Direction): Boolean =
          springFeatureConfig.validBlocks.contains(world.getBlockState(blockPos.relative(direction)).getBlock)

        val preferredDirections =
          (horizontal.iterator.filter(isAir(_)) ++ horizontal.iterator.filter(isAir(_, 2)))
            .map(_.getOpposite).filter(isValidBlock).toSeq.sortBy { direction =>
            (if (isValidBlock(direction.getClockWise)) 1 else 0) +
              (if (isValidBlock(direction.getCounterClockWise)) 1 else 0)
          }.reverse ++
            Seq(Direction.DOWN).filter(isValidBlock)

        val direction = preferredDirections.headOption.getOrElse(horizontal.find(isValidBlock).get)

        world.setBlock(blockPos.relative(direction), springBlock.defaultBlockState, 2)

      case _ =>
    }
}
