package de.lolhens.minecraft.fluidphysics.command

import com.mojang.brigadier.context.CommandContext
import de.lolhens.minecraft.fluidphysics.command.CommandHandler._
import de.lolhens.minecraft.fluidphysics.mixin.FlowableFluidAccessor
import net.minecraft.block.{BlockState, Blocks, FluidBlock, FluidDrainable}
import net.minecraft.fluid.{FlowableFluid, Fluid, Fluids}
import net.minecraft.registry.Registries
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.server.world.ServerWorld
import net.minecraft.text.Text
import net.minecraft.util.math.{BlockPos, Direction}
import net.minecraft.world.World
import net.minecraft.world.chunk.Chunk

import java.util.function.Supplier
import scala.annotation.tailrec
import scala.collection.mutable

case class RemoveLayersCommand(source: ServerCommandSource,
                               world: World,
                               pos: BlockPos,
                               fluid: Fluid,
                               numLayers: Int) extends Command {
  private val minY = pos.getY
  private val maxY = minY + numLayers - 1

  var chunkQueues: Seq[(Chunk, Set[BlockPos])] = Seq.empty

  private def makeSupplier(str: String): Supplier[Text] = {
    () => Text.literal(str)
  }

  override def run(): Either[String, Unit] = {
    source.sendFeedback(makeSupplier("Starting layer removal"), false)

    chunkQueues = Seq(world.getChunk(pos) -> Set(pos))

    setTicking(true)

    Right(())
  }

  private def drainFluid(world: World, pos: BlockPos, state: BlockState): Unit = state.getBlock match {
    case fluidDrainable: FluidDrainable if !state.getBlock.isInstanceOf[FluidBlock] =>
      fluidDrainable.tryDrainFluid(world, pos, state)

    case _ =>
      fluid match {
        case flowableFluid: FlowableFluid if !state.isAir =>
          flowableFluid.asInstanceOf[FlowableFluidAccessor].callBeforeBreakingBlock(world, pos, state)

        case _ =>
      }

      world.setBlockState(pos, Blocks.AIR.getDefaultState, 18)
  }

  override def tick(worldContext: World): Unit = {
    if (worldContext != world) return

    chunkQueues.headOption match {
      case Some((currentChunk, positions)) =>
        val chunkQueuesTail = chunkQueues.tail

        val ignoredPositions = mutable.Set.empty[BlockPos]
        val newQueues: mutable.WeakHashMap[Chunk, mutable.Set[BlockPos]] = mutable.WeakHashMap.empty

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
            .filter(world.getFluidState(_).getFluid.matchesType(fluid))
            .flatMap { pos =>
              if (isCurrentChunkElseQueue(pos)) {
                drainFluid(world, pos, world.getBlockState(pos))
                Direction.values().iterator.map[BlockPos](pos.offset)
                  .filter(e => e.getY >= minY && e.getY <= maxY)
                  .filterNot(pos => ignoredPositions.contains(pos))
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
        source.sendFeedback(makeSupplier("Layer removal finished"), false)
    }
  }

  override def cancel(worldContext: Option[World]): Unit = {
    if (!worldContext.forall(_ == world)) return

    setTicking(false)
    source.sendFeedback(makeSupplier("Layer removal cancelled for this world"), false)
  }
}

object RemoveLayersCommand {
  private def makeSupplier(str: String): Supplier[Text] = {
    () => Text.literal(str)
  }
  def execute(context: CommandContext[ServerCommandSource]): Either[String, Unit] = {
    val player = context.getSource.getPlayer
    val world = player.getWorld
    val pos = player.getBlockPos
    val fluidOption = Some(world.getFluidState(pos).getFluid).filter(_ != Fluids.EMPTY)
    fluidOption
      .toRight("Player not standing in a fluid!")
      .map { fluid =>
        val numLayers = Iterator.iterate(pos)(_.up()).takeWhile(world.getFluidState(_).getFluid.matchesType(fluid)).length
        context.getSource.sendFeedback(makeSupplier(s"Removing $numLayers layers for ${Registries.FLUID.getId(fluid)} at $pos\n$typeConfirm"), false)
        addPending(player, RemoveLayersCommand(context.getSource, world, pos, fluid, numLayers))
      }
  }
}
