package com.minedew.fishing.mixin;

import com.minedew.fishing.access.MinedewFishingHookAccess;
import com.minedew.fishing.encounter.FishingEncounterManager;
import com.minedew.fishing.encounter.MinigameTuning;
import com.minedew.fishing.fish.HookedCatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server-authoritative bite detection and encounter lifecycle glue for {@link FishingHook}.
 * Uses vanilla's own {@code nibble} countdown (the same signal that gates vanilla's catch roll in
 * {@code retrieve()}) as the authoritative "something is biting" event, rather than guessing from
 * bobber physics.
 *
 * <p>{@code nibble} is also the hook-set window itself. Vanilla sets it on the same tick it plays
 * the splash, spawns the bubble burst and flips {@code DATA_BITING}, which is what pulls the bobber
 * under and keeps it under until the countdown ends. Handing that number to the encounter makes the
 * window and the cue the same thing, and means the mod does not have to draw a telegraph of its own.
 *
 * <p>Once the fight is on, the countdown is held up instead (see
 * {@link MinigameTuning#LINE_TAUT_NIBBLE_TICKS}): the line stays taut for the whole fight, and
 * vanilla cannot start a second bite cycle underneath a fight already in progress.
 */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin implements MinedewFishingHookAccess {
    @Shadow
    private int nibble;

    @Shadow
    @Final
    private int luck;

    @Unique
    private int minedew$lastNibble = 0;

    @Override
    public int minedew$luck() {
        return this.luck;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void minedew$onTick(CallbackInfo ci) {
        FishingHook self = (FishingHook) (Object) this;
        if (self.level().isClientSide()) return;

        Player owner = self.getPlayerOwner();
        if (!(owner instanceof ServerPlayer serverPlayer)) return;

        // Held up before the edge is read, so holding it can never look like a fresh bite
        if (FishingEncounterManager.isFightingWithHook(serverPlayer.getUUID(), self.getId())) {
            this.nibble = Math.max(this.nibble, MinigameTuning.LINE_TAUT_NIBBLE_TICKS);
        }

        boolean risingEdge = this.minedew$lastNibble <= 0 && this.nibble > 0;
        this.minedew$lastNibble = this.nibble;

        if (!risingEdge) return;
        if (FishingEncounterManager.isPlayerInEncounter(serverPlayer.getUUID())) return;

        ItemStack rod = minedew$rodStack(serverPlayer);
        if (rod == null) return;

        // Roll vanilla's own fishing table now, so the fight's identity and the eventual payout are
        // the same thing rather than two independent rolls that can disagree
        HookedCatch hooked = HookedCatch.roll((ServerLevel) self.level(), serverPlayer, self, rod);
        // this.nibble was set on this very tick, alongside the splash and the bobber going under:
        // it is the full length of the cue, so it is the full length of the hook-set window
        FishingEncounterManager.startEncounter(serverPlayer, self, hooked, this.nibble);
    }

    @Unique
    private static ItemStack minedew$rodStack(ServerPlayer player) {
        if (player.getMainHandItem().getItem() instanceof FishingRodItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof FishingRodItem) return player.getOffhandItem();
        return null;
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
