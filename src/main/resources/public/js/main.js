// main.js
// Milestone 0: connect to the server's WebSocket and test the echo.
// In later milestones this file becomes the starting point that wires
// together the network, input, lobby and game modules.

const statusEl = document.getElementById("status");
const buttonEl = document.getElementById("echo-button");
const resultEl = document.getElementById("echo-result");

// Build the WebSocket address from the page address, so the same code
// works on localhost, on a school network, and on Render (which uses https).
const wsUrl = (location.protocol === "https:" ? "wss://" : "ws://") + location.host + "/ws";
const socket = new WebSocket(wsUrl);

socket.addEventListener("open", () => {
  statusEl.textContent = "connected";
  buttonEl.disabled = false;
});

socket.addEventListener("message", (event) => {
  // The M0 server sends our own message back. textContent (not innerHTML)
  // makes sure text is shown as plain text and never run as code.
  resultEl.textContent = "Server echoed: " + event.data;
});

socket.addEventListener("close", () => {
  statusEl.textContent = "disconnected (reload the page to try again)";
  buttonEl.disabled = true;
});

socket.addEventListener("error", () => {
  statusEl.textContent = "could not connect (the network may block WebSockets)";
});

buttonEl.addEventListener("click", () => {
  socket.send(JSON.stringify({ t: "ping", at: Date.now() }));
});
