package com.foodfight.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the pure knockback math in Knockback.java. */
class KnockbackTest {

    @Test
    void formulaMatchesTheDesign() {
        // (base + growth * percent) / weight = (400 + 5 * 60) / 1.0 = 700
        assertEquals(700, Knockback.knockback(400, 5, 60, 1.0), 1e-9);
        assertEquals(350, Knockback.knockback(400, 5, 60, 2.0), 1e-9);
    }

    @Test
    void knockbackGrowsWithPercent() {
        double low = Knockback.knockback(500, 4, 10, 1.0);
        double high = Knockback.knockback(500, 4, 120, 1.0);
        assertTrue(high > low);
    }

    @Test
    void heavierFightersGetLessKnockback() {
        double light = Knockback.knockback(500, 4, 50, FighterType.SPEEDSTER.weight);
        double heavy = Knockback.knockback(500, 4, 50, FighterType.BRAWLER.weight);
        assertTrue(heavy < light);
    }

    @Test
    void launchDirectionFollowsTheAttackersFacing() {
        assertTrue(Knockback.launchVx(1000, 40, 1) > 0, "facing right launches right");
        assertTrue(Knockback.launchVx(1000, 40, -1) < 0, "facing left launches left");
        assertEquals(Knockback.launchVx(1000, 40, 1), -Knockback.launchVx(1000, 40, -1), 1e-9);
    }

    @Test
    void launchGoesUpAtTheGivenAngle() {
        double vx = Knockback.launchVx(1000, 30, 1);
        double vy = Knockback.launchVy(1000, 30);
        assertTrue(vy < 0, "up is negative y");
        assertEquals(Math.cos(Math.toRadians(30)) * 1000, vx, 1e-9);
        assertEquals(-Math.sin(Math.toRadians(30)) * 1000, vy, 1e-9);
        assertEquals(1000, Math.hypot(vx, vy), 1e-9, "total speed equals the knockback");
    }

    @Test
    void hitstunFollowsTheFormulaInTheMiddle() {
        // 0.15 + 1000 / 2500 = 0.55 seconds = 33 ticks
        assertEquals(0.55, Knockback.hitstunSeconds(1000), 1e-9);
        assertEquals(33, Knockback.hitstunTicks(1000));
    }

    @Test
    void hitstunIsClampedAtBothEnds() {
        assertEquals(0.15, Knockback.hitstunSeconds(0), 1e-9);
        assertEquals(0.9, Knockback.hitstunSeconds(5000), 1e-9);
        assertEquals(0.9, Knockback.hitstunSeconds(1875), 1e-9, "0.15 + 1875 / 2500 is exactly 0.9");
        assertEquals(54, Knockback.hitstunTicks(99999));
    }

    @Test
    void bigHitsAreOverNineHundred() {
        assertFalse(Knockback.isBigHit(900));
        assertTrue(Knockback.isBigHit(901));
    }

    @Test
    void globDamageGrowsWithDistance() {
        assertEquals(3, Knockback.globDamage(0));
        assertEquals(3, Knockback.globDamage(150));
        assertEquals(6, Knockback.globDamage(375), "halfway: 5.5 rounds up to 6");
        assertEquals(8, Knockback.globDamage(600));
        assertEquals(8, Knockback.globDamage(2000));
        int last = 0;
        for (int d = 0; d <= 700; d += 10) {
            int damage = Knockback.globDamage(d);
            assertTrue(damage >= last, "never goes down as distance grows");
            last = damage;
        }
    }
}
