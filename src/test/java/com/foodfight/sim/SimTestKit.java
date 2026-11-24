package com.foodfight.sim;

/**
 * Helpers shared by the simulation tests: make a world, put a fighter
 * somewhere, hold keys for a number of ticks.
 */
class SimTestKit {

    final GameWorld world = new GameWorld(Stage.lunchTray());

    static final InputState NOTHING = InputState.NONE;
    static final InputState LEFT = keys("left");
    static final InputState RIGHT = keys("right");
    static final InputState JUMP = keys("jump");
    static final InputState DOWN = keys("down");

    /** Builds an input with the named keys held, like keys("right", "jump"). */
    static InputState keys(String... names) {
        boolean left = false, right = false, jump = false, down = false;
        boolean attack = false, special = false, shield = false;
        for (String name : names) {
            switch (name) {
                case "left" -> left = true;
                case "right" -> right = true;
                case "jump" -> jump = true;
                case "down" -> down = true;
                case "attack" -> attack = true;
                case "special" -> special = true;
                case "shield" -> shield = true;
                default -> throw new IllegalArgumentException(name);
            }
        }
        return new InputState(left, right, jump, down, attack, special, shield);
    }

    /** A fighter at (x, y), not moving, not standing on anything yet. */
    Fighter fighterAt(FighterType type, double x, double y) {
        Fighter f = world.addFighter(world.fighters().size() + 1, type);
        f.x = x;
        f.y = y;
        return f;
    }

    /** A fighter standing on whatever platform top is at (x, y). */
    Fighter standingAt(FighterType type, double x, double y) {
        Fighter f = fighterAt(type, x, y);
        world.step(); // gravity pulls it a hair into the platform and it lands
        if (!f.onGround) {
            throw new IllegalStateException("nothing to stand on at " + x + ", " + y);
        }
        return f;
    }

    /** Holds these keys for a number of ticks. */
    void hold(Fighter f, InputState input, int ticks) {
        world.setInput(f.id, input);
        steps(ticks);
    }

    /** Taps jump: one tick held, one tick released. */
    void tapJump(Fighter f) {
        hold(f, JUMP, 1);
        hold(f, NOTHING, 1);
    }

    void steps(int ticks) {
        for (int i = 0; i < ticks; i++) {
            world.step();
        }
    }

    /** Steps until the fighter is standing, or fails after a few seconds. */
    void stepUntilLanded(Fighter f) {
        for (int i = 0; i < 600; i++) {
            world.step();
            if (f.onGround) {
                return;
            }
        }
        throw new AssertionError("fighter never landed, y = " + f.y);
    }
}
