package de.lolhens.minecraft.fluidphysics.mixin;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.TrapDoorBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TrapDoorBlock.class)
public class TrapdoorBlockMixin {
    @Inject(at = @At("RETURN"), method = "onBlockActivated", cancellable = true)
    public void onBlockActivated(BlockState state,
                                 World world,
                                 BlockPos pos,
                                 PlayerEntity player,
                                 Hand hand,
                                 BlockRayTraceResult hit,
                                 CallbackInfoReturnable<ActionResultType> info) {
        if (info.getReturnValue().isSuccessOrConsume() && !world.isRemote) {
            BlockPos up = pos.up();
            if (world.getFluidState(up).getFluid().isEquivalentTo(Fluids.WATER)) {
                world.neighborChanged(up, world.getBlockState(pos).getBlock(), pos);
            }
        }
    }

    @Inject(at = @At("RETURN"), method = "neighborChanged", cancellable = true)
    public void neighborChanged(BlockState state,
                                World world,
                                BlockPos pos,
                                Block block,
                                BlockPos fromPos,
                                boolean notify,
                                CallbackInfo info) {
        if (!world.isRemote) {
            world.neighborChanged(pos.up(), world.getBlockState(pos).getBlock(), pos);
        }
    }
}
