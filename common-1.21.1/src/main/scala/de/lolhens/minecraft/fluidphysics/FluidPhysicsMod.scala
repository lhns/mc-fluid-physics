package de.lolhens.minecraft.fluidphysics

import de.lolhens.minecraft.fluidphysics.block.SpringBlock
import de.lolhens.minecraft.fluidphysics.command.CommandHandler
import de.lolhens.minecraft.fluidphysics.config.FluidPhysicsConfig
import de.lolhens.minecraft.fluidphysics.util.RainRefill
import net.minecraft.core.Registry
import net.minecraft.core.registries.{BuiltInRegistries, Registries}
import net.minecraft.resources.{ResourceKey, ResourceLocation}
import net.minecraft.world.item.{BlockItem, Item}
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockBehaviour

/** Shared mod logic. Platform entry points ([[FluidPhysicsFabric]] / [[FluidPhysicsNeoForge]])
  * must:
  *   1. Call [[Platform.register]] with a platform-specific impl.
  *   2. Register the block and item via [[registerSpringBlock]] / [[registerSpringItem]] at the
  *      right lifecycle point (immediately on Fabric, inside `RegisterEvent` on NeoForge).
  *   3. Call [[init]] after registrations are done to wire event listeners.
  */
object FluidPhysicsMod {
  val id: String = "fluidphysics"

  lazy val config: FluidPhysicsConfig = FluidPhysicsConfig.loadOrCreate(id)

  val SPRING_BLOCK_ID: ResourceLocation = ResourceLocation.fromNamespaceAndPath(id, "spring")
  val SPRING_BLOCK_KEY: ResourceKey[Block] = ResourceKey.create(Registries.BLOCK, SPRING_BLOCK_ID)
  val SPRING_ITEM_KEY: ResourceKey[Item] = ResourceKey.create(Registries.ITEM, SPRING_BLOCK_ID)

  val SPRING_BLOCK: Block = new SpringBlock(
    BlockBehaviour.Properties.of()
      .strength(2.0f, 6.0f)
      .requiresCorrectToolForDrops()
  )

  /** Put the spring block into BuiltInRegistries.BLOCK. Platform-specific timing:
    *  - Fabric: call anywhere during mod init.
    *  - NeoForge: call inside a RegisterEvent handler for Registries.BLOCK.
    */
  def registerSpringBlock(): Unit =
    Registry.register(BuiltInRegistries.BLOCK, SPRING_BLOCK_KEY, SPRING_BLOCK)

  /** Put the BlockItem for the spring block into BuiltInRegistries.ITEM. Same timing rules. */
  def registerSpringItem(): Unit =
    Registry.register(
      BuiltInRegistries.ITEM,
      SPRING_ITEM_KEY,
      new BlockItem(SPRING_BLOCK, new Item.Properties())
    )

  /** Wire event listeners. Call after Platform.register + block/item registration. */
  def init(): Unit = {
    config  // force config load
    RainRefill.init()
    CommandHandler.init()
  }
}
