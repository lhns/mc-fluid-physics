package de.lolhens.minecraft.fluidphysics.util

import de.lolhens.minecraft.fluidphysics.mixin.FlowableFluidAccessor
import de.lolhens.minecraft.fluidphysics.{FluidPhysicsMod, horizontal}
import net.minecraft.block.{BlockState, FluidBlock, FluidDrainable, FluidFillable}
import net.minecraft.fluid.{FlowableFluid, Fluid, FluidState}
import net.minecraft.util.math.{BlockPos, Direction}
import net.minecraft.world.WorldAccess

import scala.collection.mutable
import scala.jdk.CollectionConverters._

object FluidSourceFinder {
  private def defaultMaxIterations: Int = FluidPhysicsMod.config.findSourceMaxIterations.value

  def setOf(blockPos: java.util.Collection[BlockPos]): mutable.Set[BlockPos] = blockPos.asScala.to(mutable.Set)

  @inline
  def findSource(world: WorldAccess,
                 blockPos: BlockPos,
                 fluid: Fluid): Option[BlockPos] =
    findSource(world, blockPos, fluid, Direction.UP)

  @inline
  def findSource(world: WorldAccess,
                 blockPos: BlockPos,
                 fluid: Fluid,
                 direction: Direction): Option[BlockPos] =
    findSource(world, blockPos, fluid, direction, mutable.Set.empty, ignoreFirst = false, ignoreLevel = false, defaultMaxIterations)

  @inline
  def findSource(world: WorldAccess,
                 blockPos: BlockPos,
                 fluid: Fluid,
                 direction: Direction,
                 ignoreBlocks: mutable.Set[BlockPos],
                 ignoreFirst: Boolean,
                 ignoreLevel: Boolean): Option[BlockPos] =
    findSource(world, blockPos, fluid, direction, ignoreBlocks, ignoreFirst, ignoreLevel, defaultMaxIterations)

  @inline
  def findSource(world: WorldAccess,
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

  private def findSourceInternal(world: WorldAccess,
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
      FluidPhysicsMod.config.findSourceMaxCheckedBlocks.value.exists(ignoreBlocks.size >= _))
      return None

    if (!ignoreFirst && ignoreBlocks.contains(blockPos)) return None
    ignoreBlocks.add(blockPos)

    if (!fluidState.isEmpty && fluid.matchesType(fluidState.getFluid)) {
      if (direction != Direction.DOWN) {
        val up: BlockPos = blockPos.up()
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
              world.getBlockState(blockPos.offset(direction)).isOf(springBlock)
            }

          case None =>
            false
        }

        if (!nextToSpring) {
          return Some(blockPos)
        }
      }

      val falling = fluidState.get(FlowableFluid.FALLING)

      var i = 0
      while (i < horizontal.length) {
        val nextDirection = horizontal(i)
        if (nextDirection != oppositeDirection) {
          val level = fluidState.getLevel
          val nextBlockPos: BlockPos = blockPos.offset(nextDirection)
          val nextFluidState = world.getFluidState(nextBlockPos)
          if (!nextFluidState.isEmpty) {
            val nextLevel = nextFluidState.getLevel
            val nextFalling = nextFluidState.get(FlowableFluid.FALLING)
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

  def moveSource(world: WorldAccess,
                 srcPos: BlockPos,
                 dstPos: BlockPos,
                 dstState: BlockState,
                 fluid: FlowableFluid,
                 still: FluidState): Unit = {
    // Drain source block
    val srcState = world.getBlockState(srcPos)
    srcState.getBlock match {
      case bucketPickup: FluidDrainable if !bucketPickup.isInstanceOf[FluidBlock] =>
        bucketPickup.tryDrainFluid(world, srcPos, srcState)

      case _ =>
        if (!srcState.isAir)
          fluid.asInstanceOf[FlowableFluidAccessor].callBeforeBreakingBlock(world, srcPos, srcState)

        val newSourceLevel = still.getLevel - 1
        val newSourceFluidState = fluid.getFlowing(newSourceLevel, false)
        world.setBlockState(srcPos, newSourceFluidState.getBlockState, 3)
    }

    // Flow source block to new position
    dstState.getBlock match {
      case liquidBlockContainer: FluidFillable =>
        liquidBlockContainer.tryFillWithFluid(world, dstPos, dstState, still)

      case _ =>
        if (!dstState.isAir)
          fluid.asInstanceOf[FlowableFluidAccessor].callBeforeBreakingBlock(world, dstPos, dstState)

        world.setBlockState(dstPos, still.getBlockState, 3)
    }
  }
}
