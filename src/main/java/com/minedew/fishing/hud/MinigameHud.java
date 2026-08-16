package com.minedew.fishing.hud;

import com.minedew.fishing.encounter.FishingEncounter;
import com.minedew.fishing.encounter.MinigameTuning;
import justfatlard.pandorical.api.ComponentBuilder;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.ComponentUpdateBuilder;
import justfatlard.pandorical.api.HudBuilder;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.ComponentUpdate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The fight's on-screen half: a water column with the marker and the player's bobber bar in it, a
 * catch gauge beside it, and the treasure chest once it surfaces. Everything here is a declarative
 * Pandorical HUD push; the client runs no game logic and only interpolates between the values the
 * server sends.
 *
 * <p>The species is deliberately not named anywhere on this overlay. Only the difficulty stars show,
 * so what is on the line has to be read from how it moves; the name is revealed on the catch.
 *
 * <p>Layout note: the overlay's screen position is resolved from the bounding box of its root
 * components, so every animated component stays strictly inside the static track and gauge frames.
 * If a moving part could poke outside them the whole overlay would slide around as it moved.
 *
 * <p>Pixel geometry mirrors {@code generate_textures.py}, which draws each texture at exactly the
 * size it is blitted at.
 */
public final class MinigameHud {
    private MinigameHud() {}

    public static final String OVERLAY_ID = "minedew-fishing:catch";

    private static final String TEXTURE_TRACK = "minedew-fishing:textures/gui/track.png";
    private static final String TEXTURE_GAUGE = "minedew-fishing:textures/gui/gauge.png";
    private static final String TEXTURE_GAUGE_FILL = "minedew-fishing:textures/gui/gauge_fill.png";
    private static final String TEXTURE_MARKER = "minedew-fishing:textures/gui/fish.png";
    private static final String TEXTURE_CHEST = "minedew-fishing:textures/gui/chest.png";
    private static final String TEXTURE_CHEST_OPEN = "minedew-fishing:textures/gui/chest_open.png";

    // Screen placement: right-hand side, high enough to clear the hotbar at any GUI scale
    private static final String ANCHOR = "top_right";
    private static final int MARGIN_RIGHT = 24;
    private static final int MARGIN_TOP = 28;

    // Track frame, and the water column inside its 3px border
    private static final int TRACK_X = 0;
    private static final int TRACK_Y = 14;
    private static final int TRACK_W = 26;
    private static final int TRACK_H = 150;
    private static final int INNER_X = TRACK_X + 3;
    private static final int INNER_Y = TRACK_Y + 3;
    private static final int INNER_W = 20;
    private static final int INNER_H = 144;

    // Catch gauge frame, and its fill inside a 1px border
    private static final int GAUGE_X = 30;
    private static final int GAUGE_Y = TRACK_Y;
    private static final int GAUGE_W = 8;
    private static final int GAUGE_H = TRACK_H;
    private static final int FILL_X = GAUGE_X + 1;
    private static final int FILL_Y = GAUGE_Y + 2;
    private static final int FILL_W = 6;
    private static final int FILL_H = 146;

    private static final int MARKER_SIZE = 12;
    private static final int CHEST_SIZE = 10;

    // Bobber is a plain color fill so it can pulse: a textured sprite ignores the color prop
    private static final int BOBBER_X = INNER_X + 1;
    private static final int BOBBER_W = INNER_W - 2;
    private static final String BOBBER_COLOR_IDLE = "#66E8F4FF";
    private static final String BOBBER_COLOR_ON_FISH = "#9959E86A";

    // The chest's capture meter is a ring that closes in on it as it fills, which keeps the
    // readout attached to the thing being captured instead of adding a second gauge
    private static final String CHEST_RING_COLOR = "#FFD24A";
    private static final String CHEST_RING_HIDDEN = "#00FFD24A";
    private static final float CHEST_RING_MAX_RADIUS = 11F;
    private static final float CHEST_RING_MIN_RADIUS = 3F;

    private static final String COMPONENT_BOBBER = "bobber";
    private static final String COMPONENT_MARKER = "marker";
    private static final String COMPONENT_CHEST = "chest";
    private static final String COMPONENT_CHEST_RING = "chest_ring";
    private static final String COMPONENT_FILL = "gauge_fill";

    public static boolean canRender(ServerPlayer player) {
        return PandoricalApi.isAvailable(player) && PandoricalApi.hasCapability(player, "hud");
    }

    public static void show(FishingEncounter encounter) {
        int bobberHeight = Math.round(encounter.bobberSize * INNER_H);

        HudBuilder hud = new HudBuilder(OVERLAY_ID)
            .anchor(ANCHOR)
            .offset(MARGIN_RIGHT, MARGIN_TOP)
            .text("stars", TRACK_X, 2, stars(encounter.difficulty))
            .component(new ComponentBuilder("track", ComponentType.SPRITE)
                .bounds(TRACK_X, TRACK_Y, TRACK_W, TRACK_H)
                .prop(ComponentType.PROP_TEXTURE, TEXTURE_TRACK))
            .component(new ComponentBuilder(COMPONENT_BOBBER, ComponentType.SPRITE)
                .bounds(BOBBER_X, bobberTopY(encounter.bobberPosition, encounter.bobberSize),
                    BOBBER_W, bobberHeight)
                .prop(ComponentType.PROP_COLOR, BOBBER_COLOR_IDLE))
            .component(new ComponentBuilder(COMPONENT_MARKER, ComponentType.SPRITE)
                .bounds(markerX(MARKER_SIZE), markerY(encounter.fish.position(), MARKER_SIZE),
                    MARKER_SIZE, MARKER_SIZE)
                .prop(ComponentType.PROP_TEXTURE, TEXTURE_MARKER))
            .component(new ComponentBuilder("gauge", ComponentType.SPRITE)
                .bounds(GAUGE_X, GAUGE_Y, GAUGE_W, GAUGE_H)
                .prop(ComponentType.PROP_TEXTURE, TEXTURE_GAUGE))
            .component(gaugeFill(encounter.progress));

        if (encounter.hasTreasure) {
            // Declared up front but sized to nothing: components cannot be added to a live overlay,
            // so the chest is revealed later by giving it a size (the poopsmith flush-icon pattern)
            hud.component(new ComponentBuilder(COMPONENT_CHEST, ComponentType.SPRITE)
                    .bounds(markerX(CHEST_SIZE), markerY(encounter.treasurePosition, CHEST_SIZE), 0, 0)
                    .prop(ComponentType.PROP_TEXTURE, TEXTURE_CHEST))
                .component(new ComponentBuilder(COMPONENT_CHEST_RING, ComponentType.PARTICLE_BURST)
                    .bounds(markerX(CHEST_SIZE), markerY(encounter.treasurePosition, CHEST_SIZE),
                        CHEST_SIZE, CHEST_SIZE)
                    .props(Map.of(
                        ComponentType.PROP_PARTICLE_COUNT, "5",
                        ComponentType.PROP_PARTICLE_SIZE, "2",
                        ComponentType.PROP_RADIUS, String.valueOf(CHEST_RING_MAX_RADIUS),
                        ComponentType.PROP_SPEED, "120",
                        ComponentType.PROP_COLOR, CHEST_RING_HIDDEN)));
        }

        PandoricalApi.hud().show(encounter.player, hud.build());
    }

    /**
     * One frame of the running fight. Only what actually changed is sent, so a bobber parked against
     * the top of the track stops generating traffic entirely.
     *
     * <p>Positions are pushed {@link MinigameTuning#RENDER_LEAD_TICKS} ticks ahead of the true
     * simulation: the client's interpolation filter settles exactly that far behind a value updated
     * every tick, so leading by the same amount keeps the smoothing and cancels the lag.
     */
    public static void update(FishingEncounter encounter, HudSnapshot previous) {
        List<ComponentUpdate> updates = new ArrayList<>(4);

        int lead = MinigameTuning.RENDER_LEAD_TICKS;
        int markerY = markerY(encounter.fish.projectedPosition(lead), MARKER_SIZE);
        int bobberY = bobberTopY(encounter.projectedBobberPosition(lead), encounter.bobberSize);
        int fillHeight = fillPixelHeight(encounter.progress);

        if (markerY != previous.markerY) {
            updates.add(new ComponentUpdateBuilder(COMPONENT_MARKER)
                .prop(ComponentType.PROP_Y, String.valueOf(markerY)).build());
            previous.markerY = markerY;
        }

        // Position and pulse land in one update for the bobber: two updates for the same component
        // would restart its geometry interpolation twice in the same frame
        boolean bobberMoved = bobberY != previous.bobberY;
        boolean overlapChanged = encounter.fishInsideBobber != previous.fishInside;
        if (bobberMoved || overlapChanged) {
            ComponentUpdateBuilder bobber = new ComponentUpdateBuilder(COMPONENT_BOBBER);
            if (bobberMoved) {
                bobber.prop(ComponentType.PROP_Y, String.valueOf(bobberY));
                previous.bobberY = bobberY;
            }
            if (overlapChanged) {
                bobber.prop(ComponentType.PROP_COLOR,
                    encounter.fishInsideBobber ? BOBBER_COLOR_ON_FISH : BOBBER_COLOR_IDLE);
                previous.fishInside = encounter.fishInsideBobber;
            }
            updates.add(bobber.build());
        }

        if (fillHeight != previous.fillHeight) {
            updates.add(new ComponentUpdate(COMPONENT_FILL, fillProps(fillHeight)));
            previous.fillHeight = fillHeight;
        }

        if (encounter.hasTreasure) {
            addTreasureUpdates(encounter, previous, updates);
        }

        if (!updates.isEmpty()) {
            PandoricalApi.hud().update(encounter.player, OVERLAY_ID, updates);
        }
    }

    private static void addTreasureUpdates(FishingEncounter encounter, HudSnapshot previous,
                                           List<ComponentUpdate> updates) {
        if (encounter.treasureVisible && !previous.treasureVisible) {
            updates.add(new ComponentUpdateBuilder(COMPONENT_CHEST)
                .size(CHEST_SIZE, CHEST_SIZE).build());
            updates.add(new ComponentUpdateBuilder(COMPONENT_CHEST_RING)
                .prop(ComponentType.PROP_COLOR, CHEST_RING_COLOR).build());
            previous.treasureVisible = true;
        }
        if (!encounter.treasureVisible) return;

        if (encounter.treasureSecured && !previous.treasureSecured) {
            updates.add(new ComponentUpdateBuilder(COMPONENT_CHEST)
                .prop(ComponentType.PROP_TEXTURE, TEXTURE_CHEST_OPEN).build());
            updates.add(new ComponentUpdateBuilder(COMPONENT_CHEST_RING)
                .prop(ComponentType.PROP_COLOR, CHEST_RING_HIDDEN).build());
            previous.treasureSecured = true;
            return;
        }

        // The ring tightens as the chest fills; only pushed when it moves a whole pixel
        int radius = Math.round(CHEST_RING_MAX_RADIUS
            - (CHEST_RING_MAX_RADIUS - CHEST_RING_MIN_RADIUS) * Mth.clamp(encounter.treasureProgress, 0F, 1F));
        if (!encounter.treasureSecured && radius != previous.chestRingRadius) {
            updates.add(new ComponentUpdateBuilder(COMPONENT_CHEST_RING)
                .prop(ComponentType.PROP_RADIUS, String.valueOf(radius)).build());
            previous.chestRingRadius = radius;
        }
    }

    public static void hide(ServerPlayer player) {
        PandoricalApi.hud().hide(player, OVERLAY_ID);
    }

    /** Last-pushed values, so {@link #update} can send deltas only. */
    public static final class HudSnapshot {
        public int markerY = Integer.MIN_VALUE;
        public int bobberY = Integer.MIN_VALUE;
        public int fillHeight = Integer.MIN_VALUE;
        public int chestRingRadius = Integer.MIN_VALUE;
        public boolean fishInside;
        public boolean treasureVisible;
        public boolean treasureSecured;
    }

    public static String stars(int difficulty) {
        return "★".repeat(Mth.clamp(difficulty, 1, 4));
    }

    // --- Geometry ---

    /** Top-left Y of a centered marker sprite at this track position. */
    private static int markerY(float position, int spriteSize) {
        float clamped = Mth.clamp(position, 0F, 1F);
        return INNER_Y + Math.round((1F - clamped) * INNER_H) - spriteSize / 2;
    }

    private static int markerX(int spriteSize) {
        return INNER_X + (INNER_W - spriteSize) / 2;
    }

    private static int bobberTopY(float bottomPosition, float size) {
        float top = Mth.clamp(bottomPosition + size, 0F, 1F);
        return INNER_Y + Math.round((1F - top) * INNER_H);
    }

    private static int fillPixelHeight(float progress) {
        return Math.round(Mth.clamp(progress, 0F, 1F) * FILL_H);
    }

    /**
     * A gauge that fills upward: position, height and texture source origin move together, so the
     * revealed slice is always the BOTTOM of the gradient art (the same clip-mode trick poopsmith's
     * stomach gauge uses).
     */
    private static ComponentBuilder gaugeFill(float progress) {
        int h = fillPixelHeight(progress);
        return new ComponentBuilder(COMPONENT_FILL, ComponentType.SPRITE)
            .bounds(FILL_X, FILL_Y + (FILL_H - h), FILL_W, h)
            .prop(ComponentType.PROP_TEXTURE, TEXTURE_GAUGE_FILL)
            .prop(ComponentType.PROP_TEXTURE_WIDTH, String.valueOf(FILL_W))
            .prop(ComponentType.PROP_TEXTURE_HEIGHT, String.valueOf(FILL_H))
            .prop(ComponentType.PROP_TEXTURE_V, String.valueOf(FILL_H - h));
    }

    private static Map<String, String> fillProps(int height) {
        return Map.of(
            ComponentType.PROP_Y, String.valueOf(FILL_Y + (FILL_H - height)),
            ComponentType.PROP_HEIGHT, String.valueOf(height),
            ComponentType.PROP_TEXTURE_V, String.valueOf(FILL_H - height));
    }
}
