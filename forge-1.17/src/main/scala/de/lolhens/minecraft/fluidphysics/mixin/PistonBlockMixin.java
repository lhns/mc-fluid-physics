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
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.PushReaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scala.Option;

import java.util.HashSet;
import java.util.Set;

@Mixin(PistonBaseBlock.class)
public abstract class PistonBlockMixin {
    @Inject(at = @At("HEAD"), method = "isPushable", cancellable = true)
    private static void isPushable(BlockState state,
                                   Level world,
                                   BlockPos pos,
                                   Direction motionDir,
                                   boolean canBreak,
                                   Direction pistonDir,
                                   CallbackInfoReturnable<Boolean> info) {
        FluidState fluidState = state.getFluidState();
        if (!fluidState.isEmpty() &&
                FluidPhysicsMod.config().enabledFor(fluidState.getType()) &&
                fluidState.isSource()) {
            BlockPos nextBlockPos = pos.relative(motionDir);
            BlockState nextBlockState = world.getBlockState(nextBlockPos);
            if (!(nextBlockState.isAir() ||
                    nextBlockState.getFluidState().getType().isSame(fluidState.getType()) ||
                    nextBlockState.getPistonPushReaction() == PushReaction.DESTROY)) {
                info.setReturnValue(false);
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "moveBlocks", cancellable = true)
    private void moveBlocks(Level world,
                            BlockPos pos,
                            Direction dir,
                            boolean retract,
                            CallbackInfoReturnable<Boolean> info) {
        BlockPos blockPos = pos.relative(dir);
        if (!retract && world.getBlockState(blockPos).is(Blocks.PISTON_HEAD)) {
            world.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 20);
        }

        PistonStructureResolver pistonHandler = new PistonStructureResolver(world, pos, dir, retract);
        if (!pistonHandler.resolve()) {
            info.setReturnValue(false);
        } else {
            Direction oppositeDir = dir.getOpposite();

            Set<BlockPos> blockPosSet = new HashSet<>();
            blockPosSet.add(blockPos);
            for (BlockPos movedBlockPos : pistonHandler.getToPush()) {
                blockPosSet.add(movedBlockPos);
                blockPosSet.add(movedBlockPos.relative(dir));
            }

            for (BlockPos currentBlockPos : blockPosSet) {
                BlockState blockState = world.getBlockState(currentBlockPos);
                FluidState fluidState = blockState.getFluidState();

                if (!fluidState.isEmpty() &&
                        FluidPhysicsMod.config().enabledFor(fluidState.getType()) &&
                        fluidState.getType() instanceof FlowingFluid && !fluidState.isSource()) {
                    FlowingFluid fluid = (FlowingFluid) fluidState.getType();

                    Option<BlockPos> sourcePos = FluidSourceFinder.findSource(
                            world,
                            currentBlockPos,
                            fluidState.getType(),
                            oppositeDir,
                            FluidSourceFinder.setOf(blockPosSet),
                            true,
                            true
                    );

                    if (sourcePos.isDefined()) {
                        FluidState still = fluid.getSource(false);
                        FluidSourceFinder.moveSource(world, sourcePos.get(), currentBlockPos, blockState, fluid, still);
                    }
                }
            }
        }
    }
}
