package com.foodfight.sim;

/**
 * The three fighters a player can pick, with their body size and movement
 * numbers. The numbers themselves live in Balance.java, so they can be
 * tuned in one place.
 */
public enum FighterType {
    BRAWLER("Meatball",
            Balance.BRAWLER_WIDTH, Balance.BRAWLER_HEIGHT, Balance.BRAWLER_WEIGHT,
            Balance.BRAWLER_RUN_SPEED, Balance.BRAWLER_JUMP_SPEED_1, Balance.BRAWLER_JUMP_SPEED_2),

    SPEEDSTER("French Fry",
            Balance.SPEEDSTER_WIDTH, Balance.SPEEDSTER_HEIGHT, Balance.SPEEDSTER_WEIGHT,
            Balance.SPEEDSTER_RUN_SPEED, Balance.SPEEDSTER_JUMP_SPEED_1, Balance.SPEEDSTER_JUMP_SPEED_2),

    ZONER("Ketchup",
            Balance.ZONER_WIDTH, Balance.ZONER_HEIGHT, Balance.ZONER_WEIGHT,
            Balance.ZONER_RUN_SPEED, Balance.ZONER_JUMP_SPEED_1, Balance.ZONER_JUMP_SPEED_2);

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

    FighterType(String displayName, double width, double height, double weight,
                double runSpeed, double jumpSpeed1, double jumpSpeed2) {
        this.displayName = displayName;
        this.width = width;
        this.height = height;
        this.weight = weight;
        this.runSpeed = runSpeed;
        this.jumpSpeed1 = jumpSpeed1;
        this.jumpSpeed2 = jumpSpeed2;
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
