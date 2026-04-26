package de.lolhens.minecraft.fluidphysics

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerLevel

import java.nio.file.Path

/** Platform abstraction so the common module can register events and find its config dir
  * without depending on Fabric API or the NeoForge event bus. The platform modules call
  * `Platform.register(...)` from their entry point before `FluidPhysicsMod.init()` runs.
  */
trait Platform {
  def configDir: Path
  def onServerLevelTick(cb: ServerLevel => Unit): Unit
  def onServerLevelUnload(cb: ServerLevel => Unit): Unit
  def onCommandRegistration(cb: CommandDispatcher[CommandSourceStack] => Unit): Unit
}

object Platform {
  @volatile private var _instance: Platform = _

  def instance: Platform = {
    val p = _instance
    if (p == null) throw new IllegalStateException("Platform not registered yet — call Platform.register from the platform entry point before FluidPhysicsMod.init()")
    p
  }

  def register(p: Platform): Unit = _instance = p
}
