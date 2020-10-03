package de.lolhens.minecraft.fluidphysics.mixin;

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.PushReaction;
import net.minecraft.fluid.IFluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockState.class)
public abstract class AbstractBlockStateMixin {
    @Inject(at = @At("HEAD"), method = "getPushReaction", cancellable = true)
    public void getPushReaction(CallbackInfoReturnable<PushReaction> info) {
        BlockState blockState = (BlockState) (Object) this;
        IFluidState fluidState = blockState.getFluidState();
        if (!fluidState.isEmpty() &&
                FluidPhysicsMod.config().enabledFor(fluidState.getFluid()) &&
                fluidState.isSource()) {
            info.setReturnValue(PushReaction.PUSH_ONLY);
        }
    }
}
