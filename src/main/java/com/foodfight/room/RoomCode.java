package com.foodfight.room;

import java.util.Locale;
import java.util.Random;

/**
 * Makes and checks the 4 letter room codes, like "KQTB".
 *
 * The letters I and O are left out because they look like 1 and 0,
 * which would confuse people reading a code off the board.
 */
public final class RoomCode {

    private RoomCode() {
    }

    /** The 24 letters a code can use. */
    public static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ";

    /** Every code is exactly this long. */
    public static final int LENGTH = 4;

    /**
     * Makes a random code. It might already be in use, so RoomManager keeps
     * asking for a new one until it finds a free one.
     */
    public static String random(Random random) {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * Cleans up a code typed by a player: trims spaces and makes it
     * uppercase, so "kqtb " finds room "KQTB". Returns "" for null.
     */
    public static String normalize(String typed) {
        if (typed == null) {
            return "";
        }
        return typed.trim().toUpperCase(Locale.ROOT);
    }

    /** True if the text is 4 letters, all from the allowed alphabet. */
    public static boolean isValid(String code) {
        if (code == null || code.length() != LENGTH) {
            return false;
        }
        for (int i = 0; i < code.length(); i++) {
            if (ALPHABET.indexOf(code.charAt(i)) < 0) {
                return false;
            }
        }
        return true;
    }
}
