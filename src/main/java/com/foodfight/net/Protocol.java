package com.foodfight.net;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Every message that travels over the WebSocket, written as Java records.
 *
 * Each message is a JSON object with a text field "t" that says what kind of
 * message it is, for example {"t":"join","room":"ABCD","name":"Sam"}.
 *
 * Client messages (browser to server) are read by {@link #parse(String)}.
 * Server messages (server to browser) are records that already contain their
 * "t" value, and are turned into JSON by {@link #toJson(Object)}.
 */
public final class Protocol {

    private Protocol() {
    }

    /** One shared Jackson object for reading and writing JSON. It is thread safe. */
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // =================================================================
    // Browser to server
    // =================================================================

    /** Any message a browser can send. "sealed" means only the records below count. */
    public sealed interface ClientMsg
            permits Create, Join, Pick, Start, Input, Again, Leave, Ping, Closed {
    }

    /** Make a new room and join it as the host. */
    public record Create(String name) implements ClientMsg {
    }

    /** Join an existing room by its 4 letter code. */
    public record Join(String room, String name) implements ClientMsg {
    }

    /** Choose a fighter while in the lobby (BRAWLER, SPEEDSTER or ZONER). */
    public record Pick(String fighter) implements ClientMsg {
    }

    /** Host only: start the match. */
    public record Start() implements ClientMsg {
    }

    /** The keys a player is holding right now. seq counts up with every input message. */
    public record Input(long seq, boolean left, boolean right, boolean jump, boolean down,
                        boolean attack, boolean special, boolean shield) implements ClientMsg {
    }

    /** Host only: go back to the lobby after the results screen. */
    public record Again() implements ClientMsg {
    }

    /** Leave the room. */
    public record Leave() implements ClientMsg {
    }

    /** Keep alive, the server answers with Pong. */
    public record Ping() implements ClientMsg {
    }

    /**
     * Never sent by a browser. WsHandler makes one of these when a socket
     * closes, so the room can handle a disconnect the same way it handles
     * any other message. parse() never returns it, so nobody can fake it.
     */
    public record Closed() implements ClientMsg {
    }

    /**
     * Reads one text message from a browser.
     *
     * Returns null if the text is not valid JSON, is not an object, or has a
     * "t" we do not know. Extra unknown fields are ignored. Missing fields
     * become null (for text) or false (for booleans), so a bad message can
     * never crash the server.
     *
     * @throws JsonProcessingException if the text is not JSON at all
     */
    public static ClientMsg parse(String text) throws JsonProcessingException {
        JsonNode node = MAPPER.readTree(text);
        if (node == null || !node.isObject()) {
            return null;
        }
        String type = node.path("t").asText("");
        return switch (type) {
            case "create" -> new Create(text(node, "name"));
            case "join" -> new Join(text(node, "room"), text(node, "name"));
            case "pick" -> new Pick(text(node, "fighter"));
            case "start" -> new Start();
            case "input" -> new Input(
                    node.path("seq").asLong(0),
                    bool(node, "left"), bool(node, "right"), bool(node, "jump"), bool(node, "down"),
                    bool(node, "attack"), bool(node, "special"), bool(node, "shield"));
            case "again" -> new Again();
            case "leave" -> new Leave();
            case "ping" -> new Ping();
            default -> null;
        };
    }

    /** Reads a text field, or null if it is missing or not text. */
    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value != null && value.isTextual()) ? value.textValue() : null;
    }

    /** Reads a true/false field. Anything that is not a real boolean counts as false. */
    private static boolean bool(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isBoolean() && value.booleanValue();
    }

    // =================================================================
    // Server to browser
    // =================================================================
    // Each record has "t" as its first field. The short constructor fills it
    // in, so code can write new Pong() instead of new Pong("pong").

    /** Reply to create and join: which room you are in and your player id. */
    public record Joined(String t, String room, int you, boolean host) {
        public Joined(String room, int you, boolean host) {
            this("joined", room, you, host);
        }
    }

    /** One row in the lobby player list. */
    public record LobbyPlayer(int id, String name, String fighter, boolean connected) {
    }

    /** The whole lobby, sent every time something in it changes. */
    public record Lobby(String t, String room, int hostId, List<LobbyPlayer> players) {
        public Lobby(String room, int hostId, List<LobbyPlayer> players) {
            this("lobby", room, hostId, players);
        }
    }

    /** One step of the 3, 2, 1 countdown. */
    public record Countdown(String t, int n) {
        public Countdown(int n) {
            this("countdown", n);
        }
    }

    /**
     * A snapshot of the match, sent 30 times per second while playing.
     * In Milestone 1 the player, projectile and event lists are still empty.
     * Milestone 2 fills in the players.
     */
    public record State(String t, long tick, String phase, int timeLeft,
                        List<?> players, List<?> projectiles, List<?> events) {
        public State(long tick, String phase, int timeLeft,
                     List<?> players, List<?> projectiles, List<?> events) {
            this("state", tick, phase, timeLeft, players, projectiles, events);
        }
    }

    /** Something went wrong. code is one of the error codes below. */
    public record ErrorMsg(String t, String code, String msg) {
        public ErrorMsg(String code, String msg) {
            this("error", code, msg);
        }
    }

    /** Reply to ping. */
    public record Pong(String t) {
        public Pong() {
            this("pong");
        }
    }

    // Error codes sent in ErrorMsg.
    public static final String ROOM_NOT_FOUND = "ROOM_NOT_FOUND";
    public static final String ROOM_FULL = "ROOM_FULL";
    public static final String GAME_STARTED = "GAME_STARTED";
    public static final String BAD_NAME = "BAD_NAME";
    public static final String NOT_HOST = "NOT_HOST";
    public static final String NEED_PLAYERS = "NEED_PLAYERS";
    public static final String RATE_LIMIT = "RATE_LIMIT";
    /** Not in the original list: the server already has the most rooms it allows. */
    public static final String SERVER_FULL = "SERVER_FULL";

    /** Turns a server message record into JSON text. */
    public static String toJson(Object message) {
        try {
            return MAPPER.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            // This only happens if we wrote a broken record, which is our bug.
            throw new IllegalStateException("Could not write message " + message, e);
        }
    }
}
