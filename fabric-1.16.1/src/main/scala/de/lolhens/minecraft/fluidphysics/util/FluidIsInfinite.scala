package de.lolhens.minecraft.fluidphysics.util

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod
import net.minecraft.fluid.Fluid
import net.minecraft.util.math.{BlockPos, Direction}
import net.minecraft.util.registry.Registry
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
    if (FluidPhysicsMod.config.enabledFor(fluid)) {
      val nextToSpring = FluidPhysicsMod.config.spring match {
        case Some(spring) if spring.allowInfiniteWater.value =>
          (Direction.DOWN +: horizontal).exists { direction =>
            world.getBlockState(pos.offset(direction)).isOf(spring.getBlock)
          }

        case _ => false
      }

      val isBiome = world match {
        case world: World =>
          FluidPhysicsMod.config.getFluidInfinityBiomes.exists { biomes =>
            val biome = Registry.BIOME.getId(world.getBiome(pos))
            biomes.contains(biome)
          }

        case _ => false
      }

      nextToSpring || isBiome
    } else {
      true
    }
}
