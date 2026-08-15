package com.minedew.fishing.encounter;

import net.minecraft.util.Prediction;
import com.minedew.fishing.MinedewFishing;
import com.minedew.fishing.access.MinedewFishingHookAccess;
import com.minedew.fishing.fish.FishType;
import justfatlard.pandorical.api.ComponentType;
import justfatlard.pandorical.api.ComponentUpdateBuilder;
import justfatlard.pandorical.api.HudBuilder;
import justfatlard.pandorical.api.PandoricalApi;
import justfatlard.pandorical.protocol.ComponentUpdate;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class FishingEncounterManager {
    private FishingEncounterManager() {}

    private static final String OVERLAY_ID = "minedew-fishing:encounter";
    private static final Map<UUID, FishingEncounter> ACTIVE = new ConcurrentHashMap<>();

    // --- Pacing (ticks, 20/sec). The design is "read the tell, commit in a generous window,"
    // not twitch reflexes, so windows stay long even at max difficulty. Not yet playtested;
    // tune here. ---
    private static final int[] TELL_TICKS = {10, 9, 8, 7};       // difficulty 1..4 -> 0.50s..0.35s
    private static final int[] COMMIT_TICKS = {16, 14, 12, 9};   // difficulty 1..4 -> 0.80s..0.45s
    private static final int MAX_MISSES = 2;
    private static final int OVERALL_TIMEOUT_TICKS = 400; // 20s safety net
    private static final int GRACE_TICKS = 6; // ~300ms extra tolerance after a commit window closes
    private static final float FEINT_CHANCE = 0.30F;
    private static final int FLASH_TICKS = 8; // ~400ms visible flash on hit/miss

    public static boolean isPlayerInEncounter(UUID uuid) {
        return ACTIVE.containsKey(uuid);
    }

    // --- Lifecycle ---

    public static void startEncounter(ServerPlayer player, FishingHook hook, FishType fishType) {
        if (ACTIVE.containsKey(player.getUUID())) return;
        if (!PandoricalApi.isAvailable(player)) {
            // No Pandorical client: nothing to show and no way to run the minigame.
            // Leave the hook alone; vanilla's own nibble window still governs it normally.
            return;
        }

        RandomSource random = RandomSource.create();
        int difficulty = Mth.clamp(fishType.getDifficulty(), 1, 4);
        int idx = difficulty - 1;
        int roundsNeeded = difficulty + 1;
        boolean hasTreasure = random.nextFloat() < 0.15F + random.nextFloat() * 0.10F;
        int treasureRound = hasTreasure ? 1 + random.nextInt(roundsNeeded) : 0;

        FishingEncounter enc = new FishingEncounter(
            player, hook.getId(), fishType, random,
            roundsNeeded, MAX_MISSES, TELL_TICKS[idx], COMMIT_TICKS[idx],
            hasTreasure, treasureRound, OVERALL_TIMEOUT_TICKS
        );
        ACTIVE.put(player.getUUID(), enc);

        ((ServerLevel) player.level()).playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_SPLASH, SoundSource.PLAYERS, 1.0F, 1.0F);
        showHud(enc);
        advanceToNextRound(enc);
    }

    public static void abortIfActive(ServerPlayer player) {
        FishingEncounter enc = ACTIVE.remove(player.getUUID());
        if (enc == null) return;
        PandoricalApi.hud().hide(player, OVERLAY_ID);
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        Iterator<Map.Entry<UUID, FishingEncounter>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            FishingEncounter enc = it.next().getValue();

            // A click handled between ticks (see handleClick) may already have resolved this
            // encounter: succeed()/fail() already did cleanup, so just drop the entry quietly.
            // Without this check-first ordering, the branch below would see the hook it just
            // discarded/retrieved as "removed" and fire a second, contradicting cleanup a tick late.
            if (enc.finished) {
                it.remove();
                continue;
            }

            if (server.getPlayerList().getPlayer(enc.player.getUUID()) == null) {
                it.remove();
                continue;
            }

            FishingHook hook = resolveHook(enc);
            if (hook == null || hook.isRemoved()) {
                PandoricalApi.hud().hide(enc.player, OVERLAY_ID);
                it.remove();
                continue;
            }

            if (enc.flashFadeTicksRemaining > 0) {
                enc.flashFadeTicksRemaining--;
                if (enc.flashFadeTicksRemaining == 0) clearFlash(enc);
            }

            enc.overallTicksRemaining--;
            if (enc.overallTicksRemaining <= 0) {
                fail(enc);
                it.remove();
                continue;
            }

            switch (enc.phase) {
                case TELL -> {
                    enc.ticksRemainingInPhase--;
                    if (enc.ticksRemainingInPhase <= 0) beginCommit(enc);
                }
                case COMMIT -> {
                    enc.ticksRemainingInPhase--;
                    if (enc.ticksRemainingInPhase <= 0 && !enc.roundResolved) {
                        enc.phase = FishingEncounter.Phase.GRACE;
                        enc.graceTicksRemaining = GRACE_TICKS;
                    }
                }
                case GRACE -> {
                    enc.graceTicksRemaining--;
                    if (enc.graceTicksRemaining <= 0 && !enc.roundResolved) {
                        if (enc.currentRoundIsFeint) {
                            resolveFeintWithheld(enc);
                        } else {
                            applyOutcome(enc, false);
                        }
                    }
                }
            }

            if (enc.finished) it.remove();
        }
    }

    // --- Click handling (from the server-side rod-use interception) ---

    /**
     * Resolve a click against the server's own phase state at receipt time.
     * The click arrives as the vanilla rod-use packet (see FishingRodItemMixin),
     * so there is no client mod, no custom packet, and no client-reported
     * timing: the GRACE phase after each commit window IS the latency
     * tolerance, roughly the round trip a real click needs to arrive late.
     */
    public static void handleClick(ServerPlayer player) {
        FishingEncounter enc = ACTIVE.get(player.getUUID());
        if (enc == null || enc.roundResolved || enc.finished) return;

        // Click receipt feedback: quiet, actor-inclusive; the outcome sound
        // and HUD flash follow from applyOutcome
        ((ServerLevel) player.level()).playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.35F, 1.6F);

        // TELL = clicked before the window opened: flinched
        boolean withinWindow = enc.phase == FishingEncounter.Phase.COMMIT
            || enc.phase == FishingEncounter.Phase.GRACE;

        enc.roundResolved = true;
        boolean hit = withinWindow && !enc.currentRoundIsFeint;
        applyOutcome(enc, hit);
    }

    // --- Round resolution ---

    private static void applyOutcome(FishingEncounter enc, boolean hit) {
        if (hit) {
            enc.hitsCompleted++;
            if (enc.isTreasureRound()) enc.treasureSecured = true;
        } else {
            enc.missesUsed++;
        }
        flash(enc, hit ? "hit" : "miss");

        if (enc.missesUsed > enc.maxMisses) {
            fail(enc);
        } else if (enc.hitsCompleted >= enc.roundsNeeded) {
            succeed(enc);
        } else {
            advanceToNextRound(enc);
        }
    }

    private static void resolveFeintWithheld(FishingEncounter enc) {
        enc.roundResolved = true;
        flash(enc, "read");
        advanceToNextRound(enc);
    }

    private static void advanceToNextRound(FishingEncounter enc) {
        enc.currentRound++;
        boolean forceNoFeint = enc.hasTreasure && enc.currentRound == enc.treasureRound;
        boolean feintEligible = enc.fishType.getDifficulty() >= 4 && !forceNoFeint;
        enc.currentRoundIsFeint = feintEligible && enc.random.nextFloat() < FEINT_CHANCE;
        enc.roundResolved = false;
        enc.phase = FishingEncounter.Phase.TELL;
        enc.ticksRemainingInPhase = enc.tellTicks;

        updateHudForPhase(enc, "tell");
    }

    private static void beginCommit(FishingEncounter enc) {
        enc.phase = FishingEncounter.Phase.COMMIT;
        enc.ticksRemainingInPhase = enc.commitTicks;

        updateHudForPhase(enc, "commit");
    }

    // --- Outcome ---

    private static void succeed(FishingEncounter enc) {
        enc.finished = true;
        FishingHook hook = resolveHook(enc);
        ItemStack rod = rodStack(enc.player);
        if (hook != null && rod != null) {
            ((MinedewFishingHookAccess) hook).minedew$forceNibble();
            hook.retrieve(rod);
        }
        if (enc.treasureSecured) grantTreasure(enc.player);

        PandoricalApi.hud().hide(enc.player, OVERLAY_ID);
    }

    private static void fail(FishingEncounter enc) {
        enc.finished = true;
        FishingHook hook = resolveHook(enc);
        if (hook != null && !hook.isRemoved()) hook.discard();

        ((ServerLevel) enc.player.level()).playSound(null, enc.player.getX(), enc.player.getY(), enc.player.getZ(), SoundEvents.ITEM_BREAK.value(), SoundSource.PLAYERS, 1.0F, 0.8F);
        PandoricalApi.hud().hide(enc.player, OVERLAY_ID);
    }

    private static void grantTreasure(ServerPlayer player) {
        RandomSource random = RandomSource.create();
        ItemStack treasure;
        float roll = random.nextFloat();

        if (roll < 0.05F) treasure = new ItemStack(Items.DIAMOND);
        else if (roll < 0.15F) treasure = new ItemStack(Items.GOLD_INGOT, 1 + random.nextInt(2));
        else if (roll < 0.3F) treasure = new ItemStack(Items.EMERALD, 1 + random.nextInt(3));
        else if (roll < 0.45F) treasure = new ItemStack(Items.IRON_INGOT, 2 + random.nextInt(4));
        else if (roll < 0.6F) treasure = new ItemStack(Items.NAUTILUS_SHELL);
        else if (roll < 0.75F) treasure = new ItemStack(Items.NAME_TAG);
        else if (roll < 0.9F) treasure = new ItemStack(Items.LAPIS_LAZULI, 3 + random.nextInt(5));
        else treasure = new ItemStack(Items.EXPERIENCE_BOTTLE, 3 + random.nextInt(6));

        if (!player.getInventory().add(treasure)) {
            player.drop(treasure, false, Prediction.SERVER_ONLY);
        }
        ((ServerLevel) player.level()).playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7F, 1.8F);
        MinedewFishing.LOGGER.info("[minedew-fishing] {} secured a treasure bonus: {}",
            player.getName().getString(), treasure.getItem());
    }

    // --- Helpers ---

    private static FishingHook resolveHook(FishingEncounter enc) {
        Entity entity = enc.player.level().getEntity(enc.hookEntityId);
        return entity instanceof FishingHook hook ? hook : null;
    }

    private static ItemStack rodStack(ServerPlayer player) {
        if (player.getMainHandItem().getItem() instanceof FishingRodItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof FishingRodItem) return player.getOffhandItem();
        return null;
    }

    // --- Pandorical HUD ---

    private static void showHud(FishingEncounter enc) {
        HudBuilder hud = new HudBuilder(OVERLAY_ID)
            .anchor("center")
            .offset(0, -60)
            .sprite("flash", -26, -26, 52, 52, Map.of(ComponentType.PROP_COLOR, "#00FFFFFF"))
            .particleBurst("ring", -20, -20, 40, 40, 8, 16F, 70F, Map.of(ComponentType.PROP_COLOR, ringColor(enc, "tell")))
            .text("fish_name", -40, -54, enc.fishType.getDisplayName())
            .text("difficulty_stars", -18, -42, difficultyStars(enc.fishType.getDifficulty()))
            .text("prompt", -12, 24, "...")
            .text("progress", -26, 38, progressText(enc));

        if (enc.hasTreasure) {
            hud.text("treasure_hint", -46, -66, "♱ treasure on the line");
        }

        PandoricalApi.hud().show(enc.player, hud.build());
    }

    private static void updateHudForPhase(FishingEncounter enc, String phase) {
        boolean commit = "commit".equals(phase);
        List<ComponentUpdate> updates = new ArrayList<>();
        updates.add(new ComponentUpdateBuilder("ring")
            .prop(ComponentType.PROP_COLOR, ringColor(enc, phase))
            .prop(ComponentType.PROP_SPEED, String.valueOf(commit ? 260F : 70F))
            .prop(ComponentType.PROP_RADIUS, String.valueOf(commit ? 20F : 16F))
            .build());
        updates.add(new ComponentUpdateBuilder("prompt")
            .prop(ComponentType.PROP_TEXT, commit ? "CLICK!" : "...")
            .build());
        updates.add(new ComponentUpdateBuilder("progress")
            .prop(ComponentType.PROP_TEXT, progressText(enc))
            .build());
        PandoricalApi.hud().update(enc.player, OVERLAY_ID, updates);
    }

    private static void flash(FishingEncounter enc, String kind) {
        String color = switch (kind) {
            case "hit" -> "#8000FF00";
            case "miss" -> "#80FF3030";
            default -> "#80FFFFFF"; // "read": correctly withheld on a feint
        };
        List<ComponentUpdate> updates = new ArrayList<>();
        updates.add(new ComponentUpdateBuilder("flash").prop(ComponentType.PROP_COLOR, color).build());
        updates.add(new ComponentUpdateBuilder("progress").prop(ComponentType.PROP_TEXT, progressText(enc)).build());
        PandoricalApi.hud().update(enc.player, OVERLAY_ID, updates);
        enc.flashFadeTicksRemaining = FLASH_TICKS;
    }

    private static void clearFlash(FishingEncounter enc) {
        if (enc.finished) return;
        PandoricalApi.hud().update(enc.player, OVERLAY_ID, List.of(
            new ComponentUpdateBuilder("flash").prop(ComponentType.PROP_COLOR, "#00FFFFFF").build()
        ));
    }

    private static String ringColor(FishingEncounter enc, String phase) {
        boolean commit = "commit".equals(phase);
        if (enc.currentRoundIsFeint) return "#AA55FF"; // purple: a fake-out, read the pattern
        if (enc.isTreasureRound()) return commit ? "#FFD700" : "#FFEA9E"; // gold: treasure round
        return commit ? "#55FF55" : "#5599FF"; // green on commit, blue on tell
    }

    private static String progressText(FishingEncounter enc) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < enc.roundsNeeded; i++) {
            sb.append(i < enc.hitsCompleted ? '●' : '○');
        }
        return sb.toString();
    }

    private static String difficultyStars(int difficulty) {
        return "★".repeat(Mth.clamp(difficulty, 1, 4));
    }
}
