package com.foodfight.sim;

/**
 * One fighter in a match: where it is, how fast it is moving, its damage
 * and lives, and what keys its player is holding.
 *
 * (x, y) is the middle of the fighter's feet. y grows downward, so a
 * negative vy means moving up.
 *
 * GameWorld changes these fields every tick. The fields are package private
 * (no "public") so only the sim package can change them. Other code reads
 * them through the getters at the bottom.
 */
public class Fighter {

    public final int id;
    public final FighterType type;

    // Position and speed
    double x;
    double y;
    double vx;
    double vy;
    /** 1 means facing right, -1 means facing left. */
    int face = 1;

    // Damage and lives
    int percent = 0;
    int stocks = Balance.STARTING_STOCKS;
    int falls = 0;
    /** The tick this fighter lost its last stock, or -1 if still in. */
    long eliminatedAtTick = -1;

    // Ground and jumping
    boolean onGround = false;
    Stage.Platform standingOn = null;
    int jumpsUsed = 0;
    /** The bread slice we are dropping through, ignored while dropThroughTicks > 0. */
    Stage.Platform ignoredPlatform = null;
    int dropThroughTicks = 0;

    // Timers, all counted in ticks
    int invulnerableTicks = 0;
    int respawnTicks = 0;
    int hitstunTicks = 0;

    // Input
    InputState input = InputState.NONE;
    /** Was jump held last tick? Used to spot the moment jump is pressed. */
    boolean jumpWasHeld = false;

    Fighter(int id, FighterType type, double x, double y) {
        this.id = id;
        this.type = type;
        this.x = x;
        this.y = y;
    }

    /** Lost every stock: now a spectator. */
    public boolean isOut() {
        return stocks <= 0;
    }

    /** On the stage right now: not out and not waiting to respawn. */
    public boolean isAlive() {
        return !isOut() && respawnTicks == 0;
    }

    /** The box this fighter can be hit in. */
    public Rect hurtbox() {
        return new Rect(x - type.width / 2, y - type.height, type.width, type.height);
    }

    /** A short word for what the fighter is doing, sent to browsers for drawing. */
    public String stateName() {
        if (isOut()) {
            return "out";
        }
        if (respawnTicks > 0) {
            return "dead";
        }
        if (hitstunTicks > 0) {
            return "hitstun";
        }
        if (onGround) {
            return Math.abs(vx) > 1 ? "run" : "idle";
        }
        return vy < 0 ? "jump" : "fall";
    }

    // ---------- getters ----------

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double vx() {
        return vx;
    }

    public double vy() {
        return vy;
    }

    public int face() {
        return face;
    }

    public int percent() {
        return percent;
    }

    public int stocks() {
        return stocks;
    }

    public int falls() {
        return falls;
    }

    public boolean onGround() {
        return onGround;
    }

    public int jumpsUsed() {
        return jumpsUsed;
    }

    public int invulnerableTicks() {
        return invulnerableTicks;
    }

    public int respawnTicks() {
        return respawnTicks;
    }

    public long eliminatedAtTick() {
        return eliminatedAtTick;
    }

    public InputState input() {
        return input;
    }
}
