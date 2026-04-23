package de.lolhens.minecraft.fluidphysics.util

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod
import net.minecraft.fluid.Fluid
import net.minecraft.util.math.{BlockPos, Direction}
import net.minecraft.world.{World, WorldView}

import scala.jdk.CollectionConverters._

object FluidIsInfinite {
  private val localWorld: ThreadLocal[WorldView] = new ThreadLocal()
  private val localPos: ThreadLocal[BlockPos] = new ThreadLocal()

  def set(world: WorldView, pos: BlockPos): Unit = {
    localWorld.set(world)
    localPos.set(pos)
  }

  def world: WorldView = localWorld.get()

  def pos: BlockPos = localPos.get()

  private val horizontal: Array[Direction] = Direction.Type.HORIZONTAL.iterator().asScala.toArray

  def isInfinite(fluid: Fluid): Boolean =
    if (FluidPhysicsMod.config.isEnabledFor(fluid)) {
      def isNextToSpring = FluidPhysicsMod.config.spring match {
        case Some(spring) if spring.allowInfiniteWater.value =>
          (Direction.DOWN +: horizontal).exists { direction =>
            world.getBlockState(pos.offset(direction)).isOf(spring.getBlock)
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
