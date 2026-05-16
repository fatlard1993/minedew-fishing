package com.minedew.fishing.client;

import com.minedew.fishing.fish.FishBehavior;
import com.minedew.fishing.fish.FishType;
import net.minecraft.util.RandomSource;

public class MinigameState {
    private final FishBehavior fishBehavior;
    private final int fishingBobberEntityId;
    private float captureBarPosition;
    private float captureBarVelocity;
    private float progress;
    private boolean isActive;
    private boolean hasTreasure;
    private float treasurePosition;
    private float treasureProgress;

    private static final float TREASURE_HEIGHT = 0.12F;
    private static final float TREASURE_PROGRESS_GAIN_RATE = 0.01F;
    private static final float TREASURE_PROGRESS_LOSS_RATE = 0.008F;
    private static final float CAPTURE_BAR_HEIGHT = 0.18F;
    private static final float BAR_RISE_ACCELERATION = 0.006F;
    private static final float BAR_GRAVITY = 0.004F;
    private static final float BAR_MAX_VELOCITY = 0.032F;
    private static final float BAR_DAMPING = 0.92F;
    private static final float BAR_FRICTION = 0.96F;
    private static final float PROGRESS_GAIN_RATE = 0.018F;
    private static final float PROGRESS_LOSS_RATE = 0.012F;
    private static final int MAX_DURATION_TICKS = 600;

    private int tickCount;

    public MinigameState(FishType fishType, int fishingBobberEntityId) {
        RandomSource random = RandomSource.create();
        this.fishBehavior = new FishBehavior(fishType, random);
        this.fishingBobberEntityId = fishingBobberEntityId;
        this.captureBarPosition = 0.5F;
        this.captureBarVelocity = 0.0F;
        this.progress = 0.0F;
        this.isActive = true;
        this.tickCount = 0;
        this.hasTreasure = random.nextFloat() < 0.15F + random.nextFloat() * 0.1F;
        if (this.hasTreasure) {
            this.treasurePosition = 0.15F + random.nextFloat() * 0.7F;
            this.treasureProgress = 0.0F;
            System.out.println("[Minedew] Treasure chest appeared at position: " + this.treasurePosition);
        }
    }

    public void tick(boolean isPlayerHolding) {
        if (!this.isActive) return;

        this.tickCount++;
        this.fishBehavior.update();

        if (isPlayerHolding) {
            this.captureBarVelocity += BAR_RISE_ACCELERATION;
        } else {
            this.captureBarVelocity -= BAR_GRAVITY;
            this.captureBarVelocity *= BAR_FRICTION;
        }

        this.captureBarVelocity *= BAR_DAMPING;
        this.captureBarVelocity = Math.max(-BAR_MAX_VELOCITY, Math.min(BAR_MAX_VELOCITY, this.captureBarVelocity));
        this.captureBarPosition += this.captureBarVelocity;

        if (this.captureBarPosition < 0.0F) {
            this.captureBarPosition = 0.0F;
            this.captureBarVelocity = Math.abs(this.captureBarVelocity) * 0.25F;
        } else if (this.captureBarPosition + CAPTURE_BAR_HEIGHT > 1.0F) {
            this.captureBarPosition = 1.0F - CAPTURE_BAR_HEIGHT;
            this.captureBarVelocity = -Math.abs(this.captureBarVelocity) * 0.25F;
        }

        if (this.isFishCaptured()) {
            this.progress += PROGRESS_GAIN_RATE;
            if (this.progress >= 1.0F) {
                this.progress = 1.0F;
                this.isActive = false;
            }
        } else {
            this.progress -= PROGRESS_LOSS_RATE;
            if (this.progress < 0.0F) {
                this.progress = 0.0F;
            }
        }

        if (this.hasTreasure && this.treasureProgress < 1.0F) {
            if (this.isTreasureCaptured()) {
                this.treasureProgress += TREASURE_PROGRESS_GAIN_RATE;
                if (this.treasureProgress >= 1.0F) {
                    this.treasureProgress = 1.0F;
                    System.out.println("[Minedew] Treasure chest captured!");
                }
            } else {
                this.treasureProgress -= TREASURE_PROGRESS_LOSS_RATE;
                if (this.treasureProgress < 0.0F) {
                    this.treasureProgress = 0.0F;
                }
            }
        }
    }

    public boolean isFishCaptured() {
        float fishPos = this.fishBehavior.getPosition();
        return fishPos >= this.captureBarPosition && fishPos <= this.captureBarPosition + CAPTURE_BAR_HEIGHT;
    }

    public boolean isTreasureCaptured() {
        if (!this.hasTreasure) return false;
        return this.treasurePosition >= this.captureBarPosition
            && this.treasurePosition <= this.captureBarPosition + CAPTURE_BAR_HEIGHT;
    }

    public boolean isSuccess() {
        return this.progress >= 1.0F;
    }

    public boolean isFailure() {
        return this.tickCount >= MAX_DURATION_TICKS;
    }

    public float getTimeRemaining() {
        return Math.max(0.0F, 1.0F - (float) this.tickCount / MAX_DURATION_TICKS);
    }

    public float getFishPosition() {
        return this.fishBehavior.getPosition();
    }

    public float getCaptureBarPosition() {
        return this.captureBarPosition;
    }

    public float getCaptureBarHeight() {
        return CAPTURE_BAR_HEIGHT;
    }

    public float getProgress() {
        return this.progress;
    }

    public boolean isActive() {
        return this.isActive;
    }

    public void setActive(boolean active) {
        this.isActive = active;
    }

    public FishType getFishType() {
        return this.fishBehavior.getFishType();
    }

    public int getFishingBobberEntityId() {
        return this.fishingBobberEntityId;
    }

    public int getTickCount() {
        return this.tickCount;
    }

    public boolean hasTreasure() {
        return this.hasTreasure;
    }

    public float getTreasurePosition() {
        return this.treasurePosition;
    }

    public float getTreasureProgress() {
        return this.treasureProgress;
    }

    public float getTreasureHeight() {
        return TREASURE_HEIGHT;
    }

    public boolean isTreasureComplete() {
        return this.hasTreasure && this.treasureProgress >= 1.0F;
    }
}
