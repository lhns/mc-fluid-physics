package de.lolhens.minecraft.fluidphysics.mixin;

import de.lolhens.minecraft.fluidphysics.util.FluidIsInfinite;
import net.minecraft.fluid.Fluids;
import net.minecraft.fluid.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WaterFluid.class)
public class WaterFluidMixin {
    @Inject(at = @At("HEAD"), method = "canConvertToSource", cancellable = true)
    protected void canConvertToSource(CallbackInfoReturnable<Boolean> info) {
        if (!FluidIsInfinite.isInfinite(Fluids.WATER)) {
            info.setReturnValue(false);
        }
    }
}
