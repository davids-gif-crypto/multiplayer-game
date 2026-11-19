package com.foodfight.net;

import com.foodfight.room.PlayerLink;
import com.foodfight.room.Room;
import com.foodfight.sim.Balance;
import io.javalin.websocket.WsContext;
import org.eclipse.jetty.websocket.api.BatchMode;
import org.eclipse.jetty.websocket.api.WriteCallback;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * One browser's WebSocket connection.
 *
 * It remembers which room the browser is in, and sends messages to it.
 *
 * Sending is "asynchronous": send() hands the text to Jetty and returns at
 * once, without waiting for the browser to receive it. This matters because
 * the single game thread sends the snapshots for every room. If it had to
 * wait for a slow Chromebook on bad Wi-Fi, every game on the server would
 * freeze. We count how many sends are still in flight, so the room can skip
 * snapshots for a browser that has fallen behind.
 */
public class Connection implements PlayerLink {

    /** More unfinished sends than this means the browser is backed up. */
    private static final int MAX_PENDING_SENDS = 20;

    private final WsContext ctx;
    private final RateLimiter limiter = new RateLimiter(Balance.MAX_MESSAGES_PER_SECOND);
    private final AtomicReference<Room> room = new AtomicReference<>();
    private final AtomicInteger pendingSends = new AtomicInteger();

    /** Called by Jetty when a send finishes, either way. */
    private final WriteCallback onSendDone = new WriteCallback() {
        @Override
        public void writeSuccess() {
            pendingSends.decrementAndGet();
        }

        @Override
        public void writeFailed(Throwable error) {
            pendingSends.decrementAndGet();
        }
    };

    public Connection(WsContext ctx) {
        this.ctx = ctx;
        // Send every message right away instead of letting Jetty bundle them.
        ctx.session.getRemote().setBatchMode(BatchMode.OFF);
    }

    @Override
    public void send(Object message) {
        if (!ctx.session.isOpen()) {
            return;
        }
        String json = Protocol.toJson(message);
        pendingSends.incrementAndGet();
        try {
            ctx.session.getRemote().sendString(json, onSendDone);
        } catch (RuntimeException e) {
            // The socket closed between the check above and now. That is fine.
            pendingSends.decrementAndGet();
        }
    }

    @Override
    public boolean isBackedUp() {
        return pendingSends.get() > MAX_PENDING_SENDS;
    }

    @Override
    public void detach(Room fromRoom) {
        // Only clear it if we are still pointing at that room. If the player
        // already moved to a different room, leave that alone.
        room.compareAndSet(fromRoom, null);
    }

    /** The room this browser is in, or null. */
    public Room room() {
        return room.get();
    }

    /** Points this browser at a new room (or null) and returns the old one. */
    public Room swapRoom(Room newRoom) {
        return room.getAndSet(newRoom);
    }

    public RateLimiter limiter() {
        return limiter;
    }

    public String id() {
        return ctx.sessionId();
    }
}
