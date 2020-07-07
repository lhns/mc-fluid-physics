package de.lolhens.fluidphysics.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.piston.PistonBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.block.AbstractBlock.AbstractBlockState.class)
public abstract class AbstractBlockStateMixin {
    @Shadow
    public abstract BlockState asBlockState();

    @Inject(at = @At("HEAD"), method = "getPistonBehavior", cancellable = true)
    public void getPistonBehavior(CallbackInfoReturnable<PistonBehavior> info) {
        BlockState blockState = asBlockState();
        if (blockState.getFluidState().isStill()) {
            info.setReturnValue(PistonBehavior.PUSH_ONLY);
        }
    }
}
