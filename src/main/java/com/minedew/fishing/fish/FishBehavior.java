package com.minedew.fishing.fish;

import net.minecraft.util.RandomSource;

public class FishBehavior {
    private final FishType fishType;
    private final RandomSource random;
    private float position;
    private float velocity;
    private float targetPosition;
    private int patternTimer;
    private int directionChangeTimer;
    private static final float MAX_VELOCITY = 0.025F;
    private static final float DAMPING = 0.94F;

    public FishBehavior(FishType fishType, RandomSource random) {
        this.fishType = fishType;
        this.random = random;
        this.position = 0.5F + (random.nextFloat() - 0.5F) * 0.4F;
        this.velocity = 0.0F;
        this.targetPosition = this.position;
        this.patternTimer = 0;
        this.directionChangeTimer = 0;
    }

    public void update() {
        this.patternTimer++;
        this.directionChangeTimer++;

        switch (this.fishType.getPattern()) {
            case SLOW_SINUSOIDAL -> this.updateSlowSinusoidal();
            case MODERATE_JUMPY -> this.updateModerateJumpy();
            case MODERATE_DART -> this.updateModerateDart();
            case FAST_ERRATIC -> this.updateFastErratic();
            case FAST_DART -> this.updateFastDart();
            case LEGENDARY_CHAOS -> this.updateLegendaryChaos();
            case LEGENDARY_TELEPORT -> this.updateLegendaryTeleport();
        }

        this.velocity *= DAMPING;
        float maxVel = MAX_VELOCITY * this.fishType.getSpeed();
        this.velocity = Math.max(-maxVel, Math.min(maxVel, this.velocity));
        this.position += this.velocity;

        if (this.position < 0.05F) {
            this.position = 0.05F;
            this.velocity = Math.abs(this.velocity) * 0.4F;
        } else if (this.position > 0.95F) {
            this.position = 0.95F;
            this.velocity = -Math.abs(this.velocity) * 0.4F;
        }
    }

    private void updateSlowSinusoidal() {
        float sinValue = (float) Math.sin(this.patternTimer * 0.04F);
        this.targetPosition = 0.5F + sinValue * 0.35F;
        float diff = this.targetPosition - this.position;
        this.velocity += diff * 0.0025F * this.fishType.getAggressiveness();
        this.velocity -= 5.0E-4F;
    }

    private void updateModerateJumpy() {
        if (this.directionChangeTimer > 50 + this.random.nextInt(50)) {
            this.directionChangeTimer = 0;
            if (this.random.nextFloat() < 0.7F * this.fishType.getAggressiveness()) {
                this.targetPosition = this.random.nextFloat() * 0.8F + 0.1F;
                this.velocity += (this.targetPosition - this.position) * 0.15F;
            }
        } else {
            float diff = this.targetPosition - this.position;
            this.velocity += diff * 0.003F;
        }
        this.velocity -= 3.0E-4F;
    }

    private void updateModerateDart() {
        if (this.directionChangeTimer > 25 + this.random.nextInt(35)) {
            this.directionChangeTimer = 0;
            this.targetPosition = this.random.nextFloat() * 0.8F + 0.1F;
        }
        float diff = this.targetPosition - this.position;
        this.velocity += diff * 0.006F * this.fishType.getAggressiveness();
        this.velocity -= 4.0E-4F;
    }

    private void updateFastErratic() {
        if (this.directionChangeTimer > 12 + this.random.nextInt(18)) {
            this.directionChangeTimer = 0;
            this.targetPosition = this.random.nextFloat() * 0.9F + 0.05F;
            this.velocity += (this.random.nextFloat() - 0.5F) * 0.015F * this.fishType.getAggressiveness();
        }
        float diff = this.targetPosition - this.position;
        this.velocity += diff * 0.008F;
        this.velocity += (this.random.nextFloat() - 0.5F) * 0.004F;
        if (this.random.nextFloat() < 0.3F) {
            this.velocity -= 6.0E-4F;
        }
    }

    private void updateFastDart() {
        if (this.directionChangeTimer > 10 + this.random.nextInt(20)) {
            this.directionChangeTimer = 0;
            this.targetPosition = this.random.nextFloat() * 0.85F + 0.075F;
            float burst = (this.targetPosition - this.position) * 0.2F;
            this.velocity += burst * this.fishType.getAggressiveness();
        } else {
            float diff = this.targetPosition - this.position;
            this.velocity += diff * 0.005F;
        }
        if (this.random.nextFloat() < 0.2F) {
            this.velocity -= 8.0E-4F;
        }
    }

    private void updateLegendaryChaos() {
        if (this.random.nextFloat() < 0.15F * this.fishType.getAggressiveness()) {
            this.targetPosition = this.random.nextFloat() * 0.9F + 0.05F;
            this.velocity += (this.random.nextFloat() - 0.5F) * 0.02F;
        }
        this.velocity += (this.random.nextFloat() - 0.5F) * 0.005F;
        if (this.random.nextFloat() < 0.08F) {
            this.velocity *= -1.3F;
        }
        if (this.random.nextFloat() < 0.4F) {
            this.velocity += (this.random.nextFloat() - 0.5F) * 0.01F;
        }
    }

    private void updateLegendaryTeleport() {
        if (this.directionChangeTimer > 15 + this.random.nextInt(25)) {
            this.directionChangeTimer = 0;
            if (this.random.nextFloat() < 0.35F * this.fishType.getAggressiveness()) {
                this.position = this.random.nextFloat() * 0.9F + 0.05F;
                this.velocity = (this.random.nextFloat() - 0.5F) * 0.01F;
            } else {
                this.targetPosition = this.random.nextFloat() * 0.85F + 0.075F;
                this.velocity = (this.targetPosition - this.position) * 0.25F;
            }
        } else {
            this.velocity += (this.random.nextFloat() - 0.5F) * 0.006F;
        }
        if (this.random.nextFloat() < 0.3F) {
            this.velocity += (this.random.nextFloat() - 0.5F) * 0.008F;
        }
    }

    public float getPosition() {
        return this.position;
    }

    public FishType getFishType() {
        return this.fishType;
    }
}
