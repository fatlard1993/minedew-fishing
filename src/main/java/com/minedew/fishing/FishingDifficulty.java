package com.minedew.fishing;

import java.util.Locale;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;

/**
 * How hard the fight is, on top of how big the fish is: the server's choice, or an op's for one
 * player, so a child learning the game and a player who finds it too easy can share a river.
 *
 * <p>Read {@link com.minedew.fishing.encounter.MinigameTuning} before moving these. The meter's
 * drain is only ever eased, never raised: above about a 52% break-even nobody can keep a fish in
 * the bar long enough, so Hard comes from a narrower bar and a quicker fish instead. And the
 * fish's speed stays under the bobber's at every level, or it is not hard but unfair. Easiest
 * lets a bar parked mid-track land the smaller fish; for a player just learning, that is a fish.
 */
public enum FishingDifficulty implements StringRepresentable {
    EASIEST("Easiest", 1.35F, 0.6F, 0.75F, 0.7F),
    EASY("Easy", 1.15F, 0.8F, 0.9F, 0.85F),
    NORMAL("Normal", 1F, 1F, 1F, 1F),
    HARD("Hard", 0.9F, 1F, 1.1F, 1.1F);

    public static final Codec<FishingDifficulty> CODEC = StringRepresentable.fromEnum(FishingDifficulty::values);

    /** Set by an op for this player, over the server's. Absent means the server's. */
    public static final AttachmentType<FishingDifficulty> CHOSEN = AttachmentRegistry.<FishingDifficulty>builder()
        .persistent(CODEC)
        .copyOnDeath()
        .buildAndRegister(Identifier.fromNamespaceAndPath(MinedewFishing.MOD_ID, "difficulty"));

    public final String label;
    /** The bobber's height, times the fish's own. */
    public final float bar;
    /** How fast the meter falls with the fish outside the bar, times the fish's own. */
    public final float drain;
    /** The fish's top speed, times its own. */
    public final float fishSpeed;
    /** How jumpy its pattern is, times its own. */
    public final float erratic;

    FishingDifficulty(String label, float bar, float drain, float fishSpeed, float erratic) {
        this.label = label;
        this.bar = bar;
        this.drain = drain;
        this.fishSpeed = fishSpeed;
        this.erratic = erratic;
    }

    /** Loads the class, which is what registers the attachment; it must happen at init. */
    public static void init() {}

    public static FishingDifficulty of(ServerPlayer player) {
        FishingDifficulty chosen = player.getAttached(CHOSEN);
        return chosen != null ? chosen : FishingConfig.difficulty();
    }

    /** Null clears it, back to the server's. */
    public static void choose(ServerPlayer player, FishingDifficulty difficulty) {
        if (difficulty == null) player.removeAttached(CHOSEN);
        else player.setAttached(CHOSEN, difficulty);
    }

    public static FishingDifficulty chosen(ServerPlayer player) {
        return player.getAttached(CHOSEN);
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Null for anything that is not one of them. */
    public static FishingDifficulty named(String name) {
        for (FishingDifficulty difficulty : values()) {
            if (difficulty.getSerializedName().equals(name.trim().toLowerCase(Locale.ROOT))) return difficulty;
        }
        return null;
    }
}
