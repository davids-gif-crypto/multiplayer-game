package com.foodfight.sim;

/**
 * Every tuning number in the game lives here, so the feel of the game can be
 * adjusted in one place without hunting through the code.
 *
 * Times are counted in ticks. The server runs 60 ticks per second, so
 * 60 ticks = 1 second, 30 ticks = half a second, and so on.
 *
 * Later milestones add physics, fighter and combat numbers to this file.
 */
public final class Balance {

    private Balance() {
        // Only constants here, nobody needs to make a Balance object.
    }

    // ---------------------------------------------------------------
    // Timing
    // ---------------------------------------------------------------

    /** How many simulation steps the server runs every second. */
    public static final int TICKS_PER_SECOND = 60;

    /** Length of one simulation step in seconds (1/60). */
    public static final double DT = 1.0 / TICKS_PER_SECOND;

    /** Send a state snapshot every this many ticks (2 means 30 per second). */
    public static final int SNAPSHOT_EVERY_TICKS = 2;

    /** If the server falls behind, run at most this many extra steps to catch up. */
    public static final int MAX_CATCH_UP_STEPS = 2;

    // ---------------------------------------------------------------
    // Match flow
    // ---------------------------------------------------------------

    /** The countdown shows 3, 2, 1 before a match. */
    public static final int COUNTDOWN_FROM = 3;

    /** Ticks each countdown number stays on screen (1 second). */
    public static final int COUNTDOWN_STEP_TICKS = 60;

    /** Length of a match in seconds (5 minutes). */
    public static final int MATCH_SECONDS = 300;

    /** A player who disconnects mid match is eliminated after this long (5 seconds). */
    public static final int DISCONNECT_GRACE_TICKS = 5 * TICKS_PER_SECOND;

    /** If a player sends nothing for this long (2 seconds), their held keys are cleared. */
    public static final int INPUT_TIMEOUT_TICKS = 2 * TICKS_PER_SECOND;

    // ---------------------------------------------------------------
    // Rooms and server limits
    // ---------------------------------------------------------------

    /** Most players in one room. */
    public static final int MAX_PLAYERS = 4;

    /** Fewest players needed before the host can start. */
    public static final int MIN_PLAYERS_TO_START = 2;

    /** Most rooms the server will hold at once. */
    public static final int MAX_ROOMS = 50;

    /** A room with nobody connected is deleted after this long (60 seconds). */
    public static final int EMPTY_ROOM_DELETE_TICKS = 60 * TICKS_PER_SECOND;

    /** A room sitting on the results screen is deleted after this long (10 minutes). */
    public static final int RESULTS_IDLE_DELETE_TICKS = 10 * 60 * TICKS_PER_SECOND;

    /** Longest allowed nickname, in characters. */
    public static final int MAX_NAME_LENGTH = 12;

    /** Most messages one browser may send per second before we drop the extras. */
    public static final int MAX_MESSAGES_PER_SECOND = 120;
}
