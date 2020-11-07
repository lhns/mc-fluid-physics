package de.lolhens.minecraft.fluidphysics.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FlowingFluid.class)
public interface FlowableFluidAccessor {
    @Invoker
    void callBeforeReplacingBlock(IWorld world, BlockPos pos, BlockState state);
}
