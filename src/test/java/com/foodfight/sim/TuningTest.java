package com.foodfight.sim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The knockback tuning check. Run it with
 *
 *     ./gradlew tuning
 *
 * to print the table. ./gradlew test runs it too (quietly), so if someone
 * changes Balance.java in a way that breaks the goals, the test fails.
 *
 * HOW WE MEASURE A LAUNCH
 * A fighter stands in the middle of the tray (x = 640) and gets hit to the
 * right. We let the real GameWorld fly it with no keys held until its
 * hitstun ends, and measure how far sideways it went. If it leaves the
 * blast zone before hitstun ends, that is a KO "from center stage". After
 * hitstun the player can steer back, so we stop measuring there.
 *
 * THE GOALS (from the design)
 *   1. A Brawler hit on a Speedster at 0% sends it roughly 300 units.
 *   2. At 100 to 120% the same hit sends it close to the edge of the blast
 *      zone (the edge is 840 units from the middle), but does not KO.
 *   3. Around 150% it is a KO from center stage.
 *   4. A Brawler target survives about 40 to 50 percent longer than a
 *      Speedster (it needs 1.4 to 1.5 times the percent to be KO'd).
 *
 * WHY THE NUMBERS CHANGED
 * The first design numbers (weights 0.8 / 1.0 / 1.3, Brawler base 420 and
 * growth 9) sent a Speedster only about 165 units at 0%, KO'd it from the
 * middle at about 90%, and let a Brawler survive about twice as long.
 * Two things had to move:
 *   - Goal 4 cannot be met with those weights. Knockback is divided by
 *     weight, so a Brawler 1.625 times heavier survives at least 1.625 times
 *     longer, no matter what the base and growth are. The weights moved
 *     closer together: 0.9 / 1.0 / 1.1. Same order as before.
 *   - Goals 1 and 3 need more knockback at low percent and less growth per
 *     percent: base 420 to 730, growth 9 to 4.3. The jab and the glob were
 *     scaled the same way (base times about 1.74, growth times about 0.48),
 *     so all three attacks keep their strength compared to each other.
 */
class TuningTest {

    private static final double CENTER_X = 640;
    private static final double BLAST_EDGE_DISTANCE = 1480 - CENTER_X;

    /** The result of one launch. */
    record Launch(double knockback, double distance, boolean ko) {
    }

    /** Hits a fighter standing in the middle and flies it until hitstun ends. */
    static Launch launch(FighterType targetType, int percentBefore, AttackStats attack, int damage) {
        GameWorld world = new GameWorld(Stage.lunchTray());
        Fighter target = world.addFighter(1, targetType);
        target.x = CENTER_X;
        target.y = 520;
        world.step(); // settle onto the tray
        target.percent = percentBefore;

        world.applyHit(target, attack, damage, 1, 2, CENTER_X, 480);
        double knockback = Knockback.knockback(attack.baseKnockback(), attack.knockbackGrowth(),
                target.percent, targetType.weight);

        for (int i = 0; i < 600; i++) {
            world.step();
            if (target.stocks < Balance.STARTING_STOCKS) {
                return new Launch(knockback, BLAST_EDGE_DISTANCE, true);
            }
            if (target.hitstunTicks == 0) {
                break;
            }
        }
        return new Launch(knockback, Math.abs(target.x - CENTER_X), false);
    }

    /** The lowest percent (before the hit) at which this hit KOs from the middle. */
    static int koPercent(FighterType targetType, AttackStats attack, int damage) {
        for (int percent = 0; percent <= Balance.MAX_PERCENT; percent++) {
            if (launch(targetType, percent, attack, damage).ko()) {
                return percent;
            }
        }
        return -1; // never
    }

    private static final AttackStats BRAWLER_HIT = FighterType.BRAWLER.attack;
    private static final int BRAWLER_DAMAGE = BRAWLER_HIT.damage();

    @Test
    void printTheTuningTable() {
        System.out.println();
        System.out.println("Brawler hit (" + BRAWLER_DAMAGE + " damage) on a fighter in the middle of the tray.");
        System.out.println("Distance is how far it flew sideways before hitstun ended.");
        System.out.println("The blast zone edge is " + (int) BLAST_EDGE_DISTANCE + " units away.");
        System.out.println();
        System.out.printf("  %-8s %-24s %-24s %-24s%n", "percent", "SPEEDSTER", "ZONER", "BRAWLER");
        for (int percent = 0; percent <= 240; percent += (percent < 100 ? 25 : 10)) {
            StringBuilder row = new StringBuilder(String.format("  %-8s", percent + "%"));
            // Columns go light to heavy: Speedster, Zoner, Brawler.
            for (FighterType type : new FighterType[] {FighterType.SPEEDSTER, FighterType.ZONER, FighterType.BRAWLER}) {
                Launch l = launch(type, percent, BRAWLER_HIT, BRAWLER_DAMAGE);
                String cell = l.ko() ? String.format("kb %4.0f  KO", l.knockback())
                        : String.format("kb %4.0f  %4.0f units", l.knockback(), l.distance());
                row.append(String.format(" %-24s", cell));
            }
            System.out.println(row);
        }

        System.out.println();
        System.out.println("KO percent from center stage (lowest percent where one hit KOs):");
        System.out.printf("  %-26s %-10s %-10s %-10s%n", "attack", "SPEEDSTER", "ZONER", "BRAWLER");
        printKoRow("Brawler swing (10 dmg)", BRAWLER_HIT, BRAWLER_DAMAGE);
        printKoRow("Speedster jab (4 dmg)", FighterType.SPEEDSTER.attack, FighterType.SPEEDSTER.attack.damage());
        printKoRow("Ketchup glob, far (8 dmg)", FighterType.ZONER.attack, Balance.GLOB_MAX_DAMAGE);

        int speedsterKo = koPercent(FighterType.SPEEDSTER, BRAWLER_HIT, BRAWLER_DAMAGE);
        int brawlerKo = koPercent(FighterType.BRAWLER, BRAWLER_HIT, BRAWLER_DAMAGE);
        System.out.printf("%nA Brawler survives %.2f times as long as a Speedster (goal: 1.4 to 1.5).%n",
                (double) brawlerKo / speedsterKo);
        System.out.println();
    }

    private static void printKoRow(String label, AttackStats attack, int damage) {
        StringBuilder row = new StringBuilder(String.format("  %-26s", label));
        for (FighterType type : new FighterType[] {FighterType.SPEEDSTER, FighterType.ZONER, FighterType.BRAWLER}) {
            int ko = koPercent(type, attack, damage);
            row.append(String.format(" %-10s", ko < 0 ? "never" : ko + "%"));
        }
        System.out.println(row);
    }

    // ---------- the goals, checked ----------

    @Test
    void goal1_atZeroPercentASpeedsterFliesAboutThreeHundredUnits() {
        Launch l = launch(FighterType.SPEEDSTER, 0, BRAWLER_HIT, BRAWLER_DAMAGE);
        assertFalse(l.ko());
        assertTrue(l.distance() >= 250 && l.distance() <= 350, "flew " + l.distance());
    }

    @Test
    void goal2_atOneHundredToOneTwentyPercentItGetsCloseToTheEdge() {
        for (int percent : new int[] {100, 110, 120}) {
            Launch l = launch(FighterType.SPEEDSTER, percent, BRAWLER_HIT, BRAWLER_DAMAGE);
            assertFalse(l.ko(), "should survive at " + percent + "%");
            assertTrue(l.distance() >= 0.7 * BLAST_EDGE_DISTANCE,
                    "at " + percent + "% it only flew " + l.distance());
        }
    }

    @Test
    void goal3_aroundOneHundredFiftyPercentItIsAKo() {
        int ko = koPercent(FighterType.SPEEDSTER, BRAWLER_HIT, BRAWLER_DAMAGE);
        assertTrue(ko >= 140 && ko <= 160, "Speedster KO percent was " + ko);
    }

    @Test
    void goal4_aBrawlerSurvivesFortyToFiftyPercentLonger() {
        double ratio = (double) koPercent(FighterType.BRAWLER, BRAWLER_HIT, BRAWLER_DAMAGE)
                / koPercent(FighterType.SPEEDSTER, BRAWLER_HIT, BRAWLER_DAMAGE);
        assertTrue(ratio >= 1.38 && ratio <= 1.55, "ratio was " + ratio);
    }

    @Test
    void theFightersStillKeepTheirTradeOffs() {
        // Light to heavy: Speedster, Zoner, Brawler.
        assertTrue(FighterType.SPEEDSTER.weight < FighterType.ZONER.weight);
        assertTrue(FighterType.ZONER.weight < FighterType.BRAWLER.weight);
        // The Brawler hits hardest, the jab is the weakest melee hit.
        assertTrue(FighterType.BRAWLER.attack.damage() > FighterType.SPEEDSTER.attack.damage());
        assertTrue(FighterType.BRAWLER.attack.baseKnockback() > FighterType.SPEEDSTER.attack.baseKnockback());
        // Speed: Speedster fastest, Brawler slowest.
        assertTrue(FighterType.SPEEDSTER.runSpeed > FighterType.ZONER.runSpeed);
        assertTrue(FighterType.ZONER.runSpeed > FighterType.BRAWLER.runSpeed);
    }
}
