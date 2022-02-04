package de.lolhens.minecraft.fluidphysics.mixin;

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod;
import de.lolhens.minecraft.fluidphysics.util.FluidIsInfinite;
import de.lolhens.minecraft.fluidphysics.util.FluidSourceFinder;
import de.lolhens.minecraft.fluidphysics.util.TaskQueue;
import net.minecraft.block.*;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scala.Option;

@Mixin(FlowableFluid.class)
public abstract class FlowableFluidMixin implements FlowableFluidAccessor {
    //method_15748: isMatchingOrEmpty
    //method_15744: flowSideways
    //method_15740: surroundedStillCount
    //method_15736: canFlowDownInto

    @Shadow
    protected abstract boolean isMatchingAndStill(FluidState state);

    private boolean canFlowDownIntoTrapdoor(BlockState state) {
        Fluid fluid = (FlowableFluid) (Object) this;
        if (fluid.matchesType(Fluids.WATER) && state.getBlock() instanceof TrapdoorBlock) {
            return !state.get(TrapdoorBlock.WATERLOGGED) &&
                    (state.get(TrapdoorBlock.HALF) == BlockHalf.BOTTOM || state.get(TrapdoorBlock.OPEN));
        }
        return false;
    }

    //canFlowDownInto
    @Inject(at = @At("RETURN"), method = "method_15736", cancellable = true)
    private void method_15736(BlockView blockView,
                              Fluid fluid,
                              BlockPos pos,
                              BlockState state,
                              BlockPos fromPos,
                              BlockState fromState,
                              CallbackInfoReturnable<Boolean> info) {
        if (canFlowDownIntoTrapdoor(fromState)) {
            info.setReturnValue(true);
        } else if (info.getReturnValue() &&
                FluidPhysicsMod.config().isEnabledFor(fluid, (World) blockView, pos) &&
                FluidPhysicsMod.config().getFlowOverSources()) {
            FluidState fluidState = fromState.getFluidState();
            if (isMatchingAndStill(fluidState)) {
                info.setReturnValue(false);
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "canFlow", cancellable = true)
    protected void canFlow(BlockView blockView,
                           BlockPos fluidPos,
                           BlockState fluidBlockState,
                           Direction flowDirection,
                           BlockPos flowTo,
                           BlockState flowToBlockState,
                           FluidState fluidState,
                           Fluid updatedFluid,
                           CallbackInfoReturnable<Boolean> info) {
        Fluid fluid = fluidState.getFluid();
        if (flowDirection == Direction.DOWN &&
                FluidPhysicsMod.config().isEnabledFor(fluid, (World) blockView, fluidPos)) {
            if (((FlowableFluid) (Object) this).matchesType(fluid)) {
                boolean isUnfillableAtSeaLevel = false;
                if (blockView instanceof World) {
                    World world = (World) blockView;
                    if (FluidPhysicsMod.config().isUnfillableInBiome(fluid, world, flowTo))
                        isUnfillableAtSeaLevel = flowTo.getY() == world.getSeaLevel() - 1;
                }
                if (!fluidState.isStill() || isUnfillableAtSeaLevel)
                    info.setReturnValue(true);
            } else if (canFlowDownIntoTrapdoor(flowToBlockState)) {
                info.setReturnValue(true);
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "getUpdatedState")
    protected void getUpdatedState(WorldView world, BlockPos pos, BlockState state, CallbackInfoReturnable<FluidState> info) {
        FluidIsInfinite.set(world, pos);
    }

    @Shadow
    public abstract FluidState getStill(boolean falling);

    @Shadow
    public abstract FluidState getFlowing(int level, boolean falling);

    @Inject(at = @At("HEAD"), method = "flow", cancellable = true)
    protected void flow(WorldAccess world,
                        BlockPos pos,
                        BlockState state,
                        Direction direction,
                        FluidState fluidState,
                        CallbackInfo info) {
        FluidState still = getStill(false);

        if (!FluidPhysicsMod.config().isEnabledFor(still.getFluid(), (World) world, pos)) return;

        BlockPos up = pos.up();

        if (direction == Direction.DOWN || world.getFluidState(up).getFluid().matchesType(still.getFluid())) {
            BlockState blockStateBelow = world.getBlockState(pos.down());

            boolean isFlowingOntoPiston = blockStateBelow.getBlock() instanceof PistonBlock && blockStateBelow.get(FacingBlock.FACING) == Direction.UP;
            if (isFlowingOntoPiston) return;

            TaskQueue.enqueue(() -> {
                Option<BlockPos> sourcePos = FluidSourceFinder.findSource(world, up, still.getFluid());

                if (sourcePos.isDefined()) {
                    FluidSourceFinder.moveSource(world, sourcePos.get(), pos, state, (FlowableFluid) (Object) this, still);

                    // Cancel default flow algorithm after source was moved
                    //info.cancel();
                } else if (!isMatchingAndStill(state.getFluidState())) {
                    // INVERT: Cancel default flow algorithm if no source was found and new pos already contains the fluid source
                    // Run default algorithm
                    if (state.getBlock() instanceof FluidFillable) {
                        ((FluidFillable) state.getBlock()).tryFillWithFluid(world, pos, state, fluidState);
                    } else {
                        if (!state.isAir()) {
                            callBeforeBreakingBlock(world, pos, state);
                        }

                        world.setBlockState(pos, fluidState.getBlockState(), 3);
                    }
                }

                return null;
            });
            info.cancel();
            /*Option<BlockPos> sourcePos = FluidSourceFinder.findSource(world, up, still.getFluid());

            if (sourcePos.isDefined()) {
                FluidSourceFinder.moveSource(world, sourcePos.get(), pos, state, (FlowableFluid) (Object) this, still);

                // Cancel default flow algorithm after source was moved
                info.cancel();
            } else if (isMatchingAndStill(state.getFluidState())) {
                // Cancel default flow algorithm if no source was found and new pos already contains the fluid source
                info.cancel();
            }*/
        }
    }
}
