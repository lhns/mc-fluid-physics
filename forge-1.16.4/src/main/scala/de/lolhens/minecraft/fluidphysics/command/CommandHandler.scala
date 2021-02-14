package de.lolhens.minecraft.fluidphysics.command

import cats.syntax.either._
import com.mojang.brigadier.context.CommandContext
import net.minecraft.command.CommandSource
import net.minecraft.command.Commands._
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.util.text.{ITextComponent, StringTextComponent}
import net.minecraft.world.World
import net.minecraft.world.server.ServerWorld
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.TickEvent.Phase
import net.minecraftforge.event.world.WorldEvent
import net.minecraftforge.event.{RegisterCommandsEvent, TickEvent}

import java.util.UUID

object CommandHandler {
  implicit def literalText(string: String): ITextComponent = new StringTextComponent(string)

  val typeConfirm = "type '/fluidphysics confirm' to continue"

  @volatile private var confirmationPending: Map[UUID, Command] = Map.empty
  @volatile private var ticking: Set[(UUID, Command)] = Set.empty

  trait Command {
    def source: CommandSource

    def run(): Either[String, Unit]

    protected final def setTicking(value: Boolean): Unit = CommandHandler.synchronized {
      if (value)
        ticking += (source.asPlayer.getUniqueID -> this)
      else
        ticking -= (source.asPlayer.getUniqueID -> this)
    }

    def tick(world: World): Unit

    def cancel(world: Option[World]): Unit
  }

  def addPending(player: PlayerEntity, command: Command): Unit =
    confirmationPending += (player.getUniqueID -> command)

  private def isOperator(source: CommandSource): Boolean =
    source.getServer.getPlayerList.canSendCommands(source.asPlayer.getGameProfile)

  private def commandResult(either: Either[String, Unit])
                           (implicit context: CommandContext[CommandSource]): Int = either match {
    case Right(_) => 1
    case Left(error) =>
      context.getSource.sendErrorMessage(error)
      -1
  }

  private def mustBeOperator(f: => Either[String, Unit])
                            (implicit context: CommandContext[CommandSource]): Either[String, Unit] =
    if (isOperator(context.getSource)) f
    else Left("Player must be operator!")

  def init(): Unit = {
    MinecraftForge.EVENT_BUS.addListener { event: TickEvent.WorldTickEvent =>
      (event.phase, event.world) match {
        case (Phase.END, serverWorld: ServerWorld) =>
          ticking.foreach(_._2.tick(serverWorld))

        case _ =>
      }
    }

    def cancelAll(world: World): Unit = {
      ticking.foreach(_._2.cancel(Some(world)))
    }

    MinecraftForge.EVENT_BUS.addListener { event: WorldEvent.Unload =>
      cancelAll(event.getWorld.asInstanceOf[World])
      println("UNLOAD")
    }

    MinecraftForge.EVENT_BUS.addListener { registerCommandsEvent: RegisterCommandsEvent =>
      registerCommandsEvent.getDispatcher.register(
        literal("fluidphysics")
          .`then`(literal("removelayers").executes(implicit context => commandResult {
            mustBeOperator {
              RemoveLayersCommand.execute(context)
            }
          }))
          .`then`(literal("confirm").executes(implicit context => commandResult {
            mustBeOperator {
              val player = context.getSource.asPlayer
              confirmationPending.get(player.getUniqueID)
                .toRight("No pending command!")
                .map { pending =>
                  confirmationPending -= player.getUniqueID
                  pending.run()
                }
            }
          }))
          .`then`(literal("cancel").executes(implicit context => commandResult {
            mustBeOperator {
              val player = context.getSource.asPlayer
              confirmationPending.get(player.getUniqueID)
                .toRight("No pending command!")
                .map { _ =>
                  confirmationPending -= player.getUniqueID
                  context.getSource.sendFeedback("Pending command cancelled", false)
                }
                .leftFlatMap { left =>
                  Some(ticking.filter(_._1 == player.getUniqueID))
                    .filter(_.nonEmpty)
                    .toRight(left)
                    .map(_.foreach(_._2.cancel(None)))
                }
            }
          }))
      )
    }
  }
}
