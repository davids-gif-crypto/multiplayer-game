package com.foodfight.sim;

/**
 * The three fighters a player can pick, with their body size, movement and
 * attack. The numbers themselves live in Balance.java, so they can be tuned
 * in one place.
 */
public enum FighterType {
    BRAWLER("Meatball",
            Balance.BRAWLER_WIDTH, Balance.BRAWLER_HEIGHT, Balance.BRAWLER_WEIGHT,
            Balance.BRAWLER_RUN_SPEED, Balance.BRAWLER_JUMP_SPEED_1, Balance.BRAWLER_JUMP_SPEED_2,
            // A slow, heavy swing.
            new AttackStats(Balance.BRAWLER_STARTUP_TICKS, Balance.BRAWLER_ACTIVE_TICKS,
                    Balance.BRAWLER_RECOVERY_TICKS, Balance.BRAWLER_DAMAGE,
                    Balance.BRAWLER_BASE_KNOCKBACK, Balance.BRAWLER_KNOCKBACK_GROWTH,
                    Balance.BRAWLER_LAUNCH_ANGLE, Balance.BRAWLER_HITBOX_WIDTH,
                    Balance.BRAWLER_HITBOX_HEIGHT, Balance.BRAWLER_HITBOX_OFFSET, false)),

    SPEEDSTER("French Fry",
            Balance.SPEEDSTER_WIDTH, Balance.SPEEDSTER_HEIGHT, Balance.SPEEDSTER_WEIGHT,
            Balance.SPEEDSTER_RUN_SPEED, Balance.SPEEDSTER_JUMP_SPEED_1, Balance.SPEEDSTER_JUMP_SPEED_2,
            // A fast jab.
            new AttackStats(Balance.SPEEDSTER_STARTUP_TICKS, Balance.SPEEDSTER_ACTIVE_TICKS,
                    Balance.SPEEDSTER_RECOVERY_TICKS, Balance.SPEEDSTER_DAMAGE,
                    Balance.SPEEDSTER_BASE_KNOCKBACK, Balance.SPEEDSTER_KNOCKBACK_GROWTH,
                    Balance.SPEEDSTER_LAUNCH_ANGLE, Balance.SPEEDSTER_HITBOX_WIDTH,
                    Balance.SPEEDSTER_HITBOX_HEIGHT, Balance.SPEEDSTER_HITBOX_OFFSET, false)),

    ZONER("Ketchup",
            Balance.ZONER_WIDTH, Balance.ZONER_HEIGHT, Balance.ZONER_WEIGHT,
            Balance.ZONER_RUN_SPEED, Balance.ZONER_JUMP_SPEED_1, Balance.ZONER_JUMP_SPEED_2,
            // Fires a ketchup glob. No melee hitbox, and the damage depends on
            // how far the glob flew, so those are 0 here.
            new AttackStats(Balance.ZONER_STARTUP_TICKS, Balance.ZONER_ACTIVE_TICKS,
                    Balance.ZONER_RECOVERY_TICKS, 0,
                    Balance.ZONER_BASE_KNOCKBACK, Balance.ZONER_KNOCKBACK_GROWTH,
                    Balance.ZONER_LAUNCH_ANGLE, 0, 0, 0, true));

    /** The friendly name shown in the lobby. */
    public final String displayName;
    /** Hurtbox width. */
    public final double width;
    /** Hurtbox height. */
    public final double height;
    /** Heavier fighters get launched less far. */
    public final double weight;
    /** Top running speed. */
    public final double runSpeed;
    /** Upward speed of the first jump. */
    public final double jumpSpeed1;
    /** Upward speed of the double jump. */
    public final double jumpSpeed2;
    /** The basic attack. */
    public final AttackStats attack;

    FighterType(String displayName, double width, double height, double weight,
                double runSpeed, double jumpSpeed1, double jumpSpeed2, AttackStats attack) {
        this.displayName = displayName;
        this.width = width;
        this.height = height;
        this.weight = weight;
        this.runSpeed = runSpeed;
        this.jumpSpeed1 = jumpSpeed1;
        this.jumpSpeed2 = jumpSpeed2;
        this.attack = attack;
    }

    /** Jump speed for a jump number: 0 is the first jump, 1 is the double jump. */
    public double jumpSpeed(int jumpsAlreadyUsed) {
        return jumpsAlreadyUsed == 0 ? jumpSpeed1 : jumpSpeed2;
    }

    /**
     * Turns text like "ZONER" into FighterType.ZONER.
     * Returns null for anything that is not a fighter, instead of crashing,
     * because the text comes from a browser and could be anything.
     */
    public static FighterType fromName(String name) {
        if (name == null) {
            return null;
        }
        for (FighterType type : values()) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return null;
    }
}
