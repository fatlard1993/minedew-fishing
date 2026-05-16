package com.minedew.fishing.network;

import com.minedew.fishing.MinedewFishing;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FishingMinigameStartPayload(String fishType, int difficulty, int fishingBobberEntityId) implements CustomPacketPayload {
    public static final Type<FishingMinigameStartPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(MinedewFishing.MOD_ID, "start_minigame"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FishingMinigameStartPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, FishingMinigameStartPayload::fishType,
            ByteBufCodecs.VAR_INT, FishingMinigameStartPayload::difficulty,
            ByteBufCodecs.VAR_INT, FishingMinigameStartPayload::fishingBobberEntityId,
            FishingMinigameStartPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
