package de.lolhens.fluidphysics

import de.lolhens.fluidphysics.block.SpringBlock
import de.lolhens.fluidphysics.config.Config
import de.lolhens.fluidphysics.util.RainRefill
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import net.minecraft.block.{Block, Material}
import net.minecraft.fluid.{Fluid, Fluids}
import net.minecraft.item.{BlockItem, Item, ItemGroup}
import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry

object FluidPhysicsMod extends ModInitializer {
  val modId: String = "fluidphysics"
  lazy val config: Config = Config.loadOrCreate(modId)

  val SPRING_BLOCK_IDENTIFIER = new Identifier(modId, "spring")
  lazy val SPRING_BLOCK: Block = new SpringBlock(FabricBlockSettings.of(Material.STONE).requiresTool().hardness(2.0F).resistance(6.0F))

  override def onInitialize(): Unit = {
    config
    if (config.springBlock.contains(SPRING_BLOCK_IDENTIFIER)) {
      Registry.register(Registry.BLOCK, SPRING_BLOCK_IDENTIFIER, SPRING_BLOCK)
      Registry.register(Registry.ITEM, SPRING_BLOCK_IDENTIFIER, new BlockItem(SPRING_BLOCK, new Item.Settings().group(ItemGroup.BUILDING_BLOCKS)))
    }

    RainRefill.init()
  }

  def debugFluidState: Boolean = config.debugFluidState

  lazy val springBlock: Option[Block] = config.springBlock.map(Registry.BLOCK.get)

  def springAllowsInfiniteWater: Boolean = config.springAllowsInfiniteWater

  def flowOverSources: Boolean = config.flowOverSources

  def enabledFor(fluid: Fluid): Boolean = fluid match {
    case Fluids.WATER | Fluids.FLOWING_WATER => config.enabledForWater
    case Fluids.LAVA | Fluids.FLOWING_LAVA => config.enabledForLava
    case _ => false
  }

  case class RainRefillOptions(probability: Double,
                               refillFluid: Fluid => Boolean)

  def rainRefill: Option[RainRefillOptions] = Some(RainRefillOptions(
    probability = 0.2,
    refillFluid = fluid => Array(Fluids.WATER).exists(_.matchesType(fluid))
  ))
}
