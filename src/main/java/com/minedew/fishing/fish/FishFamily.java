package com.minedew.fishing.fish;

/**
 * Which fish are kin, and so how they move: every {@link Fish} swims its family's pattern, and its
 * own accent on top. Grouped the way the animals are, not by the vanilla body a fish borrows or the
 * fillet it cuts into - a pike and a perch are both whitefish on the plate and nothing alike on the
 * line.
 *
 * <p>The vanilla four keep exactly the numbers they always had (cod's groundfish, salmon's
 * salmonids, pufferfish, the reef), so nothing a player already learned has moved.
 */
public enum FishFamily {
    GROUNDFISH("Groundfish", 1.00F, 0.90F, FishMovementPattern.MODERATE_DART),
    SUNFISHES("Sunfishes", 1.00F, 0.92F, FishMovementPattern.LEAP),
    PERCHES("Perches", 1.01F, 0.92F, FishMovementPattern.STALK),
    CARPS("Carps", 1.00F, 0.92F, FishMovementPattern.CRUISE),
    CATFISHES("Catfishes", 0.99F, 0.92F, FishMovementPattern.BOTTOM_GRIND),
    PIKES("Pikes", 1.02F, 0.95F, FishMovementPattern.AMBUSH),
    STURGEONS("Sturgeons", 0.99F, 0.92F, FishMovementPattern.BOTTOM_GRIND),
    CICHLIDS("Cichlids", 1.01F, 0.92F, FishMovementPattern.TERRITORIAL),
    CHARACINS("Characins", 1.00F, 0.92F, FishMovementPattern.SNAP),
    SALMONIDS("Salmonids", 1.03F, 0.95F, FishMovementPattern.FAST_DART),
    HERRINGS("Herrings", 1.00F, 0.92F, FishMovementPattern.SCHOOL),
    TUNAS("Tunas", 1.00F, 0.92F, FishMovementPattern.RUN),
    EELS("Eels", 1.00F, 0.92F, FishMovementPattern.SERPENTINE),
    SCORPIONFISHES("Scorpionfishes", 0.99F, 0.92F, FishMovementPattern.LURK),
    PUFFERS("Puffers", 0.98F, 0.90F, FishMovementPattern.SLOW_FLOATER),
    SCULPINS("Sculpins", 0.98F, 0.90F, FishMovementPattern.SLUMP),
    REEF("Reef Fish", 1.02F, 1.00F, FishMovementPattern.FAST_ERRATIC);

    private final String displayName;
    private final float baseSpeed;
    private final float baseAggressiveness;
    private final FishMovementPattern pattern;

    FishFamily(String displayName, float baseSpeed, float baseAggressiveness, FishMovementPattern pattern) {
        this.displayName = displayName;
        this.baseSpeed = baseSpeed;
        this.baseAggressiveness = baseAggressiveness;
        this.pattern = pattern;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public float getBaseSpeed() {
        return this.baseSpeed;
    }

    public float getBaseAggressiveness() {
        return this.baseAggressiveness;
    }

    public FishMovementPattern getPattern() {
        return this.pattern;
    }
}
