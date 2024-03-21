package de.lolhens.minecraft.fluidphysics

import de.lolhens.minecraft.fluidphysics.block.SpringBlock
import de.lolhens.minecraft.fluidphysics.command.CommandHandler
import de.lolhens.minecraft.fluidphysics.config.FluidPhysicsConfig
import de.lolhens.minecraft.fluidphysics.util.RainRefill
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.minecraft.block.Block
import net.minecraft.item.{BlockItem, Item, ItemGroups}
import net.minecraft.registry.{Registries, Registry}
import net.minecraft.util.Identifier


object FluidPhysicsMod extends ModInitializer {
  val id: String = "fluidphysics"

  lazy val config: FluidPhysicsConfig = FluidPhysicsConfig.loadOrCreate(id)

  val SPRING_BLOCK_ID = new Identifier(id, "spring")
  private val SPRING_BLOCK: Block = new SpringBlock(FabricBlockSettings.create().requiresTool().hardness(2.0F).resistance(6.0F))

  override def onInitialize(): Unit = {
    config

    Registry.register(Registries.BLOCK, SPRING_BLOCK_ID, SPRING_BLOCK)
    Registry.register(Registries.ITEM, SPRING_BLOCK_ID, new BlockItem(SPRING_BLOCK, new Item.Settings()))
    ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS).register(entries => entries.add(SPRING_BLOCK))

    RainRefill.init()
    CommandHandler.init()
  }
}
