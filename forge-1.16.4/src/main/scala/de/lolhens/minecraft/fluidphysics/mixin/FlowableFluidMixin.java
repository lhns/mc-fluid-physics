package de.lolhens.minecraft.fluidphysics.mixin;

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod;
import de.lolhens.minecraft.fluidphysics.util.FluidIsInfinite;
import de.lolhens.minecraft.fluidphysics.util.FluidSourceFinder;
import net.minecraft.block.*;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
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
    protected abstract boolean isSourceBlockOfThisType(FluidState state);

    private boolean canFlowDownIntoTrapdoor(BlockState state) {
        Fluid fluid = (FlowingFluid) (Object) this;
        if (fluid.isSame(Fluids.WATER) && state.getBlock() instanceof TrapDoorBlock) {
            return !state.getValue(TrapDoorBlock.WATERLOGGED) &&
                    (state.getValue(TrapDoorBlock.HALF) == Half.BOTTOM || state.getValue(TrapDoorBlock.OPEN));
        }
        return false;
    }

    @Inject(at = @At("RETURN"), method = "isWaterHole", cancellable = true)
    private void isWaterHole(IBlockReader world,
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
            FluidState fluidState = fromState.getFluidState();
            if (isSourceBlockOfThisType(fluidState)) {
                info.setReturnValue(false);
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "canSpreadTo", cancellable = true)
    protected void canSpreadTo(IBlockReader world,
                               BlockPos fluidPos,
                               BlockState fluidBlockState,
                               Direction flowDirection,
                               BlockPos flowTo,
                               BlockState flowToBlockState,
                               FluidState fluidState,
                               Fluid fluid,
                               CallbackInfoReturnable<Boolean> info) {
        if (flowDirection == Direction.DOWN && FluidPhysicsMod.config().enabledFor(fluid)) {
            if (((FlowingFluid) (Object) this).isSame(fluidState.getType()) && !fluidState.isSource()) {
                info.setReturnValue(true);
            } else if (canFlowDownIntoTrapdoor(flowToBlockState)) {
                info.setReturnValue(true);
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "getNewLiquid")
    protected void getNewLiquid(IWorldReader world, BlockPos pos, BlockState state, CallbackInfoReturnable<FluidState> info) {
        FluidIsInfinite.set(world, pos);
    }

    @Shadow
    public abstract FluidState getSource(boolean falling);

    @Shadow
    public abstract FluidState getFlowing(int level, boolean falling);

    @Inject(at = @At("HEAD"), method = "spreadTo", cancellable = true)
    protected void spreadTo(IWorld world,
                            BlockPos pos,
                            BlockState state,
                            Direction direction,
                            FluidState fluidState,
                            CallbackInfo info) {
        FluidState still = getSource(false);

        if (!FluidPhysicsMod.config().enabledFor(still.getType())) return;

        BlockPos up = pos.above();

        if (direction == Direction.DOWN || world.getFluidState(up).getType().isSame(still.getType())) {
            BlockState blockStateBelow = world.getBlockState(pos.below());

            boolean isFlowingOntoPiston = blockStateBelow.getBlock() instanceof PistonBlock && blockStateBelow.getValue(DirectionalBlock.FACING) == Direction.UP;
            if (isFlowingOntoPiston) return;

            Option<BlockPos> sourcePos = FluidSourceFinder.findSource(world, up, still.getType());

            if (sourcePos.isDefined()) {
                int newSourceLevel = still.getAmount() - 1;
                FluidState newSourceFluidState = getFlowing(newSourceLevel, false);

                BlockState sourceState = world.getBlockState(sourcePos.get());

                // Drain source block
                if (sourceState.getBlock() instanceof IBucketPickupHandler && !(sourceState.getBlock() instanceof FlowingFluidBlock)) {
                    ((IBucketPickupHandler) sourceState.getBlock()).takeLiquid(world, sourcePos.get(), sourceState);
                } else {
                    if (!sourceState.getBlock().isAir(sourceState, world, sourcePos.get())) {
                        this.callBeforeDestroyingBlock(world, sourcePos.get(), sourceState);
                    }

                    world.setBlock(sourcePos.get(), newSourceFluidState.createLegacyBlock(), 3);
                }

                // Flow source block to new position
                if (state.getBlock() instanceof ILiquidContainer) {
                    ((ILiquidContainer) state.getBlock()).placeLiquid(world, pos, state, still);
                } else {
                    if (!state.getBlock().isAir(state, world, pos)) {
                        this.callBeforeDestroyingBlock(world, pos, state);
                    }

                    world.setBlock(pos, still.createLegacyBlock(), 3);
                }

                // Cancel default flow algorithm
                info.cancel();
            }
        }
    }
}
