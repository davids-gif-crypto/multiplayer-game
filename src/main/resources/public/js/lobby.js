// lobby.js
// The home screen (nickname, create, join) and the room lobby screen
// (room code, player list, fighter cards, Start button).

import { send } from "./net.js";
import { drawFighter } from "./render.js";
import { setText } from "./ui.js";

// The three fighters, with the 1 to 5 stat bars shown on their cards.
export const FIGHTERS = [
  { type: "BRAWLER", name: "Meatball", blurb: "Slow, heavy hits. Hard to knock away.", speed: 2, weight: 5, power: 5 },
  { type: "SPEEDSTER", name: "French Fry", blurb: "Lightning fast jabs, but flies far when hit.", speed: 5, weight: 2, power: 2 },
  { type: "ZONER", name: "Ketchup", blurb: "Shoots ketchup globs. Stronger from far away.", speed: 3, weight: 3, power: 3 },
];

// One color per player slot, in join order. The game uses the same colors.
export const PLAYER_COLORS = ["#e74c3c", "#2d7dd2", "#27ae60", "#f39c12"];

const NAME_KEY = "foodfight.name";        // where the nickname is saved
const ROOM_CODE_LETTERS = /[^A-Z]/g;      // anything that is not A to Z
const BUSY_TIMEOUT_MS = 5000;             // give up waiting for a reply after this

let busyTimer = null;

// =====================================================================
// Home screen
// =====================================================================

export function setupHome(app) {
  const nameInput = document.getElementById("name-input");
  const codeInput = document.getElementById("code-input");
  const createButton = document.getElementById("create-button");
  const joinButton = document.getElementById("join-button");

  nameInput.value = loadName();

  // Keep the room code as up to 4 capital letters while typing.
  codeInput.addEventListener("input", () => {
    codeInput.value = cleanCode(codeInput.value);
  });

  // A shared link like ?room=ABCD fills in the code for you.
  const codeFromLink = cleanCode(new URLSearchParams(location.search).get("room") || "");
  if (codeFromLink.length === 4) {
    codeInput.value = codeFromLink;
  }

  createButton.addEventListener("click", () => {
    const name = readName();
    if (name !== null) {
      startWaiting(app);
      send({ t: "create", name: name });
    }
  });

  const join = () => {
    const name = readName();
    if (name === null) {
      return;
    }
    const code = codeInput.value;
    if (code.length !== 4) {
      setText("home-error", "Room codes are 4 letters long.");
      codeInput.focus();
      return;
    }
    startWaiting(app);
    send({ t: "join", room: code, name: name });
  };
  joinButton.addEventListener("click", join);

  // Pressing Enter in a box does the obvious thing.
  codeInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      join();
    }
  });
  nameInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter") {
      if (codeInput.value.length === 4) {
        join();
      } else {
        createButton.click();
      }
    }
  });
}

// Called once the socket opens: enable the buttons and pick what to focus.
export function homeReady(app) {
  refreshHomeButtons(app);
  const codeInput = document.getElementById("code-input");
  if (codeInput.value.length === 4) {
    document.getElementById("join-button").focus();
  } else {
    document.getElementById("name-input").focus();
  }
}

// The buttons work only while connected and not already waiting for a reply.
export function refreshHomeButtons(app) {
  const usable = app.connected && !app.busy;
  document.getElementById("create-button").disabled = !usable;
  document.getElementById("join-button").disabled = !usable;
}

// Stop waiting for the server (it replied, or it sent an error).
export function stopWaiting(app) {
  app.busy = false;
  clearTimeout(busyTimer);
  refreshHomeButtons(app);
}

function startWaiting(app) {
  setText("home-error", "");
  app.busy = true;
  refreshHomeButtons(app);
  // If no reply comes, do not leave the buttons stuck forever.
  clearTimeout(busyTimer);
  busyTimer = setTimeout(() => stopWaiting(app), BUSY_TIMEOUT_MS);
}

// Returns the trimmed nickname, or null (with an error shown) if it is empty.
function readName() {
  const nameInput = document.getElementById("name-input");
  const name = nameInput.value.trim();
  if (name === "") {
    setText("home-error", "Please type a nickname first.");
    nameInput.focus();
    return null;
  }
  saveName(name);
  return name;
}

function cleanCode(text) {
  return text.toUpperCase().replace(ROOM_CODE_LETTERS, "").slice(0, 4);
}

// localStorage can throw (for example in private windows), so be careful.
function loadName() {
  try {
    return localStorage.getItem(NAME_KEY) || "";
  } catch (err) {
    return "";
  }
}

function saveName(name) {
  try {
    localStorage.setItem(NAME_KEY, name);
  } catch (err) {
    // Not saving the name is fine.
  }
}

// =====================================================================
// Lobby screen
// =====================================================================

export function setupLobby(app, onLeave) {
  buildFighterCards();

  document.getElementById("start-button").addEventListener("click", () => {
    setText("lobby-error", "");
    send({ t: "start" });
  });

  document.getElementById("leave-button").addEventListener("click", () => {
    send({ t: "leave" });
    onLeave();
  });

  document.getElementById("copy-link-button").addEventListener("click", () => copyInviteLink(app));
}

// Builds the three fighter cards once. Each card is a button with a small
// drawing of the fighter and three stat bars.
function buildFighterCards() {
  const holder = document.getElementById("fighter-cards");
  const ratio = Math.min(window.devicePixelRatio || 1, 1.5);

  for (const fighter of FIGHTERS) {
    const card = document.createElement("button");
    card.type = "button";
    card.className = "fighter-card";
    card.dataset.type = fighter.type;

    // Preview drawing. The canvas has more pixels than its on-screen size
    // on sharp screens, so the drawing does not look blurry.
    const canvas = document.createElement("canvas");
    canvas.width = 100 * ratio;
    canvas.height = 90 * ratio;
    canvas.style.width = "100px";
    canvas.style.height = "90px";
    const ctx = canvas.getContext("2d");
    ctx.scale(ratio, ratio);
    drawFighter(ctx, fighter.type, 50, 84, 1, 1);
    card.appendChild(canvas);

    const title = document.createElement("span");
    title.className = "card-title";
    title.textContent = fighter.name;
    card.appendChild(title);

    const blurb = document.createElement("span");
    blurb.className = "card-blurb";
    blurb.textContent = fighter.blurb;
    card.appendChild(blurb);

    card.appendChild(statBar("Speed", fighter.speed));
    card.appendChild(statBar("Weight", fighter.weight));
    card.appendChild(statBar("Power", fighter.power));

    card.addEventListener("click", () => send({ t: "pick", fighter: fighter.type }));
    holder.appendChild(card);
  }
}

function statBar(label, value) {
  const row = document.createElement("span");
  row.className = "stat";

  const name = document.createElement("span");
  name.className = "stat-name";
  name.textContent = label;

  const bar = document.createElement("span");
  bar.className = "stat-bar";
  const fill = document.createElement("span");
  fill.className = "stat-fill";
  fill.style.display = "block";
  fill.style.width = (value * 20) + "%";
  bar.appendChild(fill);

  row.appendChild(name);
  row.appendChild(bar);
  return row;
}

// Redraws the lobby from the latest "lobby" message stored in app.
export function renderLobby(app) {
  setText("lobby-code", app.room);
  setText("player-count", "(" + app.players.length + " of 4)");

  // Player list. Every bit of text from other players goes in with
  // textContent, so nobody can sneak HTML or scripts into a nickname.
  const list = document.getElementById("player-list");
  list.replaceChildren();
  app.players.forEach((player, index) => {
    const row = document.createElement("li");

    const dot = document.createElement("span");
    dot.className = "dot";
    dot.style.background = PLAYER_COLORS[index % PLAYER_COLORS.length];
    row.appendChild(dot);

    const name = document.createElement("span");
    name.className = "name";
    name.textContent = player.name;
    row.appendChild(name);

    if (player.id === app.hostId) {
      row.appendChild(badge("host", ""));
    }
    if (player.id === app.myId) {
      row.appendChild(badge("you", "you"));
    }

    const fighterName = document.createElement("span");
    fighterName.className = "fighter-name";
    fighterName.textContent = fighterDisplayName(player.fighter);
    row.appendChild(fighterName);

    list.appendChild(row);
  });

  // Highlight the card of the fighter I picked.
  const me = app.players.find((p) => p.id === app.myId);
  for (const card of document.querySelectorAll(".fighter-card")) {
    card.classList.toggle("chosen", me !== undefined && card.dataset.type === me.fighter);
  }

  // Start button: only the host sees it, and it needs 2 or more players.
  const isHost = app.hostId === app.myId;
  const enough = app.players.length >= 2;
  const startButton = document.getElementById("start-button");
  startButton.classList.toggle("hidden", !isHost);
  startButton.disabled = !enough;
  if (!isHost) {
    setText("start-hint", "Waiting for the host to start the match...");
  } else if (!enough) {
    setText("start-hint", "Waiting for at least 2 players. Share the room code!");
  } else {
    setText("start-hint", "");
  }
}

function badge(text, extraClass) {
  const span = document.createElement("span");
  span.className = "badge " + extraClass;
  span.textContent = text;
  return span;
}

export function fighterDisplayName(type) {
  const fighter = FIGHTERS.find((f) => f.type === type);
  return fighter ? fighter.name : type;
}

// Copies a link like https://site/?room=ABCD so classmates can click it.
async function copyInviteLink(app) {
  const link = location.origin + location.pathname + "?room=" + app.room;
  let copied = false;

  // The modern clipboard API only works on https or localhost.
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(link);
      copied = true;
    }
  } catch (err) {
    copied = false;
  }

  // On plain http (a school LAN), fall back to the older copy command.
  if (!copied) {
    const box = document.createElement("textarea");
    box.value = link;
    box.style.position = "fixed";
    box.style.opacity = "0";
    document.body.appendChild(box);
    box.select();
    try {
      copied = document.execCommand("copy");
    } catch (err) {
      copied = false;
    }
    box.remove();
  }

  // If even that failed, show the link so it can be copied by hand.
  setText("copy-feedback", copied ? "Copied!" : link);
  if (copied) {
    setTimeout(() => setText("copy-feedback", ""), 2000);
  }
}
