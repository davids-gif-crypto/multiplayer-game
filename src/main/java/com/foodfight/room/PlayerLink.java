package com.foodfight.room;

/**
 * The way a Room talks back to one player's browser.
 *
 * In the real server this is a WebSocket connection (net.Connection).
 * In tests it is a fake that just stores the messages in a list. Because the
 * room only knows about this small interface, it can be tested without
 * starting a web server.
 */
public interface PlayerLink {

    /** Sends one message record (from Protocol) to this player's browser. */
    void send(Object message);

    /**
     * True if this browser has not finished receiving earlier messages yet.
     * The room skips sending snapshots to a backed up browser, because a newer
     * snapshot will come along soon anyway.
     */
    boolean isBackedUp();

    /**
     * Called by the room when this player is no longer in it (they left,
     * their join was refused, or the room was deleted), so the connection
     * stops sending its messages to that room.
     */
    void detach(Room room);
}
