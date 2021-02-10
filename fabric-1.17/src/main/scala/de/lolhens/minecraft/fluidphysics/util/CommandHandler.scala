package de.lolhens.minecraft.fluidphysics.util

import cats.syntax.either._
import com.mojang.brigadier.context.CommandContext
import de.lolhens.minecraft.fluidphysics.horizontal
import de.lolhens.minecraft.fluidphysics.mixin.FlowableFluidAccessor
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.block.{Blocks, FluidBlock, FluidDrainable}
import net.minecraft.fluid.{FlowableFluid, Fluid, Fluids}
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.LiteralText
import net.minecraft.util.math.{BlockPos, Direction}
import net.minecraft.util.registry.Registry
import net.minecraft.world.World

import java.util.UUID
import scala.collection.mutable

object CommandHandler {
  private implicit def literalText(string: String): LiteralText = new LiteralText(string)

  private var confirmationPending: Map[UUID, PendingCommand] = Map.empty
  private var ticking: Set[(UUID, PendingCommand)] = Set.empty

  trait PendingCommand {
    def source: ServerCommandSource

    def run(): Either[String, Unit]

    protected final def setTicking(value: Boolean): Unit = {
      if (value)
        ticking += (source.getPlayer.getUuid -> this)
      else
        ticking -= (source.getPlayer.getUuid -> this)
    }

    def tick(): Unit

    def cancel(): Unit
  }

  case class PendingRemoveLayersCommand(source: ServerCommandSource,
                                        world: World,
                                        pos: BlockPos,
                                        fluid: Fluid,
                                        numLayers: Int) extends PendingCommand {
    val worldQueues: mutable.WeakHashMap[World, (Set[(BlockPos, Fluid)], Set[(BlockPos, Fluid)])] = mutable.WeakHashMap.empty

    override def run(): Either[String, Unit] = {
      source.sendFeedback("Starting layer removal", false)

      worldQueues.addOne(world, worldQueues.getOrElse(world, (Set.empty[(BlockPos, Fluid)], Set.empty[(BlockPos, Fluid)])) match {
        case (highPrioQueue, lowPrioQueue) =>
          (highPrioQueue.incl((pos, fluid)), lowPrioQueue)
      })

      setTicking(true)

      Right(())
    }

    override def tick(): Unit = { // Do whole chunks
      var newHighPrioQueue: Set[(BlockPos, Fluid)] = Set.empty
      var newLowPrioQueue: Set[(BlockPos, Fluid)] = Set.empty

      worldQueues.get(world) match {
        case Some((highPrioQueue, lowPrioQueue)) =>
          val (thisTickQueue, (nextHighPrioTickQueue, nextLowPrioTickQueue)) = {
            val (a, b) = highPrioQueue.splitAt(1000)
            val (c, d) = lowPrioQueue.splitAt(100)
            (a ++ c, (b, d))
          }

          thisTickQueue.foreach {
            case (pos, fluid) =>
              val blockState = world.getBlockState(pos)
              if (blockState.getFluidState.getFluid.matchesType(fluid)) {
                blockState.getBlock match {
                  case fluidDrainable: FluidDrainable if !blockState.getBlock.isInstanceOf[FluidBlock] =>
                    fluidDrainable.tryDrainFluid(world, pos, blockState)

                  case _ =>
                    fluid match {
                      case flowableFluid: FlowableFluid if !blockState.isAir =>
                        flowableFluid.asInstanceOf[FlowableFluidAccessor].callBeforeBreakingBlock(world, pos, blockState)
                    }

                    world.setBlockState(pos, Blocks.AIR.getDefaultState)
                }

                (horizontal.iterator ++ Iterator(Direction.UP)).foreach { direction =>
                  val offsetPos = pos.offset(direction)
                  val blockState = world.getBlockState(offsetPos)
                  if (blockState.getFluidState.getFluid.matchesType(fluid)) {
                    if (blockState.getFluidState.isStill)
                      newHighPrioQueue += ((offsetPos, fluid))
                    else
                      newLowPrioQueue += ((offsetPos, fluid))
                  }
                }
              }
          }

          newHighPrioQueue = nextHighPrioTickQueue ++ newHighPrioQueue
          newLowPrioQueue = nextLowPrioTickQueue ++ newLowPrioQueue

          if (newHighPrioQueue.isEmpty && newLowPrioQueue.isEmpty) {
            worldQueues.remove(world)
            setTicking(false)
            source.sendFeedback("Layer removal finished", false)
          } else {
            worldQueues.addOne(world, (newHighPrioQueue, newLowPrioQueue))
          }

        case None =>
          setTicking(false)
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
      ticking.foreach(_._2.tick())
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
