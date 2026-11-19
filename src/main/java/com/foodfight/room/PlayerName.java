package com.foodfight.room;

import com.foodfight.sim.Balance;

import java.util.Collection;

/**
 * Cleans up nicknames typed by players.
 *
 * Rules: trim spaces, remove invisible control characters, keep at most
 * 12 characters, and refuse names that end up empty. If two players pick the
 * same name, the second becomes "Sam 2", the third "Sam 3", and so on.
 */
public final class PlayerName {

    private PlayerName() {
    }

    /**
     * Returns the cleaned name, or null if nothing is left (which means the
     * server should answer BAD_NAME).
     */
    public static String clean(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder kept = new StringBuilder();
        // codePoints() walks real characters, so an emoji (which Java stores
        // as two chars) is never cut in half.
        raw.codePoints().forEach(cp -> {
            boolean control = Character.isISOControl(cp);
            // FORMAT characters are invisible ones, like the trick that flips
            // text right to left. We do not want those in names either.
            boolean invisible = Character.getType(cp) == Character.FORMAT;
            if (!control && !invisible) {
                kept.appendCodePoint(cp);
            }
        });
        String name = limit(kept.toString().trim(), Balance.MAX_NAME_LENGTH).trim();
        return name.isEmpty() ? null : name;
    }

    /**
     * Makes {@code name} different from every name in {@code taken}
     * (ignoring upper and lower case) by adding " 2", " 3", ... at the end.
     * The base name is shortened if needed so the result still fits in 12.
     */
    public static String makeUnique(String name, Collection<String> taken) {
        if (!isTaken(name, taken)) {
            return name;
        }
        for (int n = 2; ; n++) {
            String suffix = " " + n;
            String base = limit(name, Balance.MAX_NAME_LENGTH - suffix.length()).trim();
            String candidate = base + suffix;
            if (!isTaken(candidate, taken)) {
                return candidate;
            }
        }
    }

    private static boolean isTaken(String name, Collection<String> taken) {
        for (String other : taken) {
            if (other.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** Keeps at most {@code max} characters (counting an emoji as one). */
    private static String limit(String text, int max) {
        if (text.codePointCount(0, text.length()) <= max) {
            return text;
        }
        int end = text.offsetByCodePoints(0, max);
        return text.substring(0, end);
    }
}
