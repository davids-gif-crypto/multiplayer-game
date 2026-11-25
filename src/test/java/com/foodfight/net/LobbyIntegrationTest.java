package com.foodfight.net;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodfight.Main;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Starts the real server on a random port and talks to it with real
 * WebSocket clients, the same way browsers would.
 */
class LobbyIntegrationTest {

    private static Javalin app;
    private static int port;
    private final List<TestClient> clients = new ArrayList<>();

    @BeforeAll
    static void startServer() {
        app = Main.createApp();
        app.start("127.0.0.1", 0);
        port = app.port();
    }

    @AfterAll
    static void stopServer() {
        app.stop();
    }

    private TestClient client() throws Exception {
        TestClient client = TestClient.connect(port);
        clients.add(client);
        return client;
    }

    @Test
    void pingGetsPong() throws Exception {
        TestClient c = client();
        c.send("t", "ping");
        c.await("pong");
    }

    @Test
    void createThenJoinWithTheCode() throws Exception {
        TestClient host = client();
        String code = host.createRoom("Sam");
        assertEquals(4, code.length());

        TestClient guest = client();
        JsonNode joined = guest.joinRoom(code.toLowerCase(), "Alex"); // any case works
        assertFalse(joined.get("host").asBoolean());

        JsonNode lobby = host.awaitLobby(l -> l.get("players").size() == 2);
        assertEquals("Alex", lobby.get("players").get(1).get("name").asText());
    }

    @Test
    void wrongCodeIsNotFound() throws Exception {
        TestClient c = client();
        c.send("t", "join", "room", "IOIO", "name", "Sam");
        assertEquals("ROOM_NOT_FOUND", c.awaitErrorCode());
    }

    @Test
    void blankNameIsRefused() throws Exception {
        TestClient c = client();
        c.send("t", "create", "name", "   ");
        assertEquals("BAD_NAME", c.awaitErrorCode());
    }

    @Test
    void fifthPlayerFindsTheRoomFull() throws Exception {
        TestClient host = client();
        String code = host.createRoom("P1");
        for (int i = 2; i <= 4; i++) {
            client().joinRoom(code, "P" + i);
        }
        TestClient fifth = client();
        fifth.send("t", "join", "room", code, "name", "P5");
        assertEquals("ROOM_FULL", fifth.awaitErrorCode());
    }

    @Test
    void hostLeavingTheLobbyPassesHostOn() throws Exception {
        TestClient host = client();
        String code = host.createRoom("Host");
        TestClient guest = client();
        int guestId = guest.joinRoom(code, "Guest").get("you").asInt();

        host.send("t", "leave");
        JsonNode lobby = guest.awaitLobby(l -> l.get("players").size() == 1);
        assertEquals(guestId, lobby.get("hostId").asInt());
    }

    @Test
    void fullMatchStartFlow() throws Exception {
        TestClient host = client();
        String code = host.createRoom("Host");
        TestClient guest1 = client();
        TestClient guest2 = client();
        int guest1Id = guest1.joinRoom(code, "Fry").get("you").asInt();
        guest2.joinRoom(code, "Bottle");

        // Fighter picks show up in the lobby for everyone.
        guest1.send("t", "pick", "fighter", "SPEEDSTER");
        guest2.send("t", "pick", "fighter", "ZONER");
        host.awaitLobby(l -> l.path("players").path(1).path("fighter").asText().equals("SPEEDSTER")
                && l.path("players").path(2).path("fighter").asText().equals("ZONER"));

        // Only the host may start.
        guest1.send("t", "start");
        assertEquals("NOT_HOST", guest1.awaitErrorCode());

        host.send("t", "start");
        assertEquals(3, guest2.await("countdown").get("n").asInt());
        assertEquals(2, guest2.await("countdown").get("n").asInt());
        assertEquals(1, guest2.await("countdown").get("n").asInt());

        // Snapshots should arrive about 30 times per second.
        guest2.await("state");
        double rate = countStatesPerSecond(guest2, 2000);
        assertTrue(rate >= 25 && rate <= 35, "snapshot rate was " + rate);

        // Nobody can join once the match is running.
        TestClient late = client();
        late.send("t", "join", "room", code, "name", "Late");
        assertEquals("GAME_STARTED", late.awaitErrorCode());

        // The host drops off without saying goodbye: host moves on and the
        // match keeps going for everyone else.
        host.abort();
        JsonNode lobby = guest1.awaitLobby(l -> l.get("hostId").asInt() == guest1Id);
        assertFalse(lobby.get("players").get(0).get("connected").asBoolean());
        guest1.drain();
        guest1.await("state", Duration.ofSeconds(2));

        // Another player disconnects mid match, and the last one is still fine.
        guest2.close();
        guest1.drain();
        guest1.await("state", Duration.ofSeconds(2));
    }

    @Test
    void heldKeysMoveTheFighterOverTheNetwork() throws Exception {
        TestClient host = client();
        String code = host.createRoom("Host");
        TestClient guest = client();
        int guestId = guest.joinRoom(code, "Runner").get("you").asInt();
        host.send("t", "start");

        // Wait for the match to begin, then hold right.
        JsonNode state = guest.await("state");
        while (!state.get("phase").asText().equals("PLAYING")) {
            state = guest.await("state");
        }
        double startX = playerX(state, guestId);
        assertEquals(540, startX, 1e-9, "second player spawns at x 540");

        guest.send("t", "input", "seq", 1, "right", true);
        TimeUnit.MILLISECONDS.sleep(500);
        guest.drain();
        double laterX = playerX(guest.await("state"), guestId);
        assertTrue(laterX > startX + 50, "moved right, x went from " + startX + " to " + laterX);
    }

    @Test
    void attackingACloseFighterSendsAHitEvent() throws Exception {
        TestClient host = client();
        String code = host.createRoom("Meatball");
        int hostId = 0;
        TestClient guest = client();
        int guestId = guest.joinRoom(code, "Target").get("you").asInt();
        host.send("t", "start");

        JsonNode state = host.await("state");
        while (!state.get("phase").asText().equals("PLAYING")) {
            state = host.await("state");
        }
        for (JsonNode p : state.get("players")) {
            if (p.get("id").asInt() != guestId) {
                hostId = p.get("id").asInt();
            }
        }

        // The host (a Brawler at x 380) walks toward the guest (at x 540)...
        host.send("t", "input", "seq", 1, "right", true);
        while (playerX(state, hostId) < 450) {
            state = host.await("state");
        }
        // ...stops, and swings.
        host.send("t", "input", "seq", 2, "attack", true);
        host.send("t", "input", "seq", 3);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        boolean hit = false;
        while (!hit && System.nanoTime() < deadline) {
            for (JsonNode event : guest.await("state").get("events")) {
                if (event.get("k").asText().equals("hit") && event.get("id").asInt() == guestId) {
                    assertEquals(10, event.get("dmg").asInt());
                    hit = true;
                }
            }
        }
        assertTrue(hit, "the guest should have been hit");
    }

    private static double playerX(JsonNode state, int id) {
        for (JsonNode player : state.get("players")) {
            if (player.get("id").asInt() == id) {
                return player.get("x").asDouble();
            }
        }
        throw new AssertionError("player " + id + " not in snapshot");
    }

    @Test
    void garbageDoesNotBreakTheConnection() throws Exception {
        TestClient c = client();
        c.sendRaw("this is not json");
        c.sendRaw("{\"t\":\"nonsense\"}");
        c.sendRaw("{\"t\":\"start\"}"); // start without a room is ignored
        c.send("t", "ping");
        c.await("pong");
    }

    @Test
    void floodingIsRateLimited() throws Exception {
        // Send pings as fast as we can until the server complains. On a slow
        // or busy computer 130 pings can take more than a second, so we keep
        // going (up to 3000) instead of sending a fixed number.
        TestClient c = client();
        boolean limited = false;
        for (int i = 0; i < 3000 && !limited; i++) {
            c.send("t", "ping");
            if (i % 50 == 49) {
                limited = c.drain().stream()
                        .anyMatch(m -> m.path("code").asText().equals("RATE_LIMIT"));
            }
        }
        assertTrue(limited, "the server never sent RATE_LIMIT");
    }

    /** Counts "state" messages for a while and returns how many per second. */
    private static double countStatesPerSecond(TestClient client, long millis) throws InterruptedException {
        client.drain();
        long start = System.nanoTime();
        TimeUnit.MILLISECONDS.sleep(millis);
        long elapsed = System.nanoTime() - start;
        long count = client.drain().stream().filter(m -> m.path("t").asText().equals("state")).count();
        return count / (elapsed / 1e9);
    }
}
