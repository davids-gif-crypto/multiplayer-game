// interp.js
// Smooth motion.
//
// The server sends 30 snapshots per second, but the screen draws 60 frames
// per second. If we just drew the newest snapshot, fighters would move in
// little jumps. Instead we keep the last few snapshots and draw the world
// as it was about 100 ms ago. At that moment we almost always have one
// snapshot just before and one just after, so we can blend ("interpolate")
// between them for perfectly smooth motion. The price is 100 ms of delay.

const TICK_MS = 1000 / 60;          // the server runs 60 ticks per second
const INTERP_DELAY_MS = 100;        // how far in the past we draw
const MAX_EXTRAPOLATE_MS = 100;     // how far we guess ahead if snapshots stop
const MAX_SNAPSHOTS = 20;           // about 0.66 seconds of history
const TELEPORT_DISTANCE = 250;      // a bigger jump than this is a teleport, so snap

// Each entry: { time: server time in ms, players: [...] }
const buffer = [];

// localTime - serverTime, worked out from when snapshots arrive.
let clockOffset = null;

// Snapshot rate, for the debug overlay.
let arrivals = 0;
let rateWindowStart = 0;
export const interpStats = { snapshotsPerSecond: 0 };

// Forget everything (a new match is starting).
export function resetInterp() {
  buffer.length = 0;
  clockOffset = null;
}

// Stores one "state" message. localNow is performance.now().
export function addSnapshot(msg, localNow) {
  const serverTime = msg.tick * TICK_MS;

  // Estimate how server time lines up with our clock. Some snapshots are
  // delayed by the network, so we trust the fastest one we have seen
  // (the smallest offset), and only drift slowly toward slower ones.
  const offsetSample = localNow - serverTime;
  if (clockOffset === null || offsetSample < clockOffset) {
    clockOffset = offsetSample;
  } else {
    clockOffset += (offsetSample - clockOffset) * 0.02;
  }

  // A snapshot older than the newest one means something odd happened
  // (like a server restart). Start fresh.
  if (buffer.length > 0 && serverTime <= buffer[buffer.length - 1].time) {
    buffer.length = 0;
  }
  buffer.push({ time: serverTime, players: msg.players });
  if (buffer.length > MAX_SNAPSHOTS) {
    buffer.shift();
  }

  // Count snapshots per second.
  arrivals++;
  if (localNow - rateWindowStart >= 1000) {
    interpStats.snapshotsPerSecond = Math.round((arrivals * 1000) / (localNow - rateWindowStart));
    arrivals = 0;
    rateWindowStart = localNow;
  }
}

// Works out where every fighter should be drawn right now.
// out is a Map of player id to a view object. The objects are reused every
// frame, so drawing does not create garbage for the browser to clean up.
export function sampleFighters(localNow, out) {
  for (const view of out.values()) {
    view.seen = false;
  }
  if (buffer.length === 0) {
    return;
  }

  const renderTime = localNow - clockOffset - INTERP_DELAY_MS;
  const newest = buffer[buffer.length - 1];

  // Find the two snapshots around renderTime.
  let older = buffer[0];
  let newer = buffer[0];
  let blend = 0;
  let extrapolateSeconds = 0;

  if (renderTime >= newest.time) {
    // No snapshot that new yet: guess ahead a little using the speed.
    older = newest;
    newer = newest;
    extrapolateSeconds = Math.min(renderTime - newest.time, MAX_EXTRAPOLATE_MS) / 1000;
  } else if (renderTime > buffer[0].time) {
    for (let i = buffer.length - 2; i >= 0; i--) {
      if (buffer[i].time <= renderTime) {
        older = buffer[i];
        newer = buffer[i + 1];
        blend = (renderTime - older.time) / (newer.time - older.time);
        break;
      }
    }
  }

  for (const p of newer.players) {
    let view = out.get(p.id);
    if (view === undefined) {
      view = { id: p.id, x: 0, y: 0, face: 1, fighter: "", st: "", inv: 0, percent: 0, stocks: 0, visible: false, seen: false };
      out.set(p.id, view);
    }
    view.seen = true;
    view.face = p.face;
    view.fighter = p.fighter;
    view.st = p.st;
    view.inv = p.inv;
    view.percent = p.percent;
    view.stocks = p.stocks;
    view.visible = p.respawn === 0 && p.st !== "out" && p.st !== "dead";

    const before = findPlayer(older.players, p.id);
    if (extrapolateSeconds > 0) {
      view.x = p.x + p.vx * extrapolateSeconds;
      view.y = p.y + p.vy * extrapolateSeconds;
    } else if (before === null || shouldSnap(before, p)) {
      // Do not slide smoothly through a knockout or a respawn.
      view.x = p.x;
      view.y = p.y;
    } else {
      view.x = before.x + (p.x - before.x) * blend;
      view.y = before.y + (p.y - before.y) * blend;
    }
  }

  // Anyone not in the snapshot is not drawn.
  for (const view of out.values()) {
    if (!view.seen) {
      view.visible = false;
    }
  }
}

function findPlayer(players, id) {
  for (const p of players) {
    if (p.id === id) {
      return p;
    }
  }
  return null;
}

function shouldSnap(before, after) {
  if (before.respawn > 0 || after.respawn > 0) {
    return true; // knocked out or just respawned
  }
  const dx = after.x - before.x;
  const dy = after.y - before.y;
  return dx * dx + dy * dy > TELEPORT_DISTANCE * TELEPORT_DISTANCE;
}
