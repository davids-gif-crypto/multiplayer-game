package com.foodfight.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A pretend browser for tests and the bot client. It uses the WebSocket
 * client built into Java (java.net.http), so no extra library is needed.
 *
 * Every message from the server is stored in a queue. Tests then wait for
 * the message type they expect with {@link #await(String)}.
 */
public class TestClient implements WebSocket.Listener {

    private static final Duration DEFAULT_WAIT = Duration.ofSeconds(5);

    private final BlockingQueue<JsonNode> inbox = new LinkedBlockingQueue<>();
    private final StringBuilder partial = new StringBuilder();
    private WebSocket socket;

    /** Opens a connection to the server on this port. */
    public static TestClient connect(int port) throws Exception {
        TestClient client = new TestClient();
        client.socket = HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + port + "/ws"), client)
                .get(5, TimeUnit.SECONDS);
        return client;
    }

    // ---------- sending ----------

    /** Sends a message built from pairs, like send("t", "join", "room", "ABCD"). */
    public void send(Object... keysAndValues) {
        ObjectNode node = Protocol.MAPPER.createObjectNode();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            node.set((String) keysAndValues[i], Protocol.MAPPER.valueToTree(keysAndValues[i + 1]));
        }
        sendRaw(node.toString());
    }

    /** Sends text exactly as given, even if it is not valid JSON. */
    public synchronized void sendRaw(String text) {
        // join() waits until this send finished, because the Java WebSocket
        // does not allow starting a new send while the last one is still going.
        socket.sendText(text, true).join();
    }

    /** Creates a room and returns its code. */
    public String createRoom(String name) {
        send("t", "create", "name", name);
        return await("joined").get("room").asText();
    }

    /** Joins a room and returns the "joined" reply. */
    public JsonNode joinRoom(String code, String name) {
        send("t", "join", "room", code, "name", name);
        return await("joined");
    }

    /** Closes the socket politely. */
    public void close() {
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
    }

    /** Drops the connection without a goodbye, like a laptop losing Wi-Fi. */
    public void abort() {
        socket.abort();
    }

    // ---------- receiving ----------

    /** Waits for the next message of this type, skipping other types. Fails after 5 seconds. */
    public JsonNode await(String type) {
        return await(type, DEFAULT_WAIT);
    }

    public JsonNode await(String type, Duration wait) {
        long deadline = System.nanoTime() + wait.toNanos();
        try {
            while (true) {
                long left = deadline - System.nanoTime();
                JsonNode msg = inbox.poll(Math.max(left, 0), TimeUnit.NANOSECONDS);
                if (msg == null) {
                    throw new AssertionError("Timed out waiting for a '" + type + "' message");
                }
                if (type.equals(msg.path("t").asText())) {
                    return msg;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for " + type);
        }
    }

    /** Waits for an error message and returns its code. */
    public String awaitErrorCode() {
        return await("error").get("code").asText();
    }

    /** Waits for a lobby message that matches a test, skipping older ones. */
    public JsonNode awaitLobby(java.util.function.Predicate<JsonNode> test) {
        while (true) {
            JsonNode lobby = await("lobby");
            if (test.test(lobby)) {
                return lobby;
            }
        }
    }

    /** Takes every message that has arrived so far, without waiting. */
    public List<JsonNode> drain() {
        List<JsonNode> all = new ArrayList<>();
        inbox.drainTo(all);
        return all;
    }

    // ---------- WebSocket.Listener ----------

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
        // A long message can arrive in pieces. Glue them together first.
        partial.append(data);
        if (last) {
            try {
                inbox.add(Protocol.MAPPER.readTree(partial.toString()));
            } catch (Exception e) {
                System.out.println("TestClient got bad JSON: " + partial);
            }
            partial.setLength(0);
        }
        ws.request(1); // ask for the next message
        return null;
    }
}
