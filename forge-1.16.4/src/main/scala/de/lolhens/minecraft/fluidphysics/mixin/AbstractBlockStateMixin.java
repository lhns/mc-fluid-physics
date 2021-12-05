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
    public abstract BlockState asState();

    @Inject(at = @At("HEAD"), method = "getPistonPushReaction", cancellable = true)
    public void getPistonPushReaction(CallbackInfoReturnable<PushReaction> info) {
        BlockState blockState = asState();
        FluidState fluidState = blockState.getFluidState();
        if (!fluidState.isEmpty() &&
                FluidPhysicsMod.config().isEnabledFor(fluidState.getType()) &&
                fluidState.isSource()) {
            info.setReturnValue(PushReaction.PUSH_ONLY);
        }
    }
}
