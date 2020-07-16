package de.lolhens.fluidphysics

import de.lolhens.fluidphysics.block.SpringBlock
import de.lolhens.fluidphysics.config.FluidPhysicsConfig
import de.lolhens.fluidphysics.util.RainRefill
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.minecraft.block.{Block, Material}
import net.minecraft.item.{BlockItem, Item, ItemGroup}
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry

object FluidPhysicsMod extends ModInitializer {
  val modId: String = "fluidphysics"
  lazy val config: FluidPhysicsConfig = FluidPhysicsConfig.loadOrCreate(modId)

  val SPRING_BLOCK_IDENTIFIER = new Identifier(modId, "spring")
  private lazy val SPRING_BLOCK: Block = new SpringBlock(FabricBlockSettings.of(Material.STONE).requiresTool().hardness(2.0F).resistance(6.0F))

  override def onInitialize(): Unit = {
    config

    Registry.register(Registry.BLOCK, SPRING_BLOCK_IDENTIFIER, SPRING_BLOCK)
    Registry.register(Registry.ITEM, SPRING_BLOCK_IDENTIFIER, new BlockItem(SPRING_BLOCK, new Item.Settings().group(ItemGroup.BUILDING_BLOCKS)))

    RainRefill.init()
  }
}
