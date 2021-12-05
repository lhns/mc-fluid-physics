package de.lolhens.minecraft.fluidphysics.mixin;

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.PushReaction;
import net.minecraft.fluid.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {
    @Shadow
    public abstract BlockState func_230340_p_();

    @Inject(at = @At("HEAD"), method = "getPushReaction", cancellable = true)
    public void getPushReaction(CallbackInfoReturnable<PushReaction> info) {
        BlockState blockState = func_230340_p_();
        FluidState fluidState = blockState.getFluidState();
        if (!fluidState.isEmpty() &&
                FluidPhysicsMod.config().isEnabledFor(fluidState.getFluid()) &&
                fluidState.isSource()) {
            info.setReturnValue(PushReaction.PUSH_ONLY);
        }
    }
}
