package de.lolhens.minecraft.fluidphysics

import de.lolhens.minecraft.fluidphysics.block.SpringBlock
import de.lolhens.minecraft.fluidphysics.command.CommandHandler
import de.lolhens.minecraft.fluidphysics.config.FluidPhysicsConfig
import de.lolhens.minecraft.fluidphysics.util.RainRefill
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.{BlockItem, CreativeModeTab, Item}
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.Material
import net.minecraftforge.event.RegistryEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
import net.minecraftforge.fml.{ModContainer, ModLoadingContext}
import org.apache.logging.log4j.LogManager

@Mod("fluidphysics")
object FluidPhysicsMod {
  val container: ModContainer = ModLoadingContext.get().getActiveContainer
  private val logger = LogManager.getLogger

  lazy val config: FluidPhysicsConfig = FluidPhysicsConfig.loadOrCreate(container.getModId)

  val SPRING_BLOCK_ID = new ResourceLocation(container.getModId, "spring")
  val SPRING_BLOCK: Block = new SpringBlock(BlockBehaviour.Properties.of(Material.STONE).requiresCorrectToolForDrops().strength(2.0F, 6.0F)).setRegistryName(SPRING_BLOCK_ID)

  FMLJavaModLoadingContext.get.getModEventBus.addListener { _: FMLCommonSetupEvent =>
    config
  }

  FMLJavaModLoadingContext.get.getModEventBus.addGenericListener(classOf[Block], { blockRegistryEvent: RegistryEvent.Register[Block] =>
    blockRegistryEvent.getRegistry.register(SPRING_BLOCK)
  })

  FMLJavaModLoadingContext.get.getModEventBus.addGenericListener(classOf[Item], { itemRegistryEvent: RegistryEvent.Register[Item] =>
    itemRegistryEvent.getRegistry.register(new BlockItem(SPRING_BLOCK, new Item.Properties().tab(CreativeModeTab.TAB_BUILDING_BLOCKS)).setRegistryName(SPRING_BLOCK_ID))
  })

  RainRefill.init()
  CommandHandler.init()
}
