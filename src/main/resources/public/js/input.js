// input.js
// Reads the keyboard during a match and tells the server which keys are held.
//
// We send a message whenever the held keys change, and also every 100 ms as a
// "heartbeat", so the server knows we are still here. If the server hears
// nothing for 2 seconds it lets go of our keys for us.

import { send } from "./net.js";

// event.code names the physical key, so this works on any keyboard layout.
// Several keys can do the same thing (A and the left arrow both mean "left").
const KEY_TO_ACTION = {
  KeyA: "left",
  ArrowLeft: "left",
  KeyD: "right",
  ArrowRight: "right",
  KeyW: "jump",
  Space: "jump",
  ArrowUp: "jump",
  KeyS: "down",
  ArrowDown: "down",
  KeyJ: "attack",
  KeyK: "special",
  KeyL: "shield",
};

const ACTIONS = ["left", "right", "jump", "down", "attack", "special", "shield"];
const HEARTBEAT_MS = 100;

// What the server currently thinks we are holding.
const held = { left: false, right: false, jump: false, down: false, attack: false, special: false, shield: false };

// Physical keys that are down right now, like "KeyA" or "Space".
const pressedKeys = new Set();

let active = false;
let seq = 0;
let heartbeatTimer = null;

// Start listening (called when the match screen opens).
export function enableInput() {
  if (active) {
    return;
  }
  active = true;
  pressedKeys.clear();
  updateHeld();
  sendInput();
  heartbeatTimer = setInterval(sendInput, HEARTBEAT_MS);
}

// Stop listening (called when the match screen closes).
export function disableInput() {
  if (!active) {
    return;
  }
  releaseAll();
  active = false;
  clearInterval(heartbeatTimer);
  heartbeatTimer = null;
}

window.addEventListener("keydown", (event) => {
  if (!active) {
    return; // outside a match, keys behave normally (for typing names)
  }
  const action = KEY_TO_ACTION[event.code];
  if (action === undefined) {
    return;
  }
  // Stop Space from scrolling the page, arrows from moving things, and so on.
  event.preventDefault();
  // Holding a key down makes the browser repeat keydown. We only care about
  // the first one, since we track "held" ourselves.
  if (event.repeat) {
    return;
  }
  pressedKeys.add(event.code);
  updateHeld();
});

window.addEventListener("keyup", (event) => {
  if (!active || KEY_TO_ACTION[event.code] === undefined) {
    return;
  }
  event.preventDefault();
  pressedKeys.delete(event.code);
  updateHeld();
});

// If the window loses focus (alt tab, clicking another app, a popup), the
// browser never tells us about keyup. Let go of everything so the fighter
// does not keep running by itself.
window.addEventListener("blur", releaseAll);
document.addEventListener("visibilitychange", () => {
  if (document.hidden) {
    releaseAll();
  }
});

function releaseAll() {
  if (!active) {
    return;
  }
  pressedKeys.clear();
  updateHeld();
}

// Works out each action from the pressed keys, and sends if anything changed.
function updateHeld() {
  let changed = false;
  for (const action of ACTIONS) {
    let isDown = false;
    for (const code of pressedKeys) {
      if (KEY_TO_ACTION[code] === action) {
        isDown = true;
        break;
      }
    }
    if (held[action] !== isDown) {
      held[action] = isDown;
      changed = true;
    }
  }
  if (changed) {
    sendInput();
  }
}

function sendInput() {
  seq++;
  send({
    t: "input",
    seq: seq,
    left: held.left,
    right: held.right,
    jump: held.jump,
    down: held.down,
    attack: held.attack,
    special: held.special,
    shield: held.shield,
  });
}
