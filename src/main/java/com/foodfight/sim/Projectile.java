package com.foodfight.sim;

/**
 * A ketchup glob fired by a Zoner. It flies sideways, droops a little, and
 * disappears when it hits someone, leaves the blast zone, or runs out of time.
 * It passes straight through platforms.
 */
public class Projectile {

    public final int id;
    public final int ownerId;
    /** The owner's attack, used for the knockback when the glob hits. */
    final AttackStats stats;

    double x;
    double y;
    double vx;
    double vy;

    /** Where it was fired from, to work out how far it flew. */
    final double startX;
    final double startY;

    int ticksLeft = Balance.GLOB_LIFETIME_TICKS;
    boolean alive = true;

    Projectile(int id, int ownerId, AttackStats stats, double x, double y, double vx) {
        this.id = id;
        this.ownerId = ownerId;
        this.stats = stats;
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = 0;
        this.startX = x;
        this.startY = y;
    }

    /** Straight line distance from where it was fired. */
    public double distanceTravelled() {
        return Math.hypot(x - startX, y - startY);
    }

    /** 1 if flying right, -1 if flying left. */
    public int direction() {
        return vx >= 0 ? 1 : -1;
    }

    /**
     * True if this round glob touches the rectangle. We find the point of the
     * rectangle closest to the glob's middle, and check if it is within the
     * radius.
     */
    public boolean touches(Rect box) {
        double closestX = MathUtil.clamp(x, box.left(), box.right());
        double closestY = MathUtil.clamp(y, box.top(), box.bottom());
        double dx = x - closestX;
        double dy = y - closestY;
        return dx * dx + dy * dy <= Balance.GLOB_RADIUS * Balance.GLOB_RADIUS;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double vx() {
        return vx;
    }

    public boolean alive() {
        return alive;
    }
}
