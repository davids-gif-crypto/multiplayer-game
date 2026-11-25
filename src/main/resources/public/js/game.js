// game.js
// The match screen: starts and stops drawing, keyboard input and smoothing,
// and shows the countdown and the timer.

import { initRenderer, resizeRenderer, drawFrame, spawnSplat } from "./render.js";
import { resetInterp, addSnapshot, sampleWorld } from "./interp.js";
import { enableInput, disableInput } from "./input.js";
import { PLAYER_COLORS } from "./lobby.js";

// Everything the renderer needs. The maps are reused every frame (see interp.js).
const scene = {
  fighters: new Map(), // player id to fighter view
  globs: new Map(),    // glob id to glob view
  roster: new Map(),   // player id to { name, color }, from the lobby list
  myId: 0,
};

const countdownEl = document.getElementById("countdown");
const timerEl = document.getElementById("match-timer");

let running = false;
let lastTimerText = "";
let goTimer = null;
let lastFrameTime = 0;

initRenderer(document.getElementById("game-canvas"));

// Called when the countdown starts (or when a snapshot arrives and we are
// somehow not on the match screen yet).
export function enterMatch(app) {
  scene.myId = app.myId;
  updateRoster(app);
  resetInterp();
  scene.fighters.clear();
  scene.globs.clear();
  resizeRenderer();
  enableInput();
  document.body.classList.add("in-match");
  if (!running) {
    running = true;
    lastFrameTime = performance.now();
    requestAnimationFrame(frame);
  }
}

// Called when we leave the match screen or lose the connection.
export function leaveMatch() {
  running = false;
  disableInput();
  document.body.classList.remove("in-match");
}

// Names and colors come from the lobby list. A player's color is the color
// of their slot in join order, same as in the lobby.
export function updateRoster(app) {
  scene.roster.clear();
  app.players.forEach((player, index) => {
    scene.roster.set(player.id, { name: player.name, color: PLAYER_COLORS[index % PLAYER_COLORS.length] });
  });
}

export function showCountdown(n) {
  clearTimeout(goTimer);
  countdownEl.textContent = String(n);
  countdownEl.classList.remove("hidden");
}

export function onState(msg) {
  addSnapshot(msg, performance.now());

  // Only touch the page when the text actually changes.
  const timerText = formatTime(msg.timeLeft);
  if (timerText !== lastTimerText) {
    timerEl.textContent = timerText;
    lastTimerText = timerText;
  }

  // The first PLAYING snapshot after the countdown: flash "GO!".
  if (msg.phase === "PLAYING" && !countdownEl.classList.contains("hidden") && countdownEl.textContent !== "GO!") {
    countdownEl.textContent = "GO!";
    goTimer = setTimeout(() => countdownEl.classList.add("hidden"), 700);
  }
}

// One animation frame: work out positions, play due events, then draw.
function frame(now) {
  if (!running) {
    return;
  }
  // Seconds since the last frame, capped so a long pause does not make
  // particles jump across the screen.
  const dt = Math.min((now - lastFrameTime) / 1000, 0.1);
  lastFrameTime = now;

  sampleWorld(now, scene.fighters, scene.globs, handleEvent);
  drawFrame(scene, now, dt);
  requestAnimationFrame(frame);
}

// Effects for things that happened in the match.
function handleEvent(event) {
  if (event.k === "hit") {
    spawnSplat(event.x, event.y, event.big);
    const view = scene.fighters.get(event.id);
    if (view !== undefined) {
      view.flash = 3; // white for 3 frames
    }
  }
  // Knockout bursts and jump puffs come in later milestones.
}

// 125 seconds becomes "2:05".
export function formatTime(seconds) {
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return minutes + ":" + (rest < 10 ? "0" : "") + rest;
}
