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
    @Inject(at = @At("RETURN"), method = "use", cancellable = true)
    public void use(BlockState state,
                    World world,
                    BlockPos pos,
                    PlayerEntity player,
                    Hand hand,
                    BlockRayTraceResult hit,
                    CallbackInfoReturnable<ActionResultType> info) {
        if (info.getReturnValue().consumesAction() && !world.isClientSide) {
            BlockPos up = pos.above();
            if (world.getFluidState(up).getType().isSame(Fluids.WATER)) {
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
        if (!world.isClientSide) {
            world.neighborChanged(pos.above(), world.getBlockState(pos).getBlock(), pos);
        }
    }
}
