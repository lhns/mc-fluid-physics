package de.lolhens.minecraft.fluidphysics

import de.lolhens.minecraft.fluidphysics.block.SpringBlock
import de.lolhens.minecraft.fluidphysics.command.CommandHandler
import de.lolhens.minecraft.fluidphysics.config.FluidPhysicsConfig
import de.lolhens.minecraft.fluidphysics.util.RainRefill
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.minecraft.block.{Block, Material}
import net.minecraft.item.{BlockItem, Item, ItemGroup}
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry

object FluidPhysicsMod extends ModInitializer {
  val id: String = "fluidphysics"

  lazy val config: FluidPhysicsConfig = FluidPhysicsConfig.loadOrCreate(id)

  val SPRING_BLOCK_ID = new Identifier(id, "spring")
  val SPRING_BLOCK: Block = new SpringBlock(FabricBlockSettings.of(Material.STONE).requiresTool().hardness(2.0F).resistance(6.0F))

  override def onInitialize(): Unit = {
    config

    Registry.register(Registry.BLOCK, SPRING_BLOCK_ID, SPRING_BLOCK)
    Registry.register(Registry.ITEM, SPRING_BLOCK_ID, new BlockItem(SPRING_BLOCK, new Item.Settings().group(ItemGroup.BUILDING_BLOCKS)))

    RainRefill.init()
    CommandHandler.init()
  }
}
