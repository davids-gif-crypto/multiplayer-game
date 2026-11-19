package com.foodfight.sim;

/**
 * The three fighters a player can pick.
 *
 * Their physics and combat numbers are added in later milestones
 * (and kept in Balance.java). For now each one just has a display name.
 */
public enum FighterType {
    BRAWLER("Meatball"),
    SPEEDSTER("French Fry"),
    ZONER("Ketchup");

    /** The friendly name shown in the lobby. */
    public final String displayName;

    FighterType(String displayName) {
        this.displayName = displayName;
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
