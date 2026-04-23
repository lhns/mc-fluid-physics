package de.lolhens.minecraft.fluidphysics.mixin;

import de.lolhens.minecraft.fluidphysics.util.FluidIsInfinite;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.WaterFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// NOTE: 1.20.5 renamed the no-args `isInfinite()` to `canConvertToSource(Level)`.
// 1.21.2 changed it again to take ServerLevel — on 1.21.1 it's still Level.
@Mixin(WaterFluid.class)
public class WaterFluidMixin {
    @Inject(at = @At("HEAD"), method = "canConvertToSource(Lnet/minecraft/world/level/Level;)Z", cancellable = true)
    protected void fluidphysics$canConvertToSource(Level level, CallbackInfoReturnable<Boolean> info) {
        if (!FluidIsInfinite.isInfinite(Fluids.WATER)) {
            info.setReturnValue(false);
        }
    }
}
