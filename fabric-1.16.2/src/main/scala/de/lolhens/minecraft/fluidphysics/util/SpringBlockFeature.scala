package de.lolhens.minecraft.fluidphysics.util

import de.lolhens.minecraft.fluidphysics.{FluidPhysicsMod, horizontal}
import net.minecraft.util.math.{BlockPos, Direction}
import net.minecraft.world.WorldAccess
import net.minecraft.world.gen.feature.SpringFeatureConfig

object SpringBlockFeature {
  def generate(world: WorldAccess,
               blockPos: BlockPos,
               springFeatureConfig: SpringFeatureConfig): Unit =
    FluidPhysicsMod.config.spring.map(_.getBlock) match {
      case Some(springBlock) =>
        def isAir(direction: Direction, offset: Int = 1): Boolean =
          world.isAir(blockPos.offset(direction, offset))

        def isValidBlock(direction: Direction): Boolean =
          springFeatureConfig.validBlocks.contains(world.getBlockState(blockPos.offset(direction)).getBlock)

        val preferredDirections =
          (horizontal.iterator.filter(isAir(_)) ++ horizontal.iterator.filter(isAir(_, 2)))
            .map(_.getOpposite).filter(isValidBlock).toSeq.sortBy { direction =>
            (if (isValidBlock(direction.rotateYClockwise())) 1 else 0) +
              (if (isValidBlock(direction.rotateYCounterclockwise())) 1 else 0)
          }.reverse ++
            Seq(Direction.DOWN).filter(isValidBlock)

        val direction = preferredDirections.headOption.getOrElse(horizontal.find(isValidBlock).get)

        world.setBlockState(blockPos.offset(direction), springBlock.getDefaultState, 2)

      case _ =>
    }
}
