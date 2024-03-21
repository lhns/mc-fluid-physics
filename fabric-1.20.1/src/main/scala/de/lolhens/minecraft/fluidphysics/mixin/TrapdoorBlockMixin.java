package de.lolhens.minecraft.fluidphysics.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TrapdoorBlock.class)
public class TrapdoorBlockMixin {
    @Inject(at = @At("RETURN"), method = "onUse", cancellable = true)
    public void onUse(BlockState state,
                      World world,
                      BlockPos pos,
                      PlayerEntity player,
                      Hand hand,
                      BlockHitResult hit,
                      CallbackInfoReturnable<ActionResult> info) {
        if (info.getReturnValue().isAccepted() && !world.isClient) {
            BlockPos up = pos.up();
            if (world.getFluidState(up).getFluid().matchesType(Fluids.WATER)) {
                world.updateNeighbor(up, world.getBlockState(pos).getBlock(), pos);
            }
        }
    }

    @Inject(at = @At("RETURN"), method = "neighborUpdate", cancellable = true)
    public void neighborUpdate(BlockState state,
                               World world,
                               BlockPos pos,
                               Block block,
                               BlockPos fromPos,
                               boolean notify,
                               CallbackInfo info) {
        if (!world.isClient) {
            world.updateNeighbor(pos.up(), world.getBlockState(pos).getBlock(), pos);
        }
    }
}
