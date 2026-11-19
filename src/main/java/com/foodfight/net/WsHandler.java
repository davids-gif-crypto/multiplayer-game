package com.foodfight.net;

import io.javalin.websocket.WsConfig;

/**
 * Handles the WebSocket at /ws.
 *
 * Milestone 0 version: it simply sends every text message straight back
 * (an "echo"). This proves the browser and server can talk. In Milestone 1
 * this class will read real game messages (create, join, input, ...) and
 * pass them to the right room.
 */
public class WsHandler {

    /** Tells Javalin what to do when a socket connects, sends a message, or closes. */
    public void configure(WsConfig ws) {
        ws.onConnect(ctx -> System.out.println("Socket connected: " + ctx.sessionId()));

        ws.onMessage(ctx -> {
            // Echo the text back to the same browser.
            ctx.send(ctx.message());
        });

        ws.onClose(ctx -> System.out.println("Socket closed: " + ctx.sessionId()));

        ws.onError(ctx -> System.out.println("Socket error: " + ctx.error()));
    }
}
