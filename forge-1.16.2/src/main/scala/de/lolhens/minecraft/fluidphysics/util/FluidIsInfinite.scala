package de.lolhens.minecraft.fluidphysics.util

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod
import net.minecraft.fluid.Fluid
import net.minecraft.util.Direction
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IWorld
import net.minecraft.world.biome.Biome

import scala.jdk.CollectionConverters._

object FluidIsInfinite {
  private val localWorld: ThreadLocal[IWorld] = new ThreadLocal()
  private val localPos: ThreadLocal[BlockPos] = new ThreadLocal()

  def set(world: IWorld, pos: BlockPos): Unit = {
    localWorld.set(world);
    localPos.set(pos)
  }

  def world: IWorld = localWorld.get()

  def pos: BlockPos = localPos.get()

  private val horizontal: Array[Direction] = Direction.Plane.HORIZONTAL.iterator().asScala.toArray

  def isInfinite(fluid: Fluid): Boolean =
    if (FluidPhysicsMod.config.enabledFor(fluid)) {
      val nextToSpring = FluidPhysicsMod.config.spring match {
        case Some(spring) if spring.allowInfiniteWater =>
          (Direction.DOWN +: horizontal).exists { direction =>
            world.getBlockState(pos.offset(direction)).isIn(spring.getBlock)
          }
        case _ =>
          false
      }

      val isBiome =
        if (FluidPhysicsMod.config.biomeDependentFluidInfinity) {
          val biomeCategory = world.getBiome(pos).getCategory
          biomeCategory == Biome.Category.OCEAN || biomeCategory == Biome.Category.RIVER
        } else {
          false
        }

      nextToSpring || isBiome
    } else {
      true
    }
}
