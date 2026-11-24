package com.foodfight.sim;

import java.util.ArrayList;
import java.util.List;

import static com.foodfight.sim.MathUtil.approach;

/**
 * The game simulation for one match. Plain Java: no networking, no threads,
 * no web code. The room calls {@link #step()} 60 times per second, and each
 * step moves every fighter forward by 1/60 of a second.
 *
 * Order of one step for each fighter:
 * 1. Count down timers (respawn, invulnerability, drop through).
 * 2. Read the held keys: run, jump, drop through (skipped during hitstun).
 * 3. Apply gravity.
 * 4. Move, then land on platforms or bump into the tray from below.
 * 5. Check the blast zone. Flying out costs a stock.
 */
public class GameWorld {

    private static final double DT = Balance.DT;

    private final Stage stage;
    private final List<Fighter> fighters = new ArrayList<>();
    private final List<GameEvent> events = new ArrayList<>();
    private long tick = 0;

    public GameWorld(Stage stage) {
        this.stage = stage;
    }

    /**
     * Adds a fighter at the next free spawn point, facing the middle.
     * It starts exactly on the tray and counts as standing after the first step.
     */
    public Fighter addFighter(int id, FighterType type) {
        double x = stage.spawnX(fighters.size());
        Fighter fighter = new Fighter(id, type, x, stage.spawnY());
        fighter.face = (x < stage.centerX()) ? 1 : -1;
        fighters.add(fighter);
        return fighter;
    }

    /** Sets the keys a player is holding. Unknown ids are ignored. */
    public void setInput(int id, InputState input) {
        Fighter fighter = fighter(id);
        if (fighter != null) {
            fighter.input = input;
        }
    }

    /** Runs one tick (1/60 of a second) of the simulation. */
    public void step() {
        tick++;
        for (Fighter fighter : fighters) {
            updateFighter(fighter);
        }
    }

    // =================================================================
    // One fighter, one tick
    // =================================================================

    private void updateFighter(Fighter f) {
        if (f.isOut()) {
            return; // spectators do nothing
        }

        boolean jumpPressed = f.input.jump() && !f.jumpWasHeld;
        f.jumpWasHeld = f.input.jump();

        // Knocked out and waiting to come back?
        if (f.respawnTicks > 0) {
            f.respawnTicks--;
            if (f.respawnTicks == 0) {
                respawn(f);
            }
            return;
        }

        // 1. Timers
        if (f.invulnerableTicks > 0) {
            f.invulnerableTicks--;
        }
        if (f.dropThroughTicks > 0) {
            f.dropThroughTicks--;
        }

        // 2. Controls. In hitstun the player has no control at all and just
        // keeps flying, slowed a little by air drag.
        if (f.hitstunTicks > 0) {
            f.hitstunTicks--;
            f.vx *= 1 - Balance.HITSTUN_AIR_DRAG * DT;
        } else {
            runControl(f);
            if (jumpPressed) {
                tryJump(f);
            } else if (f.input.down()) {
                tryDropThrough(f);
            }
        }

        // 3. Gravity
        applyGravity(f);

        // 4. Move and collide
        moveAndCollide(f);

        // 5. Blast zone
        checkBlastZone(f);
    }

    /** Left and right: speed up toward run speed, or slow down if nothing is held. */
    private void runControl(Fighter f) {
        int direction = (f.input.right() ? 1 : 0) - (f.input.left() ? 1 : 0);
        if (direction != 0) {
            f.face = direction;
            double accel = f.onGround ? Balance.GROUND_ACCEL : Balance.AIR_ACCEL;
            f.vx = approach(f.vx, direction * f.type.runSpeed, accel * DT);
        } else {
            double decel = f.onGround ? Balance.GROUND_DECEL : Balance.AIR_DECEL;
            f.vx = approach(f.vx, 0, decel * DT);
        }
    }

    /**
     * Jump if any jumps are left. Walking off a ledge does not use a jump, so
     * the first jump in the air after walking off is a full first jump.
     */
    private void tryJump(Fighter f) {
        if (f.jumpsUsed >= Balance.MAX_JUMPS) {
            return;
        }
        f.vy = -f.type.jumpSpeed(f.jumpsUsed);
        f.jumpsUsed++;
        f.onGround = false;
        f.standingOn = null;
        events.add(new GameEvent.Jump(f.id, f.x, f.y));
    }

    /** Holding Down while standing on a bread slice drops you through it. */
    private void tryDropThrough(Fighter f) {
        if (f.onGround && f.standingOn != null && f.standingOn.oneWay()) {
            f.ignoredPlatform = f.standingOn;
            f.dropThroughTicks = Balance.DROP_THROUGH_TICKS;
            f.onGround = false;
            f.standingOn = null;
        }
    }

    private void applyGravity(Fighter f) {
        f.vy += Balance.GRAVITY * DT;
        boolean fastFalling = f.input.down() && f.hitstunTicks == 0;
        double maxFall = fastFalling ? Balance.FAST_FALL_SPEED : Balance.MAX_FALL_SPEED;
        if (f.vy > maxFall) {
            f.vy = maxFall;
        }
    }

    /**
     * Moves the fighter by its speed, then checks the platforms.
     *
     * Landing: only while moving down, and only if the feet were at or above
     * the platform top before the move and at or below it after. That is how
     * one way platforms work: coming up from below, the feet start below the
     * top, so there is no landing.
     *
     * The feet (the middle of the bottom) must be over the platform, so a
     * fighter can hang half over the edge before falling off.
     *
     * Standing still works the same way: gravity pulls the fighter a tiny
     * bit into the platform every tick, and it lands again.
     */
    private void moveAndCollide(Fighter f) {
        double oldFeet = f.y;
        double oldHead = f.y - f.type.height;

        f.x += f.vx * DT;
        f.y += f.vy * DT;
        f.onGround = false;
        f.standingOn = null;

        Stage.Platform landOn = null;
        for (Stage.Platform p : stage.platforms()) {
            boolean overPlatform = f.x >= p.left() && f.x <= p.right();
            if (!overPlatform) {
                continue;
            }

            boolean ignoring = p == f.ignoredPlatform && f.dropThroughTicks > 0;
            boolean crossedTop = f.vy >= 0 && oldFeet <= p.top() && f.y >= p.top();
            if (crossedTop && !ignoring) {
                // If we somehow crossed two platforms at once, land on the higher one.
                if (landOn == null || p.top() < landOn.top()) {
                    landOn = p;
                }
            }

            // The tray is solid: bump your head on its underside.
            double head = f.y - f.type.height;
            boolean crossedBottom = f.vy < 0 && oldHead >= p.bottom() && head < p.bottom();
            if (!p.oneWay() && crossedBottom) {
                f.y = p.bottom() + f.type.height;
                f.vy = 0;
            }
        }

        if (landOn != null) {
            f.y = landOn.top();
            f.vy = 0;
            f.onGround = true;
            f.standingOn = landOn;
            f.jumpsUsed = 0;
        }
    }

    /** Losing a stock happens when the fighter's middle leaves the blast zone. */
    private void checkBlastZone(Fighter f) {
        double centerX = f.x;
        double centerY = f.y - f.type.height / 2;
        Rect zone = stage.blastZone();

        String side = null;
        if (centerX < zone.left()) {
            side = "left";
        } else if (centerX > zone.right()) {
            side = "right";
        } else if (centerY < zone.top()) {
            side = "up";
        } else if (centerY > zone.bottom()) {
            side = "down";
        }
        if (side != null) {
            knockOut(f, side, centerX, centerY);
        }
    }

    private void knockOut(Fighter f, String side, double centerX, double centerY) {
        f.stocks--;
        f.falls++;
        f.vx = 0;
        f.vy = 0;
        f.hitstunTicks = 0;
        events.add(new GameEvent.Ko(f.id, centerX, centerY, side));

        if (f.stocks > 0) {
            f.respawnTicks = Balance.RESPAWN_DELAY_TICKS;
        } else {
            f.eliminatedAtTick = tick;
        }
    }

    /** Back on stage: above the middle, damage reset, briefly invulnerable. */
    private void respawn(Fighter f) {
        f.x = stage.respawnX();
        f.y = stage.respawnY();
        f.vx = 0;
        f.vy = 0;
        f.percent = 0;
        f.invulnerableTicks = Balance.RESPAWN_INVULNERABLE_TICKS;
        f.jumpsUsed = 0;
        f.onGround = false;
        f.standingOn = null;
        f.dropThroughTicks = 0;
        f.hitstunTicks = 0;
        events.add(new GameEvent.Respawn(f.id));
    }

    // =================================================================
    // Reading the world
    // =================================================================

    public List<Fighter> fighters() {
        return fighters;
    }

    public Fighter fighter(int id) {
        for (Fighter f : fighters) {
            if (f.id == id) {
                return f;
            }
        }
        return null;
    }

    /** Hands over the events since the last call, and starts a fresh list. */
    public List<GameEvent> takeEvents() {
        List<GameEvent> taken = new ArrayList<>(events);
        events.clear();
        return taken;
    }

    public long tick() {
        return tick;
    }

    public Stage stage() {
        return stage;
    }
}
