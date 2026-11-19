# Food Fight: Claude Code build prompt

Paste everything below this line into Claude Code, from an empty folder that will become the project repo.

---

You are helping me, an IB Computer Science student, build a multiplayer browser fighting game called **Food Fight** for a 2 week school project. Up to 4 classmates play together in one room, mostly on school Chromebooks, by opening a link and typing a 4 letter room code. Nothing can be installed on a Chromebook, so the whole client is a normal web page.

I have to understand, explain, and defend every file in this project, so write clear, plain, well commented code. Prefer simple over clever. No frameworks on the client, no build step on the client, no extra libraries beyond the list below. Write all comments and the README in natural student friendly English and do not use em dashes anywhere.

## 1. How to work

1. Read this whole prompt first. Then write a short plan (milestones in section 14) and start building. Only stop to ask me something if you are truly blocked.
2. Work milestone by milestone. At the end of each milestone: run `./gradlew test`, start the server and smoke test it, fix problems, then make a git commit with a clear message.
3. You cannot see me play, so prove things work with automated tests and a headless bot client (section 12). If Playwright or a headless Chrome is available, also load the page and check for console errors and that the canvas draws. If not, skip that and say so.
4. Keep all tuning numbers (speeds, damage, knockback, timers) in one file, `Balance.java`, so I can adjust the feel without hunting through code.
5. At the end, give me a short summary: what works, what is stubbed, how to run it, how to deploy it, and what to playtest first.

## 2. Game summary

- 2D platform fighter, free for all, 2 to 4 players, 3 to 5 minute matches.
- Theme: a cafeteria food brawl on a lunch tray.
- Each hit raises the target's damage percent. Higher percent means bigger knockback. If a fighter flies out past the blast zone they lose a stock (a life). Everyone starts with 3 stocks. Lose all 3 and you become a spectator. Last fighter with stocks wins.
- The server is the single source of truth. Browsers only send inputs and draw what the server sends. Nobody can cheat by editing their own damage.

## 3. Tech stack (fixed)

| Part | Choice |
|---|---|
| Server language | Java 21 (use a Gradle toolchain for 21) |
| Web framework | Javalin 6.x (latest 6.x). Serves static files and WebSockets |
| JSON | Jackson (`jackson-databind`; add it explicitly, or use `javalin-bundle`) |
| Logging | `slf4j-simple` so Javalin does not warn about a missing logger |
| Build | Gradle with the Gradle wrapper committed (`./gradlew`). Kotlin DSL or Groovy, either is fine |
| Fat jar | Shadow plugin (`com.gradleup.shadow`) so `java -jar` works |
| Tests | JUnit 5 |
| Client | Plain HTML, CSS, JavaScript (ES modules), HTML5 Canvas 2D. No npm, no bundler, no CDN links, no web fonts (school filters block third party domains, so use only system fonts and same origin files) |
| Hosting | Render free tier using a Dockerfile |

## 4. Repo layout

```
food-fight/
  build.gradle(.kts), settings.gradle(.kts), gradlew, gradle/wrapper/...
  Dockerfile, render.yaml, .dockerignore, .gitignore, README.md
  src/main/java/com/foodfight/
    Main.java                    // starts Javalin, reads PORT env var, default 8080
    net/    WsHandler.java, Protocol.java (message records), RateLimiter.java
    room/   Room.java, RoomManager.java, RoomCode.java, Lobby state
    sim/    GameWorld.java, Fighter.java, FighterType.java, Stage.java,
            Projectile.java, InputState.java, Knockback.java, Balance.java, Rect.java
  src/main/resources/public/
    index.html, css/style.css
    js/main.js, net.js, input.js, lobby.js, game.js, render.js, interp.js, ui.js
  src/test/java/com/foodfight/
    sim/ ...unit tests, net/ ...integration test, tools/BotClient.java
```

The `sim` package must be plain Java with no Javalin imports, so it is easy to unit test and easy for me to explain.

## 5. Server architecture

- **One shared game thread**: a single `ScheduledExecutorService` runs `tick()` for every active room at a fixed 60 Hz (`scheduleAtFixedRate`, fixed `dt = 1/60`). Handle slow ticks without a death spiral (if a tick falls behind, run at most 2 catch up steps then drop the rest).
- **Thread safety**: WebSocket handler threads never touch game state directly. They put parsed messages on a thread safe queue per room (`ConcurrentLinkedQueue`). The game thread drains the queue at the start of each tick. Sending snapshots uses the Javalin `WsContext.send`.
- **Snapshots**: the server simulates at 60 Hz but sends a state snapshot every 2nd tick (30 per second) to save bandwidth on school Wi-Fi. Events (hits, KOs) are included in the snapshot that follows them so the client can draw splats and KO bursts.
- **Rooms**: `RoomManager` holds a `ConcurrentHashMap<String, Room>`. Room codes are 4 uppercase letters from `ABCDEFGHJKLMNPQRSTUVWXYZ` (no I or O), guaranteed unique among live rooms. A room is deleted 60 seconds after its last player leaves, and also when a finished match has been idle for 10 minutes. Cap total rooms at 50.
- **Room phases**: `LOBBY` -> `COUNTDOWN` (3, 2, 1) -> `PLAYING` -> `RESULTS` -> back to `LOBBY` when the host presses Play again.
- **Health check**: `GET /health` returns `ok` with HTTP 200 (Render uses this).
- **Startup**: read the `PORT` environment variable (default 8080), bind to `0.0.0.0`, and print both `http://localhost:PORT` and the machine's LAN address so I can test with other laptops.
- **WebSocket settings**: path `/ws`, idle timeout 5 minutes, max message size 1 KB. The client sends a ping message every 20 seconds (`{"t":"ping"}`) and the server replies `{"t":"pong"}` so free hosting proxies do not cut idle sockets.

## 6. Protocol (JSON text messages over one WebSocket)

Every message has a string field `t`. Use Java records for the message types and Jackson to read and write them. Ignore unknown fields. Ignore and log any message that fails to parse.

**Client to server**

| t | Fields | Meaning |
|---|---|---|
| `create` | `name` | Make a room and join it as host |
| `join` | `room`, `name` | Join an existing room (code is case insensitive) |
| `pick` | `fighter` (`BRAWLER`, `SPEEDSTER`, `ZONER`) | Choose a fighter while in lobby |
| `start` | none | Host only. Needs 2 or more players |
| `input` | `seq`, `left`, `right`, `jump`, `down`, `attack`, `special`, `shield` (all booleans except seq) | Current held keys. Sent whenever the key state changes, plus once per 100 ms as a heartbeat |
| `again` | none | Host only. Return the room to the lobby after results |
| `leave` | none | Leave the room |
| `ping` | none | Keep alive |

**Server to client**

| t | Fields | Meaning |
|---|---|---|
| `joined` | `room`, `you` (player id), `host` (bool) | Reply to create and join |
| `lobby` | `room`, `hostId`, `players` [{id, name, fighter, connected}] | Sent on every lobby change |
| `countdown` | `n` | 3, 2, 1 |
| `state` | `tick`, `phase`, `timeLeft`, `players`, `projectiles`, `events` | Snapshot, 30 per second during play |
| `results` | `placements` [{id, name, fighter, place, kos, falls}] | Match over |
| `error` | `code`, `msg` | `ROOM_NOT_FOUND`, `ROOM_FULL`, `GAME_STARTED`, `BAD_NAME`, `NOT_HOST`, `NEED_PLAYERS`, `RATE_LIMIT` |
| `pong` | none | Keep alive reply |

A `state` player entry: `{id, x, y, vx, vy, face (-1 or 1), fighter, percent, stocks, st (state string), inv (invulnerable ticks left), respawn (ticks until respawn, 0 if alive)}`. Round positions to 1 decimal place to keep messages small.
Projectile entry: `{id, owner, x, y, vx}`.
Event entries: `{k:"hit", x, y, dmg, big}`, `{k:"ko", id, x, y, dir}`, `{k:"respawn", id}`, `{k:"jump", id, x, y}`.

**Security and robustness (server)**

- Names: trim, remove control characters, max 12 characters, must not be empty after trimming. If a name is already used in the room, append " 2", " 3", and so on.
- Rate limit: at most 120 incoming messages per second per connection. If exceeded, send `RATE_LIMIT` and drop the extra messages.
- Reject `join` when the room is full (4), the match already started, or the code does not exist.
- Only the host may `start` and `again`. Ignore `input` from sockets that are not players in a playing room.
- If the host disconnects, give host to the next connected player. If a player disconnects mid match, their fighter is treated as eliminated (stocks to 0) after a 5 second grace period.
- Never trust client numbers. Only the booleans in `input` affect the simulation.

## 7. Stage and world

Logical world size is **1280 x 720** units. The client scales this to any window size. The camera is fixed and shows the whole stage.

- **Main platform (the lunch tray)**: top surface at y = 520, from x = 240 to x = 1040, 40 thick. Fighters can stand on top and fall off either side. No walls.
- **Two floating platforms (bread slices)**: x 300 to 520 at y = 360, and x 760 to 980 at y = 360, 24 thick. One way: you can jump up through them from below and land on top. Holding Down while standing on one drops you through it (ignore that platform for 12 ticks).
- **Blast zone**: a fighter whose center leaves the rectangle x from -200 to 1480, y from -250 to 900 is KO'd. Draw the visible canvas as 0..1280 by 0..720, so KOs happen just off screen.
- **Spawn points** at y = 520 (standing on the tray): x = 380, 540, 740, 900, assigned in join order.
- **Respawn**: 90 ticks (1.5 s) after a KO, if stocks remain, respawn at x = 640, y = 80 with percent reset to 0 and 120 ticks of invulnerability. Fighters may not attack while invulnerable (so they cannot spawn camp), but they can move.

## 8. Fighters, physics and combat (all numbers go in `Balance.java`)

Fixed time step `dt = 1/60`. Positions are the fighter's center bottom (feet). Hurtbox is an axis aligned rectangle.

**Shared physics**

- Gravity 2400 units/s squared. Max fall speed 1100. Fast fall (hold Down while falling) max fall speed 1700.
- Horizontal movement: accelerate toward `runSpeed` in the held direction. Ground acceleration 2400, air acceleration 1400. With no input, decelerate: ground 3000, air 600.
- Jump: pressing jump is edge triggered (detect the rising edge on the server). Fighters have 2 jumps total: the first from the ground, the second in the air (double jump). Walking off an edge does not use up a jump, so you still have both. Landing resets jumps.
- Landing on a platform only happens when moving downward and the feet crossed the platform top during this tick.
- During hitstun the player has no control and keeps the launch velocity with slight air drag.

**Fighters**

| | Brawler (meatball) | Speedster (french fry) | Zoner (ketchup bottle) |
|---|---|---|---|
| Hurtbox (w x h) | 56 x 56 | 36 x 72 | 44 x 76 |
| Weight | 1.3 | 0.8 | 1.0 |
| Run speed | 250 | 400 | 300 |
| Jump speeds (1st, 2nd) | 780, 700 | 860, 780 | 800, 720 |
| Basic attack | Slow heavy swing | Fast jab | Fires a ketchup glob |
| Startup, active, recovery (ticks) | 11, 7, 18 | 4, 5, 8 | 10, 1, 14 |
| Damage | 10 | 4 | 3 to 8 depending on how far the glob traveled |
| Base knockback | 420 | 260 | 200 |
| Knockback growth per percent | 9 | 6 | 5 |
| Launch angle (degrees up from horizontal) | 40 | 35 | 30 |
| Melee hitbox (in front of fighter) | 70 x 50, offset 20 | 50 x 36, offset 12 | none |

Trade offs to preserve: the Brawler hits hard and is hard to launch but slow. The Speedster is fast with quick weak hits and is easy to launch. The Zoner is weak up close, strong at range: its glob does only 3 damage if it hits within 150 units of the Zoner and scales up to 8 at 600 units or more.

**Zoner projectile**: radius 14, speed 650 units/s horizontally in the facing direction, tiny gravity 150, lifetime 72 ticks (1.2 s), at most 2 alive per player, cooldown 25 ticks between shots, spawned from the front of the bottle. It disappears when it hits a fighter (not its owner), leaves the blast zone, or expires. It passes through platforms.

**Attack rules**

- An attack goes through STARTUP, ACTIVE, RECOVERY. The hitbox only exists during ACTIVE and is mirrored by facing direction. Each attack can hit each target once.
- You cannot attack during hitstun, while invulnerable after respawn, or while already in an attack.
- Being hit cancels the victim's current attack.
- You face the direction you last pressed (left or right). Facing does not change mid attack.

**Hit resolution** (put in `Knockback.java` as pure static functions with unit tests)

1. `target.percent += damage`
2. `kb = (baseKnockback + growth * target.percent) / target.weight`
3. Launch velocity: `vx = cos(angle) * kb * direction`, `vy = -sin(angle) * kb`, where `direction` is the attacker's facing (-1 or 1). The target's old velocity is replaced.
4. Hitstun time in seconds: `clamp(0.15 + kb / 2500, 0.15, 0.9)`.
5. Emit a `hit` event at the contact point, with `big = kb > 900`.

**Tuning goal** (write a test that prints these and tweak `Balance.java` until they feel right, then leave the numbers): a Brawler hit on a Speedster at 0% should send it roughly 300 units. Around 100 to 120% the same hit should send a Speedster close to the edge of the blast zone from center stage. Around 150% it should be a KO from center stage. A Brawler target should survive about 40 to 50 percent longer than a Speedster.

**Win condition and placement**

- Match ends when 1 or 0 players have stocks left, or when the 5 minute timer runs out.
- On timer end, rank by stocks (more is better), then lower percent. Anyone tied shares the place.
- Placement for eliminated players: the first one out is last place, and so on.
- Track `kos` (times you were the last attacker who hit a player before their KO, within 3 seconds) and `falls` (times you were KO'd).
- Self destructs (no attacker) count as a fall but give no KO to anyone.

## 9. Client

**Screens** (one HTML page, show and hide sections, no routing):

1. **Home**: nickname field (saved in `localStorage`), big Create room button, and a room code input with a Join button. If the URL has `?room=ABCD`, prefill the code and focus the Join button, so teachers can share one link. Show errors from the server inline, with friendly text.
2. **Room lobby**: huge room code, a Copy invite link button, list of players with their nickname and picked fighter, three fighter cards (Meatball, French Fry, Ketchup) showing a small preview and 3 stat bars (speed, weight, power), highlight the chosen one. The host sees a Start button, which is disabled with a hint until at least 2 players are in.
3. **Countdown**: big 3, 2, 1 over the stage.
4. **Match**: the canvas, plus a HUD.
5. **Results**: placements list with place, nickname, fighter, KOs, falls. Host sees Play again, everyone sees Leave.

**Rendering (HTML5 Canvas 2D)**

- One canvas that fills the window. Keep a fixed logical resolution of 1280 x 720 and scale to fit with letterboxing (preserve aspect ratio). Re-fit on `resize`. Cap the device pixel ratio at 1.5 so Chromebooks stay fast.
- Use `requestAnimationFrame`. Never use `shadowBlur`, big gradients, or per frame `getImageData`. Pre-render the static background to an offscreen canvas once.
- Draw everything with simple shapes for now (no images needed):
  - Background: pale cafeteria wall, a tan table surface at the bottom.
  - Stage: the tray (gray blue slab with a lighter top and darker compartment cutouts on the front face), and two bread slices (tan crust with lighter crumb, rounded corners and two bumps on top).
  - **Meatball**: brown circle with darker spots, white eyes with angry brows.
  - **French fry**: tall yellow rounded rectangle with 3 fry tips on top and eyes.
  - **Ketchup bottle**: red rounded bottle with white cap and nozzle, cream label, eyes.
  - Ketchup glob projectile: red circle with a small trail.
  - Each fighter gets a colored name tag and a small colored triangle over their head so players can find themselves. Draw the local player's tag with a bright outline.
- Facing direction flips the drawing. States: blink while invulnerable, squash and stretch on landing and jumping (cheap scale tweak), a white flash for 3 frames when hit, a tilt toward the launch direction during hitstun.
- Hit effects: a splat burst (a small polygon star plus 6 to 8 droplets that fade over 300 ms). Make the splat bigger when `big` is true. Cap live particles at 80.
- KO effect: a big burst at the screen edge nearest to where the fighter left, with a short screen shake (6 frames).
- **HUD**: a card per player along the top (colored dot, nickname, damage percent in large text that gets redder as it grows, stock dots where a lost stock is a hollow dot), and the match timer in the center. Spectators (eliminated players) see a "You are spectating" banner and keep watching the match.
- Optional debug overlay toggled with the backtick key: FPS, ping (round trip using ping and pong), snapshot rate, and a toggle to draw hurtboxes and hitboxes.

**Smooth motion**: the server sends 30 snapshots per second but the screen draws at 60. Keep a small buffer of recent snapshots and render the world about 100 ms in the past, interpolating positions between the two snapshots around that time. If no newer snapshot is available, extrapolate for at most 100 ms using velocity, then freeze. Do not interpolate through a KO or respawn (snap instead).

**Input**

- Use `keydown` and `keyup` with `event.code` so it works on any keyboard layout: Move `KeyA` and `KeyD`, jump `KeyW` or `Space`, fast fall and drop through `KeyS`, attack `KeyJ`, special `KeyK`, shield `KeyL`. Also accept the arrow keys as an alternate for movement and jump, since some Chromebook keyboards are awkward.
- Call `preventDefault()` for these keys during the match so Space does not scroll the page and Chromebook search or other shortcuts do not interfere. Ignore auto repeat events.
- Send an `input` message only when the key state changes, and also every 100 ms as a heartbeat. Include an increasing `seq`.
- On window `blur` and on `visibilitychange`, release all keys and send the cleared state, otherwise fighters get stuck running.
- Add a "How to play" panel on the home and lobby screens listing the controls.
- Special attack and shield keys are wired up and sent but do nothing on the server until the stretch goals.

**Networking on the client**

- Build the WebSocket URL from the page location: `(location.protocol === "https:" ? "wss://" : "ws://") + location.host + "/ws"`. This makes it work on localhost, on a LAN, and on Render without changes.
- On close, show a "Connection lost" overlay with a Reconnect button that returns to the home screen. Do not auto loop reconnect.
- Use `textContent`, never `innerHTML`, for anything that came from another player (nicknames), to prevent script injection.

## 10. Chromebook and school network requirements

- Must run in current Chrome on ChromeOS, plus Edge and Safari. Test no features newer than 2 years old. No WebGL needed.
- Target a steady 60 FPS on a low end Chromebook (Intel Celeron, 4 GB RAM). Keep per frame work small, reuse objects, avoid allocating arrays in the draw loop.
- Total page weight under 300 KB. No external domains at all.
- Same origin only. Works over plain `ws://` on a local network and `wss://` on Render.
- Keyboard only. Touch support is not required.
- Show a clear friendly message if WebSocket cannot connect (it may be blocked on some networks).

## 11. Deployment (Render free tier)

Create these files and describe the steps in the README:

- `Dockerfile` (multi stage): stage 1 uses a Gradle + JDK 21 image to run `./gradlew shadowJar --no-daemon`; stage 2 uses a small `eclipse-temurin:21-jre` image, copies the jar, `EXPOSE 8080`, and runs `java -XX:MaxRAMPercentage=75 -jar app.jar`. Add `.dockerignore`.
- `render.yaml` defining a Docker web service on the free plan with `healthCheckPath: /health`.
- README section "Deploy to Render": push to GitHub, New Web Service, pick the repo, runtime Docker, plan Free, done. Mention that the free tier sleeps after about 15 minutes with no traffic and the first request after that can take 30 to 60 seconds, so open the link before class starts. Mention an optional free uptime pinger that hits `/health` every 10 minutes.
- Also document how to run on a local network if Render is blocked at school: run `./gradlew run`, find the printed LAN address, share it.

## 12. Testing (JUnit 5, plus a bot)

Write real tests and keep them fast. They must all pass before each commit.

**Unit tests for `sim`**

- Gravity: a fighter in the air accelerates down and never exceeds max fall speed.
- Platform landing: lands on the tray, lands on a bread slice from above, passes through a bread slice from below, and drops through when Down is held.
- Jumping: first jump, double jump, no third jump, jumps reset after landing.
- Horizontal movement: accelerates to run speed and decelerates to zero.
- Hit detection: hitbox overlaps hurtbox gives a hit, a miss gives nothing, each attack hits each target once, facing mirrors the hitbox, invulnerable fighters are not hit.
- Knockback: knockback increases with percent, decreases with weight, direction follows the attacker's facing, hitstun clamps correctly.
- Zoner projectile damage scales with travel distance, max 2 projectiles, cooldown works.
- Blast zone: leaving on each of the four sides removes a stock, respawn happens after 90 ticks, invulnerability counts down, percent resets.
- Win condition: last player with stocks wins, 0 stocks means spectator, timer end tiebreakers, placement order.
- `RoomCode`: always 4 characters from the allowed alphabet, no duplicates among live rooms.
- `Room`: capacity of 4, duplicate nickname handling, host transfer, deletion after empty.

**Integration test** (`net`): start the real Javalin server on port 0 (random), connect 3 clients using the JDK built in `java.net.http.WebSocket`, then check: create room, join with code, a wrong code returns `ROOM_NOT_FOUND`, a 5th join returns `ROOM_FULL`, pick fighters, host starts, countdown arrives, `state` snapshots arrive at about 30 per second, sending `attack` input causes a `hit` event when two fighters are close, host leaving transfers host, a disconnect mid match is handled.

**Bot client** (`src/test/java/.../tools/BotClient.java` or a `bots` Gradle task): runs 4 bots in one room for 60 seconds with random inputs and prints average snapshot rate, max gap between snapshots, average server tick time, and whether any exception was logged. Use it to check that 4 players is smooth.

## 13. Edge cases that must be handled

Wrong room code, room full, joining after the match started, duplicate nicknames, empty or very long nicknames, host leaving in lobby and during a match, a player disconnecting mid match, a player closing the tab on the results screen, two people pressing Start at once, the browser tab going to the background, a very slow client that stops sending inputs (after 2 seconds of no messages, clear their held keys), and everyone eliminated on the same tick (treat as a tie for first among those who lost their last stock that tick).

## 14. Milestones

Do these in order, with a commit after each. If time is short, stop after M6 and call that the must-have release.

- **M0 Project skeleton**: Gradle project, Javalin serves `index.html`, `/health`, WebSocket echo, README stub. Runs with `./gradlew run`.
- **M1 Rooms and lobby**: create and join with room codes, nicknames, fighter picking, host start, with the lobby UI. Unit and integration tests for rooms.
- **M2 Movement**: the sim with gravity, run, jump, double jump, platforms, blast zone and respawn. Client draws shapes and interpolates. Movement tests.
- **M3 Combat**: basic attacks for all three fighters, hit detection, damage percent, knockback, hitstun, hit splats. Combat tests and the tuning printout.
- **M4 Match flow**: stocks, KOs, spectating, win condition, timer, results screen, Play again.
- **M5 Polish and feel**: HUD, screen shake, squash and stretch, debug overlay, friendly errors, edge cases from section 13.
- **M6 Deploy and test**: Dockerfile, render.yaml, bot client, README with deploy steps and a playtest checklist. Verify `docker build` works if Docker is available, otherwise state that it is untested.
- **M7 Stretch goals** (only if I ask, in this order): special attacks and shield, spectator paper wad (or crumb) drop, sprites, sound effects, second stage, items, SQLite class leaderboard.

## 15. Stretch goal designs (do not build until I say so, but leave clean hooks)

- **Special attack (K)**: Brawler ground pound shockwave, Speedster dash through, Zoner ketchup puddle that slows opponents. Add `special` handling behind a switch in `Balance.java`.
- **Shield (L)**: a bubble that absorbs hits for up to 3 seconds of meter, drains while held, breaks if emptied.
- **Spectator heckling**: eliminated players press a key to drop a small crumb onto the stage that gives 1 damage and a tiny flinch, with a 5 second cooldown.
- **Sprites**: allow swapping shape drawing for images from `/public/img/` with a fallback to shapes.
- **Class leaderboard**: SQLite file with wins and KOs per nickname.

## 16. Definition of done for the must have release

- Four browsers on four devices can join one room in under 30 seconds using a link and a code.
- Everyone sees the same match with smooth motion at 60 FPS and no stuck keys.
- Hits raise percent, knockback grows with percent, KOs cost stocks, last player standing gets a results screen.
- Wrong codes, full rooms, host leaving and disconnects do not crash anything.
- `./gradlew test` passes, the bot client reports a steady snapshot rate, and the README lets a classmate deploy it on Render from scratch.

Start now with the plan and M0.
