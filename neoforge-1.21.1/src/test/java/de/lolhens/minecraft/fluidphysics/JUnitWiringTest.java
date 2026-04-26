package de.lolhens.minecraft.fluidphysics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Smoke test that proves the JUnit 5 wiring works end-to-end. Real Tier 1 tests go alongside
 * this file once shared logic (e.g. FluidSourceFinder's BFS) is refactored behind MC-free seams.
 * See memory/neoforge_testing_stack.md for the two-tier testing rationale — Tier 1 must NOT
 * touch any class that transitively loads MC types (Block, Level, ItemStack…).
 */
public class JUnitWiringTest {
    @Test
    void sanity() {
        assertEquals(2, 1 + 1);
    }
}
