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
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// Method-name map (Yarn 1.18.2 -> Mojmap 1.21.1):
//   canFlowDownInto    -> canPassThroughWall
//   canFlow            -> canSpreadTo
//   getUpdatedState    -> getNewLiquid
//   flow               -> spreadTo
//   isMatchingAndStill -> isSourceBlockOfThisType
//   getStill           -> getSource
//   getFlowing         -> getFlowing (same)
// Signatures may have drifted; verify against 1.21.1 source on first compile.
@Mixin(FlowingFluid.class)
public abstract class FlowableFluidMixin implements FlowableFluidAccessor {

    @Shadow
    protected abstract boolean isSourceBlockOfThisType(FluidState state);

    @Shadow
    public abstract FluidState getSource(boolean falling);

    @Shadow
    public abstract FluidState getFlowing(int level, boolean falling);

    private boolean fluidphysics$canFlowDownIntoTrapdoor(BlockState state) {
        Fluid fluid = (FlowingFluid) (Object) this;
        if (fluid.isSame(Fluids.WATER) && state.getBlock() instanceof TrapDoorBlock) {
            return !state.getValue(TrapDoorBlock.WATERLOGGED) &&
                    (state.getValue(TrapDoorBlock.HALF) == Half.BOTTOM || state.getValue(TrapDoorBlock.OPEN));
        }
        return false;
    }

    // In 1.18.2 the equivalent method (`canFlowDownInto` / `method_15736`) was hardcoded to the
    // DOWN direction. In 1.21.1 `canPassThroughWall` takes a Direction and is also called from
    // `getNewLiquid` for HORIZONTAL neighbor counting — if we force-return false for horizontal
    // source neighbors, source-count tracking breaks and `getNewLiquid` returns EMPTY for cells
    // next to a source, killing horizontal spread entirely. Gate both branches on direction=DOWN
    // to preserve the original 1.18.2 semantics.
    @Inject(at = @At("RETURN"), method = "canPassThroughWall", cancellable = true)
    private void fluidphysics$canPassThroughWall(Direction direction,
                                                 BlockGetter blockGetter,
                                                 BlockPos pos,
                                                 BlockState state,
                                                 BlockPos fromPos,
                                                 BlockState fromState,
                                                 CallbackInfoReturnable<Boolean> info) {
        if (direction != Direction.DOWN) return;
        Fluid self = (FlowingFluid) (Object) this;
        if (fluidphysics$canFlowDownIntoTrapdoor(fromState)) {
            info.setReturnValue(true);
        } else if (Boolean.TRUE.equals(info.getReturnValue()) &&
                blockGetter instanceof Level &&
                FluidPhysicsMod.config().isEnabledFor(self, (Level) blockGetter, pos) &&
                FluidPhysicsMod.config().getFlowOverSources()) {
            FluidState fluidState = fromState.getFluidState();
            if (isSourceBlockOfThisType(fluidState)) {
                info.setReturnValue(false);
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "canSpreadTo", cancellable = true)
    protected void fluidphysics$canSpreadTo(BlockGetter blockGetter,
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
                blockGetter instanceof Level &&
                FluidPhysicsMod.config().isEnabledFor(fluid, (Level) blockGetter, fluidPos)) {
            Level level = (Level) blockGetter;
            if (((FlowingFluid) (Object) this).isSame(fluid)) {
                boolean isUnfillableAtSeaLevel = false;
                if (FluidPhysicsMod.config().isUnfillableInBiome(fluid, level, flowTo)) {
                    isUnfillableAtSeaLevel = flowTo.getY() == level.getSeaLevel() - 1;
                }
                if (!fluidState.isSource() || isUnfillableAtSeaLevel) {
                    info.setReturnValue(true);
                }
            } else if (fluidphysics$canFlowDownIntoTrapdoor(flowToBlockState)) {
                info.setReturnValue(true);
            }
        }
    }

    @Inject(at = @At("HEAD"), method = "getNewLiquid")
    protected void fluidphysics$getNewLiquid(Level level, BlockPos pos, BlockState state, CallbackInfoReturnable<FluidState> info) {
        FluidIsInfinite.set(level, pos);
    }

    @Inject(at = @At("HEAD"), method = "spreadTo", cancellable = true)
    protected void fluidphysics$spreadTo(LevelAccessor level,
                                         BlockPos pos,
                                         BlockState state,
                                         Direction direction,
                                         FluidState fluidState,
                                         CallbackInfo info) {
        FluidState still = getSource(false);

        if (!(level instanceof Level) ||
                !FluidPhysicsMod.config().isEnabledFor(still.getType(), (Level) level, pos)) return;

        BlockPos up = pos.above();

        if (direction == Direction.DOWN || level.getFluidState(up).getType().isSame(still.getType())) {
            BlockState blockStateBelow = level.getBlockState(pos.below());

            boolean isFlowingOntoPiston = blockStateBelow.getBlock() instanceof PistonBaseBlock
                    && blockStateBelow.getValue(DirectionalBlock.FACING) == Direction.UP;
            if (isFlowingOntoPiston) return;

            BlockPos sourcePos = FluidSourceFinder.findSourceOrNull(level, up, still.getType());

            if (sourcePos != null) {
                FluidSourceFinder.moveSource(level, sourcePos, pos, state, (FlowingFluid) (Object) this, still);
                info.cancel();
            } else if (isSourceBlockOfThisType(state.getFluidState())) {
                info.cancel();
            }
        }
    }
}
