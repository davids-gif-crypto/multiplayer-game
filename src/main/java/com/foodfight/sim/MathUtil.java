package com.foodfight.sim;

/** Small math helpers used by the simulation. */
public final class MathUtil {

    private MathUtil() {
    }

    /**
     * Moves {@code value} toward {@code target} by at most {@code maxStep},
     * without going past it. Used for speeding up and slowing down smoothly.
     * Example: approach(100, 250, 40) is 140, approach(240, 250, 40) is 250.
     */
    public static double approach(double value, double target, double maxStep) {
        if (value < target) {
            return Math.min(value + maxStep, target);
        }
        return Math.max(value - maxStep, target);
    }

    /** Keeps {@code value} between {@code min} and {@code max}. */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Rounds to 1 decimal place, to keep network messages small. 12.345 becomes 12.3. */
    public static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
