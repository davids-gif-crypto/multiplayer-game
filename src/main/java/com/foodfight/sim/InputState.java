package com.foodfight.sim;

/**
 * The keys one player is holding right now. The browser sends this whenever
 * a key goes down or up. Only these true/false values ever reach the
 * simulation, so a player cannot cheat by sending made up numbers.
 */
public record InputState(boolean left, boolean right, boolean jump, boolean down,
                         boolean attack, boolean special, boolean shield) {

    /** Nothing held. */
    public static final InputState NONE = new InputState(false, false, false, false, false, false, false);
}
