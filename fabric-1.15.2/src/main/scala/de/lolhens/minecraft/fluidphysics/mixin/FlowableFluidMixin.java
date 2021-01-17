package de.lolhens.minecraft.fluidphysics.mixin;

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod;
import de.lolhens.minecraft.fluidphysics.util.FluidIsInfinite;
import de.lolhens.minecraft.fluidphysics.util.FluidSourceFinder;
import net.minecraft.block.*;
import net.minecraft.fluid.BaseFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.IWorld;
import net.minecraft.world.WorldView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scala.Option;

@Mixin(BaseFluid.class)
public abstract class FlowableFluidMixin implements FlowableFluidAccessor {
    //method_15748: isMatchingOrEmpty
    //method_15744: flowSideways
    //method_15740: surroundedStillCount
    //method_15736: canFlowDownInto

    @Shadow
    protected abstract boolean isMatchingAndStill(FluidState state);

    //canFlowDownInto
    @Inject(at = @At("RETURN"), method = "method_15736", cancellable = true)
    private void method_15736(BlockView world,
                              Fluid fluid,
                              BlockPos pos,
                              BlockState state,
                              BlockPos fromPos,
                              BlockState fromState,
                              CallbackInfoReturnable<Boolean> info) {
        if (info.getReturnValue() &&
                FluidPhysicsMod.config().enabledFor(fluid) &&
                FluidPhysicsMod.config().getFlowOverSources()) {
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
                FluidPhysicsMod.config().enabledFor(fluid) &&
                ((BaseFluid) (Object) this).matchesType(fluidState.getFluid()) &&
                !fluidState.isStill()) {
            info.setReturnValue(true);
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
    protected void flow(IWorld world,
                        BlockPos pos,
                        BlockState state,
                        Direction direction,
                        FluidState fluidState,
                        CallbackInfo info) {
        FluidState still = getStill(false);

        if (!FluidPhysicsMod.config().enabledFor(still.getFluid())) return;

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
                        this.callBeforeBreakingBlock(world, sourcePos.get(), sourceState);
                    }

                    world.setBlockState(sourcePos.get(), newSourceFluidState.getBlockState(), 3);
                }

                // Flow source block to new position
                if (state.getBlock() instanceof FluidFillable) {
                    ((FluidFillable) state.getBlock()).tryFillWithFluid(world, pos, state, still);
                } else {
                    if (!state.isAir()) {
                        this.callBeforeBreakingBlock(world, pos, state);
                    }

                    world.setBlockState(pos, still.getBlockState(), 3);
                }

                // Cancel default flow algorithm
                info.cancel();
            }
        }
    }
}
