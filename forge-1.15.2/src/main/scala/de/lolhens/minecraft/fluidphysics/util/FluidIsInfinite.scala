package de.lolhens.minecraft.fluidphysics.util

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod
import net.minecraft.fluid.Fluid
import net.minecraft.util.Direction
import net.minecraft.util.math.BlockPos
import net.minecraft.world.{IWorldReader, World}

import scala.jdk.CollectionConverters._

object FluidIsInfinite {
  private val localWorld: ThreadLocal[IWorldReader] = new ThreadLocal()
  private val localPos: ThreadLocal[BlockPos] = new ThreadLocal()

  def set(world: IWorldReader, pos: BlockPos): Unit = {
    localWorld.set(world)
    localPos.set(pos)
  }

  def world: IWorldReader = localWorld.get()

  def pos: BlockPos = localPos.get()

  private val horizontal: Array[Direction] = Direction.Plane.HORIZONTAL.iterator().asScala.toArray

  def isInfinite(fluid: Fluid): Boolean =
    if (FluidPhysicsMod.config.isEnabledFor(fluid)) {
      def isNextToSpring = FluidPhysicsMod.config.spring match {
        case Some(spring) if spring.allowInfiniteWater.value =>
          (Direction.DOWN +: horizontal).exists { direction =>
            world.getBlockState(pos.offset(direction)).getBlock == spring.getBlock
          }

        case _ => false
      }

      def isInfiniteInBiome = world match {
        case world: World =>
          FluidPhysicsMod.config.isInfiniteInBiome(fluid, world, pos)

        case _ => false
      }

      isInfiniteInBiome || isNextToSpring
    } else {
      true
    }
}
