package de.lolhens.minecraft.fluidphysics.mixin;

import de.lolhens.minecraft.fluidphysics.util.FluidIsInfinite;
import net.minecraft.fluid.Fluids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(net.minecraft.fluid.WaterFluid.class)
public class WaterFluidMixin {
    @Inject(at = @At("HEAD"), method = "isInfinite", cancellable = true)
    protected void isInfinite(CallbackInfoReturnable<Boolean> info) {
        if (!FluidIsInfinite.isInfinite(Fluids.WATER)) {
            info.setReturnValue(false);
        }
    }
}
