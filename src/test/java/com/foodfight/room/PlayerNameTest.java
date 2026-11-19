package com.foodfight.room;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Tests for nickname cleaning. */
class PlayerNameTest {

    @Test
    void spacesAreTrimmed() {
        assertEquals("Sam", PlayerName.clean("   Sam  "));
    }

    @Test
    void controlAndInvisibleCharactersAreRemoved() {
        assertEquals("Sam", PlayerName.clean("Sa\u0000m\n"));
        assertEquals("Sam", PlayerName.clean("‮Sam"));  // right to left trick
    }

    @Test
    void longNamesAreCutToTwelve() {
        assertEquals("ABCDEFGHIJKL", PlayerName.clean("ABCDEFGHIJKLMNOP"));
    }

    @Test
    void emojiAreNotCutInHalf() {
        String thirteenFaces = "😀".repeat(13);
        String cleaned = PlayerName.clean(thirteenFaces);
        assertEquals(12, cleaned.codePointCount(0, cleaned.length()));
    }

    @Test
    void emptyNamesAreRefused() {
        assertNull(PlayerName.clean(null));
        assertNull(PlayerName.clean(""));
        assertNull(PlayerName.clean("     "));
        assertNull(PlayerName.clean("\u0000\u0001"));
    }

    @Test
    void uniqueNamesGetNumbers() {
        assertEquals("Sam", PlayerName.makeUnique("Sam", List.of("Alex")));
        assertEquals("Sam 2", PlayerName.makeUnique("Sam", List.of("Sam")));
        assertEquals("Sam 3", PlayerName.makeUnique("Sam", List.of("Sam", "Sam 2")));
        assertEquals("sam 2", PlayerName.makeUnique("sam", List.of("SAM")));
    }

    @Test
    void uniqueLongNamesStillFitInTwelve() {
        assertEquals("ABCDEFGHIJ 2", PlayerName.makeUnique("ABCDEFGHIJKL", List.of("ABCDEFGHIJKL")));
    }
}
