package com.foodfight.room;

import com.foodfight.net.Protocol;
import com.foodfight.net.Protocol.Again;
import com.foodfight.net.Protocol.Closed;
import com.foodfight.net.Protocol.Countdown;
import com.foodfight.net.Protocol.Create;
import com.foodfight.net.Protocol.Join;
import com.foodfight.net.Protocol.Joined;
import com.foodfight.net.Protocol.Leave;
import com.foodfight.net.Protocol.Lobby;
import com.foodfight.net.Protocol.Pick;
import com.foodfight.net.Protocol.Start;
import com.foodfight.net.Protocol.State;
import com.foodfight.sim.Balance;
import com.foodfight.sim.FighterType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for Room, using fake links instead of real sockets.
 * We call room.tick() by hand, so time only moves when the test says so.
 */
class RoomTest {

    private Room room;

    @BeforeEach
    void makeRoom() {
        room = new Room("ABCD");
    }

    // ---------- helpers ----------

    private FakeLink create(String name) {
        FakeLink link = new FakeLink();
        room.enqueue(link, new Create(name));
        room.tick();
        return link;
    }

    private FakeLink join(String name) {
        FakeLink link = new FakeLink();
        room.enqueue(link, new Join("ABCD", name));
        room.tick();
        return link;
    }

    private void send(FakeLink link, Protocol.ClientMsg msg) {
        room.enqueue(link, msg);
        room.tick();
    }

    private void ticks(int count) {
        for (int i = 0; i < count; i++) {
            room.tick();
        }
    }

    private int idOf(FakeLink link) {
        return link.last(Joined.class).you();
    }

    /** Makes a room with a host and one guest, then starts the countdown. */
    private FakeLink[] twoPlayersCountingDown() {
        FakeLink host = create("Host");
        FakeLink guest = join("Guest");
        send(host, new Start());
        return new FakeLink[] {host, guest};
    }

    // ---------- joining ----------

    @Test
    void creatorBecomesHost() {
        FakeLink host = create("Sam");
        Joined joined = host.last(Joined.class);
        assertTrue(joined.host());
        assertEquals("ABCD", joined.room());
        assertEquals(joined.you(), room.hostId());
        assertEquals(1, host.last(Lobby.class).players().size());
    }

    @Test
    void joinerIsNotHostAndEveryoneSeesTheLobby() {
        FakeLink host = create("Sam");
        FakeLink guest = join("Alex");
        assertFalse(guest.last(Joined.class).host());
        assertEquals(2, host.last(Lobby.class).players().size());
        assertEquals(2, guest.last(Lobby.class).players().size());
    }

    @Test
    void roomHoldsFourPlayersAtMost() {
        create("P1");
        join("P2");
        join("P3");
        FakeLink fourth = join("P4");
        assertEquals(4, fourth.last(Lobby.class).players().size());

        FakeLink fifth = join("P5");
        assertEquals(Protocol.ROOM_FULL, fifth.lastErrorCode());
        assertNull(fifth.last(Joined.class));
        assertEquals(1, fifth.detachCount, "the refused link is told it is not in the room");
        assertEquals(4, room.players().size());
    }

    @Test
    void duplicateNamesGetANumber() {
        create("Sam");
        join("Sam");
        join("sam"); // same name in different case also counts
        List<String> names = room.players().stream().map(Player::name).toList();
        assertEquals(List.of("Sam", "Sam 2", "sam 3"), names);
    }

    @Test
    void blankNameIsRefused() {
        create("Sam");
        FakeLink blank = join("    ");
        assertEquals(Protocol.BAD_NAME, blank.lastErrorCode());
        assertEquals(1, room.players().size());
    }

    @Test
    void messagesFromStrangersAreIgnored() {
        create("Sam");
        FakeLink stranger = new FakeLink();
        send(stranger, new Start());
        send(stranger, new Pick("ZONER"));
        assertTrue(stranger.sent.isEmpty());
        assertEquals(Phase.LOBBY, room.phase());
    }

    // ---------- fighters ----------

    @Test
    void pickingAFighterUpdatesTheLobby() {
        FakeLink host = create("Sam");
        assertEquals("BRAWLER", host.last(Lobby.class).players().get(0).fighter());

        send(host, new Pick("ZONER"));
        assertEquals("ZONER", host.last(Lobby.class).players().get(0).fighter());
        assertEquals(FighterType.ZONER, room.players().get(0).fighter());

        send(host, new Pick("PIZZA")); // not a fighter, ignored
        assertEquals(FighterType.ZONER, room.players().get(0).fighter());
    }

    // ---------- starting ----------

    @Test
    void onlyTheHostCanStart() {
        create("Host");
        FakeLink guest = join("Guest");
        send(guest, new Start());
        assertEquals(Protocol.NOT_HOST, guest.lastErrorCode());
        assertEquals(Phase.LOBBY, room.phase());
    }

    @Test
    void startNeedsTwoPlayers() {
        FakeLink host = create("Host");
        send(host, new Start());
        assertEquals(Protocol.NEED_PLAYERS, host.lastErrorCode());
        assertEquals(Phase.LOBBY, room.phase());
    }

    @Test
    void countdownGoesThreeTwoOneThenPlaying() {
        FakeLink[] links = twoPlayersCountingDown();
        assertEquals(Phase.COUNTDOWN, room.phase());

        // The whole countdown lasts 3 seconds = 180 ticks, counting the tick
        // that handled Start. So 178 more ticks is still counting down...
        ticks(3 * Balance.COUNTDOWN_STEP_TICKS - 2);
        assertEquals(Phase.COUNTDOWN, room.phase());
        // ...and one more starts the match.
        ticks(1);
        assertEquals(Phase.PLAYING, room.phase());

        List<Integer> numbers = links[1].all(Countdown.class).stream().map(Countdown::n).toList();
        assertEquals(List.of(3, 2, 1), numbers);
    }

    @Test
    void pressingStartTwiceOnlyStartsOnce() {
        FakeLink host = create("Host");
        join("Guest");
        room.enqueue(host, new Start());
        room.enqueue(host, new Start());
        room.tick();
        assertEquals(1, host.all(Countdown.class).size());
    }

    @Test
    void snapshotsAreSentEveryOtherTick() {
        FakeLink[] links = twoPlayersCountingDown();
        ticks(3 * Balance.COUNTDOWN_STEP_TICKS);
        int before = links[0].all(State.class).size();

        ticks(60);
        assertEquals(30, links[0].all(State.class).size() - before);
    }

    @Test
    void backedUpBrowsersSkipSnapshots() {
        FakeLink[] links = twoPlayersCountingDown();
        ticks(3 * Balance.COUNTDOWN_STEP_TICKS);
        links[1].backedUp = true;
        int before = links[1].all(State.class).size();
        ticks(20);
        assertEquals(before, links[1].all(State.class).size());
    }

    @Test
    void cannotJoinAfterTheMatchStarted() {
        twoPlayersCountingDown();
        FakeLink late = join("Late");
        assertEquals(Protocol.GAME_STARTED, late.lastErrorCode());
        assertEquals(2, room.players().size());
    }

    @Test
    void againOnlyWorksForTheHostOnTheResultsScreen() {
        FakeLink[] links = twoPlayersCountingDown();
        send(links[1], new Again());
        assertEquals(Protocol.NOT_HOST, links[1].lastErrorCode());
        send(links[0], new Again()); // not on results yet, so nothing happens
        assertEquals(Phase.COUNTDOWN, room.phase());
    }

    // ---------- leaving and host transfer ----------

    @Test
    void hostLeavingInLobbyPassesHostOn() {
        FakeLink host = create("Host");
        FakeLink guest = join("Guest");
        send(host, new Leave());

        assertEquals(idOf(guest), room.hostId());
        assertEquals(1, room.players().size());
        assertEquals(idOf(guest), guest.last(Lobby.class).hostId());
        assertEquals(1, host.detachCount);
    }

    @Test
    void hostDisconnectingMidMatchPassesHostOn() {
        FakeLink[] links = twoPlayersCountingDown();
        ticks(3 * Balance.COUNTDOWN_STEP_TICKS);
        send(links[0], new Closed());

        assertEquals(idOf(links[1]), room.hostId());
        // The player stays on the list (for the results) but is not connected.
        assertEquals(2, room.players().size());
        assertFalse(room.players().get(0).connected());
        assertEquals(1, room.connectedCount());
    }

    @Test
    void disconnectedPlayersStopGettingMessages() {
        FakeLink[] links = twoPlayersCountingDown();
        ticks(3 * Balance.COUNTDOWN_STEP_TICKS);
        send(links[1], new Closed());
        int before = links[1].sent.size();
        ticks(30);
        assertEquals(before, links[1].sent.size());
    }

    // ---------- the match ----------

    private static Protocol.Input keys(boolean left, boolean right) {
        return new Protocol.Input(1, left, right, false, false, false, false, false);
    }

    @Test
    void countdownSnapshotsShowFightersStandingAtTheirSpawns() {
        FakeLink[] links = twoPlayersCountingDown();
        ticks(10);
        State state = links[1].last(State.class);
        assertEquals("COUNTDOWN", state.phase());
        assertEquals(2, state.players().size());
        assertEquals(380, state.players().get(0).x(), 1e-9);
        assertEquals(540, state.players().get(1).x(), 1e-9);
        assertEquals(520, state.players().get(1).y(), 1e-9);
        assertEquals(Balance.MATCH_SECONDS, state.timeLeft());
    }

    @Test
    void fightersDoNotMoveDuringTheCountdown() {
        FakeLink[] links = twoPlayersCountingDown();
        send(links[0], keys(false, true));
        ticks(60);
        assertEquals(380, room.world().fighter(idOf(links[0])).x(), 1e-9);
    }

    @Test
    void heldKeysMoveTheFighterDuringTheMatch() {
        FakeLink[] links = twoPlayersCountingDown();
        ticks(3 * Balance.COUNTDOWN_STEP_TICKS);
        assertEquals(Phase.PLAYING, room.phase());

        send(links[0], keys(false, true));
        ticks(30);
        assertTrue(room.world().fighter(idOf(links[0])).x() > 380);
        assertEquals(540, room.world().fighter(idOf(links[1])).x(), 1e-9, "the other one did not move");
    }

    @Test
    void keysAreReleasedAfterTwoSecondsOfSilence() {
        FakeLink[] links = twoPlayersCountingDown();
        ticks(3 * Balance.COUNTDOWN_STEP_TICKS);
        send(links[0], keys(false, true));
        int hostId = idOf(links[0]);
        ticks(Balance.INPUT_TIMEOUT_TICKS - 5);
        assertTrue(room.world().fighter(hostId).input().right(), "still held");
        ticks(10);
        assertFalse(room.world().fighter(hostId).input().right(), "let go after the timeout");
    }

    @Test
    void disconnectingReleasesKeys() {
        FakeLink[] links = twoPlayersCountingDown();
        ticks(3 * Balance.COUNTDOWN_STEP_TICKS);
        send(links[0], keys(true, false));
        send(links[0], new Closed());
        assertFalse(room.world().fighter(idOf(links[0])).input().left());
    }

    @Test
    void inputInTheLobbyIsIgnored() {
        FakeLink host = create("Host");
        send(host, keys(false, true));
        assertNull(room.world());
        assertEquals(Phase.LOBBY, room.phase());
    }

    // ---------- deleting ----------

    @Test
    void emptyRoomIsDeletedAfterSixtySeconds() {
        FakeLink host = create("Host");
        send(host, new Leave()); // this tick counts as the first empty tick
        ticks(Balance.EMPTY_ROOM_DELETE_TICKS - 2);
        assertFalse(room.shouldDelete());
        ticks(1);
        assertTrue(room.shouldDelete());
    }

    @Test
    void roomWithPlayersIsNotDeleted() {
        create("Host");
        ticks(Balance.EMPTY_ROOM_DELETE_TICKS + 10);
        assertFalse(room.shouldDelete());
    }

    @Test
    void roomNobodyEverJoinedIsDeletedToo() {
        ticks(Balance.EMPTY_ROOM_DELETE_TICKS);
        assertTrue(room.shouldDelete());
    }
}
