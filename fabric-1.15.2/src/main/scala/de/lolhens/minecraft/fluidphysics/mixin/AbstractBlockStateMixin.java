package de.lolhens.minecraft.fluidphysics.mixin;

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.fluid.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockState.class)
public abstract class AbstractBlockStateMixin {
    @Inject(at = @At("HEAD"), method = "getPistonBehavior", cancellable = true)
    public void getPistonBehavior(CallbackInfoReturnable<PistonBehavior> info) {
        BlockState blockState = (BlockState) (Object) this;
        FluidState fluidState = blockState.getFluidState();
        if (!fluidState.isEmpty() &&
                FluidPhysicsMod.config().isEnabledFor(fluidState.getFluid()) &&
                fluidState.isStill()) {
            info.setReturnValue(PistonBehavior.PUSH_ONLY);
        }
    }
}
