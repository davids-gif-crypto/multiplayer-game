// game.js
// The match screen.
//
// Milestone 1 version: shows the 3, 2, 1 countdown and the match timer only.
// Milestone 2 replaces this with the canvas, the fighters and smooth motion.

import { setText } from "./ui.js";

export function showCountdown(n) {
  setText("countdown", String(n));
  setText("match-info", "Get ready!");
}

export function onState(msg) {
  setText("countdown", "");
  setText("match-info", "Match running. Time left " + formatTime(msg.timeLeft) +
    ". (The fighters show up here in Milestone 2.)");
}

// 125 seconds becomes "2:05".
export function formatTime(seconds) {
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  return minutes + ":" + (rest < 10 ? "0" : "") + rest;
}
