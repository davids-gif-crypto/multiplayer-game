package com.foodfight.sim;

import java.util.List;

/**
 * The level: where the platforms are, where fighters spawn, and how far
 * they can fly before they are knocked out.
 *
 * The world is 1280 wide and 720 tall, and y grows downward.
 * If you change the layout here, also change the drawing in render.js.
 */
public class Stage {

    /**
     * Something you can stand on.
     * One way platforms (the bread) can be jumped up through from below and
     * dropped through by holding Down. The tray is not one way: you land on
     * it from above and bump your head on it from below.
     */
    public record Platform(String name, Rect rect, boolean oneWay) {
        public double left() {
            return rect.left();
        }

        public double right() {
            return rect.right();
        }

        public double top() {
            return rect.top();
        }

        public double bottom() {
            return rect.bottom();
        }
    }

    private final List<Platform> platforms;
    private final Rect blastZone;
    private final double[] spawnXs;
    private final double spawnY;
    private final double respawnX;
    private final double respawnY;

    public Stage(List<Platform> platforms, Rect blastZone, double[] spawnXs, double spawnY,
                 double respawnX, double respawnY) {
        this.platforms = List.copyOf(platforms);
        this.blastZone = blastZone;
        this.spawnXs = spawnXs.clone();
        this.spawnY = spawnY;
        this.respawnX = respawnX;
        this.respawnY = respawnY;
    }

    /** The cafeteria lunch tray stage. */
    public static Stage lunchTray() {
        return new Stage(
                List.of(
                        // The main platform, from x 240 to 1040, top at y 520, 40 thick.
                        new Platform("tray", new Rect(240, 520, 800, 40), false),
                        // Two floating bread slices, 24 thick.
                        new Platform("left bread", new Rect(300, 360, 220, 24), true),
                        new Platform("right bread", new Rect(760, 360, 220, 24), true)),
                // Fly out of this box and you lose a stock. The screen is 0 to 1280
                // by 0 to 720, so knockouts happen just off screen.
                new Rect(-200, -250, 1680, 1150),
                // Spawn points along the tray, in join order.
                new double[] {380, 540, 740, 900}, 520,
                // Respawn point: high above the middle of the tray.
                640, 80);
    }

    public List<Platform> platforms() {
        return platforms;
    }

    public Rect blastZone() {
        return blastZone;
    }

    /** Spawn x for the n-th fighter (0 based). Wraps around if there are more fighters than spots. */
    public double spawnX(int index) {
        return spawnXs[index % spawnXs.length];
    }

    public double spawnY() {
        return spawnY;
    }

    public double respawnX() {
        return respawnX;
    }

    public double respawnY() {
        return respawnY;
    }

    /** The middle of the stage, used to face fighters inward at the start. */
    public double centerX() {
        return 640;
    }
}
