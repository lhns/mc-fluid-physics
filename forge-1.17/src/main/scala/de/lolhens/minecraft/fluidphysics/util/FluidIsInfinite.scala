package de.lolhens.minecraft.fluidphysics.util

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.{BlockGetter, Level}

import scala.jdk.CollectionConverters._

object FluidIsInfinite {
  private val localWorld: ThreadLocal[BlockGetter] = new ThreadLocal()
  private val localPos: ThreadLocal[BlockPos] = new ThreadLocal()

  def set(world: BlockGetter, pos: BlockPos): Unit = {
    localWorld.set(world)
    localPos.set(pos)
  }

  def world: BlockGetter = localWorld.get()

  def pos: BlockPos = localPos.get()

  private val horizontal: Array[Direction] = Direction.Plane.HORIZONTAL.iterator().asScala.toArray

  def isInfinite(fluid: Fluid): Boolean =
    if (FluidPhysicsMod.config.isEnabledFor(fluid)) {
      def isNextToSpring = FluidPhysicsMod.config.spring match {
        case Some(spring) if spring.allowInfiniteWater.value =>
          (Direction.DOWN +: horizontal).exists { direction =>
            world.getBlockState(pos.relative(direction)).is(spring.getBlock)
          }

        case _ => false
      }

      def isInfiniteInBiome = world match {
        case world: Level =>
          FluidPhysicsMod.config.isInfiniteInBiome(fluid, world, pos)

        case _ => false
      }

      isInfiniteInBiome || isNextToSpring
    } else {
      true
    }
}
