package de.lolhens.minecraft.fluidphysics.command

import cats.syntax.either.*
import com.mojang.brigadier.context.CommandContext
import de.lolhens.minecraft.fluidphysics.Platform
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

import java.util.UUID

object CommandHandler {
  given literalText: Conversion[String, Component] = Component.literal(_)

  val typeConfirm = "type '/fluidphysics confirm' to continue"

  @volatile private var confirmationPending: Map[UUID, Command] = Map.empty
  @volatile private var ticking: Set[(UUID, Command)] = Set.empty

  trait Command {
    def source: CommandSourceStack

    def run(): Either[String, Unit]

    protected final def setTicking(value: Boolean): Unit = CommandHandler.synchronized {
      val player = source.getPlayer
      if (player == null) return
      val key = player.getUUID -> this
      if (value) ticking += key
      else ticking -= key
    }

    def tick(world: Level): Unit

    def cancel(world: Option[Level]): Unit
  }

  def addPending(player: Player, command: Command): Unit =
    confirmationPending += (player.getUUID -> command)

  private def isOperator(source: CommandSourceStack): Boolean = {
    val server = source.getServer
    val player = source.getPlayer
    player != null && server.getPlayerList.isOp(player.getGameProfile)
  }

  private def commandResult(either: Either[String, Unit])
                           (using context: CommandContext[CommandSourceStack]): Int = either match {
    case Right(_) => 1
    case Left(error) =>
      context.getSource.sendFailure(Component.literal(error))
      -1
  }

  private def mustBeOperator(f: => Either[String, Unit])
                            (using context: CommandContext[CommandSourceStack]): Either[String, Unit] =
    if (isOperator(context.getSource)) f
    else Left("Player must be operator!")

  def init(): Unit = {
    Platform.instance.onServerLevelTick { world =>
      ticking.foreach(_._2.tick(world))
    }

    Platform.instance.onServerLevelUnload { world =>
      ticking.foreach(_._2.cancel(Some(world)))
    }

    Platform.instance.onCommandRegistration { dispatcher =>
      dispatcher.register(
        literal("fluidphysics")
          .`then`(literal("removelayers").executes { ctx =>
            given CommandContext[CommandSourceStack] = ctx
            commandResult {
              mustBeOperator {
                RemoveLayersCommand.execute(ctx)
              }
            }
          })
          .`then`(literal("confirm").executes { ctx =>
            given CommandContext[CommandSourceStack] = ctx
            commandResult {
              mustBeOperator {
                val player = ctx.getSource.getPlayerOrException
                confirmationPending.get(player.getUUID)
                  .toRight("No pending command!")
                  .map { pending =>
                    confirmationPending -= player.getUUID
                    pending.run()
                  }
              }
            }
          })
          .`then`(literal("cancel").executes { ctx =>
            given CommandContext[CommandSourceStack] = ctx
            commandResult {
              mustBeOperator {
                val player = ctx.getSource.getPlayerOrException
                confirmationPending.get(player.getUUID)
                  .toRight("No pending command!")
                  .map { _ =>
                    confirmationPending -= player.getUUID
                    ctx.getSource.sendSuccess(() => Component.literal("Pending command cancelled"), false)
                  }
                  .leftFlatMap { left =>
                    Some(ticking.filter(_._1 == player.getUUID))
                      .filter(_.nonEmpty)
                      .toRight(left)
                      .map(_.foreach(_._2.cancel(None)))
                  }
              }
            }
          })
      )
    }
  }
}
