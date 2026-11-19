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
| `src/main/java/com/foodfight/Main.java` | Starts the web server, the health check and the WebSocket |
| `src/main/java/com/foodfight/net/` | WebSocket handling (right now just an echo) |
| `src/main/resources/public/` | The web page: HTML, CSS and JavaScript |
| `src/test/java/com/foodfight/` | JUnit 5 tests |

## Progress

- [x] M0 Project skeleton: Gradle, web page, `/health`, WebSocket echo
- [ ] M1 Rooms and lobby
- [ ] M2 Movement
- [ ] M3 Combat
- [ ] M4 Match flow
- [ ] M5 Polish and feel
- [ ] M6 Deploy and test (must have release)
