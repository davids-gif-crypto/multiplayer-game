package com.foodfight.net;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.foodfight.net.Protocol.ClientMsg;
import com.foodfight.net.Protocol.Closed;
import com.foodfight.net.Protocol.Create;
import com.foodfight.net.Protocol.ErrorMsg;
import com.foodfight.net.Protocol.Join;
import com.foodfight.net.Protocol.Leave;
import com.foodfight.net.Protocol.Ping;
import com.foodfight.net.Protocol.Pong;
import com.foodfight.room.PlayerName;
import com.foodfight.room.Room;
import com.foodfight.room.RoomManager;
import io.javalin.websocket.WsConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles the WebSocket at /ws.
 *
 * This runs on Jetty's web threads, NOT the game thread, so it never changes
 * a room directly. It reads each message, does the quick checks that do not
 * need room state (rate limit, valid JSON, nickname, does the room exist),
 * then drops the message in the right room's inbox.
 */
public class WsHandler {

    private static final Logger log = LoggerFactory.getLogger(WsHandler.class);

    private final RoomManager rooms;

    /** Socket id to connection, for every open socket. */
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    public WsHandler(RoomManager rooms) {
        this.rooms = rooms;
    }

    /** Tells Javalin what to do when a socket connects, sends a message, or closes. */
    public void configure(WsConfig ws) {
        ws.onConnect(ctx -> connections.put(ctx.sessionId(), new Connection(ctx)));

        ws.onMessage(ctx -> {
            Connection conn = connections.get(ctx.sessionId());
            if (conn != null) {
                onText(conn, ctx.message());
            }
        });

        ws.onClose(ctx -> {
            Connection conn = connections.remove(ctx.sessionId());
            if (conn != null) {
                Room room = conn.swapRoom(null);
                if (room != null) {
                    room.enqueue(conn, new Closed());
                }
            }
        });

        ws.onError(ctx -> log.debug("Socket error: {}", String.valueOf(ctx.error())));
    }

    /** One text message arrived from a browser. */
    void onText(Connection conn, String text) {
        switch (conn.limiter().check(System.currentTimeMillis())) {
            case ALLOW -> {
                // carry on below
            }
            case WARN_AND_DROP -> {
                conn.send(new ErrorMsg(Protocol.RATE_LIMIT, "Too many messages. Slow down a little."));
                return;
            }
            case DROP -> {
                return;
            }
        }

        ClientMsg msg;
        try {
            msg = Protocol.parse(text);
        } catch (JsonProcessingException e) {
            log.warn("Ignored a message that is not JSON: {}", shorten(text));
            return;
        }
        if (msg == null) {
            log.warn("Ignored an unknown message: {}", shorten(text));
            return;
        }
        handle(conn, msg);
    }

    private void handle(Connection conn, ClientMsg msg) {
        switch (msg) {
            case Ping ping -> conn.send(new Pong());
            case Create create -> create(conn, create);
            case Join join -> join(conn, join);
            case Leave leave -> {
                Room room = conn.swapRoom(null);
                if (room != null) {
                    room.enqueue(conn, leave);
                }
            }
            default -> {
                // pick, start, input and again go to the player's room.
                Room room = conn.room();
                if (room != null) {
                    room.enqueue(conn, msg);
                }
            }
        }
    }

    private void create(Connection conn, Create create) {
        String name = PlayerName.clean(create.name());
        if (name == null) {
            conn.send(new ErrorMsg(Protocol.BAD_NAME, "Please type a nickname."));
            return;
        }
        Room room = rooms.createRoom();
        if (room == null) {
            conn.send(new ErrorMsg(Protocol.SERVER_FULL, "The server has too many rooms right now."));
            return;
        }
        moveTo(conn, room, new Create(name));
    }

    private void join(Connection conn, Join join) {
        String name = PlayerName.clean(join.name());
        if (name == null) {
            conn.send(new ErrorMsg(Protocol.BAD_NAME, "Please type a nickname."));
            return;
        }
        Room room = rooms.find(join.room());
        if (room == null) {
            conn.send(new ErrorMsg(Protocol.ROOM_NOT_FOUND, "There is no room with that code."));
            return;
        }
        if (room == conn.room()) {
            return; // already in this room
        }
        // The room decides if there is space, since only it knows its players.
        moveTo(conn, room, new Join(room.code(), name));
    }

    /**
     * Points the connection at a new room, and leaves the old one if needed.
     * We point at the room straight away (before the room has accepted the
     * join) so that a quick "pick" sent right after "join" is not lost.
     * If the room refuses the join, it calls detach() to undo this.
     */
    private void moveTo(Connection conn, Room room, ClientMsg joinMessage) {
        Room old = conn.swapRoom(room);
        if (old != null && old != room) {
            old.enqueue(conn, new Leave());
        }
        room.enqueue(conn, joinMessage);
    }

    /** Keeps log lines short even if someone sends a huge message. */
    private static String shorten(String text) {
        return text.length() <= 80 ? text : text.substring(0, 80) + "...";
    }
}
