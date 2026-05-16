package com.minedew.fishing.mixin;

import com.minedew.fishing.client.MinigameManager;
import com.minedew.fishing.fish.FishType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingHook.class)
public abstract class FishingBobberEntityMixin {
    @Unique
    private boolean minedew$minigameStarted = false;
    @Unique
    private int minedew$ticksSinceStart = 0;
    @Unique
    private FishType minedew$currentFish = null;
    @Unique
    private boolean minedew$fishBiting = false;
    @Unique
    private int minedew$biteStartTick = 0;
    @Unique
    private static final int HOOK_TIMING_WINDOW = 40;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        FishingHook self = (FishingHook) (Object) this;
        if (!self.level().isClientSide()) return;

        Player player = self.getPlayerOwner();
        if (player == null || player != Minecraft.getInstance().player) return;

        this.minedew$ticksSinceStart++;

        if (!this.minedew$minigameStarted && !this.minedew$fishBiting && this.minedew$ticksSinceStart > 40) {
            double yVel = self.getDeltaMovement().y;
            if (yVel < -0.05) {
                System.out.println("[Minedew] Fish bite detected! Y velocity: " + yVel);
                ClientLevel world = (ClientLevel) self.level();
                BlockPos pos = self.blockPosition();
                boolean isRaining = world.isRaining();
                int timeOfDay = (int) (world.getOverworldClockTime() % 24000L);
                RandomSource random = RandomSource.create();
                FishType fishType = FishType.getRandomFish(world.getBiome(pos), isRaining, timeOfDay, random);

                this.minedew$currentFish = fishType;
                this.minedew$fishBiting = true;
                this.minedew$biteStartTick = this.minedew$ticksSinceStart;
                MinigameManager.getInstance().startHookTimingWindow(fishType);
            }
        }

        if (this.minedew$fishBiting && !this.minedew$minigameStarted) {
            int ticksSinceBite = this.minedew$ticksSinceStart - this.minedew$biteStartTick;
            if (ticksSinceBite > HOOK_TIMING_WINDOW) {
                System.out.println("[Minedew] Missed hook timing! Fish escaped.");
                MinigameManager.getInstance().missedHookTiming();
                this.minedew$fishBiting = false;
                this.minedew$currentFish = null;
            }
        }
    }

    @Inject(method = "retrieve", at = @At("HEAD"), cancellable = true)
    private void onRetrieve(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
        FishingHook self = (FishingHook) (Object) this;
        Player player = self.getPlayerOwner();
        if (player == null) return;

        if (MinigameManager.getInstance().isMinigameActive()) {
            System.out.println("[Minedew] Blocking retrieval during minigame");
            cir.setReturnValue(0);
            return;
        }

        if (this.minedew$fishBiting && !this.minedew$minigameStarted) {
            int ticksSinceBite = this.minedew$ticksSinceStart - this.minedew$biteStartTick;
            if (ticksSinceBite <= HOOK_TIMING_WINDOW && this.minedew$currentFish != null) {
                System.out.println("[Minedew] Hook timing success! Starting minigame.");
                this.minedew$minigameStarted = true;
                this.minedew$fishBiting = false;
                MinigameManager.getInstance().startMinigame(
                    this.minedew$currentFish.name(),
                    this.minedew$currentFish.getDifficulty(),
                    self.getId()
                );
                cir.setReturnValue(0);
                return;
            }
        }

        if (!this.minedew$fishBiting && this.minedew$ticksSinceStart > 40 && !this.minedew$minigameStarted) {
            System.out.println("[Minedew] Clicked too early! Fish scared away.");
        } else if (this.minedew$minigameStarted) {
            cir.setReturnValue(0);
        }
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void onRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        FishingHook self = (FishingHook) (Object) this;
        if (self.level().isClientSide()) {
            MinigameManager manager = MinigameManager.getInstance();
            if (manager.isMinigameActive()) {
                manager.endMinigame(false);
            }
            if (manager.isHookTimingActive()) {
                manager.missedHookTiming();
            }
        }

        this.minedew$minigameStarted = false;
        this.minedew$ticksSinceStart = 0;
        this.minedew$currentFish = null;
        this.minedew$fishBiting = false;
        this.minedew$biteStartTick = 0;
    }
}
