package de.lolhens.fluidphysics.mixin;

import de.lolhens.fluidphysics.FluidPhysicsMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.fluid.FluidState;
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
        FluidState fluidState = blockState.getFluidState();
        if (!fluidState.isEmpty() && FluidPhysicsMod.enabledFor(fluidState.getFluid()) && fluidState.isStill()) {
            info.setReturnValue(PistonBehavior.PUSH_ONLY);
        }
    }
}
