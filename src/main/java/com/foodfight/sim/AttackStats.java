package com.foodfight.sim;

/**
 * Everything about one fighter's basic attack (the J key).
 *
 * An attack has three parts, counted in ticks:
 *   startup  - winding up, nothing can be hit yet
 *   active   - the hitbox exists (or, for the Zoner, the glob is fired)
 *   recovery - cooling down, you cannot attack again yet
 *
 * The knockback numbers feed the formula in Knockback.java.
 * The hitbox numbers are 0 for the Zoner, whose attack fires a glob instead.
 */
public record AttackStats(int startupTicks, int activeTicks, int recoveryTicks, int damage,
                          double baseKnockback, double knockbackGrowth, double launchAngleDegrees,
                          double hitboxWidth, double hitboxHeight, double hitboxOffset,
                          boolean firesGlob) {

    /** How long the whole attack lasts, start to finish. */
    public int totalTicks() {
        return startupTicks + activeTicks + recoveryTicks;
    }
}
