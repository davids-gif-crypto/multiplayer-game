# Food Fight

A multiplayer browser fighting game for 2 to 4 players, set in a cafeteria food brawl on a lunch tray. Players open a link, type a 4 letter room code, and fight. Everything runs in a normal web page, so it works on school Chromebooks with nothing to install.

The full design is in [PROMPT.md](PROMPT.md).

## How to run it on your computer

You need Java installed (any version 17 or newer is enough to start Gradle). The build uses Java 21, and Gradle downloads Java 21 by itself the first time if you do not have it.

```bash
./gradlew run
```

Then open the link it prints, for example `http://localhost:8080`. It also prints a LAN address like `http://192.168.1.20:8080` that other laptops on the same Wi-Fi can open.

To use a different port, set the `PORT` environment variable:

```bash
PORT=9000 ./gradlew run
```

## How to run the tests

```bash
./gradlew test
```

## How to build one runnable jar

```bash
./gradlew shadowJar
```

```bash
java -jar build/libs/app.jar
```

## Project layout

| Path | What it is |
|---|---|
| `src/main/java/com/foodfight/Main.java` | Starts the web server, the health check, the WebSocket and the game thread |
| `src/main/java/com/foodfight/net/` | WebSocket messages (`Protocol`), one browser connection (`Connection`), rate limiting |
| `src/main/java/com/foodfight/room/` | Rooms, room codes, nicknames, host, lobby, countdown, and the 60 Hz game loop (`RoomManager`) |
| `src/main/java/com/foodfight/sim/` | The game simulation. Plain Java with no web code. `GameWorld` runs the physics, `Stage` is the level layout, `Balance.java` holds every tuning number |
| `src/main/resources/public/js/` | The browser code: `net.js` (WebSocket), `input.js` (keyboard), `interp.js` (smooth motion), `render.js` (drawing), `game.js` (match screen), `lobby.js` and `ui.js` (screens) |
| `src/main/resources/public/` | The web page: HTML, CSS and JavaScript (no frameworks, no build step) |
| `src/test/java/com/foodfight/` | JUnit 5 tests, including a test that starts the real server and connects with WebSocket clients |

## How the server is put together

- Browsers only send messages. They never change game state themselves.
- Each room has an inbox (a thread safe queue). Web threads put messages in the inbox.
- One game thread runs 60 times per second. Each time, it empties every room's inbox and updates the room. Since only this one thread ever changes a room, the room code needs no locks.
- During a match the room sends a snapshot 30 times per second (every 2nd tick).
- Messages to browsers are sent without waiting for them to arrive, so one slow Chromebook cannot freeze the game for everyone. If a browser falls behind, it skips a few snapshots until it catches up.

## How the browser draws the match

- The world is always 1280 x 720 units. The canvas scales it to fit the window, with dark bars on the sides so nothing gets stretched.
- The stage never moves, so it is drawn once onto a hidden canvas and copied each frame.
- Snapshots arrive 30 times per second but the screen draws 60. `interp.js` draws the world about 100 ms in the past and blends between the two snapshots around that moment, so motion looks smooth. If snapshots stop, it guesses ahead for at most 100 ms, then freezes.
- Knockouts and respawns snap instead of sliding across the screen.

## Progress

- [x] M0 Project skeleton: Gradle, web page, `/health`, WebSocket echo
- [x] M1 Rooms and lobby: room codes, nicknames, fighter picking, host start, countdown
- [x] M2 Movement: gravity, running, jumping, double jump, platforms, blast zone, respawning, smooth drawing
- [ ] M3 Combat
- [ ] M4 Match flow
- [ ] M5 Polish and feel
- [ ] M6 Deploy and test (must have release)
