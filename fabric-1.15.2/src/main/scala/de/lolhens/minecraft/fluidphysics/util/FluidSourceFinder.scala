package de.lolhens.minecraft.fluidphysics.util

import de.lolhens.minecraft.fluidphysics.{FluidPhysicsMod, horizontal}
import net.minecraft.fluid.{BaseFluid, Fluid, FluidState}
import net.minecraft.util.math.{BlockPos, Direction}
import net.minecraft.world.IWorld

import scala.collection.mutable
import scala.jdk.CollectionConverters._

object FluidSourceFinder {
  private def defaultMaxIterations: Int = FluidPhysicsMod.config.findSourceMaxIterations

  def setOf(blockPos: java.util.Collection[BlockPos]): mutable.Set[BlockPos] = blockPos.asScala.to(mutable.Set)

  def findSource(world: IWorld,
                 blockPos: BlockPos,
                 fluid: Fluid): Option[BlockPos] =
    findSource(world, blockPos, fluid, Direction.UP)

  def findSource(world: IWorld,
                 blockPos: BlockPos,
                 fluid: Fluid,
                 direction: Direction): Option[BlockPos] =
    findSource(world, blockPos, fluid, direction, mutable.Set.empty, ignoreFirst = false, ignoreLevel = false, defaultMaxIterations)

  def findSource(world: IWorld,
                 blockPos: BlockPos,
                 fluid: Fluid,
                 direction: Direction,
                 ignoreBlocks: mutable.Set[BlockPos],
                 ignoreFirst: Boolean,
                 ignoreLevel: Boolean): Option[BlockPos] =
    findSource(world, blockPos, fluid, direction, ignoreBlocks, ignoreFirst, ignoreLevel, defaultMaxIterations)

  def findSource(world: IWorld,
                 blockPos: BlockPos,
                 fluid: Fluid,
                 direction: Direction,
                 ignoreBlocks: mutable.Set[BlockPos],
                 ignoreFirst: Boolean,
                 ignoreLevel: Boolean,
                 maxIterations: Int): Option[BlockPos] =
    findSourceInternal(
      world,
      blockPos,
      world.getFluidState(blockPos),
      fluid,
      direction,
      ignoreBlocks,
      ignoreFirst,
      ignoreLevel,
      maxIterations,
      0
    )

  private def findSourceInternal(world: IWorld,
                                 blockPos: BlockPos,
                                 fluidState: FluidState,
                                 fluid: Fluid,
                                 direction: Direction,
                                 ignoreBlocks: mutable.Set[BlockPos],
                                 ignoreFirst: Boolean,
                                 ignoreLevel: Boolean,
                                 maxIterations: Int,
                                 iteration: Int): Option[BlockPos] = {
    if (iteration > maxIterations ||
      FluidPhysicsMod.config.findSourceMaxCheckedBlocks.exists(ignoreBlocks.size >= _))
      return None

    if (!ignoreFirst && ignoreBlocks.contains(blockPos)) return None
    ignoreBlocks.add(blockPos)

    if (!fluidState.isEmpty && fluid.matchesType(fluidState.getFluid)) {
      if (direction != Direction.DOWN) {
        val up = blockPos.up()
        val upFluidState = world.getFluidState(up)
        if (!upFluidState.isEmpty && fluid.matchesType(upFluidState.getFluid)) {
          val sourcePos = findSourceInternal(world, up, upFluidState, fluid, Direction.UP, ignoreBlocks, ignoreFirst = false, ignoreLevel = false, maxIterations, iteration + 1)
          if (sourcePos.isDefined) return sourcePos
        }
      }

      val oppositeDirection = direction.getOpposite

      if (!ignoreFirst && fluidState.isStill) {
        val nextToSpring = FluidPhysicsMod.config.spring.map(_.getBlock) match {
          case Some(springBlock) =>
            (Direction.DOWN +: horizontal).filterNot(_ == oppositeDirection).exists { direction =>
              world.getBlockState(blockPos.offset(direction)).getBlock == springBlock
            }

          case None =>
            false
        }

        if (!nextToSpring) {
          return Some(blockPos)
        }
      }

      val falling = fluidState.get(BaseFluid.FALLING)

      var i = 0
      while (i < horizontal.length) {
        val nextDirection = horizontal(i)
        if (nextDirection != oppositeDirection) {
          val level = fluidState.getLevel
          val nextBlockPos = blockPos.offset(nextDirection)
          val nextFluidState = world.getFluidState(nextBlockPos)
          if (!nextFluidState.isEmpty) {
            val nextLevel = nextFluidState.getLevel
            val nextFalling = nextFluidState.get(BaseFluid.FALLING)
            if (nextLevel > level || (falling && !nextFalling) || ignoreLevel) {
              val sourcePos = findSourceInternal(world, nextBlockPos, nextFluidState, fluid, nextDirection, ignoreBlocks, ignoreFirst = false, ignoreLevel, maxIterations, iteration + 1)
              if (sourcePos.isDefined) return sourcePos
            }
          }
        }

        i += 1
      }
    }

    None
  }
}
