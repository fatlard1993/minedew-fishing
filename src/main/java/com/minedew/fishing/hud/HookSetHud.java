package com.minedew.fishing.hud;

import com.minedew.fishing.encounter.FishingEncounter;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.ComponentUpdateBuilder;
import justfatlard.pandorical.api.HudBuilder;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.ComponentUpdate;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

/**
 * The hook set: one telegraphed window around the reticle, before the bar fight opens.
 *
 * <p>A ring winds up slowly while the tell runs, then snaps bright and fast the moment clicking
 * becomes correct. It is a {@code particle_burst}, so the client animates the orbit itself off a
 * wall-clock phase and the server only pushes the two or three prop changes that mark the phase
 * transitions: no per-tick traffic for a continuously moving thing.
 *
 * <p>Nothing here names the species. What bit is meant to be read from how it fights, not announced
 * before the fight starts; the difficulty stars are the one tell given away, because the weight on
 * the line is obvious in the real world too.
 */
public final class HookSetHud {
    private HookSetHud() {}

    public static final String OVERLAY_ID = "minedew-fishing:hookset";

    private static final String COMPONENT_RING = "ring";
    private static final String COMPONENT_PROMPT = "prompt";

    private static final float TELL_SPEED = 70F;
    private static final float COMMIT_SPEED = 300F;
    private static final float TELL_RADIUS = 16F;
    private static final float COMMIT_RADIUS = 22F;
    private static final String TELL_COLOR = "#5599FF";
    private static final String COMMIT_COLOR = "#55FF55";

    public static void show(FishingEncounter encounter) {
        HudBuilder hud = new HudBuilder(OVERLAY_ID)
            .anchor("center")
            .offset(0, -60)
            .particleBurst(COMPONENT_RING, -20, -20, 40, 40, 8, TELL_RADIUS, TELL_SPEED,
                Map.of(ComponentType.PROP_COLOR, TELL_COLOR))
            .text("stars", -18, -42, MinigameHud.stars(encounter.difficulty))
            .text(COMPONENT_PROMPT, -14, 26, "...");
        PandoricalApi.hud().show(encounter.player, hud.build());
    }

    /** Called on each hook-set phase change: tell winds up, commit says click. */
    public static void updatePhase(FishingEncounter encounter) {
        boolean commit = encounter.hookWindowOpen();
        List<ComponentUpdate> updates = List.of(
            new ComponentUpdateBuilder(COMPONENT_RING)
                .prop(ComponentType.PROP_COLOR, commit ? COMMIT_COLOR : TELL_COLOR)
                .prop(ComponentType.PROP_SPEED, String.valueOf(commit ? COMMIT_SPEED : TELL_SPEED))
                .prop(ComponentType.PROP_RADIUS, String.valueOf(commit ? COMMIT_RADIUS : TELL_RADIUS))
                .build(),
            new ComponentUpdateBuilder(COMPONENT_PROMPT)
                .prop(ComponentType.PROP_TEXT, commit ? "SET!" : "...")
                .build());
        PandoricalApi.hud().update(encounter.player, OVERLAY_ID, updates);
    }

    public static void hide(ServerPlayer player) {
        PandoricalApi.hud().hide(player, OVERLAY_ID);
    }
}
