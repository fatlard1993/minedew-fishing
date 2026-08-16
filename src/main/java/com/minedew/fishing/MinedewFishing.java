package com.minedew.fishing;

import com.minedew.fishing.encounter.FishingEncounterManager;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.api.VanillaItemOverride;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entirely server-side: the minigame's input is the vanilla rod right-click (observed server-side
 * in FishingRodItemMixin: one click sets the hook, then one click is one upward impulse on the
 * bobber), its output is Pandorical HUD pushes, so there is no client entrypoint, no custom packet,
 * and no client jar to install.
 */
public class MinedewFishing implements ModInitializer {
    public static final String MOD_ID = "minedew-fishing";
    public static final Logger LOGGER = LoggerFactory.getLogger("minedew-fishing");

    /**
     * Raw and cooked cod and salmon, reskinned as fillets for Pandorical clients.
     *
     * <p>The point is legibility of the size reward: a trophy fish paying out five whole cod reads
     * as five fish, while five fillets read as one big fish cut up, which is what actually happened.
     * Vanilla clients see the ordinary fish item and the ordinary name; nothing about the mod
     * depends on the reskin landing.
     */
    private static final String[][] FILLET_OVERRIDES = {
        {"minecraft:cod", "Cod Fillet", "textures/item/cod_fillet.png"},
        {"minecraft:cooked_cod", "Cooked Cod Fillet", "textures/item/cooked_cod_fillet.png"},
        {"minecraft:salmon", "Salmon Fillet", "textures/item/salmon_fillet.png"},
        {"minecraft:cooked_salmon", "Cooked Salmon Fillet", "textures/item/cooked_salmon_fillet.png"},
    };

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(FishingEncounterManager::tick);

        for (String[] override : FILLET_OVERRIDES) {
            PandoricalApi.content().overrideVanillaItem(override[0], new VanillaItemOverride()
                .name(override[1])
                .textureFrom(MOD_ID, override[2]));
        }
        // Also carries the minigame's own GUI art to clients
        PandoricalApi.content().registerModAssets(MOD_ID);

        LOGGER.info("[minedew-fishing] Initialized - server-authoritative fishing minigame ready");
    }
}
