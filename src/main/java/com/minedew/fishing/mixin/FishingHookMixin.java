package com.minedew.fishing.mixin;

import com.minedew.fishing.access.MinedewFishingHookAccess;
import com.minedew.fishing.encounter.FishingEncounterManager;
import com.minedew.fishing.fish.FishType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server-authoritative bite detection and encounter lifecycle glue for {@link FishingHook}.
 * Uses vanilla's own {@code nibble} countdown (the same signal that gates vanilla's catch roll in
 * {@code retrieve()}) as the authoritative "a fish is biting" event, rather than guessing from
 * bobber physics.
 */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin implements MinedewFishingHookAccess {
    @Shadow
    private int nibble;

    @Unique
    private int minedew$lastNibble = 0;

    @Override
    public void minedew$forceNibble() {
        this.nibble = 1;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void minedew$onTick(CallbackInfo ci) {
        FishingHook self = (FishingHook) (Object) this;
        if (self.level().isClientSide()) return;

        Player owner = self.getPlayerOwner();
        if (!(owner instanceof ServerPlayer serverPlayer)) return;

        boolean risingEdge = this.minedew$lastNibble <= 0 && this.nibble > 0;
        this.minedew$lastNibble = this.nibble;

        if (!risingEdge) return;
        if (FishingEncounterManager.isPlayerInEncounter(serverPlayer.getUUID())) return;

        ServerLevel world = (ServerLevel) self.level();
        BlockPos pos = self.blockPosition();
        boolean isRaining = world.isRaining();
        int timeOfDay = (int) (world.getOverworldClockTime() % 24000L);
        RandomSource random = RandomSource.create();
        FishType fishType = FishType.getRandomFish(world.getBiome(pos), isRaining, timeOfDay, random);

        FishingEncounterManager.startEncounter(serverPlayer, self, fishType);
    }

    @Inject(method = "remove", at = @At("HEAD"))
    private void minedew$onRemove(Entity.RemovalReason reason, CallbackInfo ci) {
        FishingHook self = (FishingHook) (Object) this;
        if (self.level().isClientSide()) return;

        Player owner = self.getPlayerOwner();
        if (owner instanceof ServerPlayer serverPlayer) {
            FishingEncounterManager.abortIfActive(serverPlayer);
        }
    }
}
