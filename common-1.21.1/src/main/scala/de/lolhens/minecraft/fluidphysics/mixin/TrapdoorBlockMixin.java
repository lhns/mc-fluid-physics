package de.lolhens.minecraft.fluidphysics.mixin;

import net.minecraft.core.BlockPos;
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

// Yarn: onUse(state, world, pos, player, hand, hit) -> Mojmap: useWithoutItem(state, level, pos, player, hit)  (Hand param dropped in 1.20.5)
// Yarn: neighborUpdate -> Mojmap: neighborChanged
@Mixin(TrapDoorBlock.class)
public class TrapdoorBlockMixin {
    @Inject(at = @At("RETURN"), method = "useWithoutItem", cancellable = true)
    public void fluidphysics$useWithoutItem(BlockState state,
                                            Level level,
                                            BlockPos pos,
                                            Player player,
                                            BlockHitResult hit,
                                            CallbackInfoReturnable<InteractionResult> info) {
        InteractionResult result = info.getReturnValue();
        if (result != null && result.consumesAction() && !level.isClientSide) {
            BlockPos up = pos.above();
            if (level.getFluidState(up).getType().isSame(Fluids.WATER)) {
                level.neighborChanged(up, level.getBlockState(pos).getBlock(), pos);
            }
        }
    }

    @Inject(at = @At("RETURN"), method = "neighborChanged", cancellable = true)
    public void fluidphysics$neighborChanged(BlockState state,
                                             Level level,
                                             BlockPos pos,
                                             Block block,
                                             BlockPos fromPos,
                                             boolean notify,
                                             CallbackInfo info) {
        if (!level.isClientSide) {
            level.neighborChanged(pos.above(), level.getBlockState(pos).getBlock(), pos);
        }
    }
}
