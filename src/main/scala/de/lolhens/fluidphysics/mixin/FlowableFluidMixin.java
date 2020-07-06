package de.lolhens.fluidphysics.mixin;

import de.lolhens.fluidphysics.util.FluidSourceFinder;
import net.minecraft.block.*;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scala.Option;

@Mixin(net.minecraft.fluid.FlowableFluid.class)
public abstract class FlowableFluidMixin {
    //method_15748: isMatchingOrEmpty
    //method_15744: flowSideways
    //method_15740: surroundedStillCount
    //method_15736: canFlowDownInto

    @Shadow
    public abstract boolean isMatchingAndStill(FluidState state);

    //canFlowDownInto
    @Inject(at = @At("RETURN"), method = "method_15736", cancellable = true)
    private void method_15736(BlockView world,
                              Fluid fluid,
                              BlockPos pos,
                              BlockState state,
                              BlockPos fromPos,
                              BlockState fromState,
                              CallbackInfoReturnable<Boolean> info) {
        if (info.getReturnValue()) {
            FluidState fluidState = fromState.getFluidState();
            if (isMatchingAndStill(fluidState)) {
                info.setReturnValue(false);
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "canFlow", cancellable = true)
    protected void canFlow(BlockView world,
                           BlockPos fluidPos,
                           BlockState fluidBlockState,
                           Direction flowDirection,
                           BlockPos flowTo,
                           BlockState flowToBlockState,
                           FluidState fluidState,
                           Fluid fluid,
                           CallbackInfoReturnable<Boolean> info) {
        if (flowDirection == Direction.DOWN &&
                !fluidState.isEmpty() &&
                fluidBlockState.getFluidState().getFluid().matchesType(fluid) &&
                !fluidState.isStill()) {
            info.setReturnValue(true);
        }
    }

    @Shadow
    protected abstract void beforeBreakingBlock(WorldAccess world, BlockPos pos, BlockState state);

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
        BlockPos up = pos.up();

        if (direction == Direction.DOWN || world.getFluidState(up).getFluid().matchesType(still.getFluid())) {
            BlockState blockStateBelow = world.getBlockState(pos.down());

            boolean isFlowingOntoPiston = blockStateBelow.getBlock() instanceof PistonBlock && blockStateBelow.get(FacingBlock.FACING) == Direction.UP;
            if (isFlowingOntoPiston) return;

            Option<BlockPos> sourcePos = FluidSourceFinder.findSource(world, up, still.getFluid());

            if (sourcePos.isDefined()) {
                int newSourceLevel = still.getLevel() - 1;
                FluidState newSourceFluidState = getFlowing(newSourceLevel, false);

                BlockState sourceState = world.getBlockState(sourcePos.get());

                // Drain source block
                if (sourceState.getBlock() instanceof FluidDrainable && !(sourceState.getBlock() instanceof FluidBlock)) {
                    ((FluidDrainable) sourceState.getBlock()).tryDrainFluid(world, sourcePos.get(), sourceState);
                } else {
                    if (!sourceState.isAir()) {
                        this.beforeBreakingBlock(world, sourcePos.get(), sourceState);
                    }

                    world.setBlockState(sourcePos.get(), newSourceFluidState.getBlockState(), 3);
                }

                // Flow source block to new position
                if (state.getBlock() instanceof FluidFillable) {
                    ((FluidFillable) state.getBlock()).tryFillWithFluid(world, pos, state, still);
                } else {
                    if (!state.isAir()) {
                        this.beforeBreakingBlock(world, pos, state);
                    }

                    world.setBlockState(pos, still.getBlockState(), 3);
                }

                // Cancel default flow algorithm
                info.cancel();
            }
        }
    }
}
