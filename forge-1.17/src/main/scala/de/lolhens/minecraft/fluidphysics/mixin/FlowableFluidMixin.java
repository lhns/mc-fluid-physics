package de.lolhens.minecraft.fluidphysics.mixin;

import de.lolhens.minecraft.fluidphysics.FluidPhysicsMod;
import de.lolhens.minecraft.fluidphysics.util.FluidIsInfinite;
import de.lolhens.minecraft.fluidphysics.util.FluidSourceFinder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
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
    private void isWaterHole(BlockGetter blockView,
                             Fluid fluid,
                             BlockPos pos,
                             BlockState state,
                             BlockPos fromPos,
                             BlockState fromState,
                             CallbackInfoReturnable<Boolean> info) {
        if (canFlowDownIntoTrapdoor(fromState)) {
            info.setReturnValue(true);
        } else if (info.getReturnValue() &&
                FluidPhysicsMod.config().isEnabledFor(fluid, (Level) blockView, pos) &&
                FluidPhysicsMod.config().getFlowOverSources()) {
            FluidState fluidState = fromState.getFluidState();
            if (isSourceBlockOfThisType(fluidState)) {
                info.setReturnValue(false);
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "canSpreadTo", cancellable = true)
    protected void canSpreadTo(BlockGetter blockView,
                               BlockPos fluidPos,
                               BlockState fluidBlockState,
                               Direction flowDirection,
                               BlockPos flowTo,
                               BlockState flowToBlockState,
                               FluidState fluidState,
                               Fluid updatedFluid,
                               CallbackInfoReturnable<Boolean> info) {
        Fluid fluid = fluidState.getType();
        if (flowDirection == Direction.DOWN &&
                FluidPhysicsMod.config().isEnabledFor(fluid, (Level) blockView, fluidPos)) {
            if (((FlowingFluid) (Object) this).isSame(fluid)) {
                boolean isUnfillableAtSeaLevel = false;
                if (blockView instanceof Level) {
                    Level world = (Level) blockView;
                    if (FluidPhysicsMod.config().isUnfillableInBiome(fluid, world, flowTo))
                        isUnfillableAtSeaLevel = flowTo.getY() == world.getSeaLevel() - 1;
                }
                if (!fluidState.isSource() || isUnfillableAtSeaLevel)
                    info.setReturnValue(true);
            } else if (canFlowDownIntoTrapdoor(flowToBlockState)) {
                info.setReturnValue(true);
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "getNewLiquid")
    protected void getNewLiquid(LevelReader world, BlockPos pos, BlockState state, CallbackInfoReturnable<FluidState> info) {
        FluidIsInfinite.set(world, pos);
    }

    @Shadow
    public abstract FluidState getSource(boolean falling);

    @Shadow
    public abstract FluidState getFlowing(int level, boolean falling);

    @Inject(at = @At("HEAD"), method = "spreadTo", cancellable = true)
    protected void spreadTo(LevelAccessor world,
                            BlockPos pos,
                            BlockState state,
                            Direction direction,
                            FluidState fluidState,
                            CallbackInfo info) {
        FluidState still = getSource(false);

        if (!FluidPhysicsMod.config().isEnabledFor(still.getType(), (Level) world, pos)) return;

        BlockPos up = pos.above();

        if (direction == Direction.DOWN || world.getFluidState(up).getType().isSame(still.getType())) {
            BlockState blockStateBelow = world.getBlockState(pos.below());

            boolean isFlowingOntoPiston = blockStateBelow.getBlock() instanceof PistonBaseBlock && blockStateBelow.getValue(DirectionalBlock.FACING) == Direction.UP;
            if (isFlowingOntoPiston) return;

            Option<BlockPos> sourcePos = FluidSourceFinder.findSource(world, up, still.getType());

            if (sourcePos.isDefined()) {
                FluidSourceFinder.moveSource(world, sourcePos.get(), pos, state, (FlowingFluid) (Object) this, still);

                // Cancel default flow algorithm
                info.cancel();
            } else if (isSourceBlockOfThisType(state.getFluidState())) {
                // Cancel default flow algorithm if no source was found and new pos already contains the fluid source
                info.cancel();
            }
        }
    }
}
