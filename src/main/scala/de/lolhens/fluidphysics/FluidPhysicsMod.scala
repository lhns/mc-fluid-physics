package de.lolhens.fluidphysics

import de.lolhens.fluidphysics.block.SpringBlock
import de.lolhens.fluidphysics.mixin.ThreadedAnvilChunkStorageAccessor
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.block.{Block, Blocks, Material}
import net.minecraft.fluid.{FlowableFluid, Fluid}
import net.minecraft.item.{BlockItem, Item, ItemGroup}
import net.minecraft.server.world.{ServerChunkManager, ServerWorld}
import net.minecraft.util.Identifier
import net.minecraft.util.math.{BlockPos, ChunkPos}
import net.minecraft.util.registry.Registry
import net.minecraft.world.Heightmap
import net.minecraft.world.chunk.{ChunkStatus, WorldChunk}

import scala.jdk.CollectionConverters._
import scala.util.Random

object FluidPhysicsMod extends ModInitializer {
  private val SPRING_BLOCK: Block = new SpringBlock(FabricBlockSettings.of(Material.STONE).requiresTool().hardness(2.0F).resistance(6.0F))

  def loadedChunks(serverWorld: ServerWorld): Seq[ChunkPos] = {
    val maxLevel = 33 + ChunkStatus.getTargetGenerationRadius(ChunkStatus.FULL)
    val chunkManager: ServerChunkManager = serverWorld.getChunkManager
    chunkManager
      .threadedAnvilChunkStorage.asInstanceOf[ThreadedAnvilChunkStorageAccessor]
      .callEntryIterator()
      .asScala
      .filterNot(_.getLevel > maxLevel)
      .map(_.getPos)
      .toSeq
  }

  override def onInitialize(): Unit = {
    Registry.register(Registry.BLOCK, new Identifier("fluidphysics", "spring"), SPRING_BLOCK)
    Registry.register(Registry.ITEM, new Identifier("fluidphysics", "spring"), new BlockItem(SPRING_BLOCK, new Item.Settings().group(ItemGroup.BUILDING_BLOCKS)))

    ServerTickEvents.END_WORLD_TICK.register { world =>
      val chunks = loadedChunks(world)
      if (chunks.nonEmpty) {
        val chunkPos = chunks(Random.nextInt(chunks.size))
        val chunk: WorldChunk = world.getChunk(chunkPos.x, chunkPos.z)
        val randX = Random.nextInt(16)
        val randZ = Random.nextInt(16)
        val y = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE).get(randX, randZ)
        val blockPos = new BlockPos(chunkPos.getStartX + randX, y, chunkPos.getStartZ + randZ)
        val waterPos: BlockPos = blockPos.down()
        val blockState = world.getBlockState(waterPos)
        val fluidState = blockState.getFluidState
        if (!fluidState.isEmpty && blockState.getBlock == Blocks.WATER && !fluidState.isStill) {
          val still = fluidState.getFluid.asInstanceOf[FlowableFluid].getStill(false)
          world.setBlockState(waterPos, still.getBlockState)
        }
        //println(blockPos)
        //world.setBlockState(blockPos, Blocks.RED_WOOL.getDefaultState)
      }
    }
  }

  def debugFluidState: Boolean = false

  def springBlock: Option[Block] = {
    Some(SPRING_BLOCK)
  }

  def springAllowsInfiniteWater: Boolean = true

  def flowOverSources: Boolean = true

  def enabledFor(fluid: Fluid): Boolean = true
}
