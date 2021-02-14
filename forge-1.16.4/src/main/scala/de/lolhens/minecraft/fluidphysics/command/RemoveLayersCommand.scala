package de.lolhens.minecraft.fluidphysics.command

import com.mojang.brigadier.context.CommandContext
import de.lolhens.minecraft.fluidphysics.command.CommandHandler._
import de.lolhens.minecraft.fluidphysics.mixin.FlowableFluidAccessor
import net.minecraft.block.{BlockState, Blocks, FlowingFluidBlock, IBucketPickupHandler}
import net.minecraft.command.CommandSource
import net.minecraft.fluid.{FlowingFluid, Fluid, Fluids}
import net.minecraft.util.Direction
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraft.world.chunk.IChunk
import net.minecraftforge.registries.ForgeRegistries

import scala.annotation.tailrec
import scala.collection.mutable

case class RemoveLayersCommand(source: CommandSource,
                               world: World,
                               pos: BlockPos,
                               fluid: Fluid,
                               numLayers: Int) extends Command {
  private val minY = pos.getY
  private val maxY = minY + numLayers - 1

  var chunkQueues: Seq[(IChunk, Set[BlockPos])] = Seq.empty

  override def run(): Either[String, Unit] = {
    source.sendFeedback("Starting layer removal", false)

    chunkQueues = Seq(world.getChunk(pos) -> Set(pos))

    setTicking(true)

    Right(())
  }

  private def drainFluid(world: World, pos: BlockPos, state: BlockState): Unit = state.getBlock match {
    case fluidDrainable: IBucketPickupHandler if !state.getBlock.isInstanceOf[FlowingFluidBlock] =>
      fluidDrainable.pickupFluid(world, pos, state)

    case _ =>
      fluid match {
        case flowableFluid: FlowingFluid if !state.getBlock.isAir(state, world, pos) =>
          flowableFluid.asInstanceOf[FlowableFluidAccessor].callBeforeReplacingBlock(world, pos, state)

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
        val newQueues: mutable.WeakHashMap[IChunk, mutable.Set[BlockPos]] = mutable.WeakHashMap.empty

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
            .filter(world.getFluidState(_).getFluid.isEquivalentTo(fluid))
            .flatMap { pos =>
              if (isCurrentChunkElseQueue(pos)) {
                drainFluid(world, pos, world.getBlockState(pos))
                Direction.values().iterator.map(pos.offset)
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
        source.sendFeedback("Layer removal finished", false)
    }
  }

  override def cancel(worldContext: Option[World]): Unit = {
    if (!worldContext.forall(_ == world)) return

    setTicking(false)
    source.sendFeedback("Layer removal cancelled for this world", false)
  }
}

object RemoveLayersCommand {
  def execute(context: CommandContext[CommandSource]): Either[String, Unit] = {
    val player = context.getSource.asPlayer
    val world = player.getServerWorld
    val pos = player.getPosition
    val fluidOption = Some(world.getFluidState(pos).getFluid).filter(_ != Fluids.EMPTY)
    fluidOption
      .toRight("Player not standing in a fluid!")
      .map { fluid =>
        val numLayers = Iterator.iterate(pos)(_.up()).takeWhile(world.getFluidState(_).getFluid.isEquivalentTo(fluid)).length
        context.getSource.sendFeedback(s"Removing $numLayers layers for ${ForgeRegistries.FLUIDS.getKey(fluid)} at $pos\n$typeConfirm", false)
        addPending(player, RemoveLayersCommand(context.getSource, world, pos, fluid, numLayers))
      }
  }
}
