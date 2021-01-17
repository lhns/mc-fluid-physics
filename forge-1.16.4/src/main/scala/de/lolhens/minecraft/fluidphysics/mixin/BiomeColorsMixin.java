package de.lolhens.minecraft.fluidphysics.mixin;

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod;
import net.minecraft.block.Blocks;
import net.minecraft.block.FlowingFluidBlock;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockDisplayReader;
import net.minecraft.world.biome.BiomeColors;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BiomeColors.class)
public class BiomeColorsMixin {
    @Inject(at = @At("HEAD"), method = "getWaterColor", cancellable = true)
    private static void getWaterColor(IBlockDisplayReader view, BlockPos pos, CallbackInfoReturnable<Integer> info) {
        if (FluidPhysicsMod.config().getDebugFluidState()) {
            int r = 0;
            int g = 0;
            int b = 0;

            try {
                FluidState state = ((FlowingFluidBlock) Blocks.WATER).getFluidState(view.getBlockState(pos));
                r = 255 / 8 * state.getLevel();
                g = state.isSource() ? 255 : 0;
                b = state.get(FlowingFluid.FALLING) ? 255 : 0;
            } catch (Exception ignored) {
            }

            int color = (r << 16) | (g << 8) | b;
            info.setReturnValue(color);
        }
    }
}
