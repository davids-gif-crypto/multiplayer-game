package com.foodfight.sim;

import java.util.HashSet;
import java.util.Set;

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

    /** Which part of an attack a fighter is in. */
    public enum AttackPhase { NONE, STARTUP, ACTIVE, RECOVERY }

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

    // Attacking
    boolean attacking = false;
    /** Ticks since the attack started. 1 on the first tick. */
    int attackTicks = 0;
    /** Ids of everyone this attack already hit, so it hits each target once. */
    final Set<Integer> hitThisAttack = new HashSet<>();
    /** Zoner only: ticks until another glob may be fired. */
    int globCooldownTicks = 0;

    // Who hit us last, and when. Used to give out KO credit.
    int lastHitBy = 0;
    long lastHitTick = -1;

    // Input
    InputState input = InputState.NONE;
    /**
     * Set the moment the jump key goes down, and used up by the next tick.
     * Remembering the press like this means a very quick tap is never
     * missed, even if the key went down and back up between two ticks
     * (which happens when network messages arrive bunched together).
     */
    boolean jumpTapped = false;
    /** Same idea for the attack key. */
    boolean attackTapped = false;
    /** Same idea for the special key. */
    boolean specialTapped = false;

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

    /** Which part of the attack we are in, worked out from the attack timer. */
    public AttackPhase attackPhase() {
        if (!attacking) {
            return AttackPhase.NONE;
        }
        AttackStats attack = type.attack;
        if (attackTicks <= attack.startupTicks()) {
            return AttackPhase.STARTUP;
        }
        if (attackTicks <= attack.startupTicks() + attack.activeTicks()) {
            return AttackPhase.ACTIVE;
        }
        return AttackPhase.RECOVERY;
    }

    /**
     * The melee hitbox, in front of the fighter and mirrored by facing. It
     * exists whether or not the attack is active, so the debug view can draw
     * it. GameWorld only uses it during the active part.
     */
    public Rect attackHitbox() {
        AttackStats attack = type.attack;
        double nearEdge = x + face * attack.hitboxOffset();
        double left = (face > 0) ? nearEdge : nearEdge - attack.hitboxWidth();
        double middleY = y - type.height / 2;
        return new Rect(left, middleY - attack.hitboxHeight() / 2, attack.hitboxWidth(), attack.hitboxHeight());
    }

    /**
     * A short word for what the fighter is doing, sent to browsers for drawing:
     * idle, run, jump, fall, startup, active, recovery, hitstun, dead or out.
     */
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
        switch (attackPhase()) {
            case STARTUP:
                return "startup";
            case ACTIVE:
                return "active";
            case RECOVERY:
                return "recovery";
            default:
                break;
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

    public int hitstunTicks() {
        return hitstunTicks;
    }

    public boolean attacking() {
        return attacking;
    }

    public int lastHitBy() {
        return lastHitBy;
    }

    public long lastHitTick() {
        return lastHitTick;
    }

    public long eliminatedAtTick() {
        return eliminatedAtTick;
    }

    public InputState input() {
        return input;
    }
}
