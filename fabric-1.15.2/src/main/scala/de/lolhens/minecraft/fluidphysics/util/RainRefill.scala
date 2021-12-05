package de.lolhens.minecraft.fluidphysics.util

import de.lolhens.minecraft.fluidphysics.config.FluidPhysicsConfig.RainRefillConfig
import de.lolhens.minecraft.fluidphysics.mixin.ThreadedAnvilChunkStorageAccessor
import de.lolhens.minecraft.fluidphysics.{FluidPhysicsMod, horizontal}
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.block.BlockState
import net.minecraft.fluid.{BaseFluid, FluidState}
import net.minecraft.server.world.{ChunkHolder, ServerChunkManager, ServerWorld}
import net.minecraft.util.math.{BlockPos, ChunkPos}
import net.minecraft.world.chunk.{ChunkStatus, WorldChunk}
import net.minecraft.world.{Heightmap, World}

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.Random

object RainRefill {
  def init(): Unit = {
    ServerTickEvents.END_WORLD_TICK.register { world =>
      refillInLoadedChunks(world)
    }
  }

  private def loadedChunks(serverWorld: ServerWorld): Seq[ChunkPos] = {
    val chunkManager: ServerChunkManager = serverWorld.getChunkManager
    chunkManager
      .threadedAnvilChunkStorage.asInstanceOf[ThreadedAnvilChunkStorageAccessor]
      .callEntryIterator()
      .asScala
      .flatMap(_.getTickingFuture.getNow(ChunkHolder.UNLOADED_WORLD_CHUNK).left.toScala)
      .map(_.getPos)
      .toSeq
  }

  def refillInLoadedChunks(world: ServerWorld): Int = {
    if (world.isRaining) FluidPhysicsMod.config.rainRefill.foreach { rainRefillOptions =>
      val startTime = System.currentTimeMillis()
      val chunks = loadedChunks(world)
      chunks.foreach { chunkPos =>
        refillInChunk(world, chunkPos, rainRefillOptions)
      }
      return (System.currentTimeMillis() - startTime).toInt
    }
    0
  }

  private def getHighestBlock(chunk: WorldChunk, chunkX: Int, chunkZ: Int): BlockPos = {
    val y = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE).get(chunkX, chunkZ)
    new BlockPos(chunk.getPos.getStartX + chunkX, y, chunk.getPos.getStartZ + chunkZ)
  }

  private def shouldRefill(world: World,
                           blockPos: BlockPos,
                           blockState: BlockState,
                           fluidState: FluidState,
                           fluid: BaseFluid,
                           rainRefillOptions: RainRefillConfig): Boolean = {
    if (!fluidState.isStill && rainRefillOptions.canRainAt(world, blockPos)) {
      def onlySources[A](iterable: IterableOnce[A])(pos: A => BlockPos): List[A] =
        iterable.iterator.filter { e =>
          val fluidState = world.getFluidState(pos(e))
          fluid.matchesType(fluidState.getFluid) && fluidState.isStill
        }.toList

      val sourceDirections = onlySources(horizontal.iterator)(blockPos.offset)

      val edgeSources = onlySources(
        sourceDirections.iterator.flatMap { dir =>
          val pos = blockPos.offset(dir)
          List(pos.offset(dir.rotateYClockwise()), pos.offset(dir.rotateYCounterclockwise()))
        }.distinct
      )(identity)

      val numSources = sourceDirections.size + edgeSources.size

      numSources >= 2
    } else
      false
  }

  private def runWithProbability(probability: Double)(f: => Unit): Unit = {
    val count: Int = {
      val intProbability = probability.toInt
      intProbability + (if (Random.nextDouble() < (probability - intProbability)) 1 else 0)
    }

    for (_ <- 0 until count) f
  }

  private def refillInChunk(world: ServerWorld,
                            chunkPos: ChunkPos,
                            rainRefillOptions: RainRefillConfig): Unit = {
    lazy val chunk: WorldChunk = world.getChunk(chunkPos.x, chunkPos.z)

    runWithProbability(rainRefillOptions.probability.value) {
      val blockPos: BlockPos = getHighestBlock(chunk, Random.nextInt(16), Random.nextInt(16)).down()
      val blockState = world.getBlockState(blockPos)
      val fluidState = blockState.getFluidState
      fluidState.getFluid match {
        case fluid: BaseFluid if !fluidState.isEmpty && fluidState.getBlockState.getBlock == blockState.getBlock =>
          if (rainRefillOptions.canRefillFluid(fluid) && shouldRefill(world, blockPos, blockState, fluidState, fluid, rainRefillOptions)) {
            val still = fluid.getStill(false)
            world.setBlockState(blockPos, still.getBlockState)
          }

        case _ =>
      }
    }
  }
}
