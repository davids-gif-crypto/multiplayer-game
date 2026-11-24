package com.foodfight.sim;

import org.junit.jupiter.api.Test;

import static com.foodfight.sim.FighterType.BRAWLER;
import static com.foodfight.sim.FighterType.ZONER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Landing on the tray and the bread, one way platforms, dropping through. */
class PlatformTest {

    private static final double TRAY_TOP = 520;
    private static final double TRAY_BOTTOM = 560;
    private static final double BREAD_TOP = 360;

    private final SimTestKit kit = new SimTestKit();

    @Test
    void landsOnTheTray() {
        Fighter f = kit.fighterAt(BRAWLER, 640, 300);
        kit.stepUntilLanded(f);
        assertEquals(TRAY_TOP, f.y, 1e-9);
        assertEquals(0, f.vy, 1e-9);
        assertEquals("tray", f.standingOn.name());
    }

    @Test
    void standingStillStaysPut() {
        Fighter f = kit.standingAt(BRAWLER, 640, TRAY_TOP);
        kit.steps(120);
        assertEquals(TRAY_TOP, f.y, 1e-9);
        assertTrue(f.onGround);
    }

    @Test
    void landsOnBreadFromAbove() {
        Fighter f = kit.fighterAt(BRAWLER, 410, 250); // above the left bread
        kit.stepUntilLanded(f);
        assertEquals(BREAD_TOP, f.y, 1e-9);
        assertEquals("left bread", f.standingOn.name());
    }

    @Test
    void passesUpThroughBreadFromBelowThenLandsOnIt() {
        Fighter f = kit.standingAt(BRAWLER, 410, TRAY_TOP); // under the left bread
        f.vy = -900; // a strong upward push, enough to rise above the bread
        double highest = f.y;
        for (int i = 0; i < 120 && !(f.onGround && i > 0); i++) {
            kit.world.step();
            highest = Math.min(highest, f.y);
        }
        assertTrue(highest < BREAD_TOP, "went up through the bread, highest y was " + highest);
        assertTrue(f.onGround);
        assertEquals(BREAD_TOP, f.y, 1e-9, "came back down onto the bread");
    }

    @Test
    void holdingDownDropsThroughBread() {
        Fighter f = kit.fighterAt(BRAWLER, 410, 250);
        kit.stepUntilLanded(f);
        assertEquals(BREAD_TOP, f.y, 1e-9);

        kit.hold(f, SimTestKit.DOWN, 3);
        assertFalse(f.onGround);
        assertTrue(f.y > BREAD_TOP, "fell below the bread top");

        kit.hold(f, SimTestKit.NOTHING, 0);
        kit.stepUntilLanded(f);
        assertEquals(TRAY_TOP, f.y, 1e-9, "landed on the tray below");
    }

    @Test
    void holdingDownOnTheTrayDoesNothing() {
        Fighter f = kit.standingAt(BRAWLER, 640, TRAY_TOP);
        kit.hold(f, SimTestKit.DOWN, 60);
        assertTrue(f.onGround);
        assertEquals(TRAY_TOP, f.y, 1e-9);
    }

    @Test
    void walkingOffTheEdgeMeansFalling() {
        Fighter f = kit.standingAt(BRAWLER, 1020, TRAY_TOP);
        kit.hold(f, SimTestKit.RIGHT, 30);
        assertFalse(f.onGround);
        assertTrue(f.x > 1040);
        assertTrue(f.y > TRAY_TOP);
    }

    @Test
    void canStandWithFeetRightOnTheEdge() {
        Fighter f = kit.standingAt(BRAWLER, 1040, TRAY_TOP);
        kit.steps(30);
        assertTrue(f.onGround);
    }

    @Test
    void bumpsHeadOnTheUndersideOfTheTray() {
        // Below the tray: head at 640 - 76 = 564, just under the tray bottom.
        Fighter f = kit.fighterAt(ZONER, 640, 640);
        f.vy = -900;
        for (int i = 0; i < 10; i++) {
            kit.world.step();
            assertTrue(f.y - ZONER.height >= TRAY_BOTTOM - 1e-9, "head went into the tray");
        }
        assertTrue(f.vy > 0, "now falling away from the tray");
    }
}
