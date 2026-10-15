package com.minedew.fishing.api;

import com.minedew.fishing.fish.Quarry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lets another mod put something on the line that vanilla's fishing table never would.
 *
 * <p>Asked on every bite, before vanilla's table is rolled, in the order biters were added; the
 * first to answer takes the bite and vanilla's roll is never made. Most bites should be refused, so
 * keep the answer cheap and return null early: a biter that answers too often has quietly replaced
 * fishing.
 *
 * <p>What comes back fights like anything else here. The size is rolled the same way, with rain,
 * deep water, night and a player's bad run all counted, and the fight's difficulty comes from it.
 */
public final class MinedewFishingApi {
    private MinedewFishingApi() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("minedew-fishing");

    @FunctionalInterface
    public interface Biter {
        /** What bit, or null to leave this bite to vanilla's table. */
        @Nullable Quarry bite(ServerLevel level, ServerPlayer player, FishingHook hook);
    }

    private static final List<Biter> BITERS = new ArrayList<>();

    /**
     * Biters already reported as broken. One that throws throws on every bite, and saying so each
     * time buries the line that names it.
     */
    private static final Set<Class<?>> COMPLAINED = new HashSet<>();

    public static void addBiter(Biter biter) {
        BITERS.add(biter);
    }

    /** The first biter's answer, or null when every one of them passed. */
    public static @Nullable Quarry bite(ServerLevel level, ServerPlayer player, FishingHook hook) {
        for (Biter biter : BITERS) {
            try {
                Quarry quarry = biter.bite(level, player, hook);
                if (quarry != null) return quarry;
            } catch (RuntimeException error) {
                if (COMPLAINED.add(biter.getClass())) {
                    LOGGER.warn("[minedew-fishing] {} threw on a bite; leaving its bites to vanilla",
                        biter.getClass().getName(), error);
                }
            }
        }
        return null;
    }
}
