// render.js
// Drawing code. Everything is drawn with simple shapes, no images.
//
// Right now this file draws the three fighters, which the lobby uses for its
// preview cards. Milestone 2 adds the stage, the camera scaling and the rest.
//
// Positions follow the server: (x, y) is the fighter's feet, in the middle
// at the bottom. y grows DOWNWARD, like on every canvas.

// Draws one fighter.
//   type   "BRAWLER", "SPEEDSTER" or "ZONER"
//   x, y   where the feet are
//   face   1 to look right, -1 to look left
//   scale  1 is normal size
export function drawFighter(ctx, type, x, y, face, scale) {
  ctx.save();
  ctx.translate(x, y);
  // Flipping the x scale mirrors the drawing, so every fighter only needs
  // to be drawn facing right. Forward is always +x below.
  ctx.scale(face * scale, scale);
  ctx.lineJoin = "round";
  ctx.lineCap = "round";

  if (type === "BRAWLER") {
    drawMeatball(ctx);
  } else if (type === "SPEEDSTER") {
    drawFry(ctx);
  } else {
    drawKetchup(ctx);
  }
  ctx.restore();
}

// Meatball: brown ball, about 56 x 56.
function drawMeatball(ctx) {
  ctx.fillStyle = "#8a4b2a";
  ctx.strokeStyle = "#5b2e17";
  ctx.lineWidth = 3;
  ctx.beginPath();
  ctx.arc(0, -28, 27, 0, Math.PI * 2);
  ctx.fill();
  ctx.stroke();

  // Darker spots
  ctx.fillStyle = "#6b3519";
  circle(ctx, -13, -38, 5);
  circle(ctx, -15, -16, 4);
  circle(ctx, 4, -10, 3);
  circle(ctx, 18, -14, 3);

  // Eyes look forward
  eye(ctx, 3, -32, 6);
  eye(ctx, 17, -32, 6);

  // Angry brows: the inner ends are lower than the outer ends
  ctx.strokeStyle = "#2b140a";
  ctx.lineWidth = 3;
  line(ctx, -4, -44, 8, -39);
  line(ctx, 12, -39, 24, -44);
}

// French fry: tall yellow stick with 3 fry tips on top, about 36 x 72.
function drawFry(ctx) {
  ctx.fillStyle = "#f5c542";
  ctx.strokeStyle = "#c9971c";
  ctx.lineWidth = 2.5;

  // Three tips sticking out of the top
  roundRect(ctx, -16, -72, 9, 20, 3);
  ctx.fill();
  ctx.stroke();
  roundRect(ctx, -5, -68, 9, 16, 3);
  ctx.fill();
  ctx.stroke();
  roundRect(ctx, 6, -71, 9, 19, 3);
  ctx.fill();
  ctx.stroke();

  // Main body
  roundRect(ctx, -17, -58, 34, 58, 7);
  ctx.fill();
  ctx.stroke();

  eye(ctx, 0, -42, 5);
  eye(ctx, 11, -42, 5);

  // Little smile
  ctx.strokeStyle = "#8a5a00";
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.arc(6, -30, 5, 0.2, Math.PI - 0.2);
  ctx.stroke();
}

// Ketchup bottle: red bottle with white cap and nozzle, about 44 x 76.
function drawKetchup(ctx) {
  // Nozzle and cap
  ctx.fillStyle = "#ffffff";
  ctx.strokeStyle = "#9a9a9a";
  ctx.lineWidth = 2;
  roundRect(ctx, -4, -78, 8, 10, 2);
  ctx.fill();
  ctx.stroke();
  roundRect(ctx, -13, -70, 26, 12, 3);
  ctx.fill();
  ctx.stroke();

  // Bottle
  ctx.fillStyle = "#d62828";
  ctx.strokeStyle = "#8f1b1b";
  ctx.lineWidth = 3;
  roundRect(ctx, -21, -60, 42, 60, 12);
  ctx.fill();
  ctx.stroke();

  // Cream label
  ctx.fillStyle = "#fff1d0";
  roundRect(ctx, -17, -32, 34, 20, 4);
  ctx.fill();
  ctx.fillStyle = "#d62828";
  ctx.fillRect(-11, -24, 22, 4);

  eye(ctx, 1, -45, 6);
  eye(ctx, 14, -45, 6);
}

// ---------- Small shape helpers ----------

function circle(ctx, x, y, r) {
  ctx.beginPath();
  ctx.arc(x, y, r, 0, Math.PI * 2);
  ctx.fill();
}

function line(ctx, x1, y1, x2, y2) {
  ctx.beginPath();
  ctx.moveTo(x1, y1);
  ctx.lineTo(x2, y2);
  ctx.stroke();
}

// A white eye with a black pupil shifted forward (toward +x).
function eye(ctx, x, y, r) {
  ctx.fillStyle = "#ffffff";
  circle(ctx, x, y, r);
  ctx.fillStyle = "#1a1a1a";
  circle(ctx, x + r * 0.35, y, r * 0.5);
}

// Makes a rounded rectangle path. We write our own instead of using
// ctx.roundRect so it also works in older browsers.
export function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}
