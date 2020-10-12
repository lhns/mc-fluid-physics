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
    @Inject(at = @At("HEAD"), method = "canPush", cancellable = true)
    private static void canPush(BlockState state,
                                World world,
                                BlockPos pos,
                                Direction motionDir,
                                boolean canBreak,
                                Direction pistonDir,
                                CallbackInfoReturnable<Boolean> info) {
        FluidState fluidState = state.getFluidState();
        if (!fluidState.isEmpty() &&
                FluidPhysicsMod.config().enabledFor(fluidState.getFluid()) &&
                fluidState.isSource()) {
            BlockPos nextBlockPos = pos.offset(motionDir);
            BlockState nextBlockState = world.getBlockState(nextBlockPos);
            if (!(nextBlockState.getFluidState().getFluid().isEquivalentTo(fluidState.getFluid()) ||
                    nextBlockState.getPushReaction() == PushReaction.DESTROY)) {
                info.setReturnValue(false);
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "doMove", cancellable = true)
    private void doMove(World world,
                        BlockPos pos,
                        Direction dir,
                        boolean retract,
                        CallbackInfoReturnable<Boolean> info) {
        BlockPos blockPos = pos.offset(dir);
        if (!retract && world.getBlockState(blockPos).isIn(Blocks.PISTON_HEAD)) {
            world.setBlockState(blockPos, Blocks.AIR.getDefaultState(), 20);
        }

        PistonBlockStructureHelper pistonHandler = new PistonBlockStructureHelper(world, pos, dir, retract);
        if (!pistonHandler.canMove()) {
            info.setReturnValue(false);
        } else {
            Direction oppositeDir = dir.getOpposite();

            Set<BlockPos> blockPosSet = new HashSet<>();
            blockPosSet.add(blockPos);
            for (BlockPos movedBlockPos : pistonHandler.getBlocksToMove()) {
                blockPosSet.add(movedBlockPos);
                blockPosSet.add(movedBlockPos.offset(dir));
            }

            for (BlockPos currentBlockPos : blockPosSet) {
                BlockState blockState = world.getBlockState(currentBlockPos);
                FluidState fluidState = blockState.getFluidState();

                if (!fluidState.isEmpty() &&
                        FluidPhysicsMod.config().enabledFor(fluidState.getFluid()) &&
                        fluidState.getFluid() instanceof FlowingFluid && !fluidState.isSource()) {
                    FlowingFluid fluid = (FlowingFluid) fluidState.getFluid();

                    Option<BlockPos> sourcePos = FluidSourceFinder.findSource(
                            world,
                            currentBlockPos,
                            fluidState.getFluid(),
                            oppositeDir,
                            FluidSourceFinder.setOf(blockPosSet),
                            true,
                            true
                    );

                    if (sourcePos.isDefined()) {
                        FluidState still = fluid.getStillFluidState(false);
                        int newSourceLevel = still.getLevel() - 1;
                        FluidState newSourceFluidState = fluid.getFlowingFluidState(newSourceLevel, false);

                        BlockState sourceState = world.getBlockState(sourcePos.get());

                        // Drain source block
                        if (sourceState.getBlock() instanceof IBucketPickupHandler && !(sourceState.getBlock() instanceof FlowingFluidBlock)) {
                            ((IBucketPickupHandler) sourceState.getBlock()).pickupFluid(world, sourcePos.get(), sourceState);
                        } else {
                            if (!sourceState.isAir(world, sourcePos.get())) {
                                ((FlowableFluidAccessor) fluid).callBeforeReplacingBlock(world, sourcePos.get(), sourceState);
                            }

                            world.setBlockState(sourcePos.get(), newSourceFluidState.getBlockState(), 3);
                        }

                        // Flow source block to new position
                        if (fluidState.getBlockState().getBlock() instanceof ILiquidContainer) {
                            ((ILiquidContainer) blockState.getBlock()).receiveFluid(world, currentBlockPos, blockState, still);
                        } else {
                            if (!blockState.isAir(world, currentBlockPos)) {
                                ((FlowableFluidAccessor) fluid).callBeforeReplacingBlock(world, currentBlockPos, blockState);
                            }

                            world.setBlockState(currentBlockPos, still.getBlockState(), 3);
                        }
                    }
                }
            }
        }
    }
}
