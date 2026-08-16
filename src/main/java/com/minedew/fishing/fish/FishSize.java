package com.minedew.fishing.fish;

import net.minecraft.util.RandomSource;

/**
 * How big the thing on the line is, which is where difficulty comes from. Species decides how a
 * fight <i>feels</i>; size decides how <i>hard</i> it is, and the two are read separately: pattern
 * character for the species, intensity and the difficulty stars for the size.
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
    SMALL("Small", 1, 1.00F, 0.90F, 0, 0, 0),
    MEDIUM("Medium", 2, 1.00F, 1.00F, 1, 0, 0),
    LARGE("Large", 3, 1.02F, 1.05F, 2, 170, 16),
    TROPHY("Trophy", 4, 1.05F, 1.10F, 4, 190, 20);

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

    FishSize(String displayName, int difficulty, float speedScale, float aggressionScale,
             int bonusPieces, int thrashPeriodTicks, int thrashDurationTicks) {
        this.displayName = displayName;
        this.difficulty = difficulty;
        this.speedScale = speedScale;
        this.aggressionScale = aggressionScale;
        this.bonusPieces = bonusPieces;
        this.thrashPeriodTicks = thrashPeriodTicks;
        this.thrashDurationTicks = thrashDurationTicks;
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
    public static FishSize roll(RandomSource random, boolean raining, boolean deepWater, boolean night) {
        int[] weights = BASE_WEIGHTS.clone();
        int shifts = (raining ? 1 : 0) + (deepWater ? 1 : 0) + (night ? 1 : 0);
        if (shifts > 0) {
            weights[2] += BIG_WATER_BONUS * shifts;
            weights[3] += TROPHY_BONUS * shifts;
            weights[0] = Math.max(5, weights[0] - 8 * shifts);
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
