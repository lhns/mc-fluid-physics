package de.lolhens.fluidphysics.mixin;

import de.lolhens.fluidphysics.FluidSourceFinder;
import net.minecraft.block.*;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scala.Option;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

@Mixin(net.minecraft.block.PistonBlock.class)
public class PistonBlockMixin {
    @Inject(at = @At("HEAD"), method = "isMovable", cancellable = true)
    private static void isMovable(BlockState state,
                                  World world,
                                  BlockPos pos,
                                  Direction motionDir,
                                  boolean canBreak,
                                  Direction pistonDir,
                                  CallbackInfoReturnable<Boolean> info) {
        BlockPos prevBlockPos = pos.offset(motionDir.getOpposite());

        if (world.getFluidState(prevBlockPos).isStill() && state.getFluidState().isEmpty()) {
            info.setReturnValue(false);
        }
    }

    private static Method beforeBreakingBlockMethod;

    static {
        try {
            beforeBreakingBlockMethod = FlowableFluid.class.getDeclaredMethod("beforeBreakingBlock", WorldAccess.class, BlockPos.class, BlockState.class);
            beforeBreakingBlockMethod.setAccessible(true);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    @Inject(at = @At("HEAD"), method = "move", cancellable = true)
    private void move(World world,
                      BlockPos pos,
                      Direction dir,
                      boolean retract,
                      CallbackInfoReturnable<Boolean> info) {
        BlockPos blockPos = pos.offset(dir);
        if (!retract && world.getBlockState(blockPos).isOf(Blocks.PISTON_HEAD)) {
            world.setBlockState(blockPos, Blocks.AIR.getDefaultState(), 20);
        }

        PistonHandler pistonHandler = new PistonHandler(world, pos, dir, retract);
        if (!pistonHandler.calculatePush()) {
            info.setReturnValue(false);
        } else {
            Direction oppositeDir = dir.getOpposite();

            Set<BlockPos> blockPosSet = new HashSet<>();
            blockPosSet.add(blockPos);
            for (BlockPos movedBlockPos : pistonHandler.getMovedBlocks()) {
                blockPosSet.add(movedBlockPos);
                blockPosSet.add(movedBlockPos.offset(dir));
            }

            for (BlockPos currentBlockPos : blockPosSet) {
                BlockState blockState = world.getBlockState(currentBlockPos);
                FluidState fluidState = blockState.getFluidState();

                if (!fluidState.isEmpty() && fluidState.getFluid() instanceof FlowableFluid && !fluidState.isStill()) {
                    FlowableFluid fluid = (FlowableFluid) fluidState.getFluid();

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
                        FluidState still = fluid.getStill(false);
                        int newSourceLevel = still.getLevel() - 1;
                        FluidState newSourceFluidState = fluid.getFlowing(newSourceLevel, false);

                        BlockState sourceState = world.getBlockState(sourcePos.get());

                        // Drain source block
                        if (sourceState.getBlock() instanceof FluidDrainable && !(sourceState.getBlock() instanceof FluidBlock)) {
                            ((FluidDrainable) sourceState.getBlock()).tryDrainFluid(world, sourcePos.get(), sourceState);
                        } else {
                            if (!sourceState.isAir()) {
                                try {
                                    beforeBreakingBlockMethod.invoke(fluid, world, sourcePos.get(), sourceState);
                                } catch (IllegalAccessException | InvocationTargetException e) {
                                    e.printStackTrace();
                                }
                            }

                            world.setBlockState(sourcePos.get(), newSourceFluidState.getBlockState(), 3);
                        }

                        // Flow source block to new position
                        if (fluidState.getBlockState().getBlock() instanceof FluidFillable) {
                            ((FluidFillable) blockState.getBlock()).tryFillWithFluid(world, currentBlockPos, blockState, still);
                        } else {
                            if (!blockState.isAir()) {
                                try {
                                    beforeBreakingBlockMethod.invoke(fluid, world, currentBlockPos, blockState);
                                } catch (IllegalAccessException | InvocationTargetException e) {
                                    e.printStackTrace();
                                }
                            }

                            world.setBlockState(currentBlockPos, still.getBlockState(), 3);
                        }
                    }
                }
            }
        }
    }
}
