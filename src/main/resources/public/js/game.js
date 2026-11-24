// game.js
// The match screen: starts and stops drawing, keyboard input and smoothing,
// and shows the countdown and the timer.

import { initRenderer, resizeRenderer, drawFrame } from "./render.js";
import { resetInterp, addSnapshot, sampleFighters } from "./interp.js";
import { enableInput, disableInput } from "./input.js";
import { PLAYER_COLORS } from "./lobby.js";

// Reused every frame, see interp.js.
const views = new Map();
// Player id to { name, color }, from the lobby list.
const roster = new Map();

const countdownEl = document.getElementById("countdown");
const timerEl = document.getElementById("match-timer");

let running = false;
let myId = 0;
let lastTimerText = "";
let goTimer = null;

initRenderer(document.getElementById("game-canvas"));

// Called when the countdown starts (or when a snapshot arrives and we are
// somehow not on the match screen yet).
export function enterMatch(app) {
  myId = app.myId;
  updateRoster(app);
  resetInterp();
  views.clear();
  resizeRenderer();
  enableInput();
  document.body.classList.add("in-match");
  if (!running) {
    running = true;
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
  roster.clear();
  app.players.forEach((player, index) => {
    roster.set(player.id, { name: player.name, color: PLAYER_COLORS[index % PLAYER_COLORS.length] });
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

// One animation frame: work out positions, then draw.
function frame(now) {
  if (!running) {
    return;
  }
  sampleFighters(now, views);
  drawFrame(views, roster, myId, now);
  requestAnimationFrame(frame);
}

// 125 seconds becomes "2:05".
export function formatTime(seconds) {
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return minutes + ":" + (rest < 10 ? "0" : "") + rest;
}
