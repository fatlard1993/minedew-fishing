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
 * The whole minigame input, server side: the click IS the vanilla rod
 * right-click, which the server already receives as a use packet, so no
 * client mod or custom packet is needed at all. While an encounter owns the
 * current cast this observes the use as the player's click (dispatched to
 * {@code FishingEncounterManager#handleClick}), then cancels so vanilla's own
 * {@code FishingHook.retrieve()} cannot grant an immediate real catch (our
 * resolution forces the hook's nibble state, see
 * {@code MinedewFishingHookAccess}). Observe first, cancel second: the
 * cancellation must never eat the click.
 *
 * <p>The client-side early return leaves vanilla behavior alone there;
 * whatever the client shows locally is corrected by the server's resolution,
 * exactly like any other server-authoritative interaction.
 */
@Mixin(FishingRodItem.class)
public class FishingRodItemMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void minedew$onUse(Level world, Player user, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (world.isClientSide()) return;

        if (FishingEncounterManager.isPlayerInEncounter(user.getUUID())) {
            if (user instanceof ServerPlayer serverPlayer) {
                FishingEncounterManager.handleClick(serverPlayer);
            }
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
}
