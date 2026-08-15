package com.minedew.fishing;

import com.minedew.fishing.encounter.FishingEncounterManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entirely server-side: the minigame's input is the vanilla rod right-click
 * (observed server-side in FishingRodItemMixin), its output is Pandorical HUD
 * pushes, so there is no client entrypoint, no custom packet, and no client
 * jar to install.
 */
public class MinedewFishing implements ModInitializer {
    public static final String MOD_ID = "minedew-fishing";
    public static final Logger LOGGER = LoggerFactory.getLogger("minedew-fishing");

    @Override
    public void onInitialize() {
        ServerTickEvents.END_SERVER_TICK.register(FishingEncounterManager::tick);

        LOGGER.info("[minedew-fishing] Initialized - server-authoritative fishing minigame ready");
    }
}
