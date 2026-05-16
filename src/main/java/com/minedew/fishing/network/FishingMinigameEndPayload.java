package com.minedew.fishing.network;

import com.minedew.fishing.MinedewFishing;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record FishingMinigameEndPayload(boolean success) implements CustomPacketPayload {
    public static final Type<FishingMinigameEndPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(MinedewFishing.MOD_ID, "end_minigame"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FishingMinigameEndPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.BOOL, FishingMinigameEndPayload::success,
            FishingMinigameEndPayload::new
        );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
