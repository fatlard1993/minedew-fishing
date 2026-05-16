package com.minedew.fishing;

import com.minedew.fishing.network.FishingMinigameEndPayload;
import com.minedew.fishing.network.FishingMinigameStartPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MinedewFishing implements ModInitializer {
    public static final String MOD_ID = "minedew-fishing";
    public static final Logger LOGGER = LoggerFactory.getLogger("minedew-fishing");

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(FishingMinigameStartPayload.TYPE, FishingMinigameStartPayload.STREAM_CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FishingMinigameEndPayload.TYPE, FishingMinigameEndPayload.STREAM_CODEC);
        LOGGER.info("[minedew-fishing] Initialized - Stardew Valley fishing minigame ready!");
    }
}
