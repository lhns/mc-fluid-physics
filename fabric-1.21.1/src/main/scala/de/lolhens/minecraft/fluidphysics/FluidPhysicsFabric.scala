package de.lolhens.minecraft.fluidphysics

import com.mojang.brigadier.CommandDispatcher
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.{ServerTickEvents, ServerWorldEvents}
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.commands.CommandSourceStack
import net.minecraft.server.level.ServerLevel

import java.nio.file.Path

object FluidPhysicsFabric extends ModInitializer {
  override def onInitialize(): Unit = {
    Platform.register(new Platform {
      override def configDir: Path = FabricLoader.getInstance().getConfigDir

      override def onServerLevelTick(cb: ServerLevel => Unit): Unit =
        ServerTickEvents.END_WORLD_TICK.register(world => cb(world))

      override def onServerLevelUnload(cb: ServerLevel => Unit): Unit =
        ServerWorldEvents.UNLOAD.register((_, world) => cb(world))

      override def onCommandRegistration(cb: CommandDispatcher[CommandSourceStack] => Unit): Unit =
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) => cb(dispatcher))
    })

    FluidPhysicsMod.registerSpringBlock()
    FluidPhysicsMod.registerSpringItem()
    FluidPhysicsMod.init()
  }
}
