package de.lolhens.minecraft.fluidphysics.mixin;

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.material.PushReaction;
import net.minecraft.fluid.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {
    @Inject(at = @At("HEAD"), method = "getPushReaction", cancellable = true)
    public void getPushReaction(CallbackInfoReturnable<PushReaction> info) {
        AbstractBlock.AbstractBlockState blockState = (AbstractBlock.AbstractBlockState) (Object) this;
        FluidState fluidState = blockState.getFluidState();
        if (!fluidState.isEmpty() &&
                FluidPhysicsMod.config().enabledFor(fluidState.getFluid()) &&
                fluidState.isSource()) {
            info.setReturnValue(PushReaction.PUSH_ONLY);
        }
    }
}
