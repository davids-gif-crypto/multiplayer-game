package com.foodfight.sim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.foodfight.sim.FighterType.BRAWLER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gravity, falling speed, running and stopping. */
class MovementTest {

    private final SimTestKit kit = new SimTestKit();

    // ---------- gravity ----------

    @Test
    void gravityMakesAFighterFallFasterAndFaster() {
        // x = 100 is off the side of the tray, so there is nothing to land on.
        Fighter f = kit.fighterAt(BRAWLER, 100, -200);
        double lastSpeed = f.vy;
        double lastY = f.y;
        for (int i = 0; i < 10; i++) {
            kit.world.step();
            assertTrue(f.vy > lastSpeed, "falling speed should grow");
            assertTrue(f.y > lastY, "should move down");
            lastSpeed = f.vy;
            lastY = f.y;
        }
        assertEquals(10 * Balance.GRAVITY * Balance.DT, f.vy, 1e-9);
    }

    @Test
    void fallingSpeedStopsAtTheMax() {
        Fighter f = kit.fighterAt(BRAWLER, 100, -200);
        for (int i = 0; i < 60; i++) {
            kit.world.step();
            assertTrue(f.vy <= Balance.MAX_FALL_SPEED);
        }
        assertEquals(Balance.MAX_FALL_SPEED, f.vy, 1e-9);
    }

    @Test
    void holdingDownFallsFasterButStillHasAMax() {
        Fighter f = kit.fighterAt(BRAWLER, 100, -200);
        kit.world.setInput(f.id, SimTestKit.DOWN);
        for (int i = 0; i < 50; i++) {
            kit.world.step();
            assertTrue(f.vy <= Balance.FAST_FALL_SPEED);
        }
        assertEquals(Balance.FAST_FALL_SPEED, f.vy, 1e-9);
        assertTrue(Balance.FAST_FALL_SPEED > Balance.MAX_FALL_SPEED);
    }

    // ---------- running ----------

    @ParameterizedTest
    @EnumSource(FighterType.class)
    void runningSpeedsUpToRunSpeedAndNoFurther(FighterType type) {
        Fighter f = kit.standingAt(type, 640, 520);
        kit.world.setInput(f.id, SimTestKit.RIGHT);
        for (int i = 0; i < 20; i++) {
            kit.world.step();
            assertTrue(f.vx <= type.runSpeed);
        }
        assertEquals(type.runSpeed, f.vx, 1e-9);
        assertTrue(f.onGround);
    }

    @Test
    void lettingGoSlowsDownToAStop() {
        Fighter f = kit.standingAt(BRAWLER, 640, 520);
        kit.hold(f, SimTestKit.RIGHT, 20);
        double xWhenReleased = f.x;
        kit.hold(f, SimTestKit.NOTHING, 20);
        assertEquals(0, f.vx, 1e-9);
        assertTrue(f.x > xWhenReleased, "slides a little before stopping");
        double stoppedX = f.x;
        kit.steps(10);
        assertEquals(stoppedX, f.x, 1e-9, "stays stopped");
    }

    @Test
    void speedingUpIsSlowerInTheAir() {
        Fighter grounded = kit.standingAt(BRAWLER, 640, 520);
        Fighter flying = kit.fighterAt(BRAWLER, 100, -200);
        kit.world.setInput(grounded.id, SimTestKit.RIGHT);
        kit.world.setInput(flying.id, SimTestKit.RIGHT);
        kit.steps(3);
        assertEquals(3 * Balance.GROUND_ACCEL * Balance.DT, grounded.vx, 1e-9);
        assertEquals(3 * Balance.AIR_ACCEL * Balance.DT, flying.vx, 1e-9);
    }

    @Test
    void youFaceTheWayYouLastPressed() {
        Fighter f = kit.standingAt(BRAWLER, 640, 520);
        kit.hold(f, SimTestKit.LEFT, 1);
        assertEquals(-1, f.face);
        kit.hold(f, SimTestKit.NOTHING, 5);
        assertEquals(-1, f.face, "still facing left after letting go");
        kit.hold(f, SimTestKit.RIGHT, 1);
        assertEquals(1, f.face);
    }

    @Test
    void fightersStartFacingTheMiddle() {
        GameWorld world = new GameWorld(Stage.lunchTray());
        assertEquals(1, world.addFighter(1, BRAWLER).face());   // x 380
        assertEquals(1, world.addFighter(2, BRAWLER).face());   // x 540
        assertEquals(-1, world.addFighter(3, BRAWLER).face());  // x 740
        assertEquals(-1, world.addFighter(4, BRAWLER).face());  // x 900
    }
}
