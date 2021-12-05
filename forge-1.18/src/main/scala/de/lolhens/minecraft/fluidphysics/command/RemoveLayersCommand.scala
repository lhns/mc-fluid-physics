package de.lolhens.minecraft.fluidphysics.command

import com.mojang.brigadier.context.CommandContext
import de.lolhens.minecraft.fluidphysics.command.CommandHandler._
import de.lolhens.minecraft.fluidphysics.mixin.FlowableFluidAccessor
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.{BlockPos, Direction}
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.{Blocks, BucketPickup, LiquidBlock}
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.material.{FlowingFluid, Fluid, Fluids}
import net.minecraftforge.registries.ForgeRegistries

import scala.annotation.tailrec
import scala.collection.mutable

case class RemoveLayersCommand(source: CommandSourceStack,
                               world: Level,
                               pos: BlockPos,
                               fluid: Fluid,
                               numLayers: Int) extends Command {
  private val minY = pos.getY
  private val maxY = minY + numLayers - 1

  var chunkQueues: Seq[(ChunkAccess, Set[BlockPos])] = Seq.empty

  override def run(): Either[String, Unit] = {
    source.sendSuccess("Starting layer removal", false)

    chunkQueues = Seq(world.getChunk(pos) -> Set(pos))

    setTicking(true)

    Right(())
  }

  private def drainFluid(world: Level, pos: BlockPos, state: BlockState): Unit = state.getBlock match {
    case fluidDrainable: BucketPickup if !state.getBlock.isInstanceOf[LiquidBlock] =>
      fluidDrainable.pickupBlock(world, pos, state)

    case _ =>
      fluid match {
        case flowableFluid: FlowingFluid if !state.isAir =>
          flowableFluid.asInstanceOf[FlowableFluidAccessor].callBeforeDestroyingBlock(world, pos, state)

        case _ =>
      }

      world.setBlock(pos, Blocks.AIR.defaultBlockState, 18)
  }

  override def tick(worldContext: Level): Unit = {
    if (worldContext != world) return

    chunkQueues.headOption match {
      case Some((currentChunk, positions)) =>
        val chunkQueuesTail = chunkQueues.tail

        val ignoredPositions = mutable.Set.empty[BlockPos]
        val newQueues: mutable.WeakHashMap[ChunkAccess, mutable.Set[BlockPos]] = mutable.WeakHashMap.empty

        def isCurrentChunkElseQueue(pos: BlockPos): Boolean = {
          val chunk = world.getChunk(pos)
          if (chunk == currentChunk) {
            true
          } else {
            newQueues.getOrElseUpdate(chunk, mutable.Set.empty).add(pos)
            false
          }
        }

        @tailrec
        def rec(positions: Iterator[BlockPos]): Unit = {
          val nextPositions = positions.iterator
            .tapEach(ignoredPositions.add)
            .filter(world.getFluidState(_).getType.isSame(fluid))
            .flatMap { pos =>
              if (isCurrentChunkElseQueue(pos)) {
                drainFluid(world, pos, world.getBlockState(pos))
                Direction.values().iterator.map[BlockPos](pos.relative)
                  .filter(e => e.getY >= minY && e.getY <= maxY)
                  .filterNot(ignoredPositions.contains)
                  .tapEach(ignoredPositions.add)
              } else
                Iterator.empty
            }

          if (nextPositions.nonEmpty) rec(nextPositions.toSeq.iterator)
        }

        rec(positions.iterator)

        chunkQueues = chunkQueuesTail.map {
          case (chunk, positions) =>
            val newPositions = newQueues.getOrElse(chunk, Set.empty)
            newQueues.remove(chunk)
            (chunk, positions ++ newPositions)
        } ++ newQueues.iterator.map {
          case (chunk, set) => (chunk, set.toSet)
        }.toSeq

      case None =>
        setTicking(false)
        source.sendSuccess("Layer removal finished", false)
    }
  }

  override def cancel(worldContext: Option[Level]): Unit = {
    if (!worldContext.forall(_ == world)) return

    setTicking(false)
    source.sendSuccess("Layer removal cancelled for this world", false)
  }
}

object RemoveLayersCommand {
  def execute(context: CommandContext[CommandSourceStack]): Either[String, Unit] = {
    val player = context.getSource.getPlayerOrException
    val world = player.getLevel
    val pos = player.blockPosition
    val fluidOption = Some(world.getFluidState(pos).getType).filter(_ != Fluids.EMPTY)
    fluidOption
      .toRight("Player not standing in a fluid!")
      .map { fluid =>
        val numLayers = Iterator.iterate(pos)(_.above).takeWhile(world.getFluidState(_).getType.isSame(fluid)).length
        context.getSource.sendSuccess(s"Removing $numLayers layers for ${ForgeRegistries.FLUIDS.getKey(fluid)} at $pos\n$typeConfirm", false)
        addPending(player, RemoveLayersCommand(context.getSource, world, pos, fluid, numLayers))
      }
  }
}
