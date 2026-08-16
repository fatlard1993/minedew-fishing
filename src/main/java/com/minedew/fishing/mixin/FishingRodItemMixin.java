package com.minedew.fishing.mixin;

import com.minedew.fishing.encounter.FishingEncounterManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The whole minigame input, server side: a rod right-click is one upward impulse on the bobber, and
 * it arrives as the ordinary vanilla use packet the server already receives, so no client mod and no
 * custom packet are needed at all. Tapping faster lifts the bobber, easing off lets it sink.
 *
 * <p>While an encounter owns the current cast this observes the use as an impulse (dispatched to
 * {@code FishingEncounterManager#handleReelClick}), then cancels so vanilla's own
 * {@code FishingHook.retrieve()} cannot end the cast with an immediate real catch. The encounter
 * hands out the loot itself instead (see {@code FishingEncounterManager#grantCatch}), because it was
 * rolled up front so the fight and the payout agree. Observe first, cancel second: the cancellation
 * must never eat the click.
 *
 * <p>The client-side early return leaves vanilla behavior alone there: the client still swings the
 * rod and plays its own reel sound on every tap, which reads as reeling and gives the tap immediate
 * local feedback, while the authoritative result comes back from the server exactly like any other
 * server-authoritative interaction.
 */
@Mixin(FishingRodItem.class)
public class FishingRodItemMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void minedew$onUse(Level world, Player user, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (world.isClientSide()) return;

        if (FishingEncounterManager.isPlayerInEncounter(user.getUUID())) {
            if (user instanceof ServerPlayer serverPlayer) {
                FishingEncounterManager.handleReelClick(serverPlayer);
            }
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
