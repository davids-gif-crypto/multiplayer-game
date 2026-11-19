// net.js
// The one WebSocket connection between this browser and the game server.
// Other files call send() to talk to the server, and get messages through
// the onMessage function they pass to connect().

// How often we send a keep alive ping (20 seconds). Free hosting services
// close sockets that are silent for too long.
const PING_EVERY_MS = 20000;

let socket = null;
let pingTimer = null;
let pingSentAt = 0;

// Round trip time of the last ping, in milliseconds. The debug overlay shows it.
export const netStats = { pingMs: 0 };

// Opens the connection. The callbacks are:
//   onOpen()          the socket is ready
//   onMessage(msg)    a message arrived (already turned from JSON into an object)
//   onClose()         a working connection dropped
//   onFail()          we could not connect at all (maybe the network blocks it)
export function connect({ onOpen, onMessage, onClose, onFail }) {
  // Build the address from the page address, so the same code works on
  // localhost, on a school network (ws://) and on Render (wss://).
  const url = (location.protocol === "https:" ? "wss://" : "ws://") + location.host + "/ws";
  let opened = false;

  try {
    socket = new WebSocket(url);
  } catch (err) {
    onFail();
    return;
  }

  socket.addEventListener("open", () => {
    opened = true;
    startPings();
    onOpen();
  });

  socket.addEventListener("message", (event) => {
    let msg;
    try {
      msg = JSON.parse(event.data);
    } catch (err) {
      return; // not JSON, ignore it
    }
    if (msg.t === "pong") {
      netStats.pingMs = Math.round(performance.now() - pingSentAt);
      return;
    }
    onMessage(msg);
  });

  socket.addEventListener("close", () => {
    stopPings();
    if (opened) {
      onClose();
    } else {
      onFail();
    }
  });
}

// Sends one message object as JSON. Does nothing if the socket is not open.
export function send(msg) {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify(msg));
  }
}

export function isOpen() {
  return socket !== null && socket.readyState === WebSocket.OPEN;
}

function startPings() {
  stopPings();
  pingTimer = setInterval(() => {
    pingSentAt = performance.now();
    send({ t: "ping" });
  }, PING_EVERY_MS);
}

function stopPings() {
  if (pingTimer !== null) {
    clearInterval(pingTimer);
    pingTimer = null;
  }
}
