package de.lolhens.minecraft.fluidphysics.mixin;

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeColors.class)
public class BiomeColorsMixin {
    @Inject(at = @At("HEAD"), method = "getAverageWaterColor", cancellable = true)
    private static void getWaterColor(BlockAndTintGetter view, BlockPos pos, CallbackInfoReturnable<Integer> info) {
        if (FluidPhysicsMod.config().getDebugFluidState()) {
            int r = 0;
            int g = 0;
            int b = 0;

            try {
                FluidState state = ((LiquidBlock) Blocks.WATER).getFluidState(view.getBlockState(pos));
                r = 255 / 8 * state.getAmount();
                g = state.isSource() ? 255 : 0;
                b = state.getValue(FlowingFluid.FALLING) ? 255 : 0;
            } catch (Exception ignored) {
            }

            int color = (r << 16) | (g << 8) | b;
            info.setReturnValue(color);
        }
    }
}
