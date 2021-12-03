package de.lolhens.minecraft.fluidphysics.mixin;

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod;
import de.lolhens.minecraft.fluidphysics.util.FluidSourceFinder;
import net.minecraft.block.*;
import net.minecraft.block.material.PushReaction;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scala.Option;

import java.util.HashSet;
import java.util.Set;

@Mixin(PistonBlock.class)
public abstract class PistonBlockMixin {
    @Inject(at = @At("HEAD"), method = "isPushable", cancellable = true)
    private static void isPushable(BlockState state,
                                   World world,
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
            if (!(nextBlockState.isAir(world, nextBlockPos) ||
                    nextBlockState.getFluidState().getType().isSame(fluidState.getType()) ||
                    nextBlockState.getPistonPushReaction() == PushReaction.DESTROY)) {
                info.setReturnValue(false);
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "moveBlocks", cancellable = true)
    private void moveBlocks(World world,
                            BlockPos pos,
                            Direction dir,
                            boolean retract,
                            CallbackInfoReturnable<Boolean> info) {
        BlockPos blockPos = pos.relative(dir);
        if (!retract && world.getBlockState(blockPos).is(Blocks.PISTON_HEAD)) {
            world.setBlock(blockPos, Blocks.AIR.defaultBlockState(), 20);
        }

        PistonBlockStructureHelper pistonHandler = new PistonBlockStructureHelper(world, pos, dir, retract);
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
