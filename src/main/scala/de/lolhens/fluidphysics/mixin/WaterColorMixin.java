package de.lolhens.fluidphysics.mixin;

import de.lolhens.fluidphysics.FluidPhysicsMod;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.client.color.world.BiomeColors;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeColors.class)
public class WaterColorMixin {
    @Inject(at = @At("HEAD"), method = "getWaterColor", cancellable = true)
    private static void getWaterColor(BlockRenderView view, BlockPos pos, CallbackInfoReturnable<Integer> info) {
        if (FluidPhysicsMod.debugFluidState()) {
            int r = 0;
            int g = 0;
            int b = 0;

            try {
                FluidState state = ((FluidBlock) Blocks.WATER).getFluidState(view.getBlockState(pos));
                r = 255 / 8 * state.getLevel();
                g = state.isStill() ? 255 : 0;
                b = state.get(FlowableFluid.FALLING) ? 255 : 0;
            } catch (Exception e) {
            }

            int color = (r << 16) | (g << 8) | b;
            info.setReturnValue(color);
        }
    }
}
