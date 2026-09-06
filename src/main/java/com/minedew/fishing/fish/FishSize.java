package com.minedew.fishing.fish;

import net.minecraft.util.RandomSource;

/**
 * How big the thing on the line is, which is where difficulty comes from. Species decides how a
 * fight <i>feels</i>; size decides how <i>hard</i> it is, and the two are read separately: pattern
 * character for the species, intensity for the size. Neither is ever named on the overlay.
 *
 * <p>Size selects the bobber height and the meter rates through its {@link #difficulty()} tier
 * (see {@code MinigameTuning}), nudges the fish's speed and aggressiveness, and sets how many bonus
 * pieces a landed catch pays out on top of vanilla's own loot roll.
 *
 * <p>The speed scales are deliberately almost flat. Making big fish fast is the obvious way to make
 * them hard and it does not work: the fish's speed is pinned at both ends (too fast and it outruns
 * the bobber, too slow and it never leaves mid-track where a parked bobber already sits), so there
 * is very little room on that axis. The tier ramp lives in the bobber height, the fill time and the
 * thrash instead.
 *
 * <p>{@link FishMovementPattern#TROPHY_THRASH} is layered over the species' own pattern for the two
 * big classes: a large fish thrashes occasionally, a trophy thrashes hard and often. That is the
 * tell that you have something big on, independent of which species it is.
 */
public enum FishSize {
    // Bonus pieces are ON TOP of the vanilla loot roll's own piece (reskinned as a fillet too), so
    // the totals a player sees are 2 / 3 / 4 / 6. The floor is 2 total, not 1: every encounter is
    // a fight here, so even the smallest catch pays for the minigame it cost.
    SMALL("Small", 1, 1.00F, 0.90F, 1, 0, 0, 0.6F),
    MEDIUM("Medium", 2, 1.00F, 1.00F, 2, 0, 0, 1.0F),
    LARGE("Large", 3, 1.02F, 1.05F, 3, 170, 16, 1.5F),
    TROPHY("Trophy", 4, 1.05F, 1.10F, 5, 190, 20, 2.2F);

    /** Relative odds of each size on an ordinary cast, before the situational shifts below. */
    private static final int[] BASE_WEIGHTS = {50, 30, 15, 5};
    /** Extra weight added to the two big classes in rain, deep water, or at night. */
    private static final int BIG_WATER_BONUS = 10;
    private static final int TROPHY_BONUS = 4;

    private final String displayName;
    private final int difficulty;
    private final float speedScale;
    private final float aggressionScale;
    private final int bonusPieces;
    private final int thrashPeriodTicks;
    private final int thrashDurationTicks;
    private final float displayScale;

    FishSize(String displayName, int difficulty, float speedScale, float aggressionScale,
             int bonusPieces, int thrashPeriodTicks, int thrashDurationTicks, float displayScale) {
        this.displayName = displayName;
        this.difficulty = difficulty;
        this.speedScale = speedScale;
        this.aggressionScale = aggressionScale;
        this.bonusPieces = bonusPieces;
        this.thrashPeriodTicks = thrashPeriodTicks;
        this.thrashDurationTicks = thrashDurationTicks;
        this.displayScale = displayScale;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    /** 1..4, the tier every difficulty-scaled tuning table is indexed by. */
    public int difficulty() {
        return this.difficulty;
    }

    public float getSpeedScale() {
        return this.speedScale;
    }

    public float getAggressionScale() {
        return this.aggressionScale;
    }

    /** Extra pieces of the species' item granted on a successful catch. */
    public int getBonusPieces() {
        return this.bonusPieces;
    }

    /**
     * {@code SCALE} attribute for the landing animation's fish mob: the size class read at a
     * glance, in the flesh, before the reveal text names it.
     */
    public float getDisplayScale() {
        return this.displayScale;
    }

    public FishMovementPattern getThrash() {
        return this.thrashPeriodTicks > 0 ? FishMovementPattern.TROPHY_THRASH : null;
    }

    public int getThrashPeriodTicks() {
        return this.thrashPeriodTicks;
    }

    public int getThrashDurationTicks() {
        return this.thrashDurationTicks;
    }

    /**
     * Roll a size. Rain, deep water and night all push toward the bigger classes, which is the same
     * "conditions matter" idea the old biome/weather species table carried, moved onto the axis that
     * now actually drives difficulty.
     */
    /** Per fish lost in a row: taken off the large and trophy weights, put onto small. */
    private static final int BACKOFF_LARGE_PER_MISS = 3;
    private static final int BACKOFF_TROPHY_PER_MISS = 1;
    private static final int BACKOFF_SMALL_PER_MISS = 4;

    /**
     * @param misses fish lost in a row by this player; each one tilts the odds toward a smaller
     *               fish, so a bad run drifts toward fights that can be won
     */
    public static FishSize roll(RandomSource random, boolean raining, boolean deepWater, boolean night, int misses) {
        int[] weights = BASE_WEIGHTS.clone();
        int shifts = (raining ? 1 : 0) + (deepWater ? 1 : 0) + (night ? 1 : 0);
        if (shifts > 0) {
            weights[2] += BIG_WATER_BONUS * shifts;
            weights[3] += TROPHY_BONUS * shifts;
            weights[0] = Math.max(5, weights[0] - 8 * shifts);
        }
        if (misses > 0) {
            weights[3] = Math.max(0, weights[3] - BACKOFF_TROPHY_PER_MISS * misses);
            weights[2] = Math.max(3, weights[2] - BACKOFF_LARGE_PER_MISS * misses);
            weights[0] += BACKOFF_SMALL_PER_MISS * misses;
        }

        int total = 0;
        for (int weight : weights) total += weight;
        int roll = random.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            roll -= weights[i];
            if (roll < 0) return values()[i];
        }
        return SMALL;
    }
}
