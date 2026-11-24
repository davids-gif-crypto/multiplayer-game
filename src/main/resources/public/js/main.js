// main.js
// The starting point of the game page. It connects to the server and sends
// each incoming message to the code that handles it.

import { connect } from "./net.js";
import { showScreen, getScreen, friendlyError, setText, installHowTo, showConnectionLost } from "./ui.js";
import { setupHome, homeReady, stopWaiting, refreshHomeButtons, setupLobby, renderLobby } from "./lobby.js";
import * as game from "./game.js";

// Everything this browser knows about where it is.
const app = {
  connected: false, // is the WebSocket open?
  busy: false,      // waiting for the reply to create or join?
  myId: 0,          // my player id in the room
  room: "",         // room code, "" when not in a room
  hostId: 0,        // id of the current host
  players: [],      // latest player list from the server
};

installHowTo();
setupHome(app);
setupLobby(app, goHome);
showScreen("home");

connect({
  onOpen() {
    app.connected = true;
    setText("home-status", "Connected. Ready to play!");
    homeReady(app);
  },
  onMessage: handleMessage,
  onClose() {
    app.connected = false;
    game.leaveMatch();
    refreshHomeButtons(app);
    showConnectionLost(app.room);
  },
  onFail() {
    setText("home-status", "");
    setText("home-error",
      "Could not reach the game server. Some school networks block games like this. " +
      "Try another network, or ask whoever runs the server for its local address.");
  },
});

function handleMessage(msg) {
  switch (msg.t) {
    case "joined":
      app.myId = msg.you;
      app.room = msg.room;
      stopWaiting(app);
      // Put the code in the address bar, so a refresh or a copied URL keeps it.
      history.replaceState(null, "", "?room=" + msg.room);
      setText("lobby-error", "");
      showScreen("lobby");
      break;

    case "lobby":
      app.hostId = msg.hostId;
      app.players = msg.players;
      renderLobby(app);
      game.updateRoster(app);
      break;

    case "countdown":
      openMatchScreen();
      game.showCountdown(msg.n);
      break;

    case "state":
      openMatchScreen();
      game.onState(msg);
      break;

    case "error":
      showError(msg);
      break;

    default:
      // Unknown message types are ignored, so an older page keeps working
      // if the server learns new messages.
      break;
  }
}

// Switches to the match screen, once, when the countdown starts.
function openMatchScreen() {
  if (getScreen() !== "match") {
    showScreen("match");
    game.enterMatch(app);
  }
}

// Shows a server error on whichever screen is open.
function showError(msg) {
  const text = friendlyError(msg.code, msg.msg);
  if (getScreen() === "home") {
    stopWaiting(app);
    setText("home-error", text);
  } else if (getScreen() === "lobby") {
    setText("lobby-error", text);
  } else {
    console.warn("Server error:", msg.code, msg.msg);
  }
}

// Back to the home screen after pressing Leave.
function goHome() {
  app.room = "";
  app.myId = 0;
  app.players = [];
  history.replaceState(null, "", location.pathname);
  setText("home-error", "");
  showScreen("home");
  refreshHomeButtons(app);
}
