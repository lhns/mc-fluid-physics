package de.lolhens.minecraft.fluidphysics.util

import cats.syntax.either._
import com.mojang.brigadier.context.CommandContext
import de.lolhens.minecraft.fluidphysics.mixin.FlowableFluidAccessor
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.{ServerTickEvents, ServerWorldEvents}
import net.minecraft.block.{BlockState, Blocks, FluidBlock, FluidDrainable}
import net.minecraft.fluid.{FlowableFluid, Fluid, Fluids}
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.LiteralText
import net.minecraft.util.math.{BlockPos, Direction}
import net.minecraft.util.registry.Registry
import net.minecraft.world.World
import net.minecraft.world.chunk.Chunk

import java.util.UUID
import scala.annotation.tailrec
import scala.collection.mutable

object CommandHandler {
  private implicit def literalText(string: String): LiteralText = new LiteralText(string)

  @volatile private var confirmationPending: Map[UUID, PendingCommand] = Map.empty
  @volatile private var ticking: Set[(UUID, PendingCommand)] = Set.empty

  trait PendingCommand {
    def source: ServerCommandSource

    def run(): Either[String, Unit]

    protected final def setTicking(value: Boolean): Unit = CommandHandler.synchronized {
      if (value)
        ticking += (source.getPlayer.getUuid -> this)
      else
        ticking -= (source.getPlayer.getUuid -> this)
    }

    def tick(world: World): Unit

    def cancel(): Unit
  }

  case class PendingRemoveLayersCommand(source: ServerCommandSource,
                                        world: World,
                                        pos: BlockPos,
                                        fluid: Fluid,
                                        numLayers: Int) extends PendingCommand {
    private val minY = pos.getY
    private val maxY = minY + numLayers - 1

    var chunkQueues: Seq[(Chunk, Set[BlockPos])] = Seq.empty

    override def run(): Either[String, Unit] = {
      source.sendFeedback("Starting layer removal", false)

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

    override def tick(tickingWorld: World): Unit = {
      if (tickingWorld != world) return

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

    override def cancel(): Unit = {
      setTicking(false)
      source.sendFeedback("Layer removal cancelled for this world", false)
    }
  }

  private def isOperator(source: ServerCommandSource): Boolean =
    source.getMinecraftServer.getPlayerManager.isOperator(source.getPlayer.getGameProfile)

  private def commandResult(either: Either[String, Unit])
                           (implicit context: CommandContext[ServerCommandSource]): Int = either match {
    case Right(_) => 1
    case Left(error) =>
      context.getSource.sendError(error)
      -1
  }

  private def mustBeOperator(f: => Either[String, Unit])
                            (implicit context: CommandContext[ServerCommandSource]): Either[String, Unit] =
    if (isOperator(context.getSource)) f
    else Left("Player must be operator!")

  def init(): Unit = {
    ServerTickEvents.END_WORLD_TICK.register { world =>
      ticking.foreach(_._2.tick(world))
    }

    ServerWorldEvents.UNLOAD.register { (server, world) =>
      ticking.foreach(_._2.cancel())
      ticking = Set.empty
    }

    CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) => {
      val typeConfirm = "type '/fluidphysics confirm' to continue"

      dispatcher.register(
        literal("fluidphysics")
          .`then`(literal("removelayers").executes(implicit context => commandResult {
            mustBeOperator {
              val player = context.getSource.getPlayer
              val world = player.getServerWorld
              val pos = player.getBlockPos
              val fluidOption = Some(world.getFluidState(pos).getFluid).filter(_ != Fluids.EMPTY)
              fluidOption
                .toRight("Player not standing in a fluid!")
                .map { fluid =>
                  val numLayers = Iterator.iterate(pos)(_.up()).takeWhile(world.getFluidState(_).getFluid.matchesType(fluid)).length
                  context.getSource.sendFeedback(s"Removing $numLayers layers for ${Registry.FLUID.getId(fluid)} at $pos\n$typeConfirm", false)
                  confirmationPending += (player.getUuid -> PendingRemoveLayersCommand(context.getSource, world, pos, fluid, numLayers))
                }
            }
          }))
          .`then`(literal("confirm").executes(implicit context => commandResult {
            mustBeOperator {
              val player = context.getSource.getPlayer
              confirmationPending.get(player.getUuid)
                .toRight("No pending command!")
                .map { pending =>
                  confirmationPending -= player.getUuid
                  pending.run()
                }
            }
          }))
          .`then`(literal("cancel").executes(implicit context => commandResult {
            mustBeOperator {
              val player = context.getSource.getPlayer
              confirmationPending.get(player.getUuid)
                .toRight("No pending command!")
                .map { _ =>
                  confirmationPending -= player.getUuid
                  context.getSource.sendFeedback("Pending command cancelled", false)
                }
                .leftFlatMap { left =>
                  Some(ticking.filter(_._1 == player.getUuid))
                    .filter(_.nonEmpty)
                    .toRight(left)
                    .map(_.foreach(_._2.cancel()))
                }
            }
          }))
      )
    })
  }
}
