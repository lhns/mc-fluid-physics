package de.lolhens.fluidphysics

import net.minecraft.fluid.{FlowableFluid, Fluid, FluidState}
import net.minecraft.util.math.{BlockPos, Direction}
import net.minecraft.world.WorldAccess

import scala.collection.mutable
import scala.jdk.CollectionConverters._

object FluidSourceFinder {
  private val defaultMaxIterations = 255

  def findSource(world: WorldAccess,
                 blockPos: BlockPos,
                 fluid: Fluid): Option[BlockPos] =
    findSource(world, blockPos, fluid, Direction.UP)

  def findSource(world: WorldAccess,
                 blockPos: BlockPos,
                 fluid: Fluid,
                 direction: Direction): Option[BlockPos] =
    findSource(world, blockPos, fluid, direction, defaultMaxIterations)

  def findSource(world: WorldAccess,
                 blockPos: BlockPos,
                 fluid: Fluid,
                 direction: Direction,
                 maxIterations: Int): Option[BlockPos] =
    findSourceInternal(
      world,
      blockPos,
      world.getFluidState(blockPos),
      fluid,
      direction,
      maxIterations,
      0,
      mutable.Set.empty
    )

  private val horizontal: Array[Direction] = Direction.Type.HORIZONTAL.iterator().asScala.toArray

  private def findSourceInternal(world: WorldAccess,
                                 blockPos: BlockPos,
                                 fluidState: FluidState,
                                 fluid: Fluid,
                                 direction: Direction,
                                 maxIterations: Int,
                                 iteration: Int,
                                 ignoreBlocks: mutable.Set[BlockPos]): Option[BlockPos] = {
    if (iteration > maxIterations) return None

    if (ignoreBlocks.contains(blockPos)) return None
    ignoreBlocks.add(blockPos)

    if (!fluidState.isEmpty && fluidState.getFluid.matchesType(fluid)) {
      if (direction != Direction.DOWN) {
        val up = blockPos.up()
        val upFluidState = world.getFluidState(up)
        if (!upFluidState.isEmpty && upFluidState.getFluid.matchesType(fluid)) {
          val sourcePos = findSourceInternal(world, up, upFluidState, fluid, Direction.UP, maxIterations, iteration + 1, ignoreBlocks)
          if (sourcePos.isDefined) return sourcePos
        }
      }

      if (fluidState.isStill) {
        return Some(blockPos)
      }

      val falling = fluidState.get(FlowableFluid.FALLING)

      val oppositeDirection = direction.getOpposite
      var i = 0
      while (i < horizontal.length) {
        if (direction != oppositeDirection) {
          val nextDirection = horizontal(i)
          val level = fluidState.getLevel
          val nextBlockPos = blockPos.offset(nextDirection)
          val nextFluidState = world.getFluidState(nextBlockPos)
          if (!nextFluidState.isEmpty) {
            val nextLevel = nextFluidState.getLevel
            val nextFalling = nextFluidState.get(FlowableFluid.FALLING)
            if (nextLevel > level || (falling && !nextFalling)) {
              val sourcePos = findSourceInternal(world, nextBlockPos, nextFluidState, fluid, nextDirection, maxIterations, iteration + 1, ignoreBlocks)
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