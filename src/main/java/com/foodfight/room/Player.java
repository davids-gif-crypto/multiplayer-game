package com.foodfight.room;

import com.foodfight.sim.FighterType;

/**
 * One person in a room: their id, nickname, chosen fighter and connection.
 *
 * This is lobby information only. The fighter's position, speed and damage
 * live in the game simulation (sim package) from Milestone 2 on.
 *
 * Only the game thread changes a Player, so no locking is needed.
 */
public class Player {

    final int id;
    final String name;
    final PlayerLink link;
    FighterType fighter = FighterType.BRAWLER;

    /** False once their socket closed or they pressed Leave. */
    boolean connected = true;

    /** The room tick when they disconnected, or -1 while connected. */
    long disconnectedAtTick = -1;

    /** The room tick of the last message we got from them. */
    long lastHeardTick;

    Player(int id, String name, PlayerLink link, long nowTick) {
        this.id = id;
        this.name = name;
        this.link = link;
        this.lastHeardTick = nowTick;
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public FighterType fighter() {
        return fighter;
    }

    public boolean connected() {
        return connected;
    }
}
