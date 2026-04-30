package de.lolhens.minecraft.fluidphysics

import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandSourceStack
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.fml.loading.FMLPaths
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.level.LevelEvent
import net.neoforged.neoforge.event.server.ServerStoppingEvent
import net.neoforged.neoforge.event.tick.LevelTickEvent
import net.neoforged.neoforge.registries.RegisterEvent

import java.nio.file.Path

// mcdp's NeoForge loader fills constructor parameters from a context bag of
// [IEventBus, ModContainer, Dist] with subset matching (ADR-0017 — same shape vanilla
// FMLModContainer uses; see de.lhns.mcdp.core.JavaEntrypointAdapter#fillFromBag). Using
// just (IEventBus) here matches a strict subset; add ModContainer or Dist to the ctor if
// you need them.
@Mod("fluidphysics")
class FluidPhysicsNeoForge(modBus: IEventBus) {

  Platform.register(new Platform {
    override def configDir: Path = FMLPaths.CONFIGDIR.get()

    override def onServerLevelTick(cb: ServerLevel => Unit): Unit =
      NeoForge.EVENT_BUS.addListener { (ev: LevelTickEvent.Post) =>
        ev.getLevel match {
          case sl: ServerLevel => cb(sl)
          case _ =>
        }
      }

    override def onServerLevelUnload(cb: ServerLevel => Unit): Unit =
      NeoForge.EVENT_BUS.addListener { (ev: LevelEvent.Unload) =>
        ev.getLevel match {
          case sl: ServerLevel => cb(sl)
          case _ =>
        }
      }

    override def onCommandRegistration(cb: CommandDispatcher[CommandSourceStack] => Unit): Unit =
      NeoForge.EVENT_BUS.addListener { (ev: RegisterCommandsEvent) =>
        cb(ev.getDispatcher)
      }
  })

  modBus.addListener { (ev: RegisterEvent) =>
    if (ev.getRegistryKey == Registries.BLOCK) {
      FluidPhysicsMod.registerSpringBlock()
    } else if (ev.getRegistryKey == Registries.ITEM) {
      FluidPhysicsMod.registerSpringItem()
    }
  }

  modBus.addListener { (_: FMLCommonSetupEvent) =>
    FluidPhysicsMod.init()
  }
}
