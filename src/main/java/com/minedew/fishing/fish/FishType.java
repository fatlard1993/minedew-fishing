package com.minedew.fishing.fish;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.biome.Biome;

public enum FishType {
    CARP("Carp", 1, 0.6F, 0.4F, FishMovementPattern.SLOW_SINUSOIDAL),
    SUNFISH("Sunfish", 1, 0.55F, 0.35F, FishMovementPattern.SLOW_SINUSOIDAL),
    SARDINE("Sardine", 1, 0.5F, 0.38F, FishMovementPattern.SLOW_SINUSOIDAL),
    BASS("Bass", 2, 0.8F, 0.6F, FishMovementPattern.MODERATE_JUMPY),
    TROUT("Trout", 2, 0.85F, 0.65F, FishMovementPattern.MODERATE_DART),
    SALMON("Salmon", 2, 0.9F, 0.7F, FishMovementPattern.MODERATE_DART),
    CATFISH("Catfish", 2, 0.75F, 0.58F, FishMovementPattern.MODERATE_JUMPY),
    PIKE("Pike", 3, 1.15F, 0.85F, FishMovementPattern.FAST_ERRATIC),
    TUNA("Tuna", 3, 1.25F, 0.9F, FishMovementPattern.FAST_DART),
    STURGEON("Sturgeon", 3, 1.1F, 0.82F, FishMovementPattern.FAST_ERRATIC),
    CRIMSONFISH("Crimsonfish", 4, 1.6F, 1.1F, FishMovementPattern.LEGENDARY_CHAOS),
    GLACIERFISH("Glacierfish", 4, 1.7F, 1.15F, FishMovementPattern.LEGENDARY_TELEPORT),
    MUTANT_CARP("Mutant Carp", 4, 1.8F, 1.2F, FishMovementPattern.LEGENDARY_CHAOS);

    private final String displayName;
    private final int difficulty;
    private final float speed;
    private final float aggressiveness;
    private final FishMovementPattern pattern;

    FishType(String displayName, int difficulty, float speed, float aggressiveness, FishMovementPattern pattern) {
        this.displayName = displayName;
        this.difficulty = difficulty;
        this.speed = speed;
        this.aggressiveness = aggressiveness;
        this.pattern = pattern;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public int getDifficulty() {
        return this.difficulty;
    }

    public float getSpeed() {
        return this.speed;
    }

    public float getAggressiveness() {
        return this.aggressiveness;
    }

    public FishMovementPattern getPattern() {
        return this.pattern;
    }

    public static FishType getRandomFish(Holder<Biome> biome, boolean isRaining, int timeOfDay, RandomSource random) {
        List<FishType> availableFish = new ArrayList<>();

        if (biome.is(BiomeTags.IS_OCEAN)) {
            availableFish.add(TUNA);
            availableFish.add(SALMON);
            availableFish.add(SARDINE);
            if (isRaining) {
                availableFish.add(CRIMSONFISH);
            }
        } else if (biome.is(BiomeTags.IS_RIVER)) {
            availableFish.add(TROUT);
            availableFish.add(SALMON);
            availableFish.add(BASS);
            availableFish.add(PIKE);
            if (isRaining) {
                availableFish.add(MUTANT_CARP);
            }
        } else if (biome.is(BiomeTags.IS_DEEP_OCEAN)) {
            availableFish.add(STURGEON);
            availableFish.add(TUNA);
            availableFish.add(PIKE);
            if (timeOfDay >= 18000 || timeOfDay <= 6000) {
                availableFish.add(GLACIERFISH);
            }
        } else {
            availableFish.add(CARP);
            availableFish.add(SUNFISH);
            availableFish.add(BASS);
            availableFish.add(CATFISH);
        }

        if (availableFish.isEmpty()) {
            availableFish.add(CARP);
            availableFish.add(SUNFISH);
        }

        return availableFish.get(random.nextInt(availableFish.size()));
    }
}
