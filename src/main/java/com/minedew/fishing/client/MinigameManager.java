package com.minedew.fishing.client;

import com.minedew.fishing.fish.FishType;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class MinigameManager {
    private static final MinigameManager INSTANCE = new MinigameManager();
    private MinigameState currentMinigame = null;
    private boolean wasMousePressed = false;
    private boolean hookTimingActive = false;
    private FishType hookTimingFish = null;
    private int hookTimingTickCount = 0;
    private static final int HOOK_TIMING_MAX_TICKS = 40;

    private MinigameManager() {
    }

    public static MinigameManager getInstance() {
        return INSTANCE;
    }

    public void startHookTimingWindow(FishType fishType) {
        this.hookTimingActive = true;
        this.hookTimingFish = fishType;
        this.hookTimingTickCount = 0;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.playSound(SoundEvents.FISHING_BOBBER_SPLASH, 1.0F, 2.0F);
        }
    }

    public void missedHookTiming() {
        if (this.hookTimingActive) {
            this.hookTimingActive = false;
            this.hookTimingFish = null;
            this.hookTimingTickCount = 0;
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                client.player.playSound(SoundEvents.ITEM_BREAK.value(), 1.0F, 0.5F);
            }
        }
    }

    public boolean isHookTimingActive() {
        return this.hookTimingActive;
    }

    public int getHookTimingTickCount() {
        return this.hookTimingTickCount;
    }

    public float getHookTimingProgress() {
        return 1.0F - (float) this.hookTimingTickCount / HOOK_TIMING_MAX_TICKS;
    }

    public String getHookTimingFishName() {
        return this.hookTimingFish != null ? this.hookTimingFish.getDisplayName() : "Unknown";
    }

    public void startMinigame(String fishTypeName, int difficulty, int fishingBobberEntityId) {
        this.hookTimingActive = false;
        this.hookTimingFish = null;
        this.hookTimingTickCount = 0;

        try {
            FishType fishType = FishType.valueOf(fishTypeName);
            this.currentMinigame = new MinigameState(fishType, fishingBobberEntityId);
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                client.player.playSound(SoundEvents.FISHING_BOBBER_RETRIEVE, 1.0F, 1.2F);
            }
        } catch (IllegalArgumentException e) {
            this.currentMinigame = new MinigameState(FishType.CARP, fishingBobberEntityId);
        }
    }

    public void endMinigame(boolean success) {
        if (this.currentMinigame != null) {
            Minecraft client = Minecraft.getInstance();
            if (client.player != null) {
                if (success) {
                    client.player.playSound(SoundEvents.PLAYER_LEVELUP, 0.5F, 1.5F);
                } else {
                    client.player.playSound(SoundEvents.ITEM_BREAK.value(), 1.0F, 0.8F);
                }
            }
            this.currentMinigame = null;
            this.wasMousePressed = false;
        }
    }

    public void tick() {
        if (this.hookTimingActive) {
            this.hookTimingTickCount++;
        } else if (this.currentMinigame != null) {
            Minecraft client = Minecraft.getInstance();

            if (client.level != null) {
                FishingHook bobber = (FishingHook) client.level.getEntity(this.currentMinigame.getFishingBobberEntityId());
                if (bobber == null || bobber.isRemoved()) {
                    this.endMinigame(false);
                    return;
                }
            }

            boolean isMousePressed = client.mouseHandler.isLeftPressed() || client.options.keyUse.isDown();
            if (isMousePressed && !this.wasMousePressed && client.player != null) {
                client.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.3F, 1.8F);
            }

            this.wasMousePressed = isMousePressed;
            boolean wasCaptured = this.currentMinigame.isFishCaptured();
            boolean wasTreasureCaptured = this.currentMinigame.isTreasureCaptured();

            this.currentMinigame.tick(isMousePressed);

            boolean isCaptured = this.currentMinigame.isFishCaptured();
            boolean isTreasureCaptured = this.currentMinigame.isTreasureCaptured();

            if (!wasCaptured && isCaptured && client.player != null) {
                client.player.playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 0.4F, 2.0F);
            }

            if (this.currentMinigame.hasTreasure() && !wasTreasureCaptured && isTreasureCaptured && client.player != null) {
                client.player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5F, 1.5F);
            }

            if (this.currentMinigame.isSuccess()) {
                this.handleSuccess();
            } else if (this.currentMinigame.isFailure()) {
                this.handleFailure();
            }
        }
    }

    private void handleSuccess() {
        Minecraft client = Minecraft.getInstance();
        if (client.level != null && client.gameMode != null && client.player != null) {
            boolean treasureCaptured = this.currentMinigame.isTreasureComplete();
            this.endMinigame(true);

            if (treasureCaptured) {
                this.giveTreasureRewards(client);
            }

            client.execute(() -> {
                if (client.player != null) {
                    boolean hasFishingRod = client.player.getMainHandItem().getItem() instanceof FishingRodItem
                        || client.player.getOffhandItem().getItem() instanceof FishingRodItem;
                    if (hasFishingRod) {
                        client.options.keyAttack.setDown(true);
                        client.execute(() -> {
                            try {
                                Thread.sleep(100L);
                            } catch (InterruptedException ex) {
                                Thread.currentThread().interrupt();
                            }
                            if (client.options != null) {
                                client.options.keyAttack.setDown(false);
                            }
                        });
                    }
                }
            });
        } else {
            this.endMinigame(true);
        }
    }

    private void giveTreasureRewards(Minecraft client) {
        if (client.player != null && client.level != null) {
            RandomSource random = RandomSource.create();
            ItemStack treasure;
            float roll = random.nextFloat();

            if (roll < 0.05F) {
                treasure = new ItemStack(Items.DIAMOND);
            } else if (roll < 0.15F) {
                treasure = new ItemStack(Items.GOLD_INGOT, 1 + random.nextInt(2));
            } else if (roll < 0.3F) {
                treasure = new ItemStack(Items.EMERALD, 1 + random.nextInt(3));
            } else if (roll < 0.45F) {
                treasure = new ItemStack(Items.IRON_INGOT, 2 + random.nextInt(4));
            } else if (roll < 0.6F) {
                treasure = new ItemStack(Items.NAUTILUS_SHELL);
            } else if (roll < 0.75F) {
                treasure = new ItemStack(Items.NAME_TAG);
            } else if (roll < 0.9F) {
                treasure = new ItemStack(Items.LAPIS_LAZULI, 3 + random.nextInt(5));
            } else {
                treasure = new ItemStack(Items.EXPERIENCE_BOTTLE, 3 + random.nextInt(6));
            }

            if (!client.player.getInventory().add(treasure)) {
                client.player.drop(treasure, false);
            }

            client.player.playSound(SoundEvents.PLAYER_LEVELUP, 0.7F, 1.8F);
            System.out.println("[Minedew] Treasure reward: " + treasure.getItem().getName(treasure).getString());
        }
    }

    private void handleFailure() {
        this.endMinigame(false);
    }

    public MinigameState getCurrentMinigame() {
        return this.currentMinigame;
    }

    public boolean isMinigameActive() {
        return this.currentMinigame != null && this.currentMinigame.isActive();
    }
}
