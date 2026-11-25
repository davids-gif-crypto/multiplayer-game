package com.foodfight.sim;

import static com.foodfight.sim.MathUtil.round1;

/**
 * Something that happened in the match that the browsers should show as an
 * effect, like a jump puff or a knockout burst. Events are collected during
 * the ticks between snapshots and sent along with the next snapshot.
 *
 * Every event has a short text field "k" (kind) saying what it is.
 */
public sealed interface GameEvent {

    /** A fighter jumped. (x, y) is where their feet were. */
    record Jump(String k, int id, double x, double y) implements GameEvent {
        public Jump(int id, double x, double y) {
            this("jump", id, round1(x), round1(y));
        }
    }

    /**
     * A fighter flew out of the blast zone. (x, y) is their center when it
     * happened, and dir is the side they left by: left, right, up or down.
     */
    record Ko(String k, int id, double x, double y, String dir) implements GameEvent {
        public Ko(int id, double x, double y, String dir) {
            this("ko", id, round1(x), round1(y), dir);
        }
    }

    /**
     * Fighter {@code id} got hit. (x, y) is where the hit landed, dmg is the
     * damage it did, and big means the knockback was strong (a bigger splat).
     */
    record Hit(String k, int id, double x, double y, int dmg, boolean big) implements GameEvent {
        public Hit(int id, double x, double y, int dmg, boolean big) {
            this("hit", id, round1(x), round1(y), dmg, big);
        }
    }

    /** A fighter came back after a knockout. */
    record Respawn(String k, int id) implements GameEvent {
        public Respawn(int id) {
            this("respawn", id);
        }
    }
}
