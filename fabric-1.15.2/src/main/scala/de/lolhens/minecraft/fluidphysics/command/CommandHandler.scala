package de.lolhens.minecraft.fluidphysics.command

import cats.syntax.either._
import com.mojang.brigadier.context.CommandContext
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.{ServerTickEvents, ServerWorldEvents}
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.server.command.CommandManager.literal
import net.minecraft.server.command.ServerCommandSource
import net.minecraft.text.LiteralText
import net.minecraft.world.World

import java.util.UUID

object CommandHandler {
  implicit def literalText(string: String): LiteralText = new LiteralText(string)

  val typeConfirm = "type '/fluidphysics confirm' to continue"

  @volatile private var confirmationPending: Map[UUID, Command] = Map.empty
  @volatile private var ticking: Set[(UUID, Command)] = Set.empty

  trait Command {
    def source: ServerCommandSource

    def run(): Either[String, Unit]

    protected final def setTicking(value: Boolean): Unit = CommandHandler.synchronized {
      if (value)
        ticking += (source.getPlayer.getUuid -> this)
      else
        ticking -= (source.getPlayer.getUuid -> this)
    }

    def tick(world: World): Unit

    def cancel(world: Option[World]): Unit
  }

  def addPending(player: PlayerEntity, command: Command): Unit =
    confirmationPending += (player.getUuid -> command)

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

    def cancelAll(world: World): Unit = {
      ticking.foreach(_._2.cancel(Some(world)))
    }

    ServerWorldEvents.LOAD.register { (server, world) =>
      cancelAll(world)
    }

    CommandRegistrationCallback.EVENT.register { (dispatcher, dedicated) =>
      dispatcher.register(
        literal("fluidphysics")
          .`then`(literal("removelayers").executes(implicit context => commandResult {
            mustBeOperator {
              RemoveLayersCommand.execute(context)
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
                    .map(_.foreach(_._2.cancel(None)))
                }
            }
          }))
      )
    }
  }
}
