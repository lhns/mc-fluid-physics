package de.lolhens.fluidphysics

import de.lolhens.fluidphysics.block.SpringBlock
import de.lolhens.fluidphysics.util.RainRefill
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.minecraft.block.{Block, Material}
import net.minecraft.fluid.{Fluid, Fluids}
import net.minecraft.item.{BlockItem, Item, ItemGroup}
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry

object FluidPhysicsMod extends ModInitializer {
  private val SPRING_BLOCK: Block = new SpringBlock(FabricBlockSettings.of(Material.STONE).requiresTool().hardness(2.0F).resistance(6.0F))

  override def onInitialize(): Unit = {
    Registry.register(Registry.BLOCK, new Identifier("fluidphysics", "spring"), SPRING_BLOCK)
    Registry.register(Registry.ITEM, new Identifier("fluidphysics", "spring"), new BlockItem(SPRING_BLOCK, new Item.Settings().group(ItemGroup.BUILDING_BLOCKS)))

    RainRefill.init()
  }

  def debugFluidState: Boolean = false

  def springBlock: Option[Block] = {
    Some(SPRING_BLOCK)
  }

  def springAllowsInfiniteWater: Boolean = true

  def flowOverSources: Boolean = true

  def enabledFor(fluid: Fluid): Boolean = true

  case class RainRefillOptions(probability: Double,
                               refillFluid: Fluid => Boolean)

  def rainRefill: Option[RainRefillOptions] = Some(RainRefillOptions(
    probability = 0.2,
    refillFluid = fluid => Array(Fluids.WATER).exists(_.matchesType(fluid))
  ))
}
