package de.lolhens.fluidphysics

import de.lolhens.fluidphysics.block.SpringBlock
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.minecraft.block.{Block, Material}
import net.minecraft.item.{BlockItem, Item, ItemGroup}
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry

object FluidPhysicsMod extends ModInitializer {
  val debugFluidState = false

  val SPRING_BLOCK: Block = new SpringBlock(FabricBlockSettings.of(Material.STONE).requiresTool().hardness(2.0F).resistance(6.0F))

  override def onInitialize(): Unit = {
    Registry.register(Registry.BLOCK, new Identifier("fluidphysics", "spring"), SPRING_BLOCK)
    Registry.register(Registry.ITEM, new Identifier("fluidphysics", "spring"), new BlockItem(SPRING_BLOCK, new Item.Settings().group(ItemGroup.BUILDING_BLOCKS)))
  }
}
