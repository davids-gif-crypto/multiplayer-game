package com.foodfight;

import com.foodfight.net.WsHandler;
import com.foodfight.room.RoomManager;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.time.Duration;
import java.util.Enumeration;

/**
 * Starts the Food Fight server.
 *
 * The server does three jobs:
 * 1. Serves the web page (HTML, CSS, JavaScript) from src/main/resources/public.
 * 2. Answers GET /health with "ok" so the hosting service knows we are alive.
 * 3. Runs the WebSocket at /ws that the browsers talk to during the game.
 */
public class Main {

    /** Port used when the PORT environment variable is not set. */
    public static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        int port = readPort();
        Javalin app = createApp();
        // 0.0.0.0 means "listen on every network card", so other laptops
        // on the same Wi-Fi can connect, not just this computer.
        app.start("0.0.0.0", port);
        printAddresses(app.port());
    }

    /**
     * Builds the Javalin app without starting it.
     * Tests call this too, then start it on a random free port.
     */
    public static Javalin createApp() {
        RoomManager rooms = new RoomManager();
        WsHandler wsHandler = new WsHandler(rooms);

        Javalin app = Javalin.create(config -> {
            // Start the game thread when the server starts, stop it when the server stops.
            config.events(events -> {
                events.serverStarted(rooms::start);
                events.serverStopped(rooms::stop);
            });

            // Serve everything in the "public" folder inside the jar.
            config.staticFiles.add("/public", Location.CLASSPATH);

            // WebSocket limits from the spec: idle sockets close after
            // 5 minutes, and a single message can be at most 1 KB.
            config.jetty.modifyWebSocketServletFactory(factory -> {
                factory.setIdleTimeout(Duration.ofMinutes(5));
                factory.setMaxTextMessageSize(1024);
            });

            // Keep the console quiet, we print our own start message.
            config.showJavalinBanner = false;
        });

        // Health check for Render. Must return HTTP 200 with the text "ok".
        app.get("/health", ctx -> ctx.result("ok"));

        // The single WebSocket that the game uses.
        app.ws("/ws", wsHandler::configure);

        return app;
    }

    /** Reads the PORT environment variable, or falls back to 8080. */
    static int readPort() {
        String value = System.getenv("PORT");
        if (value == null || value.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            System.out.println("PORT is not a number (" + value + "), using " + DEFAULT_PORT);
            return DEFAULT_PORT;
        }
    }

    /** Prints the localhost link and the LAN link so classmates can join. */
    private static void printAddresses(int port) {
        System.out.println();
        System.out.println("Food Fight server is running.");
        System.out.println("  On this computer: http://localhost:" + port);
        String lan = findLanAddress();
        if (lan != null) {
            System.out.println("  On your network:  http://" + lan + ":" + port);
        } else {
            System.out.println("  (Could not find a LAN address. Are you on Wi-Fi?)");
        }
        System.out.println();
    }

    /**
     * Looks through the network cards for a normal IPv4 address
     * like 192.168.1.20. Skips loopback (127.0.0.1) and cards that are off.
     * Returns null if nothing is found.
     */
    private static String findLanAddress() {
        try {
            Enumeration<NetworkInterface> cards = NetworkInterface.getNetworkInterfaces();
            while (cards.hasMoreElements()) {
                NetworkInterface card = cards.nextElement();
                if (!card.isUp() || card.isLoopback() || card.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> addresses = card.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            // Not important enough to crash the server over.
            System.out.println("Could not read network cards: " + e.getMessage());
        }
        return null;
    }
}
