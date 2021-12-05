package de.lolhens.minecraft.fluidphysics.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FlowingFluid.class)
public interface FlowableFluidAccessor {
    @Invoker
    void callBeforeDestroyingBlock(LevelAccessor world, BlockPos pos, BlockState state);
}
