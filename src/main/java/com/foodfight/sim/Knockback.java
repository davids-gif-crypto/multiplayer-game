package com.foodfight.sim;

/**
 * The math for what happens when a fighter gets hit. These are "pure"
 * functions: they only look at their inputs and return a number, so they
 * are easy to test and easy to check by hand.
 *
 * The steps of a hit (GameWorld.applyHit does them in this order):
 *   1. target percent += damage
 *   2. knockback = (base + growth * percent) / weight
 *   3. launch speed: vx = cos(angle) * knockback * direction,
 *                    vy = -sin(angle) * knockback   (minus means up)
 *   4. hitstun = 0.15 + knockback / 2500 seconds, kept between 0.15 and 0.9
 *   5. a "big" hit is one with knockback over 900
 */
public final class Knockback {

    private Knockback() {
    }

    /**
     * Step 2. How hard the target is launched, in units per second.
     * percentAfterHit already includes this hit's damage (step 1 comes first).
     */
    public static double knockback(double base, double growth, int percentAfterHit, double weight) {
        return (base + growth * percentAfterHit) / weight;
    }

    /** Step 3, sideways part. direction is 1 (launched right) or -1 (launched left). */
    public static double launchVx(double knockback, double angleDegrees, int direction) {
        return Math.cos(Math.toRadians(angleDegrees)) * knockback * direction;
    }

    /** Step 3, up and down part. Negative, because up is negative y. */
    public static double launchVy(double knockback, double angleDegrees) {
        return -Math.sin(Math.toRadians(angleDegrees)) * knockback;
    }

    /** Step 4. How long the target cannot act, in seconds. */
    public static double hitstunSeconds(double knockback) {
        double seconds = Balance.HITSTUN_MIN_SECONDS + knockback / Balance.HITSTUN_KNOCKBACK_DIVISOR;
        return MathUtil.clamp(seconds, Balance.HITSTUN_MIN_SECONDS, Balance.HITSTUN_MAX_SECONDS);
    }

    /** Step 4 again, rounded to whole ticks. */
    public static int hitstunTicks(double knockback) {
        return (int) Math.round(hitstunSeconds(knockback) * Balance.TICKS_PER_SECOND);
    }

    /** Step 5. Big hits get a bigger splat on screen. */
    public static boolean isBigHit(double knockback) {
        return knockback > Balance.BIG_HIT_KNOCKBACK;
    }

    /**
     * Damage of a ketchup glob that flew {@code distance} units before hitting.
     * 3 damage up to 150 units, 8 damage from 600 units on, and in between it
     * grows in a straight line, rounded to a whole number.
     */
    public static int globDamage(double distance) {
        double progress = (distance - Balance.GLOB_SHORT_RANGE)
                / (Balance.GLOB_LONG_RANGE - Balance.GLOB_SHORT_RANGE);
        progress = MathUtil.clamp(progress, 0, 1);
        double damage = Balance.GLOB_MIN_DAMAGE + progress * (Balance.GLOB_MAX_DAMAGE - Balance.GLOB_MIN_DAMAGE);
        return (int) Math.round(damage);
    }
}
