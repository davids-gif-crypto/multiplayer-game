package com.foodfight.room;

import com.foodfight.net.Protocol.Again;
import com.foodfight.net.Protocol.ClientMsg;
import com.foodfight.net.Protocol.Closed;
import com.foodfight.net.Protocol.Countdown;
import com.foodfight.net.Protocol.Create;
import com.foodfight.net.Protocol.ErrorMsg;
import com.foodfight.net.Protocol.Input;
import com.foodfight.net.Protocol.Join;
import com.foodfight.net.Protocol.Joined;
import com.foodfight.net.Protocol.Leave;
import com.foodfight.net.Protocol.Lobby;
import com.foodfight.net.Protocol.LobbyPlayer;
import com.foodfight.net.Protocol.Pick;
import com.foodfight.net.Protocol.PlayerState;
import com.foodfight.net.Protocol.Start;
import com.foodfight.net.Protocol.State;
import com.foodfight.sim.Balance;
import com.foodfight.sim.Fighter;
import com.foodfight.sim.FighterType;
import com.foodfight.sim.GameWorld;
import com.foodfight.sim.InputState;
import com.foodfight.sim.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.foodfight.net.Protocol.BAD_NAME;
import static com.foodfight.net.Protocol.GAME_STARTED;
import static com.foodfight.net.Protocol.NEED_PLAYERS;
import static com.foodfight.net.Protocol.NOT_HOST;
import static com.foodfight.net.Protocol.ROOM_FULL;

/**
 * One game room: up to 4 players, a lobby, and a match (a GameWorld).
 *
 * THREADING RULE: only the game thread may change a room. WebSocket threads
 * call {@link #enqueue} to drop a message in the inbox, and the game thread
 * handles the whole inbox at the start of the next {@link #tick()}. Because
 * of this, nothing else in this class needs locks, and things like two people
 * pressing Start at the same moment are handled one after the other.
 */
public class Room {

    /** A message waiting in the inbox, plus who sent it. */
    private record Incoming(PlayerLink from, ClientMsg msg) {
    }

    private final String code;
    private final Queue<Incoming> inbox = new ConcurrentLinkedQueue<>();

    /** Players in join order. Join order also decides spawn points later. */
    private final List<Player> players = new ArrayList<>();

    private Phase phase = Phase.LOBBY;
    private int hostId = 0;          // 0 means "no host"
    private int nextPlayerId = 1;

    private long tick = 0;           // ticks since the room was made
    private int phaseTicks = 0;      // ticks since the current phase began
    private int emptyTicks = 0;      // ticks in a row with nobody connected
    private int matchTicksLeft = 0;  // match timer

    /** The match simulation. Only exists during COUNTDOWN and PLAYING. */
    private GameWorld world = null;

    public Room(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /** Called from any thread: queue a message for the game thread. */
    public void enqueue(PlayerLink from, ClientMsg msg) {
        inbox.add(new Incoming(from, msg));
    }

    // =================================================================
    // The game thread calls tick() 60 times per second
    // =================================================================

    public void tick() {
        tick++;
        handleInbox();

        phaseTicks++;
        switch (phase) {
            case COUNTDOWN -> updateCountdown();
            case PLAYING -> updatePlaying();
            case LOBBY, RESULTS -> {
                // Nothing happens by itself in these phases.
            }
        }

        emptyTicks = (connectedCount() == 0) ? emptyTicks + 1 : 0;
    }

    /** True when RoomManager should delete this room. */
    public boolean shouldDelete() {
        boolean emptyTooLong = emptyTicks >= Balance.EMPTY_ROOM_DELETE_TICKS;
        boolean resultsIdleTooLong = phase == Phase.RESULTS
                && phaseTicks >= Balance.RESULTS_IDLE_DELETE_TICKS;
        return emptyTooLong || resultsIdleTooLong;
    }

    /** Called by RoomManager when the room is deleted. */
    public void close() {
        for (Player p : players) {
            p.link.detach(this);
        }
        Incoming waiting;
        while ((waiting = inbox.poll()) != null) {
            waiting.from().detach(this);
        }
    }

    // =================================================================
    // Handling messages
    // =================================================================

    private void handleInbox() {
        Incoming in;
        while ((in = inbox.poll()) != null) {
            handle(in.from(), in.msg());
        }
    }

    private void handle(PlayerLink from, ClientMsg msg) {
        // Create and Join come from people who are not players yet.
        if (msg instanceof Create create) {
            addPlayer(from, create.name(), true);
            return;
        }
        if (msg instanceof Join join) {
            addPlayer(from, join.name(), false);
            return;
        }

        // Everything else must come from a connected player in this room.
        Player player = find(from);
        if (player == null || !player.connected) {
            return;
        }
        player.lastHeardTick = tick;

        switch (msg) {
            case Pick pick -> pickFighter(player, pick.fighter());
            case Start start -> startCountdown(player);
            case Input input -> {
                // Held keys only matter while there is a match.
                if (world != null) {
                    world.setInput(player.id, new InputState(input.left(), input.right(), input.jump(),
                            input.down(), input.attack(), input.special(), input.shield()));
                }
            }
            case Again again -> backToLobby(player);
            case Leave leave -> removePlayer(player);
            case Closed closed -> removePlayer(player);
            default -> {
                // Ping is answered by WsHandler and never reaches a room.
            }
        }
    }

    private void addPlayer(PlayerLink from, String rawName, boolean asHost) {
        if (find(from) != null) {
            return; // already in this room
        }
        if (phase != Phase.LOBBY) {
            refuse(from, GAME_STARTED, "That room is in the middle of a match.");
            return;
        }
        if (players.size() >= Balance.MAX_PLAYERS) {
            refuse(from, ROOM_FULL, "That room already has " + Balance.MAX_PLAYERS + " players.");
            return;
        }
        // WsHandler already cleaned the name, but checking again here keeps
        // the room safe even if someone calls it a different way.
        String name = PlayerName.clean(rawName);
        if (name == null) {
            refuse(from, BAD_NAME, "Please type a nickname.");
            return;
        }

        List<String> takenNames = new ArrayList<>();
        for (Player p : players) {
            takenNames.add(p.name);
        }
        Player player = new Player(nextPlayerId++, PlayerName.makeUnique(name, takenNames), from, tick);
        players.add(player);

        if (asHost || hostId == 0) {
            hostId = player.id;
        }
        from.send(new Joined(code, player.id, player.id == hostId));
        broadcastLobby();
    }

    /** Sends an error and tells the connection it is not in this room. */
    private void refuse(PlayerLink from, String errorCode, String text) {
        from.send(new ErrorMsg(errorCode, text));
        from.detach(this);
    }

    private void pickFighter(Player player, String fighterName) {
        FighterType type = FighterType.fromName(fighterName);
        if (phase != Phase.LOBBY || type == null) {
            return;
        }
        player.fighter = type;
        broadcastLobby();
    }

    private void startCountdown(Player player) {
        if (player.id != hostId) {
            player.link.send(new ErrorMsg(NOT_HOST, "Only the host can start the match."));
            return;
        }
        if (phase != Phase.LOBBY) {
            return; // already started, for example Start was pressed twice
        }
        if (connectedCount() < Balance.MIN_PLAYERS_TO_START) {
            player.link.send(new ErrorMsg(NEED_PLAYERS,
                    "You need at least " + Balance.MIN_PLAYERS_TO_START + " players to start."));
            return;
        }
        // Build the match now, so the fighters can be seen standing on the
        // tray during the countdown. Join order decides the spawn points.
        world = new GameWorld(Stage.lunchTray());
        for (Player p : players) {
            world.addFighter(p.id, p.fighter);
        }
        setPhase(Phase.COUNTDOWN);
        broadcast(new Countdown(Balance.COUNTDOWN_FROM));
    }

    private void backToLobby(Player player) {
        if (player.id != hostId) {
            player.link.send(new ErrorMsg(NOT_HOST, "Only the host can start a new round."));
            return;
        }
        if (phase != Phase.RESULTS) {
            return;
        }
        // People who left during the match or on the results screen are
        // dropped now, so their spots open up for new players.
        players.removeIf(p -> !p.connected);
        world = null;
        setPhase(Phase.LOBBY);
        broadcastLobby();
    }

    /** A player pressed Leave or their connection closed. */
    private void removePlayer(Player player) {
        player.connected = false;
        player.disconnectedAtTick = tick;
        player.link.detach(this);

        if (phase == Phase.LOBBY) {
            players.remove(player);
        }
        if (world != null) {
            // Let go of their keys so their fighter does not keep running.
            world.setInput(player.id, InputState.NONE);
        }
        // During a match or the results screen the player stays on the list,
        // so the results can still show their name. Milestone 4 eliminates
        // their fighter (right away for Leave, after a grace period for a
        // lost connection).

        if (player.id == hostId) {
            passHostToNextPlayer();
        }
        broadcastLobby();
    }

    /** Gives host to the first connected player in join order. */
    private void passHostToNextPlayer() {
        hostId = 0;
        for (Player p : players) {
            if (p.connected) {
                hostId = p.id;
                return;
            }
        }
    }

    // =================================================================
    // Phases
    // =================================================================

    private void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
    }

    /**
     * Shows 3, then 2, then 1, one second apart, then starts the match.
     * The fighters stand still on the tray, but snapshots are still sent so
     * everyone can see the stage behind the big numbers.
     */
    private void updateCountdown() {
        if (phaseTicks % Balance.COUNTDOWN_STEP_TICKS == 0) {
            int stepsDone = phaseTicks / Balance.COUNTDOWN_STEP_TICKS;
            if (stepsDone >= Balance.COUNTDOWN_FROM) {
                startMatch();
                return;
            }
            broadcast(new Countdown(Balance.COUNTDOWN_FROM - stepsDone));
        }
        if (phaseTicks % Balance.SNAPSHOT_EVERY_TICKS == 0) {
            sendSnapshot();
        }
    }

    private void startMatch() {
        setPhase(Phase.PLAYING);
        matchTicksLeft = Balance.MATCH_SECONDS * Balance.TICKS_PER_SECOND;
    }

    private void updatePlaying() {
        clearStaleInputs();
        world.step();
        if (matchTicksLeft > 0) {
            matchTicksLeft--;
        }
        // Milestone 4 ends the match when the timer runs out or one fighter is left.

        if (phaseTicks % Balance.SNAPSHOT_EVERY_TICKS == 0) {
            sendSnapshot();
        }
    }

    /**
     * Browsers send their keys at least every 100 ms. If we have heard
     * nothing from a player for 2 seconds, their browser is probably frozen
     * or asleep, so let go of their keys before their fighter runs off stage.
     */
    private void clearStaleInputs() {
        for (Player p : players) {
            if (tick - p.lastHeardTick > Balance.INPUT_TIMEOUT_TICKS) {
                world.setInput(p.id, InputState.NONE);
            }
        }
    }

    private void sendSnapshot() {
        int secondsLeft = (matchTicksLeft + Balance.TICKS_PER_SECOND - 1) / Balance.TICKS_PER_SECOND;
        if (phase == Phase.COUNTDOWN) {
            secondsLeft = Balance.MATCH_SECONDS;
        }
        List<PlayerState> fighterRows = new ArrayList<>();
        for (Fighter f : world.fighters()) {
            fighterRows.add(PlayerState.of(f));
        }
        State state = new State(tick, phase.name(), secondsLeft, fighterRows, List.of(), world.takeEvents());
        for (Player p : players) {
            // A backed up browser skips this snapshot. A newer one comes soon.
            if (p.connected && !p.link.isBackedUp()) {
                p.link.send(state);
            }
        }
    }

    // =================================================================
    // Helpers
    // =================================================================

    private void broadcastLobby() {
        List<LobbyPlayer> rows = new ArrayList<>();
        for (Player p : players) {
            rows.add(new LobbyPlayer(p.id, p.name, p.fighter.name(), p.connected));
        }
        broadcast(new Lobby(code, hostId, rows));
    }

    private void broadcast(Object message) {
        for (Player p : players) {
            if (p.connected) {
                p.link.send(message);
            }
        }
    }

    private Player find(PlayerLink link) {
        for (Player p : players) {
            if (p.link == link) {
                return p;
            }
        }
        return null;
    }

    public int connectedCount() {
        int count = 0;
        for (Player p : players) {
            if (p.connected) {
                count++;
            }
        }
        return count;
    }

    // Read only views, mainly for tests.

    public Phase phase() {
        return phase;
    }

    public int hostId() {
        return hostId;
    }

    public List<Player> players() {
        return List.copyOf(players);
    }

    /** The match simulation, or null when there is no match. For tests. */
    GameWorld world() {
        return world;
    }
}
