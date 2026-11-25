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

    // ---------------------------------------------------------------
    // Physics shared by every fighter
    // Distances are in world units (the stage is 1280 x 720 units).
    // Speeds are units per second, accelerations are units per second squared.
    // ---------------------------------------------------------------

    /** How fast everyone falls faster and faster. */
    public static final double GRAVITY = 2400;

    /** Fastest normal falling speed. */
    public static final double MAX_FALL_SPEED = 1100;

    /** Fastest falling speed while holding Down (fast fall). */
    public static final double FAST_FALL_SPEED = 1700;

    /** How quickly you speed up toward your run speed on the ground. */
    public static final double GROUND_ACCEL = 2400;

    /** How quickly you speed up toward your run speed in the air. */
    public static final double AIR_ACCEL = 1400;

    /** How quickly you slow down on the ground when no direction is held. */
    public static final double GROUND_DECEL = 3000;

    /** How quickly you slow down in the air when no direction is held. */
    public static final double AIR_DECEL = 600;

    /** Jumps you get before you have to land again (ground jump plus double jump). */
    public static final int MAX_JUMPS = 2;

    /** Ticks a bread slice is ignored after you drop through it with Down. */
    public static final int DROP_THROUGH_TICKS = 12;

    /**
     * Air drag while flying in hitstun: roughly the fraction of sideways
     * speed lost per second. Kept slight, so a launched fighter keeps most of
     * its speed. (On the ground, hitstun slows you with GROUND_DECEL instead.)
     */
    public static final double HITSTUN_AIR_DRAG = 0.3;

    // ---------------------------------------------------------------
    // Stocks and respawning
    // ---------------------------------------------------------------

    /** Lives each fighter starts with. */
    public static final int STARTING_STOCKS = 3;

    /** Ticks between being knocked out and coming back (1.5 seconds). */
    public static final int RESPAWN_DELAY_TICKS = 90;

    /** Ticks you cannot be hit (and cannot attack) after respawning (2 seconds). */
    public static final int RESPAWN_INVULNERABLE_TICKS = 120;

    // ---------------------------------------------------------------
    // Getting hit (see Knockback.java for how these are used)
    //   knockback = (base + growth * percent) / weight
    //   hitstun   = 0.15 + knockback / 2500 seconds, kept between 0.15 and 0.9
    // ---------------------------------------------------------------

    public static final double HITSTUN_MIN_SECONDS = 0.15;
    public static final double HITSTUN_MAX_SECONDS = 0.9;
    public static final double HITSTUN_KNOCKBACK_DIVISOR = 2500;

    /** Hits with more knockback than this count as "big" and get a bigger splat. */
    public static final double BIG_HIT_KNOCKBACK = 900;

    /** Damage percent never goes above this, so it always fits on screen. */
    public static final int MAX_PERCENT = 999;

    /**
     * Stretch goal switch: special attacks on K. The key already reaches the
     * server, but nothing happens until this is true and the specials are built.
     */
    public static final boolean SPECIALS_ENABLED = false;

    // ---------------------------------------------------------------
    // Fighters
    //
    // Hurtbox: the box you can be hit in (width x height).
    // Weight: heavier fighters fly less far when hit.
    // Jump speed 1 is the first jump, jump speed 2 is the double jump.
    //
    // Attacks go through three parts, counted in ticks:
    //   startup (winding up), active (the hitbox exists), recovery (cooling down).
    // Launch angle is in degrees up from flat.
    // Melee hitbox: a width x height box in front of the fighter, at the
    // height of the fighter's middle. Offset is the gap from the fighter's
    // middle to the near edge of the box.
    //
    // TUNING NOTE: weight, base knockback and growth were changed from the
    // first numbers in the design (shown as "was ...") to meet the tuning
    // goals. Run ./gradlew tuning to print the table, and see TuningTest.java
    // for the goals and the reasons.
    // ---------------------------------------------------------------

    // Brawler (meatball): slow, heavy, hits hard.
    public static final double BRAWLER_WIDTH = 56;
    public static final double BRAWLER_HEIGHT = 56;
    public static final double BRAWLER_WEIGHT = 1.1;              // was 1.3
    public static final double BRAWLER_RUN_SPEED = 250;
    public static final double BRAWLER_JUMP_SPEED_1 = 780;
    public static final double BRAWLER_JUMP_SPEED_2 = 700;
    public static final int BRAWLER_STARTUP_TICKS = 11;
    public static final int BRAWLER_ACTIVE_TICKS = 7;
    public static final int BRAWLER_RECOVERY_TICKS = 18;
    public static final int BRAWLER_DAMAGE = 10;
    public static final double BRAWLER_BASE_KNOCKBACK = 730;      // was 420
    public static final double BRAWLER_KNOCKBACK_GROWTH = 4.3;    // was 9
    public static final double BRAWLER_LAUNCH_ANGLE = 40;
    public static final double BRAWLER_HITBOX_WIDTH = 70;
    public static final double BRAWLER_HITBOX_HEIGHT = 50;
    public static final double BRAWLER_HITBOX_OFFSET = 20;

    // Speedster (french fry): fast and light, quick weak jabs.
    public static final double SPEEDSTER_WIDTH = 36;
    public static final double SPEEDSTER_HEIGHT = 72;
    public static final double SPEEDSTER_WEIGHT = 0.9;            // was 0.8
    public static final double SPEEDSTER_RUN_SPEED = 400;
    public static final double SPEEDSTER_JUMP_SPEED_1 = 860;
    public static final double SPEEDSTER_JUMP_SPEED_2 = 780;
    public static final int SPEEDSTER_STARTUP_TICKS = 4;
    public static final int SPEEDSTER_ACTIVE_TICKS = 5;
    public static final int SPEEDSTER_RECOVERY_TICKS = 8;
    public static final int SPEEDSTER_DAMAGE = 4;
    public static final double SPEEDSTER_BASE_KNOCKBACK = 450;    // was 260
    public static final double SPEEDSTER_KNOCKBACK_GROWTH = 2.9;  // was 6
    public static final double SPEEDSTER_LAUNCH_ANGLE = 35;
    public static final double SPEEDSTER_HITBOX_WIDTH = 50;
    public static final double SPEEDSTER_HITBOX_HEIGHT = 36;
    public static final double SPEEDSTER_HITBOX_OFFSET = 12;

    // Zoner (ketchup bottle): in the middle, shoots ketchup globs.
    // Its damage comes from how far the glob flew (see GLOB_ below).
    public static final double ZONER_WIDTH = 44;
    public static final double ZONER_HEIGHT = 76;
    public static final double ZONER_WEIGHT = 1.0;
    public static final double ZONER_RUN_SPEED = 300;
    public static final double ZONER_JUMP_SPEED_1 = 800;
    public static final double ZONER_JUMP_SPEED_2 = 720;
    public static final int ZONER_STARTUP_TICKS = 10;
    public static final int ZONER_ACTIVE_TICKS = 1;
    public static final int ZONER_RECOVERY_TICKS = 14;
    public static final double ZONER_BASE_KNOCKBACK = 350;        // was 200
    public static final double ZONER_KNOCKBACK_GROWTH = 2.4;      // was 5
    public static final double ZONER_LAUNCH_ANGLE = 30;

    // ---------------------------------------------------------------
    // The Zoner's ketchup glob
    // ---------------------------------------------------------------

    public static final double GLOB_RADIUS = 14;
    /** Sideways speed, in the direction the Zoner faces. */
    public static final double GLOB_SPEED = 650;
    /** A tiny pull downward, so the glob droops a little. */
    public static final double GLOB_GRAVITY = 150;
    /** The glob disappears after this many ticks (1.2 seconds). */
    public static final int GLOB_LIFETIME_TICKS = 72;
    /** Each Zoner can have at most this many globs flying at once. */
    public static final int GLOB_MAX_ALIVE = 2;
    /** Ticks after a shot before the Zoner can start another one. */
    public static final int GLOB_COOLDOWN_TICKS = 25;
    /** Damage when the glob hits close to where it was fired... */
    public static final int GLOB_MIN_DAMAGE = 3;
    /** ...and when it hits after flying far. */
    public static final int GLOB_MAX_DAMAGE = 8;
    /** Up to this distance the glob does the minimum damage. */
    public static final double GLOB_SHORT_RANGE = 150;
    /** From this distance on the glob does the maximum damage. */
    public static final double GLOB_LONG_RANGE = 600;
}
