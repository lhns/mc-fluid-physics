package de.lolhens.minecraft.fluidphysics.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TrapDoorBlock.class)
public class TrapdoorBlockMixin {
    @Inject(at = @At("RETURN"), method = "use", cancellable = true)
    public void use(BlockState state,
                    Level world,
                    BlockPos pos,
                    Player player,
                    InteractionHand hand,
                    BlockHitResult hit,
                    CallbackInfoReturnable<InteractionResult> info) {
        if (info.getReturnValue().consumesAction() && !world.isClientSide) {
            BlockPos up = pos.above();
            if (world.getFluidState(up).getType().isSame(Fluids.WATER)) {
                world.neighborChanged(up, world.getBlockState(pos).getBlock(), pos);
            }
        }
    }

    @Inject(at = @At("RETURN"), method = "neighborChanged", cancellable = true)
    public void neighborChanged(BlockState state,
                                Level world,
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
