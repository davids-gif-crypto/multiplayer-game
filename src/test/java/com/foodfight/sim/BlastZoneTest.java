package com.foodfight.sim;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static com.foodfight.sim.FighterType.BRAWLER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Flying out of the blast zone, losing stocks, and respawning. */
class BlastZoneTest {

    private final SimTestKit kit = new SimTestKit();

    @ParameterizedTest(name = "leaving by the {2} side")
    @CsvSource({
            "-300, 300, left",
            "1600, 300, right",
            "640, -400, up",
            "640, 1100, down",
    })
    void leavingTheBlastZoneCostsAStock(double x, double y, String side) {
        Fighter f = kit.fighterAt(BRAWLER, x, y);
        kit.world.step();

        assertEquals(Balance.STARTING_STOCKS - 1, f.stocks);
        assertEquals(1, f.falls);
        assertEquals(Balance.RESPAWN_DELAY_TICKS, f.respawnTicks);
        assertEquals("dead", f.stateName());
        assertFalse(f.isAlive());

        List<GameEvent> events = kit.world.takeEvents();
        GameEvent.Ko ko = assertInstanceOf(GameEvent.Ko.class, events.get(0));
        assertEquals(side, ko.dir());
        assertEquals(f.id, ko.id());
    }

    @Test
    void stayingInsideIsSafe() {
        // Off screen (x below 0) but still inside the blast zone (x above -200).
        Fighter f = kit.fighterAt(BRAWLER, -150, 300);
        kit.world.step();
        assertEquals(Balance.STARTING_STOCKS, f.stocks);
    }

    @Test
    void respawnsAfterTheDelayAboveTheMiddle() {
        Fighter f = kit.fighterAt(BRAWLER, -300, 300);
        f.percent = 75;
        kit.world.step(); // knocked out
        kit.world.takeEvents();

        kit.steps(Balance.RESPAWN_DELAY_TICKS - 1);
        assertEquals("dead", f.stateName(), "one tick to go");

        kit.world.step();
        assertTrue(f.isAlive());
        assertEquals(640, f.x, 1e-9);
        assertEquals(80, f.y, 1e-9);
        assertEquals(0, f.percent, "damage resets");
        assertEquals(Balance.RESPAWN_INVULNERABLE_TICKS, f.invulnerableTicks);
        assertInstanceOf(GameEvent.Respawn.class, kit.world.takeEvents().get(0));
    }

    @Test
    void invulnerabilityCountsDownAfterRespawning() {
        Fighter f = kit.fighterAt(BRAWLER, -300, 300);
        kit.steps(1 + Balance.RESPAWN_DELAY_TICKS);
        assertEquals(Balance.RESPAWN_INVULNERABLE_TICKS, f.invulnerableTicks);

        kit.steps(60);
        assertEquals(Balance.RESPAWN_INVULNERABLE_TICKS - 60, f.invulnerableTicks);
        kit.steps(Balance.RESPAWN_INVULNERABLE_TICKS);
        assertEquals(0, f.invulnerableTicks);
    }

    @Test
    void respawnedFighterFallsBackOntoTheTray() {
        Fighter f = kit.fighterAt(BRAWLER, -300, 300);
        kit.steps(1 + Balance.RESPAWN_DELAY_TICKS);
        kit.stepUntilLanded(f);
        assertEquals(520, f.y, 1e-9);
        assertEquals(Balance.STARTING_STOCKS - 1, f.stocks);
    }

    @Test
    void losingTheLastStockMeansOut() {
        Fighter f = kit.fighterAt(BRAWLER, -300, 300);
        f.stocks = 1;
        kit.world.step();

        assertEquals(0, f.stocks);
        assertTrue(f.isOut());
        assertEquals("out", f.stateName());
        assertEquals(kit.world.tick(), f.eliminatedAtTick());

        kit.steps(Balance.RESPAWN_DELAY_TICKS * 2);
        assertTrue(f.isOut(), "never comes back");
        assertEquals(-300, f.x, 1e-9, "spectators do not move");
    }
}
