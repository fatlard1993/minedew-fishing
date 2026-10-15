package com.minedew.fishing.fish;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Whatever is on the end of the line, as far as the fight and the payout need to know.
 *
 * <p>{@link FishSpecies} is the five vanilla gives you. Anything else gets here through
 * {@link com.minedew.fishing.api.MinedewFishingApi#addBiter}, and fights, pays and is named by the
 * same rules: size is still rolled here and still decides how hard it is, the movement signature is
 * still fixed per quarry so it can be learned, and the name is still only shown once it is landed.
 *
 * <p>The two hooks with defaults are the ways a quarry can differ from a fish: it can be dressed
 * as it flies in, and it can refuse to be landed at all and come up alive instead.
 */
public interface Quarry {
    String getDisplayName();

    float getBaseSpeed();

    float getBaseAggressiveness();

    FishMovementPattern getPattern();

    /** Secondary pattern that periodically takes over, or null for a single-pattern quarry. */
    @Nullable FishMovementPattern getAccent();

    int getAccentPeriodTicks();

    int getAccentDurationTicks();

    /**
     * What a landed one pays out in: one piece as its own catch, and the size bonus on top. Null
     * pays nothing.
     */
    @Nullable Item getBonusItem();

    /** The mob flung to the player as the landing animation, or null to fling the items. */
    @Nullable EntityType<?> getDisplayEntity();

    boolean isJunk();

    /**
     * Called on the landing animation's mob once it is in the world, for a quarry whose display
     * entity is wearing somebody else's body. Theatre only: the mob is discarded on arrival.
     */
    default void dressDisplay(Mob shown, FishSize size) {}

    /**
     * Whether this one comes out of the water alive rather than into the player's hands.
     *
     * <p>Return true having put whatever came up into the world yourself. The fight still counts as
     * won - experience, advancements and the streak all treat it as landed - but nothing is handed
     * over and nothing flies in except a treasure chest won alongside it. Return false to be paid
     * out as a fish.
     */
    default boolean landAlive(ServerLevel level, ServerPlayer player, Vec3 origin, FishSize size) {
        return false;
    }
}
