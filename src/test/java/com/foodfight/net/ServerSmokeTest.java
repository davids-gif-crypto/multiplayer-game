package com.foodfight.net;

import com.foodfight.Main;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke test. Starts the real server on a random free port and checks the
 * health check and the web page. The WebSocket is tested in
 * LobbyIntegrationTest.
 */
class ServerSmokeTest {

    private static Javalin app;
    private static int port;
    private static final HttpClient http = HttpClient.newHttpClient();

    @BeforeAll
    static void startServer() {
        app = Main.createApp();
        app.start("127.0.0.1", 0);   // port 0 means "pick any free port"
        port = app.port();
    }

    @AfterAll
    static void stopServer() {
        app.stop();
    }

    @Test
    void healthCheckSaysOk() throws Exception {
        HttpResponse<String> response = get("/health");
        assertEquals(200, response.statusCode());
        assertEquals("ok", response.body());
    }

    @Test
    void servesTheHomePage() throws Exception {
        HttpResponse<String> response = get("/");
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Food Fight"));
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
