package com.foodfight.sim;

import org.junit.jupiter.api.Test;

import static com.foodfight.sim.FighterType.BRAWLER;
import static com.foodfight.sim.FighterType.SPEEDSTER;
import static com.foodfight.sim.FighterType.ZONER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Zoner's ketchup glob. */
class ProjectileTest {

    private static final InputState ATTACK = SimTestKit.keys("attack");
    private static final int STARTUP = ZONER.attack.startupTicks();

    private final SimTestKit kit = new SimTestKit();

    private Fighter zonerAt(double x, int face) {
        Fighter z = kit.standingAt(ZONER, x, 520);
        z.face = face;
        return z;
    }

    /** Presses attack and waits until the glob is out (the first active tick). */
    private void shoot(Fighter zoner) {
        kit.hold(zoner, ATTACK, 1);
        kit.hold(zoner, SimTestKit.NOTHING, STARTUP);
    }

    /** Keeps a fighter frozen in place (in the air) for a test. */
    private void pin(Fighter f, double x, double y) {
        f.x = x;
        f.y = y;
        f.vx = 0;
        f.vy = 0;
    }

    @Test
    void theGlobComesOutOnTheFirstActiveTick() {
        Fighter zoner = zonerAt(400, 1);
        kit.hold(zoner, ATTACK, 1);
        kit.hold(zoner, SimTestKit.NOTHING, STARTUP - 1);
        assertTrue(kit.world.projectiles().isEmpty(), "nothing yet during startup");

        kit.steps(1);
        assertEquals(1, kit.world.projectiles().size());
        Projectile glob = kit.world.projectiles().get(0);
        assertEquals(zoner.id, glob.ownerId);
        assertEquals(Balance.GLOB_SPEED, glob.vx, 1e-9);
        assertTrue(glob.x > zoner.x, "in front of the bottle");
    }

    @Test
    void theGlobFliesTheWayTheZonerFaces() {
        Fighter zoner = zonerAt(700, -1);
        shoot(zoner);
        Projectile glob = kit.world.projectiles().get(0);
        assertEquals(-Balance.GLOB_SPEED, glob.vx, 1e-9);
        double startX = glob.x;
        kit.steps(10);
        assertTrue(glob.x < startX - 100);
        assertTrue(glob.vy > 0, "droops a little");
    }

    @Test
    void aCloseHitDoesLittleDamage() {
        Fighter zoner = zonerAt(400, 1);
        Fighter target = kit.standingAt(SPEEDSTER, 470, 520);
        shoot(zoner);
        assertEquals(Balance.GLOB_MIN_DAMAGE, target.percent);
        assertTrue(kit.world.projectiles().isEmpty(), "the glob splatted");
        assertTrue(target.vx > 0, "pushed away from the Zoner");
        assertEquals(zoner.id, target.lastHitBy);
    }

    @Test
    void aFarHitDoesFullDamage() {
        Fighter zoner = zonerAt(300, 1);
        // Hang a Brawler in the air past the end of the tray, in the glob's path.
        Fighter target = kit.fighterAt(BRAWLER, 1100, 610);
        kit.hold(zoner, ATTACK, 1);
        for (int i = 0; i < 100 && target.percent == 0; i++) {
            pin(target, 1100, 610);
            kit.world.step();
        }
        assertEquals(Balance.GLOB_MAX_DAMAGE, target.percent);
    }

    @Test
    void atMostTwoGlobsAtOnce() {
        Fighter zoner = zonerAt(300, 1);
        kit.hold(zoner, ATTACK, 1);                       // tick 1: shot 1 (comes out on tick 11)
        kit.hold(zoner, SimTestKit.NOTHING, 34);          // wait out the cooldown
        kit.hold(zoner, ATTACK, 1);                       // tick 36: shot 2
        assertTrue(zoner.attacking);
        kit.hold(zoner, SimTestKit.NOTHING, 34);
        assertEquals(2, kit.world.projectiles().size());

        kit.hold(zoner, ATTACK, 1);                       // tick 71: both still flying
        assertFalse(zoner.attacking, "a third shot is not allowed");
        assertEquals(2, kit.world.projectiles().size());
    }

    @Test
    void theCooldownBlocksQuickShots() {
        Fighter zoner = zonerAt(300, 1);
        kit.hold(zoner, ATTACK, 1);                       // tick 1, fires on tick 11
        kit.hold(zoner, SimTestKit.NOTHING, ZONER.attack.totalTicks());
        assertFalse(zoner.attacking, "the attack itself is over");
        kit.hold(zoner, ATTACK, 1);
        assertFalse(zoner.attacking, "but the cooldown is not");
        assertTrue(zoner.globCooldownTicks > 0);

        kit.hold(zoner, SimTestKit.NOTHING, zoner.globCooldownTicks);
        kit.hold(zoner, ATTACK, 1);
        assertTrue(zoner.attacking, "now it can shoot again");
    }

    @Test
    void aGlobNeverHitsItsOwnZoner() {
        Fighter zoner = zonerAt(300, 1);
        shoot(zoner);
        Projectile glob = kit.world.projectiles().get(0);
        // Put the Zoner right where its own glob is about to be.
        pin(zoner, glob.x + 15, 520);
        kit.steps(1);
        assertEquals(0, zoner.percent);
        assertTrue(glob.alive);
    }

    @Test
    void aGlobPassesThroughInvulnerableFighters() {
        Fighter zoner = zonerAt(400, 1);
        Fighter target = kit.standingAt(SPEEDSTER, 470, 520);
        target.invulnerableTicks = 100;
        shoot(zoner);
        kit.steps(3);
        assertEquals(0, target.percent);
        assertEquals(1, kit.world.projectiles().size());
    }

    @Test
    void aGlobExpiresAfterItsLifetime() {
        Fighter zoner = zonerAt(300, 1);
        shoot(zoner);
        kit.steps(Balance.GLOB_LIFETIME_TICKS - 5);
        assertEquals(1, kit.world.projectiles().size(), "still flying");
        kit.steps(10);
        assertTrue(kit.world.projectiles().isEmpty(), "gone");
    }

    @Test
    void aGlobFliesThroughPlatforms() {
        // A glob placed inside the tray keeps going.
        Projectile glob = new Projectile(99, 1, ZONER.attack, 600, 540, Balance.GLOB_SPEED);
        kit.world.projectiles().add(glob);
        kit.steps(10);
        assertTrue(glob.alive);
        assertEquals(600 + 10 * Balance.GLOB_SPEED * Balance.DT, glob.x, 1e-6);
    }

    @Test
    void aGlobLeavingTheBlastZoneIsRemoved() {
        Projectile glob = new Projectile(99, 1, ZONER.attack, 1470, 300, Balance.GLOB_SPEED);
        kit.world.projectiles().add(glob);
        kit.steps(2);
        assertTrue(kit.world.projectiles().isEmpty());
    }

    @Test
    void touchingUsesTheGlobsRoundShape() {
        Projectile glob = new Projectile(1, 1, ZONER.attack, 100, 100, 0);
        Rect box = new Rect(110, 90, 20, 20);   // left edge 10 away: within radius 14
        assertTrue(glob.touches(box));
        Rect corner = new Rect(111, 111, 20, 20); // corner about 15.6 away: just out of reach
        assertFalse(glob.touches(corner));
    }
}
