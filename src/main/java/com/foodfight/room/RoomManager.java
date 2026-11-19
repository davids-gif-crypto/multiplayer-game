package com.foodfight.room;

import com.foodfight.sim.Balance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Holds every live room and runs the one shared game thread.
 *
 * The game thread wakes up 60 times per second and calls tick() on every
 * room. Using one thread for all rooms keeps things simple: a room is only
 * ever touched by that one thread.
 */
public class RoomManager {

    private static final Logger log = LoggerFactory.getLogger(RoomManager.class);

    /** Nanoseconds in one tick (1/60 of a second). */
    private static final long TICK_NANOS = 1_000_000_000L / Balance.TICKS_PER_SECOND;

    /** Room code to room. ConcurrentHashMap so web threads can look rooms up safely. */
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Random random;

    private ScheduledExecutorService loop;
    private long nextTickAt;

    public RoomManager() {
        this(new Random());
    }

    /** Tests pass a Random with a fixed seed so the codes are the same every run. */
    public RoomManager(Random random) {
        this.random = random;
    }

    // =================================================================
    // Rooms (called from WebSocket threads)
    // =================================================================

    /**
     * Makes a new room with an unused code.
     * Returns null if the server already has the most rooms it allows.
     * Synchronized so two people creating rooms at once cannot get the same code.
     */
    public synchronized Room createRoom() {
        if (rooms.size() >= Balance.MAX_ROOMS) {
            return null;
        }
        while (true) {
            String code = RoomCode.random(random);
            if (!rooms.containsKey(code)) {
                Room room = new Room(code);
                rooms.put(code, room);
                return room;
            }
        }
    }

    /** Finds a room by a code the player typed (any case). Null if there is none. */
    public Room find(String typedCode) {
        String code = RoomCode.normalize(typedCode);
        if (!RoomCode.isValid(code)) {
            return null;
        }
        return rooms.get(code);
    }

    public int roomCount() {
        return rooms.size();
    }

    // =================================================================
    // The game loop
    // =================================================================

    /** Ticks every room once, and deletes rooms that are finished. */
    public void tickAll() {
        for (Room room : rooms.values()) {
            try {
                room.tick();
            } catch (RuntimeException e) {
                // One broken room must never stop the other rooms.
                log.error("Room {} crashed during tick", room.code(), e);
            }
            if (room.shouldDelete()) {
                rooms.remove(room.code(), room);
                room.close();
                log.info("Deleted room {} ({} rooms left)", room.code(), rooms.size());
            }
        }
    }

    /** Starts the game thread. */
    public synchronized void start() {
        if (loop != null) {
            return;
        }
        loop = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "game-loop");
            thread.setDaemon(true); // do not keep Java running after the server stops
            return thread;
        });
        nextTickAt = System.nanoTime();
        loop.scheduleAtFixedRate(this::runDueTicks, 0, TICK_NANOS, TimeUnit.NANOSECONDS);
    }

    /** Stops the game thread. */
    public synchronized void stop() {
        if (loop != null) {
            loop.shutdownNow();
            loop = null;
        }
    }

    /**
     * Runs the ticks that are due right now.
     *
     * Normally that is exactly one. If the computer was busy and we fell
     * behind, we run up to MAX_CATCH_UP_STEPS extra ticks to catch up. If we
     * are still behind after that, we skip the missed ticks instead of
     * trying to run them all. Running them all would make the next tick late
     * too, and the server would never catch up (a "death spiral").
     */
    private void runDueTicks() {
        try {
            long now = System.nanoTime();
            int steps = 0;
            while (now - nextTickAt >= 0 && steps < 1 + Balance.MAX_CATCH_UP_STEPS) {
                tickAll();
                nextTickAt += TICK_NANOS;
                steps++;
            }
            if (now - nextTickAt >= 0) {
                // Still behind: forget the missed ticks and carry on from now.
                nextTickAt = now + TICK_NANOS;
            }
        } catch (Throwable t) {
            // If an exception escaped, the executor would silently stop
            // calling us and every game would freeze. So catch everything.
            log.error("Game loop error", t);
        }
    }
}
