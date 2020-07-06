package de.lolhens.fluidphysics.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.block.piston.PistonBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.block.AbstractBlock.class)
public class AbstractBlockMixin {
    @Inject(at = @At("HEAD"), method = "getPistonBehavior", cancellable = true)
    public void getPistonBehavior(BlockState state,
                                  CallbackInfoReturnable<PistonBehavior> info) {
        if (state.getFluidState().isStill()) {
            info.setReturnValue(PistonBehavior.PUSH_ONLY);
        }
    }
}
