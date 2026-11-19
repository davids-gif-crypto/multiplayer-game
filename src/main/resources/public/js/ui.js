// ui.js
// Small helpers for switching screens and showing messages.

const SCREENS = ["home", "lobby", "match"];

// Friendly text for each error code the server can send.
const FRIENDLY_ERRORS = {
  ROOM_NOT_FOUND: "No room has that code. Check the letters and try again.",
  ROOM_FULL: "That room already has 4 players.",
  GAME_STARTED: "That room is in the middle of a match. Wait for it to finish, then try again.",
  BAD_NAME: "Please type a nickname first (up to 12 characters).",
  NOT_HOST: "Only the host can do that.",
  NEED_PLAYERS: "You need at least 2 players to start.",
  RATE_LIMIT: "Whoa, slow down! Too many messages at once.",
  SERVER_FULL: "The server is busy right now. Please try again in a minute.",
};

let currentScreen = "home";

// Shows one screen ("home", "lobby" or "match") and hides the others.
export function showScreen(name) {
  currentScreen = name;
  for (const screen of SCREENS) {
    document.getElementById("screen-" + screen).classList.toggle("hidden", screen !== name);
  }
}

export function getScreen() {
  return currentScreen;
}

// Turns a server error into a sentence a player understands.
export function friendlyError(code, serverText) {
  return FRIENDLY_ERRORS[code] || serverText || "Something went wrong.";
}

// Sets the text of an element by id. Always textContent, never innerHTML,
// so a nickname like "<script>" shows up as plain text and never runs.
export function setText(id, text) {
  document.getElementById(id).textContent = text;
}

// Copies the "How to play" box into every spot that wants one.
export function installHowTo() {
  const template = document.getElementById("howto-template");
  for (const slot of document.querySelectorAll(".howto-slot")) {
    slot.appendChild(template.content.cloneNode(true));
  }
}

// Shows the "Connection lost" box. The Reconnect button reloads the page,
// keeping the room code in the address so rejoining is one click.
export function showConnectionLost(roomCode) {
  document.getElementById("overlay-lost").classList.remove("hidden");
  const button = document.getElementById("reconnect-button");
  button.onclick = () => {
    const query = roomCode ? "?room=" + encodeURIComponent(roomCode) : "";
    location.href = location.pathname + query;
  };
  button.focus();
}
