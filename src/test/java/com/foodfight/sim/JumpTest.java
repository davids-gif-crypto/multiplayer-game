package com.foodfight.sim;

import org.junit.jupiter.api.Test;

import static com.foodfight.sim.FighterType.BRAWLER;
import static com.foodfight.sim.FighterType.SPEEDSTER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** First jump, double jump, no third jump, and getting jumps back. */
class JumpTest {

    private static final double GRAVITY_PER_TICK = Balance.GRAVITY * Balance.DT;

    private final SimTestKit kit = new SimTestKit();

    private long jumpEvents() {
        return kit.world.takeEvents().stream().filter(e -> e instanceof GameEvent.Jump).count();
    }

    @Test
    void firstJumpUsesTheFirstJumpSpeed() {
        Fighter f = kit.standingAt(BRAWLER, 640, 520);
        kit.world.takeEvents();
        kit.hold(f, SimTestKit.JUMP, 1);
        // The jump sets the speed, then one tick of gravity is added.
        assertEquals(-BRAWLER.jumpSpeed1 + GRAVITY_PER_TICK, f.vy, 1e-9);
        assertEquals(1, f.jumpsUsed);
        assertFalse(f.onGround);

        GameEvent event = kit.world.takeEvents().get(0);
        GameEvent.Jump jump = assertInstanceOf(GameEvent.Jump.class, event);
        assertEquals(f.id, jump.id());
        assertEquals("jump", jump.k());
    }

    @Test
    void holdingJumpOnlyJumpsOnce() {
        Fighter f = kit.standingAt(BRAWLER, 640, 520);
        kit.world.takeEvents();
        kit.hold(f, SimTestKit.JUMP, 20);
        assertEquals(1, f.jumpsUsed);
        assertEquals(1, jumpEvents());
    }

    @Test
    void aTapBetweenTwoTicksStillJumps() {
        // Down and up both arrive before the next tick, like bunched up
        // network messages. The press must not be lost.
        Fighter f = kit.standingAt(BRAWLER, 640, 520);
        kit.world.setInput(f.id, SimTestKit.JUMP);
        kit.world.setInput(f.id, SimTestKit.NOTHING);
        kit.world.step();
        assertEquals(1, f.jumpsUsed);
        assertTrue(f.vy < 0);
    }

    @Test
    void aPressIsOnlyUsedOnce() {
        Fighter f = kit.standingAt(BRAWLER, 640, 520);
        kit.world.setInput(f.id, SimTestKit.JUMP);
        kit.steps(2);
        assertEquals(1, f.jumpsUsed, "the second tick did not jump again");
    }

    @Test
    void secondPressIsADoubleJump() {
        Fighter f = kit.standingAt(BRAWLER, 640, 520);
        kit.tapJump(f);
        kit.steps(5);
        kit.hold(f, SimTestKit.JUMP, 1);
        assertEquals(2, f.jumpsUsed);
        assertEquals(-BRAWLER.jumpSpeed2 + GRAVITY_PER_TICK, f.vy, 1e-9);
    }

    @Test
    void thereIsNoThirdJump() {
        Fighter f = kit.standingAt(BRAWLER, 640, 520);
        kit.tapJump(f);
        kit.tapJump(f);
        double speedBefore = f.vy;
        kit.hold(f, SimTestKit.JUMP, 1);
        assertEquals(2, f.jumpsUsed);
        assertEquals(speedBefore + GRAVITY_PER_TICK, f.vy, 1e-9, "only gravity changed the speed");
    }

    @Test
    void landingGivesJumpsBack() {
        Fighter f = kit.standingAt(BRAWLER, 640, 520);
        kit.tapJump(f);
        kit.tapJump(f);
        kit.stepUntilLanded(f);
        assertEquals(0, f.jumpsUsed);

        kit.hold(f, SimTestKit.JUMP, 1);
        assertEquals(1, f.jumpsUsed);
        assertTrue(f.vy < 0);
    }

    @Test
    void walkingOffAnEdgeKeepsBothJumps() {
        Fighter f = kit.standingAt(SPEEDSTER, 1030, 520);
        kit.hold(f, SimTestKit.RIGHT, 10);
        assertFalse(f.onGround, "walked off the tray");
        assertEquals(0, f.jumpsUsed);

        kit.hold(f, SimTestKit.JUMP, 1);
        assertEquals(-SPEEDSTER.jumpSpeed1 + GRAVITY_PER_TICK, f.vy, 1e-9, "a full first jump");
        kit.hold(f, SimTestKit.NOTHING, 1);
        kit.hold(f, SimTestKit.JUMP, 1);
        assertEquals(-SPEEDSTER.jumpSpeed2 + GRAVITY_PER_TICK, f.vy, 1e-9, "then the double jump");
        kit.hold(f, SimTestKit.NOTHING, 1);
        double speedBefore = f.vy;
        kit.hold(f, SimTestKit.JUMP, 1);
        assertEquals(speedBefore + GRAVITY_PER_TICK, f.vy, 1e-9, "and no third");
    }

    @Test
    void aDoubleJumpReachesTheBread() {
        // Not a rule from the spec, but a useful check that the numbers in
        // Balance.java still let every fighter get onto the bread slices.
        for (FighterType type : FighterType.values()) {
            SimTestKit fresh = new SimTestKit();
            Fighter f = fresh.standingAt(type, 410, 520);
            fresh.tapJump(f);
            fresh.steps(15);
            fresh.tapJump(f);
            fresh.stepUntilLanded(f);
            assertEquals(360, f.y, 1e-9, type + " should land on the bread");
        }
    }
}
