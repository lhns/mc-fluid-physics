package de.lolhens.minecraft.fluidphysics.mixin;

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod;
import de.lolhens.minecraft.fluidphysics.util.FluidIsInfinite;
import de.lolhens.minecraft.fluidphysics.util.FluidSourceFinder;
import net.minecraft.block.*;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.IFluidState;
import net.minecraft.state.properties.Half;
import net.minecraft.util.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockReader;
import net.minecraft.world.IWorld;
import net.minecraft.world.IWorldReader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import scala.Option;

@Mixin(FlowingFluid.class)
public abstract class FlowableFluidMixin implements FlowableFluidAccessor {
    @Shadow
    protected abstract boolean isSameAs(IFluidState state);

    private boolean canFlowDownIntoTrapdoor(BlockState state) {
        if (state.getBlock() instanceof TrapDoorBlock) {
            return !state.get(TrapDoorBlock.WATERLOGGED) &&
                    (state.get(TrapDoorBlock.HALF) == Half.BOTTOM || state.get(TrapDoorBlock.OPEN));
        }
        return false;
    }

    @Inject(at = @At("RETURN"), method = "func_211759_a", cancellable = true)
    private void func_211759_a(IBlockReader world,
                               Fluid fluid,
                               BlockPos pos,
                               BlockState state,
                               BlockPos fromPos,
                               BlockState fromState,
                               CallbackInfoReturnable<Boolean> info) {
        if (canFlowDownIntoTrapdoor(fromState)) {
            info.setReturnValue(true);
        } else if (info.getReturnValue() &&
                FluidPhysicsMod.config().enabledFor(fluid) &&
                FluidPhysicsMod.config().getFlowOverSources()) {
            IFluidState fluidState = fromState.getFluidState();
            if (isSameAs(fluidState)) {
                info.setReturnValue(false);
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "canFlow", cancellable = true)
    protected void canFlow(IBlockReader world,
                           BlockPos fluidPos,
                           BlockState fluidBlockState,
                           Direction flowDirection,
                           BlockPos flowTo,
                           BlockState flowToBlockState,
                           IFluidState fluidState,
                           Fluid fluid,
                           CallbackInfoReturnable<Boolean> info) {
        if (flowDirection == Direction.DOWN && FluidPhysicsMod.config().enabledFor(fluid)) {
            if (((FlowingFluid) (Object) this).isEquivalentTo(fluidState.getFluid()) && !fluidState.isSource()) {
                info.setReturnValue(true);
            } else if (canFlowDownIntoTrapdoor(flowToBlockState)) {
                info.setReturnValue(true);
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "calculateCorrectFlowingState")
    protected void calculateCorrectFlowingState(IWorldReader world, BlockPos pos, BlockState state, CallbackInfoReturnable<IFluidState> info) {
        FluidIsInfinite.set(world, pos);
    }

    @Shadow
    public abstract IFluidState getStillFluidState(boolean falling);

    @Shadow
    public abstract IFluidState getFlowingFluidState(int level, boolean falling);

    @Inject(at = @At("HEAD"), method = "flowInto", cancellable = true)
    protected void flowInto(IWorld world,
                            BlockPos pos,
                            BlockState state,
                            Direction direction,
                            IFluidState fluidState,
                            CallbackInfo info) {
        IFluidState still = getStillFluidState(false);

        if (!FluidPhysicsMod.config().enabledFor(still.getFluid())) return;

        BlockPos up = pos.up();

        if (direction == Direction.DOWN || world.getFluidState(up).getFluid().isEquivalentTo(still.getFluid())) {
            BlockState blockStateBelow = world.getBlockState(pos.down());

            boolean isFlowingOntoPiston = blockStateBelow.getBlock() instanceof PistonBlock && blockStateBelow.get(DirectionalBlock.FACING) == Direction.UP;
            if (isFlowingOntoPiston) return;

            Option<BlockPos> sourcePos = FluidSourceFinder.findSource(world, up, still.getFluid());

            if (sourcePos.isDefined()) {
                int newSourceLevel = still.getLevel() - 1;
                IFluidState newSourceFluidState = getFlowingFluidState(newSourceLevel, false);

                BlockState sourceState = world.getBlockState(sourcePos.get());

                // Drain source block
                if (sourceState.getBlock() instanceof IBucketPickupHandler && !(sourceState.getBlock() instanceof FlowingFluidBlock)) {
                    ((IBucketPickupHandler) sourceState.getBlock()).pickupFluid(world, sourcePos.get(), sourceState);
                } else {
                    if (!sourceState.isAir(world, sourcePos.get())) {
                        this.callBeforeReplacingBlock(world, sourcePos.get(), sourceState);
                    }

                    world.setBlockState(sourcePos.get(), newSourceFluidState.getBlockState(), 3);
                }

                // Flow source block to new position
                if (state.getBlock() instanceof ILiquidContainer) {
                    ((ILiquidContainer) state.getBlock()).receiveFluid(world, pos, state, still);
                } else {
                    if (!state.isAir(world, pos)) {
                        this.callBeforeReplacingBlock(world, pos, state);
                    }

                    world.setBlockState(pos, still.getBlockState(), 3);
                }

                // Cancel default flow algorithm
                info.cancel();
            }
        }
    }
}
