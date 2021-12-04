package de.lolhens.minecraft.fluidphysics.util

import de.lolhens.minecraft.fluidphysics.mixin.FlowableFluidAccessor
import de.lolhens.minecraft.fluidphysics.{FluidPhysicsMod, horizontal}
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.{BucketPickup, LiquidBlockContainer}
import net.minecraft.world.level.material.{FlowingFluid, Fluid, FluidState}

import scala.collection.mutable
import scala.jdk.CollectionConverters._

object FluidSourceFinder {
  private def defaultMaxIterations: Int = FluidPhysicsMod.config.findSourceMaxIterations.value

  def setOf(blockPos: java.util.Collection[BlockPos]): mutable.Set[BlockPos] = blockPos.asScala.to(mutable.Set)

  def findSource(world: LevelAccessor,
                 blockPos: BlockPos,
                 fluid: Fluid): Option[BlockPos] =
    findSource(world, blockPos, fluid, Direction.UP)

  def findSource(world: LevelAccessor,
                 blockPos: BlockPos,
                 fluid: Fluid,
                 direction: Direction): Option[BlockPos] =
    findSource(world, blockPos, fluid, direction, mutable.Set.empty, ignoreFirst = false, ignoreLevel = false, defaultMaxIterations)

  def findSource(world: LevelAccessor,
                 blockPos: BlockPos,
                 fluid: Fluid,
                 direction: Direction,
                 ignoreBlocks: mutable.Set[BlockPos],
                 ignoreFirst: Boolean,
                 ignoreLevel: Boolean): Option[BlockPos] =
    findSource(world, blockPos, fluid, direction, ignoreBlocks, ignoreFirst, ignoreLevel, defaultMaxIterations)

  def findSource(world: LevelAccessor,
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

  private def findSourceInternal(world: LevelAccessor,
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

    if (!fluidState.isEmpty && fluid.isSame(fluidState.getType)) {
      if (direction != Direction.DOWN) {
        val up: BlockPos = blockPos.above
        val upFluidState = world.getFluidState(up)
        if (!upFluidState.isEmpty && fluid.isSame(upFluidState.getType)) {
          val sourcePos = findSourceInternal(world, up, upFluidState, fluid, Direction.UP, ignoreBlocks, ignoreFirst = false, ignoreLevel = false, maxIterations, iteration + 1)
          if (sourcePos.isDefined) return sourcePos
        }
      }

      val oppositeDirection = direction.getOpposite

      if (!ignoreFirst && fluidState.isSource) {
        val nextToSpring = FluidPhysicsMod.config.spring.map(_.getBlock) match {
          case Some(springBlock) =>
            (Direction.DOWN +: horizontal).filterNot(_ == oppositeDirection).exists { direction =>
              world.getBlockState(blockPos.relative(direction)).is(springBlock)
            }

          case None =>
            false
        }

        if (!nextToSpring) {
          return Some(blockPos)
        }
      }

      val falling = fluidState.getValue(FlowingFluid.FALLING)

      var i = 0
      while (i < horizontal.length) {
        val nextDirection = horizontal(i)
        if (nextDirection != oppositeDirection) {
          val level = fluidState.getAmount
          val nextBlockPos: BlockPos = blockPos.relative(nextDirection)
          val nextFluidState = world.getFluidState(nextBlockPos)
          if (!nextFluidState.isEmpty) {
            val nextLevel = nextFluidState.getAmount
            val nextFalling = nextFluidState.getValue(FlowingFluid.FALLING)
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

  def moveSource(world: LevelAccessor,
                 srcPos: BlockPos,
                 dstPos: BlockPos,
                 dstState: BlockState,
                 fluid: FlowingFluid,
                 still: FluidState): Unit = {
    // Drain source block
    val srcState = world.getBlockState(srcPos)
    srcState.getBlock match {
      case bucketPickup: BucketPickup =>
        bucketPickup.pickupBlock(world, srcPos, srcState)

      case _ =>
        if (!srcState.isAir)
          fluid.asInstanceOf[FlowableFluidAccessor].callBeforeDestroyingBlock(world, srcPos, srcState)

        val newSourceLevel = still.getAmount - 1
        val newSourceFluidState = fluid.getFlowing(newSourceLevel, false)
        world.setBlock(srcPos, newSourceFluidState.createLegacyBlock, 3)
    }

    // Flow source block to new position
    dstState.getBlock match {
      case liquidBlockContainer: LiquidBlockContainer =>
        liquidBlockContainer.placeLiquid(world, dstPos, dstState, still)

      case _ =>
        if (!dstState.isAir)
          fluid.asInstanceOf[FlowableFluidAccessor].callBeforeDestroyingBlock(world, dstPos, dstState)

        world.setBlock(dstPos, still.createLegacyBlock, 3)
    }
  }
}
