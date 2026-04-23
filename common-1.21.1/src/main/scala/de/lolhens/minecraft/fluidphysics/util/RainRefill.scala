package de.lolhens.minecraft.fluidphysics.util

import de.lolhens.minecraft.fluidphysics.config.FluidPhysicsConfig.RainRefillConfig
import de.lolhens.minecraft.fluidphysics.mixin.ThreadedAnvilChunkStorageAccessor
import de.lolhens.minecraft.fluidphysics.{FluidPhysicsMod, Platform, horizontal}
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.material.{FluidState, FlowingFluid}
import net.minecraft.world.level.{ChunkPos, Level}

import scala.jdk.CollectionConverters.*
import scala.util.Random

object RainRefill {
  def init(): Unit = {
    Platform.instance.onServerLevelTick { world =>
      refillInLoadedChunks(world)
    }
  }

  private def loadedChunks(serverWorld: ServerLevel): Seq[ChunkPos] = {
    val chunkMap = serverWorld.getChunkSource.chunkMap.asInstanceOf[ThreadedAnvilChunkStorageAccessor]
    chunkMap.callGetChunks().iterator().asScala
      .flatMap { holder =>
        Option(holder.getFullChunkFuture.getNow(null)).flatMap { result =>
          if (result.isSuccess) Option(result.orElse(null.asInstanceOf[LevelChunk])).map(_.getPos)
          else None
        }
      }
      .toSeq
  }

  def refillInLoadedChunks(world: ServerLevel): Int = {
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

  private def getHighestBlock(chunk: LevelChunk, chunkX: Int, chunkZ: Int): BlockPos = {
    val y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, chunkX, chunkZ)
    new BlockPos(chunk.getPos.getMinBlockX + chunkX, y, chunk.getPos.getMinBlockZ + chunkZ)
  }

  private def shouldRefill(world: Level,
                           blockPos: BlockPos,
                           blockState: BlockState,
                           fluidState: FluidState,
                           fluid: FlowingFluid,
                           rainRefillOptions: RainRefillConfig): Boolean = {
    if (!fluidState.isSource && rainRefillOptions.canRainAt(world, blockPos)) {
      def onlySources[A](iterable: IterableOnce[A])(pos: A => BlockPos): List[A] =
        iterable.iterator.filter { e =>
          val fluidState = world.getFluidState(pos(e))
          fluid.isSame(fluidState.getType) && fluidState.isSource
        }.toList

      val sourceDirections = onlySources(horizontal.iterator)(blockPos.relative)

      val edgeSources = onlySources(
        sourceDirections.iterator.flatMap { dir =>
          val pos: BlockPos = blockPos.relative(dir)
          List[BlockPos](pos.relative(dir.getClockWise), pos.relative(dir.getCounterClockWise))
        }.distinct
      )(identity)

      sourceDirections.size + edgeSources.size >= 2
    } else false
  }

  private def runWithProbability(probability: Double)(f: => Unit): Unit = {
    val count: Int = {
      val intProbability = probability.toInt
      intProbability + (if (Random.nextDouble() < (probability - intProbability)) 1 else 0)
    }
    for (_ <- 0 until count) f
  }

  private def refillInChunk(world: ServerLevel,
                            chunkPos: ChunkPos,
                            rainRefillOptions: RainRefillConfig): Unit = {
    lazy val chunk: LevelChunk = world.getChunk(chunkPos.x, chunkPos.z)

    runWithProbability(rainRefillOptions.probability.value) {
      val blockPos: BlockPos = getHighestBlock(chunk, Random.nextInt(16), Random.nextInt(16)).below()
      val blockState = world.getBlockState(blockPos)
      val fluidState = blockState.getFluidState
      fluidState.getType match {
        case fluid: FlowingFluid if !fluidState.isEmpty && fluidState.createLegacyBlock().is(blockState.getBlock) =>
          if (rainRefillOptions.canRefillFluid(fluid) && shouldRefill(world, blockPos, blockState, fluidState, fluid, rainRefillOptions)) {
            val still = fluid.getSource(false)
            world.setBlockAndUpdate(blockPos, still.createLegacyBlock())
          }
        case _ =>
      }
    }
  }
}
