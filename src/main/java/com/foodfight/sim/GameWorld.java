package com.foodfight.sim;

import java.util.ArrayList;
import java.util.List;

import static com.foodfight.sim.MathUtil.approach;

/**
 * The game simulation for one match. Plain Java: no networking, no threads,
 * no web code. The room calls {@link #step()} 60 times per second, and each
 * step moves everything forward by 1/60 of a second.
 *
 * One step has three parts:
 *   A. Update each fighter:
 *      1. Count down timers (respawn, invulnerability, attack, cooldowns).
 *      2. Read the held keys: run, jump, drop through, attack
 *         (all skipped during hitstun).
 *      3. Apply gravity.
 *      4. Move, then land on platforms or bump into the tray from below.
 *      5. Check the blast zone. Flying out costs a stock.
 *   B. Move the ketchup globs.
 *   C. Find every hit, then apply them all. Finding them all first is fair:
 *      if two fighters hit each other on the same tick, both get hit.
 */
public class GameWorld {

    private static final double DT = Balance.DT;

    /** A hit that was found in part C but not applied yet. */
    private record PendingHit(Fighter target, AttackStats attack, int damage, int direction,
                              int attackerId, double x, double y) {
    }

    private final Stage stage;
    private final List<Fighter> fighters = new ArrayList<>();
    private final List<Projectile> projectiles = new ArrayList<>();
    private final List<GameEvent> events = new ArrayList<>();
    private long tick = 0;
    private int nextProjectileId = 1;

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

    /**
     * Sets the keys a player is holding. Unknown ids are ignored.
     * Jump and attack only do something at the moment they are pressed
     * (holding them does not repeat), so presses are noted here, as they
     * arrive, and used up by the next tick.
     */
    public void setInput(int id, InputState input) {
        Fighter fighter = fighter(id);
        if (fighter == null) {
            return;
        }
        if (input.jump() && !fighter.input.jump()) {
            fighter.jumpTapped = true;
        }
        if (input.attack() && !fighter.input.attack()) {
            fighter.attackTapped = true;
        }
        if (input.special() && !fighter.input.special()) {
            fighter.specialTapped = true;
        }
        fighter.input = input;
    }

    /** Runs one tick (1/60 of a second) of the simulation. */
    public void step() {
        tick++;
        for (Fighter fighter : fighters) {
            updateFighter(fighter);   // part A
        }
        updateProjectiles();          // part B
        resolveHits();                // part C
        projectiles.removeIf(p -> !p.alive);
    }

    // =================================================================
    // Part A: one fighter, one tick
    // =================================================================

    private void updateFighter(Fighter f) {
        // Use up the key presses noted by setInput. A press only counts on
        // the tick right after it, so holding a key does not jump or attack
        // over and over, and a press during hitstun is simply lost.
        boolean jumpPressed = f.jumpTapped;
        boolean attackPressed = f.attackTapped;
        boolean specialPressed = f.specialTapped;
        f.jumpTapped = false;
        f.attackTapped = false;
        f.specialTapped = false;

        if (f.isOut()) {
            return; // spectators do nothing
        }

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
        if (f.globCooldownTicks > 0) {
            f.globCooldownTicks--;
        }
        if (f.attacking) {
            f.attackTicks++;
            if (f.attackTicks > f.type.attack.totalTicks()) {
                f.attacking = false; // the attack is over
            }
        }

        // 2. Controls. In hitstun the player has no control at all and just
        // keeps flying, slowed a little by air drag (or by friction if they
        // are sliding along the ground).
        if (f.hitstunTicks > 0) {
            f.hitstunTicks--;
            if (f.onGround) {
                f.vx = approach(f.vx, 0, Balance.GROUND_DECEL * DT);
            } else {
                f.vx *= 1 - Balance.HITSTUN_AIR_DRAG * DT;
            }
        } else {
            runControl(f);
            if (jumpPressed) {
                tryJump(f);
            } else if (f.input.down()) {
                tryDropThrough(f);
            }
            if (attackPressed) {
                tryStartAttack(f);
            }
            if (specialPressed && Balance.SPECIALS_ENABLED) {
                trySpecial(f);
            }
        }

        // The Zoner fires its glob on the first active tick of its attack.
        if (f.attacking && f.type.attack.firesGlob()
                && f.attackTicks == f.type.attack.startupTicks() + 1) {
            fireGlob(f);
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
            // You face the way you last pressed, but not in the middle of an
            // attack, so a swing always goes where it started.
            if (!f.attacking) {
                f.face = direction;
            }
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

    /**
     * Starts the basic attack, if allowed. Not allowed while already
     * attacking, or while invulnerable after a respawn (so nobody can attack
     * the moment they come back). Hitstun is already ruled out by the caller.
     * The Zoner also needs its cooldown to be over and a free glob.
     */
    private void tryStartAttack(Fighter f) {
        if (f.attacking || f.invulnerableTicks > 0) {
            return;
        }
        if (f.type.attack.firesGlob()) {
            if (f.globCooldownTicks > 0 || globsAlive(f.id) >= Balance.GLOB_MAX_ALIVE) {
                return;
            }
        }
        f.attacking = true;
        f.attackTicks = 1;
        f.hitThisAttack.clear();
    }

    /**
     * Stretch goal hook: the special attacks (Brawler ground pound, Speedster
     * dash, Zoner ketchup puddle). Only called when Balance.SPECIALS_ENABLED
     * is true, and it does nothing yet.
     */
    private void trySpecial(Fighter f) {
        // Not built yet. See section 15 of PROMPT.md.
    }

    /** Stops an attack early, for example because the attacker got hit. */
    private void cancelAttack(Fighter f) {
        f.attacking = false;
        f.attackTicks = 0;
    }

    /** Fires a ketchup glob from the front of the bottle. */
    private void fireGlob(Fighter f) {
        double x = f.x + f.face * (f.type.width / 2 + Balance.GLOB_RADIUS);
        double y = f.y - f.type.height * 0.55;
        projectiles.add(new Projectile(nextProjectileId++, f.id, f.type.attack, x, y,
                f.face * Balance.GLOB_SPEED));
        f.globCooldownTicks = Balance.GLOB_COOLDOWN_TICKS;
    }

    private int globsAlive(int ownerId) {
        int count = 0;
        for (Projectile p : projectiles) {
            if (p.ownerId == ownerId && p.alive) {
                count++;
            }
        }
        return count;
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
        cancelAttack(f);
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
        f.globCooldownTicks = 0;
        cancelAttack(f);
        events.add(new GameEvent.Respawn(f.id));
    }

    // =================================================================
    // Part B: ketchup globs
    // =================================================================

    private void updateProjectiles() {
        for (Projectile p : projectiles) {
            p.vy += Balance.GLOB_GRAVITY * DT;
            p.x += p.vx * DT;
            p.y += p.vy * DT;
            p.ticksLeft--;
            // Globs fly through platforms, so there is no platform check here.
            if (p.ticksLeft <= 0 || !stage.blastZone().contains(p.x, p.y)) {
                p.alive = false;
            }
        }
    }

    // =================================================================
    // Part C: hits
    // =================================================================

    /** Finds every hit this tick, then applies them all. */
    private void resolveHits() {
        List<PendingHit> hits = new ArrayList<>();
        findMeleeHits(hits);
        findGlobHits(hits);
        for (PendingHit hit : hits) {
            applyHit(hit.target(), hit.attack(), hit.damage(), hit.direction(), hit.attackerId(), hit.x(), hit.y());
        }
    }

    /** Can this fighter be hit right now? */
    private static boolean canBeHit(Fighter target) {
        return target.isAlive() && target.invulnerableTicks == 0;
    }

    private void findMeleeHits(List<PendingHit> hits) {
        for (Fighter attacker : fighters) {
            AttackStats attack = attacker.type.attack;
            if (!attacker.isAlive() || attack.firesGlob()
                    || attacker.attackPhase() != Fighter.AttackPhase.ACTIVE) {
                continue;
            }
            Rect hitbox = attacker.attackHitbox();
            for (Fighter target : fighters) {
                if (target == attacker || !canBeHit(target) || attacker.hitThisAttack.contains(target.id)) {
                    continue;
                }
                Rect hurtbox = target.hurtbox();
                if (hitbox.overlaps(hurtbox)) {
                    attacker.hitThisAttack.add(target.id); // each attack hits each target once
                    // The splat goes in the middle of where the two boxes overlap.
                    double x = (Math.max(hitbox.left(), hurtbox.left()) + Math.min(hitbox.right(), hurtbox.right())) / 2;
                    double y = (Math.max(hitbox.top(), hurtbox.top()) + Math.min(hitbox.bottom(), hurtbox.bottom())) / 2;
                    hits.add(new PendingHit(target, attack, attack.damage(), attacker.face, attacker.id, x, y));
                }
            }
        }
    }

    private void findGlobHits(List<PendingHit> hits) {
        for (Projectile p : projectiles) {
            if (!p.alive) {
                continue;
            }
            for (Fighter target : fighters) {
                if (target.id == p.ownerId || !canBeHit(target)) {
                    continue; // never hits its own Zoner, and flies through invulnerable fighters
                }
                if (p.touches(target.hurtbox())) {
                    int damage = Knockback.globDamage(p.distanceTravelled());
                    hits.add(new PendingHit(target, p.stats, damage, p.direction(), p.ownerId, p.x, p.y));
                    p.alive = false; // a glob splats on the first fighter it touches
                    break;
                }
            }
        }
    }

    /**
     * Applies one hit, following the steps in Knockback.java. Package private
     * so the tuning test can launch a fighter without setting up an attack.
     */
    void applyHit(Fighter target, AttackStats attack, int damage, int direction,
                  int attackerId, double hitX, double hitY) {
        // 1. Damage
        target.percent = Math.min(target.percent + damage, Balance.MAX_PERCENT);

        // 2. Knockback
        double kb = Knockback.knockback(attack.baseKnockback(), attack.knockbackGrowth(),
                target.percent, target.type.weight);

        // 3. Launch. The old speed is thrown away.
        target.vx = Knockback.launchVx(kb, attack.launchAngleDegrees(), direction);
        target.vy = Knockback.launchVy(kb, attack.launchAngleDegrees());
        target.onGround = false;
        target.standingOn = null;

        // 4. Hitstun. Getting hit also cancels the target's own attack.
        target.hitstunTicks = Knockback.hitstunTicks(kb);
        cancelAttack(target);

        // Remember who did it, for KO credit.
        target.lastHitBy = attackerId;
        target.lastHitTick = tick;

        // 5. Tell the browsers, so they can draw a splat.
        events.add(new GameEvent.Hit(target.id, hitX, hitY, damage, Knockback.isBigHit(kb)));
    }

    // =================================================================
    // Reading the world
    // =================================================================

    public List<Fighter> fighters() {
        return fighters;
    }

    /** The globs that are flying right now. */
    public List<Projectile> projectiles() {
        return projectiles;
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
