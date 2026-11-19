package com.foodfight.room;

/**
 * The stages a room goes through:
 * LOBBY -> COUNTDOWN (3, 2, 1) -> PLAYING -> RESULTS -> back to LOBBY.
 */
public enum Phase {
    LOBBY,
    COUNTDOWN,
    PLAYING,
    RESULTS
}
