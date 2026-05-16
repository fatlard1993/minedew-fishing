package com.minedew.fishing;

import com.minedew.fishing.client.MinigameManager;
import com.minedew.fishing.network.FishingMinigameEndPayload;
import com.minedew.fishing.network.FishingMinigameStartPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class MinedewFishingClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
            FishingMinigameStartPayload.TYPE,
            (payload, context) -> context.client().execute(
                () -> MinigameManager.getInstance().startMinigame(
                    payload.fishType(), payload.difficulty(), payload.fishingBobberEntityId()
                )
            )
        );
        ClientPlayNetworking.registerGlobalReceiver(
            FishingMinigameEndPayload.TYPE,
            (payload, context) -> context.client().execute(
                () -> MinigameManager.getInstance().endMinigame(payload.success())
            )
        );
        MinedewFishing.LOGGER.info("[minedew-fishing] Client initialized");
    }
}
