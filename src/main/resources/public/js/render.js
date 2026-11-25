// render.js
// Drawing code. Everything is drawn with simple shapes, no images.
//
// The game world is always 1280 x 720 units. We scale it to fit the window
// and add dark bars on the sides (letterboxing) so it never gets stretched.
//
// Positions follow the server: (x, y) is the fighter's feet, in the middle
// at the bottom. y grows DOWNWARD, like on every canvas.

export const WORLD_WIDTH = 1280;
export const WORLD_HEIGHT = 720;

// Sharp screens have more pixels per CSS pixel (the device pixel ratio).
// Drawing all of them is slow on a Chromebook, so we stop at 1.5.
const MAX_PIXEL_RATIO = 1.5;
const LETTERBOX_COLOR = "#2b1d12";

// How tall each fighter is drawn, so name tags sit just above the head.
const FIGHTER_HEIGHT = { BRAWLER: 56, SPEEDSTER: 72, ZONER: 78 };

// The stage layout. These numbers must match Stage.java on the server.
const TRAY = { x: 240, y: 520, w: 800, h: 40 };
const BREADS = [
  { x: 300, y: 360, w: 220, h: 24 },
  { x: 760, y: 360, w: 220, h: 24 },
];

let canvas = null;
let ctx = null;
let background = null;   // the stage drawn once, then copied every frame
let pixelRatio = 1;
let worldScale = 1;      // CSS pixels per world unit
let offsetX = 0;         // where the world starts inside the window, in CSS pixels
let offsetY = 0;
const tagWidths = new Map(); // name tag widths, measured once per name

export function initRenderer(canvasElement) {
  canvas = canvasElement;
  ctx = canvas.getContext("2d");
  window.addEventListener("resize", resizeRenderer);
}

// Fits the canvas to the window. Called on resize and when a match starts.
export function resizeRenderer() {
  const cssWidth = window.innerWidth;
  const cssHeight = window.innerHeight;
  pixelRatio = Math.min(window.devicePixelRatio || 1, MAX_PIXEL_RATIO);

  canvas.width = Math.round(cssWidth * pixelRatio);
  canvas.height = Math.round(cssHeight * pixelRatio);
  canvas.style.width = cssWidth + "px";
  canvas.style.height = cssHeight + "px";

  // Biggest scale that still fits both ways, then center the leftover space.
  worldScale = Math.min(cssWidth / WORLD_WIDTH, cssHeight / WORLD_HEIGHT);
  offsetX = (cssWidth - WORLD_WIDTH * worldScale) / 2;
  offsetY = (cssHeight - WORLD_HEIGHT * worldScale) / 2;

  buildBackground();
}

// Draws the parts that never move (wall, table, tray, bread) onto a hidden
// canvas once. Each frame we copy it in one fast drawImage call instead of
// drawing all those shapes again.
function buildBackground() {
  const k = worldScale * pixelRatio;
  background = document.createElement("canvas");
  background.width = Math.ceil(WORLD_WIDTH * k);
  background.height = Math.ceil(WORLD_HEIGHT * k);
  const bg = background.getContext("2d");
  bg.scale(k, k);

  drawWall(bg);
  drawTable(bg);
  drawTray(bg);
  for (const bread of BREADS) {
    drawBread(bg, bread);
  }
}

// Draws one frame.
//   scene.fighters  Map of id to fighter views from interp.js
//   scene.globs     Map of id to glob views from interp.js
//   scene.roster    Map of id to { name, color }
//   scene.myId      my player id, so my tag stands out and I am drawn on top
//   now             time in ms, used for blinking
//   dt              seconds since the last frame, used to move particles
export function drawFrame(scene, now, dt) {
  // Letterbox bars, then the pre-drawn stage.
  ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.fillStyle = LETTERBOX_COLOR;
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.drawImage(background, Math.round(offsetX * pixelRatio), Math.round(offsetY * pixelRatio));

  // From here on we draw in world units.
  const k = worldScale * pixelRatio;
  ctx.setTransform(k, 0, 0, k, offsetX * pixelRatio, offsetY * pixelRatio);

  // Everyone else first, then me, so I am never hidden behind someone.
  for (const view of scene.fighters.values()) {
    if (view.visible && view.id !== scene.myId) {
      drawPlayer(view, scene.roster.get(view.id), false, now);
    }
  }
  const me = scene.fighters.get(scene.myId);
  if (me !== undefined && me.visible) {
    drawPlayer(me, scene.roster.get(scene.myId), true, now);
  }

  // Globs and splats go on top of the fighters.
  for (const glob of scene.globs.values()) {
    if (glob.visible) {
      drawGlob(glob);
    }
  }
  updateAndDrawParticles(dt);
}

function drawPlayer(view, info, isMe, now) {
  // Blink while invulnerable after respawning.
  const blinkOff = view.inv > 0 && Math.floor(now / 100) % 2 === 0;
  ctx.globalAlpha = blinkOff ? 0.35 : 1;

  // Lean the whole fighter a little, turning around its middle:
  // back while winding up, forward while hitting, and toward the
  // direction of the launch while in hitstun.
  const middleY = view.y - (FIGHTER_HEIGHT[view.fighter] || 70) / 2;
  let tilt = 0;
  if (view.st === "hitstun") {
    tilt = view.vx >= 0 ? 0.35 : -0.35;
  } else if (view.st === "startup") {
    tilt = -0.12 * view.face;
  } else if (view.st === "active") {
    tilt = 0.1 * view.face;
  }
  ctx.save();
  if (tilt !== 0) {
    ctx.translate(view.x, middleY);
    ctx.rotate(tilt);
    ctx.translate(-view.x, -middleY);
  }
  drawFighter(ctx, view.fighter, view.x, view.y, view.face, 1);

  // A white flash for a few frames right after being hit.
  if (view.flash > 0) {
    drawFlash(view.fighter, view.x, view.y, view.face);
    view.flash--;
  }
  ctx.restore();
  ctx.globalAlpha = 1;

  if (view.st === "active") {
    drawSwoosh(view);
  }

  if (info !== undefined) {
    const headY = view.y - (FIGHTER_HEIGHT[view.fighter] || 70);
    drawNameTag(info.name, info.color, view.x, headY, isMe);
  }
}

// The swing or jab, drawn where its hitbox is (see Balance.java).
function drawSwoosh(view) {
  ctx.save();
  ctx.translate(view.x, view.y);
  ctx.scale(view.face, 1); // forward is always +x from here
  ctx.lineCap = "round";
  if (view.fighter === "BRAWLER") {
    // A wide arc in front of the meatball.
    ctx.strokeStyle = "rgba(255, 255, 255, 0.85)";
    ctx.lineWidth = 10;
    ctx.beginPath();
    ctx.arc(28, -28, 48, -1.0, 1.0);
    ctx.stroke();
    ctx.strokeStyle = "rgba(138, 75, 42, 0.6)";
    ctx.lineWidth = 3;
    ctx.beginPath();
    ctx.arc(28, -28, 48, -0.8, 0.8);
    ctx.stroke();
  } else if (view.fighter === "SPEEDSTER") {
    // A quick straight jab.
    ctx.strokeStyle = "rgba(255, 255, 255, 0.9)";
    ctx.lineWidth = 8;
    line(ctx, 14, -36, 56, -36);
    ctx.fillStyle = "#f5c542";
    circle(ctx, 58, -36, 7);
  }
  ctx.restore();
}

// A white shape over the fighter's body.
function drawFlash(type, x, y, face) {
  ctx.save();
  ctx.translate(x, y);
  ctx.scale(face, 1);
  ctx.fillStyle = "rgba(255, 255, 255, 0.8)";
  if (type === "BRAWLER") {
    circle(ctx, 0, -28, 29);
  } else if (type === "SPEEDSTER") {
    roundRect(ctx, -18, -73, 36, 73, 7);
    ctx.fill();
  } else {
    roundRect(ctx, -22, -79, 44, 79, 12);
    ctx.fill();
  }
  ctx.restore();
}

// A ketchup glob: a red ball with a short fading trail behind it.
function drawGlob(glob) {
  const back = glob.vx >= 0 ? -1 : 1;
  ctx.fillStyle = "rgba(214, 40, 40, 0.3)";
  circle(ctx, glob.x + back * 22, glob.y, 7);
  ctx.fillStyle = "rgba(214, 40, 40, 0.55)";
  circle(ctx, glob.x + back * 12, glob.y, 10);
  ctx.fillStyle = "#d62828";
  circle(ctx, glob.x, glob.y, 14);
  ctx.fillStyle = "rgba(255, 255, 255, 0.6)";
  circle(ctx, glob.x - back * 4, glob.y - 5, 4);
}

// ---------- Splats (a small particle system) ----------
//
// All 80 particles are made once, up front. A splat grabs free ones and
// "kills" them when they fade out. Nothing new is created while playing,
// which keeps the frame rate steady on slow computers.

const MAX_PARTICLES = 80;
const SPLAT_SECONDS = 0.3;
const STAR = 0;
const DROP = 1;
const particles = [];
for (let i = 0; i < MAX_PARTICLES; i++) {
  particles.push({ alive: false, kind: DROP, x: 0, y: 0, vx: 0, vy: 0, age: 0, size: 0, spin: 0, color: "" });
}

// A splat where a hit landed: a bright star and a ring of sauce droplets.
export function spawnSplat(x, y, big) {
  const scale = big ? 1.7 : 1;
  addParticle(STAR, x, y, 0, 0, 18 * scale, "#fff3b0");
  const drops = big ? 8 : 6;
  for (let i = 0; i < drops; i++) {
    const angle = (i / drops) * Math.PI * 2 + Math.random() * 0.5;
    const speed = (180 + Math.random() * 160) * scale;
    const color = i % 2 === 0 ? "#e8432e" : "#c0392b";
    addParticle(DROP, x, y, Math.cos(angle) * speed, Math.sin(angle) * speed - 80, (4 + Math.random() * 3) * scale, color);
  }
}

function addParticle(kind, x, y, vx, vy, size, color) {
  for (const p of particles) {
    if (!p.alive) {
      p.alive = true;
      p.kind = kind;
      p.x = x;
      p.y = y;
      p.vx = vx;
      p.vy = vy;
      p.age = 0;
      p.size = size;
      p.spin = Math.random() * Math.PI;
      p.color = color;
      return;
    }
  }
  // All 80 are busy: skip this one. Nobody notices one missing droplet.
}

function updateAndDrawParticles(dt) {
  for (const p of particles) {
    if (!p.alive) {
      continue;
    }
    p.age += dt;
    if (p.age >= SPLAT_SECONDS) {
      p.alive = false;
      continue;
    }
    p.vy += 900 * dt; // droplets fall
    p.x += p.vx * dt;
    p.y += p.vy * dt;

    ctx.globalAlpha = 1 - p.age / SPLAT_SECONDS; // fade out
    ctx.fillStyle = p.color;
    if (p.kind === STAR) {
      drawStar(p.x, p.y, p.size, p.spin);
    } else {
      circle(ctx, p.x, p.y, p.size);
    }
  }
  ctx.globalAlpha = 1;
}

// An 8 pointed star, for the flash of a hit.
function drawStar(x, y, radius, spin) {
  ctx.beginPath();
  for (let i = 0; i < 16; i++) {
    const r = i % 2 === 0 ? radius : radius * 0.45;
    const a = spin + (i / 16) * Math.PI * 2;
    const px = x + Math.cos(a) * r;
    const py = y + Math.sin(a) * r;
    if (i === 0) {
      ctx.moveTo(px, py);
    } else {
      ctx.lineTo(px, py);
    }
  }
  ctx.closePath();
  ctx.fill();
}

// A colored tag with the nickname, and a small triangle pointing at the head.
function drawNameTag(name, color, x, headY, isMe) {
  ctx.font = "bold 15px system-ui, -apple-system, 'Segoe UI', Roboto, Arial, sans-serif";
  let width = tagWidths.get(name);
  if (width === undefined) {
    width = ctx.measureText(name).width + 14;
    tagWidths.set(name, width);
  }
  const tagBottom = headY - 14;
  const tagTop = tagBottom - 22;

  // Triangle
  ctx.fillStyle = color;
  ctx.beginPath();
  ctx.moveTo(x - 6, tagBottom - 1);
  ctx.lineTo(x + 6, tagBottom - 1);
  ctx.lineTo(x, headY - 5);
  ctx.closePath();
  ctx.fill();

  // Tag
  roundRect(ctx, x - width / 2, tagTop, width, 22, 6);
  ctx.fill();
  if (isMe) {
    ctx.strokeStyle = "#fff35c";
    ctx.lineWidth = 3;
    ctx.stroke();
  }

  ctx.fillStyle = "#ffffff";
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText(name, x, tagTop + 11.5);
}

// ---------- The stage (drawn once into the background) ----------

function drawWall(g) {
  g.fillStyle = "#f4ead5";
  g.fillRect(0, 0, WORLD_WIDTH, WORLD_HEIGHT);

  // Faint wall tiles
  g.strokeStyle = "#eadcc0";
  g.lineWidth = 2;
  for (let x = 0; x <= WORLD_WIDTH; x += 80) {
    line(g, x, 0, x, 620);
  }
  for (let y = 0; y <= 620; y += 80) {
    line(g, 0, y, WORLD_WIDTH, y);
  }

  // A green stripe, like a school cafeteria wall
  g.fillStyle = "#cfe3c3";
  g.fillRect(0, 200, WORLD_WIDTH, 22);
  g.fillStyle = "#b8d4a8";
  g.fillRect(0, 222, WORLD_WIDTH, 4);
}

function drawTable(g) {
  g.fillStyle = "#c8995f";
  g.fillRect(0, 620, WORLD_WIDTH, 100);
  g.fillStyle = "#ddb27a";
  g.fillRect(0, 620, WORLD_WIDTH, 10);
  g.fillStyle = "#a87d47";
  g.fillRect(0, 618, WORLD_WIDTH, 3);

  // Soft shadow of the tray on the table
  g.fillStyle = "rgba(0, 0, 0, 0.12)";
  g.beginPath();
  g.ellipse(640, 642, 400, 12, 0, 0, Math.PI * 2);
  g.fill();
}

function drawTray(g) {
  // Body
  g.fillStyle = "#6f87a0";
  roundRect(g, TRAY.x, TRAY.y, TRAY.w, TRAY.h, 10);
  g.fill();
  // Lighter top surface, where fighters stand
  g.fillStyle = "#a9bccf";
  roundRect(g, TRAY.x, TRAY.y, TRAY.w, 9, 4);
  g.fill();
  // Darker compartments on the front face
  g.fillStyle = "#566d85";
  for (let i = 0; i < 4; i++) {
    roundRect(g, TRAY.x + 30 + i * 190, TRAY.y + 16, 170, 16, 6);
    g.fill();
  }
}

function drawBread(g, b) {
  // Crust: two bumps on top, then the slab
  g.fillStyle = "#c98b4a";
  circle(g, b.x + 26, b.y + 8, 16);
  circle(g, b.x + b.w - 26, b.y + 8, 16);
  roundRect(g, b.x, b.y, b.w, b.h, 10);
  g.fill();
  // Lighter crumb inside
  g.fillStyle = "#f3d9a4";
  circle(g, b.x + 26, b.y + 9, 11);
  circle(g, b.x + b.w - 26, b.y + 9, 11);
  roundRect(g, b.x + 5, b.y + 5, b.w - 10, b.h - 10, 6);
  g.fill();
}

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
