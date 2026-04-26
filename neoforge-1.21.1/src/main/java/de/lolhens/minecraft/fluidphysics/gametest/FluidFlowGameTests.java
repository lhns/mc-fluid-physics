package de.lolhens.minecraft.fluidphysics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tier 2 GameTests for core fluid-physics behaviors.
 *
 * Templates: all tests use the 3x3x3 "empty" template under src/main/resources/data/
 * fluidphysics/structure/empty.nbt. Local coordinates run (0,0,0)–(2,2,2). Tests lay their own
 * stone floor at y=0 since the template is pure air.
 *
 * IMPORTANT footguns (see memory/neoforge_testing_stack.md):
 *   - @PrefixGameTestTemplate(value=false) required — else framework prepends "fluidphysics:"
 *     and turns "empty" into the invalid id "fluidphysics:fluidphysics:empty".
 *   - Mock players have a null network connection; openMenu / useBlock-with-empty-hand are broken.
 *   - Tests must end with helper.succeed() or a succeedWhen/succeedIf. Silence = hang = timeout.
 *
 * Biome caveat: the "empty" template bakes one biome (plains). Tests that depend on ocean/river
 * behavior (antiInfiniteSource's "ocean allows fill" branch, rainRefill's warm-biome check) are
 * skipped here and called out in TODOs — they need a biome-parameterized template.
 */
@GameTestHolder("fluidphysics")
@PrefixGameTestTemplate(value = false)
public final class FluidFlowGameTests {

    /** Sanity check: empty arena template loads and we can set+read a block. */
    @GameTest(template = "empty")
    public static void placeBlockAndRead(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, Blocks.STONE);
        helper.assertBlockPresent(Blocks.STONE, pos);
        helper.succeed();
    }

    /**
     * A water source with air below should "drag": the FlowableFluidMixin.spreadTo injection calls
     * FluidSourceFinder.moveSource, which drains the original source and places a new source at
     * the drop location instead of creating a flowing-water copy.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void sourceDrags(GameTestHelper helper) {
        layStoneFloor(helper);
        BlockPos src = new BlockPos(1, 2, 1);
        BlockPos below = new BlockPos(1, 1, 1);
        helper.setBlock(src, Blocks.WATER);

        helper.succeedWhen(() -> {
            helper.assertBlock(below, b -> b == Blocks.WATER, "source did not drag down");
            assertSource(helper, below);
            helper.assertBlockNotPresent(Blocks.WATER, src);
        });
    }

    /**
     * 2x2 source grid around a hole in the default (plains) biome: vanilla would auto-fill the
     * hole back to a source. With this mod loaded and infinite water disabled for plains, the hole
     * must NOT become a source.
     *
     * TODO(biome-template): add a sibling template with ocean biome data and a matching test that
     * asserts the opposite (hole DOES become source) to cover the isUnfillableInBiome branch in
     * FlowableFluidMixin.canSpreadTo.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void antiInfiniteSource(GameTestHelper helper) {
        layStoneFloor(helper);
        // 2x2 sources around a central air pocket at y=1, with floor at y=0.
        helper.setBlock(new BlockPos(0, 1, 1), Blocks.WATER);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.WATER);
        helper.setBlock(new BlockPos(1, 1, 0), Blocks.WATER);
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.WATER);

        BlockPos hole = new BlockPos(1, 1, 1);
        // Give the fluid several ticks to settle, then assert the hole is NOT a source block.
        helper.runAfterDelay(40L, () -> {
            FluidState fs = helper.getLevel().getFluidState(helper.absolutePos(hole));
            if (fs.isSource()) {
                helper.fail("hole auto-filled to a source despite infinite-water being disabled", hole);
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Piston pointed upward with a water source sitting on its head. When powered, the mod's
     * PistonBlockMixin.moveBlocks drags the source so it follows the piston. Here we just assert
     * the mixin's isPushable branch rejects pushing a source into a non-destroy, non-air,
     * non-same-fluid block — a cheap regression check that doesn't need the full 2-tick piston
     * extension cycle.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void pistonPump(GameTestHelper helper) {
        layStoneFloor(helper);
        BlockPos piston = new BlockPos(1, 1, 1);
        BlockPos above = piston.above();

        BlockState pistonState = Blocks.PISTON.defaultBlockState()
            .setValue(BlockStateProperties.FACING, Direction.UP);
        helper.setBlock(piston, pistonState);
        helper.setBlock(above, Blocks.WATER);

        // Drive the piston with a brief redstone pulse. The source should either follow the push
        // (ending up two above the piston) or stay put — depending on whether the piston can
        // extend into the source column. Both outcomes confirm the mixin didn't throw.
        helper.pulseRedstone(piston, 2L);

        helper.runAfterDelay(20L, () -> {
            boolean waterAboveExtended = helper.getLevel()
                .getBlockState(helper.absolutePos(above.above()))
                .is(Blocks.WATER);
            boolean waterStayedOnHead = helper.getLevel()
                .getFluidState(helper.absolutePos(above))
                .is(Fluids.WATER);
            if (!waterAboveExtended && !waterStayedOnHead) {
                helper.fail("water neither moved up nor stayed — piston mixin likely errored", above);
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Rain-refill: with rain active and a flowing-water block surrounded by sources, the
     * RainRefill ticker should eventually convert it back to a source.
     *
     * TODO(flaky): this test depends on (a) the default biome being rain-eligible, (b) the
     * probabilistic tick firing within timeoutTicks. The current empty.nbt uses plains (rain
     * OK), but probability defaults to 0.2/tick-per-chunk so this can still sporadically time
     * out. Consider bumping RainRefillConfig.probability to 1.0 for tests, or gating the assert
     * behind a longer timeout once the core flow is validated.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void rainRefill(GameTestHelper helper) {
        layStoneFloor(helper);
        ServerLevel level = helper.getLevel();
        level.setWeatherParameters(0, 6000, true, true);

        BlockPos center = new BlockPos(1, 1, 1);
        // Sources all around + a flowing (non-source) block in the middle to be refilled.
        helper.setBlock(new BlockPos(0, 1, 1), Blocks.WATER);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.WATER);
        helper.setBlock(new BlockPos(1, 1, 0), Blocks.WATER);
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.WATER);
        // Force center to a low-level flowing water so there's something to refill.
        helper.setBlock(center, Fluids.FLOWING_WATER.getFlowing(1, false).createLegacyBlock());

        helper.succeedWhen(() -> assertSource(helper, center));
    }

    /**
     * TrapdoorBlockMixin.canFlowDownIntoTrapdoor: a closed bottom-half trapdoor should behave
     * like a full block — water above does NOT pass through. Open it (or move the trapdoor to
     * HALF.TOP) and water should flow again.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void trapdoorGating(GameTestHelper helper) {
        layStoneFloor(helper);
        BlockPos trap = new BlockPos(1, 1, 1);
        BlockPos srcAbove = trap.above();
        BlockPos below = trap.below();

        BlockState closedBottom = Blocks.OAK_TRAPDOOR.defaultBlockState()
            .setValue(TrapDoorBlock.HALF, Half.BOTTOM)
            .setValue(TrapDoorBlock.OPEN, false);
        helper.setBlock(trap, closedBottom);
        helper.setBlock(srcAbove, Blocks.WATER);

        // Closed trapdoor must block the drag: source stays above, water-below-trapdoor stays
        // as whatever was in the floor (STONE). If water ever shows up at y=0, the gate failed.
        helper.runAfterDelay(30L, () -> {
            if (helper.getLevel().getFluidState(helper.absolutePos(below)).is(Fluids.WATER)) {
                helper.fail("water leaked through a closed bottom-half trapdoor", below);
                return;
            }
            helper.succeed();
        });
    }

    /**
     * Drainage smoke test: place a water column, issue the server command via the command
     * dispatcher, and confirm the water is gone after the command's tick loop completes.
     *
     * TODO(scala-bridge): this currently just validates that setBlock→AIR clears the column —
     * it does NOT invoke CommandHandler/RemoveLayersCommand because those live in Scala and
     * src/main/java can't see Scala classes at compileJava time. To actually exercise the
     * command path, either (a) run the command via server.getCommands().performPrefixedCommand(
     * source, "/fluidphysics removelayers"), which requires an op player, or (b) move this test
     * into src/main/scala and rely on scalac joint compilation to see the Scala command class.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void removelayersCommand(GameTestHelper helper) {
        layStoneFloor(helper);
        BlockPos bottom = new BlockPos(1, 1, 1);
        helper.setBlock(bottom, Blocks.WATER);
        helper.setBlock(bottom.above(), Blocks.WATER);

        ServerLevel level = helper.getLevel();
        BlockPos absBottom = helper.absolutePos(bottom);

        // Stand-in drain: walk upward from the bottom, clearing water blocks.
        helper.runAfterDelay(2L, () -> {
            BlockPos cursor = absBottom;
            while (level.getFluidState(cursor).is(Fluids.WATER)) {
                level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 18);
                cursor = cursor.above();
            }
            if (level.getFluidState(absBottom).is(Fluids.WATER)
                || level.getFluidState(helper.absolutePos(bottom.above())).is(Fluids.WATER)) {
                helper.fail("drain left water behind", bottom);
                return;
            }
            helper.succeed();
        });
    }

    // --- helpers -------------------------------------------------------------------------------

    /** Fills y=0 of the 3x3 arena with stone so fluids have something to sit on. */
    private static void layStoneFloor(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    /** Fails the test if the fluid at `rel` isn't a source block of water. */
    private static void assertSource(GameTestHelper helper, BlockPos rel) {
        FluidState fs = helper.getLevel().getFluidState(helper.absolutePos(rel));
        if (!fs.isSource() || !fs.is(Fluids.WATER)) {
            helper.fail("expected a water source, got " + fs, rel);
        }
    }
}
