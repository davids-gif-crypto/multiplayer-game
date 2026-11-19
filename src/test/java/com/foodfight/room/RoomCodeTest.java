package com.foodfight.room;

import com.foodfight.sim.Balance;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for RoomCode and for how RoomManager hands out codes. */
class RoomCodeTest {

    @Test
    void randomCodesAreFourAllowedLetters() {
        Random random = new Random(42);
        for (int i = 0; i < 2000; i++) {
            String code = RoomCode.random(random);
            assertEquals(4, code.length());
            assertTrue(RoomCode.isValid(code), code);
            assertFalse(code.contains("I") || code.contains("O"), code);
        }
    }

    @Test
    void typedCodesAreCleanedUp() {
        assertEquals("KQTB", RoomCode.normalize("  kqtb "));
        assertEquals("", RoomCode.normalize(null));
    }

    @Test
    void validityCheck() {
        assertTrue(RoomCode.isValid("ABCD"));
        assertFalse(RoomCode.isValid("ABC"));
        assertFalse(RoomCode.isValid("ABCDE"));
        assertFalse(RoomCode.isValid("ABIO"));  // I and O are not used
        assertFalse(RoomCode.isValid("abcd"));  // must be normalized first
        assertFalse(RoomCode.isValid(null));
    }

    @Test
    void liveRoomsNeverShareACode() {
        RoomManager manager = new RoomManager(new Random(7));
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < Balance.MAX_ROOMS; i++) {
            Room room = manager.createRoom();
            assertNotNull(room);
            assertTrue(codes.add(room.code()), "duplicate code " + room.code());
        }
    }

    @Test
    void aRepeatedRandomCodeIsSkipped() {
        // A "random" source that makes AAAA twice, then BBBB.
        Random scripted = new Random() {
            int calls = 0;

            @Override
            public int nextInt(int bound) {
                calls++;
                return calls <= 8 ? 0 : 1;
            }
        };
        RoomManager manager = new RoomManager(scripted);
        assertEquals("AAAA", manager.createRoom().code());
        assertEquals("BBBB", manager.createRoom().code());
    }

    @Test
    void serverStopsAtTheRoomLimit() {
        RoomManager manager = new RoomManager(new Random(1));
        for (int i = 0; i < Balance.MAX_ROOMS; i++) {
            manager.createRoom();
        }
        assertNull(manager.createRoom());
        assertEquals(Balance.MAX_ROOMS, manager.roomCount());
    }

    @Test
    void findIgnoresCase() {
        RoomManager manager = new RoomManager(new Random(3));
        Room room = manager.createRoom();
        assertSame(room, manager.find(room.code().toLowerCase()));
        assertNull(manager.find("IIII"));
        assertNull(manager.find(null));
    }

    @Test
    void emptyRoomsAreRemovedByTheManager() {
        RoomManager manager = new RoomManager(new Random(5));
        Room room = manager.createRoom();
        for (int i = 0; i < Balance.EMPTY_ROOM_DELETE_TICKS; i++) {
            manager.tickAll();
        }
        assertNull(manager.find(room.code()));
        assertEquals(0, manager.roomCount());
    }
}
