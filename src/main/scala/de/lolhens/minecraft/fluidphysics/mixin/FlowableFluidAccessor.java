package de.lolhens.minecraft.fluidphysics.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(net.minecraft.fluid.FlowableFluid.class)
public interface FlowableFluidAccessor {
    @Invoker
    void callBeforeBreakingBlock(WorldAccess world, BlockPos pos, BlockState state);
}
