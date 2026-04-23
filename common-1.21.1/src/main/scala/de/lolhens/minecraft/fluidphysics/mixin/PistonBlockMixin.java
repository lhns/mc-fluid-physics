package de.lolhens.minecraft.fluidphysics.mixin;

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod;
import de.lolhens.minecraft.fluidphysics.util.FluidSourceFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.PushReaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

// Yarn: PistonBlock.isMovable -> Mojmap: PistonBaseBlock.isPushable
// Yarn: PistonBlock.move      -> Mojmap: PistonBaseBlock.moveBlocks
// Yarn: PistonHandler         -> Mojmap: PistonStructureResolver
@Mixin(PistonBaseBlock.class)
public abstract class PistonBlockMixin {
    @Inject(at = @At("HEAD"), method = "isPushable", cancellable = true)
    private static void fluidphysics$isPushable(BlockState state,
                                                Level level,
                                                BlockPos pos,
                                                Direction motionDir,
                                                boolean canBreak,
                                                Direction pistonDir,
                                                CallbackInfoReturnable<Boolean> info) {
        FluidState fluidState = state.getFluidState();
        if (!fluidState.isEmpty() &&
                FluidPhysicsMod.config().isEnabledFor(fluidState.getType()) &&
                fluidState.isSource()) {
            BlockPos nextBlockPos = pos.relative(motionDir);
            BlockState nextBlockState = level.getBlockState(nextBlockPos);
            if (!(nextBlockState.isAir() ||
                    nextBlockState.getFluidState().getType().isSame(fluidState.getType()) ||
                    nextBlockState.getPistonPushReaction() == PushReaction.DESTROY)) {
                info.setReturnValue(false);
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "moveBlocks", cancellable = true)
    private void fluidphysics$moveBlocks(Level level,
                                         BlockPos pos,
                                         Direction dir,
                                         boolean retract,
                                         CallbackInfoReturnable<Boolean> info) {
        BlockPos blockPos = pos.relative(dir);
        if (!retract && level.getBlockState(blockPos).is(Blocks.PISTON_HEAD)) {
            level.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 20);
        }

        PistonStructureResolver resolver = new PistonStructureResolver(level, pos, dir, retract);
        if (!resolver.resolve()) {
            info.setReturnValue(false);
        } else {
            Direction oppositeDir = dir.getOpposite();

            Set<BlockPos> blockPosSet = new HashSet<>();
            blockPosSet.add(blockPos);
            for (BlockPos movedBlockPos : resolver.getToPush()) {
                blockPosSet.add(movedBlockPos);
                blockPosSet.add(movedBlockPos.relative(dir));
            }

            for (BlockPos currentBlockPos : blockPosSet) {
                BlockState blockState = level.getBlockState(currentBlockPos);
                FluidState fluidState = blockState.getFluidState();

                if (!fluidState.isEmpty() &&
                        FluidPhysicsMod.config().isEnabledFor(fluidState.getType()) &&
                        fluidState.getType() instanceof FlowingFluid && !fluidState.isSource()) {
                    FlowingFluid fluid = (FlowingFluid) fluidState.getType();

                    BlockPos sourcePos = FluidSourceFinder.findSourceOrNull(
                            level,
                            currentBlockPos,
                            fluidState.getType(),
                            oppositeDir,
                            blockPosSet,
                            true,
                            true
                    );

                    if (sourcePos != null) {
                        FluidState still = fluid.getSource(false);
                        FluidSourceFinder.moveSource(level, sourcePos, currentBlockPos, blockState, fluid, still);
                    }
                }
            }
        }
    }
}
