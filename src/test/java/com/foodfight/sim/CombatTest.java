package com.foodfight.sim;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.foodfight.sim.FighterType.BRAWLER;
import static com.foodfight.sim.FighterType.SPEEDSTER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Melee attacks: timing, hitboxes, and what a hit does. */
class CombatTest {

    private static final InputState ATTACK = SimTestKit.keys("attack");

    private final SimTestKit kit = new SimTestKit();

    /** A fighter standing on the tray at x, facing the given way. */
    private Fighter fighter(FighterType type, double x, int face) {
        Fighter f = kit.standingAt(type, x, 520);
        f.face = face;
        return f;
    }

    /** Taps attack and waits until the end of the attack's first active tick. */
    private void attackUntilActive(Fighter f) {
        kit.hold(f, ATTACK, 1);
        kit.hold(f, SimTestKit.NOTHING, f.type.attack.startupTicks());
    }

    // ---------- hitting ----------

    @Test
    void hitboxTouchingAHurtboxIsAHit() {
        Fighter attacker = fighter(BRAWLER, 500, 1);
        Fighter target = fighter(SPEEDSTER, 560, -1);
        kit.world.takeEvents();

        attackUntilActive(attacker);

        assertEquals(BRAWLER.attack.damage(), target.percent);
        assertTrue(target.hitstunTicks > 0);
        assertTrue(target.vx > 0, "launched away from the attacker");
        assertTrue(target.vy < 0, "and upward");
        assertEquals(attacker.id, target.lastHitBy);

        GameEvent.Hit hit = kit.world.takeEvents().stream()
                .filter(e -> e instanceof GameEvent.Hit).map(e -> (GameEvent.Hit) e).findFirst().orElseThrow();
        assertEquals(target.id, hit.id());
        assertEquals(BRAWLER.attack.damage(), hit.dmg());
        assertEquals("hit", hit.k());
    }

    @Test
    void noHitboxDuringStartup() {
        Fighter attacker = fighter(BRAWLER, 500, 1);
        Fighter target = fighter(SPEEDSTER, 560, -1);
        kit.hold(attacker, ATTACK, 1);
        kit.hold(attacker, SimTestKit.NOTHING, BRAWLER.attack.startupTicks() - 1);
        assertEquals(Fighter.AttackPhase.STARTUP, attacker.attackPhase());
        assertEquals(0, target.percent, "still winding up");

        kit.steps(1);
        assertEquals(Fighter.AttackPhase.ACTIVE, attacker.attackPhase());
        assertEquals(10, target.percent);
    }

    @Test
    void aMissDoesNothing() {
        Fighter attacker = fighter(BRAWLER, 500, 1);
        Fighter target = fighter(SPEEDSTER, 700, -1);
        kit.hold(attacker, ATTACK, 1);
        kit.hold(attacker, SimTestKit.NOTHING, BRAWLER.attack.totalTicks() + 5);
        assertEquals(0, target.percent);
        assertEquals(0, target.hitstunTicks);
    }

    @Test
    void eachAttackHitsEachTargetOnlyOnce() {
        Fighter attacker = fighter(BRAWLER, 500, 1);
        Fighter target = fighter(SPEEDSTER, 560, -1);
        attackUntilActive(attacker);
        assertEquals(10, target.percent);

        // Keep dragging the target back into the hitbox for the rest of the
        // active part. The same swing must not hit it again.
        for (int i = 1; i < BRAWLER.attack.activeTicks(); i++) {
            target.x = 560;
            target.y = 520;
            target.vx = 0;
            target.vy = 0;
            kit.world.step();
        }
        assertEquals(10, target.percent);
    }

    @Test
    void aNewAttackCanHitAgain() {
        Fighter attacker = fighter(BRAWLER, 500, 1);
        Fighter target = fighter(SPEEDSTER, 560, -1);
        attackUntilActive(attacker);
        kit.hold(attacker, SimTestKit.NOTHING, BRAWLER.attack.totalTicks());
        target.x = 560;
        target.y = 520;
        target.vx = 0;
        target.vy = 0;
        target.hitstunTicks = 0;
        attackUntilActive(attacker);
        assertEquals(20, target.percent);
    }

    @Test
    void facingMirrorsTheHitbox() {
        Fighter attacker = fighter(BRAWLER, 500, -1);       // facing left
        Fighter onTheLeft = fighter(SPEEDSTER, 440, 1);
        Fighter onTheRight = fighter(SPEEDSTER, 560, -1);
        attackUntilActive(attacker);

        assertEquals(10, onTheLeft.percent, "the one in front gets hit");
        assertEquals(0, onTheRight.percent, "the one behind does not");
        assertTrue(onTheLeft.vx < 0, "launched to the left, the way the attacker faces");
    }

    @Test
    void invulnerableFightersCannotBeHit() {
        Fighter attacker = fighter(BRAWLER, 500, 1);
        Fighter target = fighter(SPEEDSTER, 560, -1);
        target.invulnerableTicks = 100;
        attackUntilActive(attacker);
        kit.steps(BRAWLER.attack.activeTicks());
        assertEquals(0, target.percent);
    }

    @Test
    void theLaunchFollowsTheKnockbackFormula() {
        Fighter attacker = fighter(BRAWLER, 500, 1);
        Fighter target = fighter(SPEEDSTER, 560, -1);
        target.percent = 40;
        attackUntilActive(attacker);

        AttackStats swing = BRAWLER.attack;
        double kb = Knockback.knockback(swing.baseKnockback(), swing.knockbackGrowth(), 50, SPEEDSTER.weight);
        assertEquals(Knockback.launchVx(kb, swing.launchAngleDegrees(), 1), target.vx, 1e-9);
        assertEquals(Knockback.launchVy(kb, swing.launchAngleDegrees()), target.vy, 1e-9);
        assertEquals(Knockback.hitstunTicks(kb), target.hitstunTicks);
    }

    @Test
    void hitstunMeansNoControl() {
        Fighter attacker = fighter(BRAWLER, 500, 1);
        Fighter target = fighter(SPEEDSTER, 560, -1);
        attackUntilActive(attacker);
        double launchSpeed = target.vx;

        kit.hold(target, SimTestKit.LEFT, 3); // trying to run back does nothing yet
        assertTrue(target.vx > 0.9 * launchSpeed, "still flying right");
        assertEquals(-1, target.face, "facing did not change either");
    }

    // ---------- when attacks are allowed ----------

    @Test
    void cannotAttackWhileInvulnerable() {
        Fighter f = fighter(BRAWLER, 500, 1);
        f.invulnerableTicks = 50;
        kit.hold(f, ATTACK, 1);
        assertFalse(f.attacking);
    }

    @Test
    void cannotAttackDuringHitstun() {
        Fighter f = fighter(BRAWLER, 500, 1);
        f.hitstunTicks = 30;
        kit.hold(f, ATTACK, 1);
        assertFalse(f.attacking);
    }

    @Test
    void pressingAgainMidAttackDoesNotRestartIt() {
        Fighter f = fighter(BRAWLER, 500, 1);
        kit.tapAttack(f);
        kit.steps(3);
        int before = f.attackTicks;
        kit.hold(f, ATTACK, 1);
        assertEquals(before + 1, f.attackTicks, "the same attack kept going");
    }

    @Test
    void aTapBetweenTwoTicksStillAttacks() {
        Fighter f = fighter(SPEEDSTER, 500, 1);
        kit.world.setInput(f.id, ATTACK);
        kit.world.setInput(f.id, SimTestKit.NOTHING);
        kit.world.step();
        assertTrue(f.attacking);
    }

    @Test
    void holdingAttackOnlyAttacksOnce() {
        Fighter f = fighter(SPEEDSTER, 500, 1);
        kit.hold(f, ATTACK, SPEEDSTER.attack.totalTicks() + 10);
        assertFalse(f.attacking, "the first jab ended and no second one started");
    }

    @Test
    void theNextAttackCanStartRightAfterRecovery() {
        Fighter f = fighter(SPEEDSTER, 500, 1);
        kit.tapAttack(f);
        kit.steps(SPEEDSTER.attack.totalTicks() - 1); // now on the last tick of the jab
        assertTrue(f.attacking);
        kit.hold(f, ATTACK, 1);
        assertTrue(f.attacking);
        assertEquals(1, f.attackTicks, "a fresh jab started");
    }

    @Test
    void beingHitCancelsYourAttack() {
        Fighter jabber = fighter(SPEEDSTER, 500, 1);
        Fighter swinger = fighter(BRAWLER, 550, -1);
        // Both press attack on the same tick. The jab is much faster.
        kit.world.setInput(jabber.id, ATTACK);
        kit.world.setInput(swinger.id, ATTACK);
        kit.steps(1);
        kit.world.setInput(jabber.id, SimTestKit.NOTHING);
        kit.world.setInput(swinger.id, SimTestKit.NOTHING);
        kit.steps(SPEEDSTER.attack.startupTicks());

        assertEquals(SPEEDSTER.attack.damage(), swinger.percent, "the jab landed");
        assertFalse(swinger.attacking, "and cancelled the swing");
        kit.steps(40);
        assertEquals(0, jabber.percent, "the cancelled swing never hit");
    }

    @Test
    void facingIsLockedDuringAnAttack() {
        Fighter f = fighter(BRAWLER, 500, 1);
        kit.tapAttack(f);
        kit.hold(f, SimTestKit.LEFT, 10);
        assertEquals(1, f.face, "still facing right mid swing");
        kit.hold(f, SimTestKit.LEFT, BRAWLER.attack.totalTicks());
        assertEquals(-1, f.face, "turns around once the swing is over");
    }

    @Test
    void twoFightersHittingEachOtherOnTheSameTickBothGetHit() {
        Fighter a = fighter(SPEEDSTER, 500, 1);
        Fighter b = fighter(SPEEDSTER, 560, -1);
        kit.world.setInput(a.id, ATTACK);
        kit.world.setInput(b.id, ATTACK);
        kit.steps(1);
        kit.world.setInput(a.id, SimTestKit.NOTHING);
        kit.world.setInput(b.id, SimTestKit.NOTHING);
        kit.steps(SPEEDSTER.attack.startupTicks());

        assertEquals(4, a.percent);
        assertEquals(4, b.percent);
        List<GameEvent> events = kit.world.takeEvents();
        assertEquals(2, events.stream().filter(e -> e instanceof GameEvent.Hit).count());
    }

    @Test
    void stateNamesShowTheAttackParts() {
        Fighter f = fighter(BRAWLER, 500, 1);
        kit.tapAttack(f);
        assertEquals("startup", f.stateName());
        kit.steps(BRAWLER.attack.startupTicks());
        assertEquals("active", f.stateName());
        kit.steps(BRAWLER.attack.activeTicks());
        assertEquals("recovery", f.stateName());
        kit.steps(BRAWLER.attack.recoveryTicks());
        assertEquals("idle", f.stateName());
    }

    @Test
    void respawningClearsAnAttack() {
        Fighter f = kit.fighterAt(BRAWLER, -300, 300);
        f.attacking = true;
        f.attackTicks = 3;
        kit.steps(1 + Balance.RESPAWN_DELAY_TICKS);
        assertFalse(f.attacking);
        assertInstanceOf(GameEvent.Respawn.class, kit.world.takeEvents().get(1));
    }
}
