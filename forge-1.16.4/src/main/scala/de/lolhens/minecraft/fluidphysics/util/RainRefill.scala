package de.lolhens.minecraft.fluidphysics.util

import de.lolhens.minecraft.fluidphysics.config.FluidPhysicsConfig.RainRefillConfig
import de.lolhens.minecraft.fluidphysics.mixin.ThreadedAnvilChunkStorageAccessor
import de.lolhens.minecraft.fluidphysics.{FluidPhysicsMod, horizontal}
import net.minecraft.block.BlockState
import net.minecraft.fluid.{FlowingFluid, FluidState}
import net.minecraft.util.math.{BlockPos, ChunkPos}
import net.minecraft.world.World
import net.minecraft.world.chunk.{Chunk, ChunkStatus}
import net.minecraft.world.gen.Heightmap
import net.minecraft.world.server.{ChunkHolder, ServerChunkProvider, ServerWorld}
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.TickEvent.Phase

import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._
import scala.util.Random

object RainRefill {
  def init(): Unit = {
    MinecraftForge.EVENT_BUS.addListener { event: TickEvent.WorldTickEvent =>
      (event.phase, event.world) match {
        case (Phase.END, serverWorld: ServerWorld) =>
          refillInLoadedChunks(serverWorld)

        case _ =>
      }
    }
  }

  private lazy val maxLevel = 33 + ChunkStatus.getDistance(ChunkStatus.FULL)

  private def loadedChunks(serverWorld: ServerWorld): Seq[ChunkPos] = {
    val chunkManager: ServerChunkProvider = serverWorld.getChunkSource
    chunkManager
      .chunkMap.asInstanceOf[ThreadedAnvilChunkStorageAccessor]
      .callGetChunks()
      .asScala
      .flatMap(_.getTickingChunkFuture.getNow(ChunkHolder.UNLOADED_LEVEL_CHUNK).left.toScala)
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

  private def getHighestBlock(chunk: Chunk, chunkX: Int, chunkZ: Int): BlockPos = {
    val y = chunk.getOrCreateHeightmapUnprimed(Heightmap.Type.WORLD_SURFACE).getFirstAvailable(chunkX, chunkZ)
    new BlockPos(chunk.getPos.getMinBlockX + chunkX, y, chunk.getPos.getMinBlockZ + chunkZ)
  }

  private def shouldRefill(world: World,
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
          val pos = blockPos.relative(dir)
          List(pos.relative(dir.getClockWise), pos.relative(dir.getCounterClockWise))
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
    lazy val chunk: Chunk = world.getChunk(chunkPos.x, chunkPos.z)

    runWithProbability(rainRefillOptions.probability.value) {
      val blockPos: BlockPos = getHighestBlock(chunk, Random.nextInt(16), Random.nextInt(16)).below
      val blockState = world.getBlockState(blockPos)
      val fluidState = blockState.getFluidState
      fluidState.getType match {
        case fluid: FlowingFluid if !fluidState.isEmpty && fluidState.createLegacyBlock.getBlockState.is(blockState.getBlock) =>
          if (rainRefillOptions.canRefillFluid(fluid) && shouldRefill(world, blockPos, blockState, fluidState, fluid, rainRefillOptions)) {
            val still = fluid.getSource(false)
            world.setBlockAndUpdate(blockPos, still.createLegacyBlock)
          }

        case _ =>
      }
    }
  }
}
