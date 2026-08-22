package com.minedew.fishing.encounter;

import com.minedew.fishing.MinedewFishing;
import com.minedew.fishing.fish.FishSpecies;
import com.minedew.fishing.fish.HookedCatch;
import com.minedew.fishing.hud.MinigameHud;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns every live encounter and drives them from the server tick.
 *
 * <p>An encounter is two stages. The <b>hook set</b> is one timing window, opening on the tick of
 * the bite and running as long as vanilla's own bite lasts: a click inside it hooks whatever bit, no
 * click at all loses it. It has no overlay. Vanilla already announces a bite with a splash and a
 * bobber that goes under and stays under, which is the cue every Minecraft player has been trained
 * on, so the mod adds nothing to it but a louder splash of its own. Then the <b>fight</b>, a
 * balancing act rather than a reflex test: the marker swims up and down the track on its own, the
 * player taps the rod to keep a bobber bar under it, and the catch meter fills while the two
 * overlap.
 *
 * <p>Clicks are impulses, which is what makes this workable over a network at all: an impulse is an
 * event that can arrive whenever it arrives, where a held-input design would need continuous state
 * synchronisation to stay honest.
 *
 * <p>All numbers live in {@link MinigameTuning}; all rendering lives in {@link MinigameHud}.
 */
public final class FishingEncounterManager {
    private FishingEncounterManager() {}

    private static final Map<UUID, FishingEncounter> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, MinigameHud.HudSnapshot> HUD_STATE = new ConcurrentHashMap<>();

    public static boolean isPlayerInEncounter(UUID uuid) {
        return ACTIVE.containsKey(uuid);
    }

    /**
     * True while this exact hook is in a fight, which is when the hook mixin holds vanilla's
     * {@code nibble} up (see {@link MinigameTuning#LINE_TAUT_NIBBLE_TICKS}). Not true during the hook
     * set: there the countdown has to run down honestly, because it is the window.
     */
    public static boolean isFightingWithHook(UUID uuid, int hookEntityId) {
        FishingEncounter encounter = ACTIVE.get(uuid);
        return encounter != null && !encounter.finished
            && !encounter.inHookSet() && encounter.hookEntityId == hookEntityId;
    }

    // --- Lifecycle ---

    /**
     * @param nibbleTicks vanilla's freshly rolled {@code nibble} countdown, which is both how long
     *                    the bobber stays under and how long the hook-set window is open
     */
    public static void startEncounter(ServerPlayer player, FishingHook hook, HookedCatch hooked,
                                      int nibbleTicks) {
        if (ACTIVE.containsKey(player.getUUID())) return;
        if (!MinigameHud.canRender(player)) {
            // No Pandorical client: nothing to show and no way to run the minigame.
            // Leave the hook alone; vanilla's own nibble window still governs it normally.
            return;
        }

        FishingEncounter encounter = new FishingEncounter(
            player, hook.getId(), hooked, RandomSource.create(), nibbleTicks);
        ACTIVE.put(player.getUUID(), encounter);
        HUD_STATE.put(player.getUUID(), new MinigameHud.HudSnapshot());

        // The whole hook-set telegraph: vanilla's own splash, played again at the player and at full
        // volume so it carries at cast range. Vanilla's is 0.25 at the bobber, which is easy to miss.
        playSound(player, SoundEvents.FISHING_BOBBER_SPLASH, 1.0F, 1.0F);
        MinedewFishing.LOGGER.debug("[minedew-fishing] {} hooked {} (tier {}, bobber {}, window {}, treasure {})",
            player.getName().getString(), hooked.label(), encounter.difficulty,
            encounter.bobberSize, encounter.phaseTicksRemaining, encounter.hasTreasure);
    }

    public static void abortIfActive(ServerPlayer player) {
        FishingEncounter encounter = ACTIVE.remove(player.getUUID());
        HUD_STATE.remove(player.getUUID());
        if (encounter == null) return;
        MinigameHud.hide(player);
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;

        Iterator<Map.Entry<UUID, FishingEncounter>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            FishingEncounter encounter = it.next().getValue();

            if (encounter.finished) {
                forget(it, encounter);
                continue;
            }
            if (server.getPlayerList().getPlayer(encounter.player.getUUID()) == null) {
                forget(it, encounter);
                continue;
            }

            FishingHook hook = resolveHook(encounter);
            if (hook == null || hook.isRemoved()) {
                MinigameHud.hide(encounter.player);
                forget(it, encounter);
                continue;
            }

            if (encounter.inHookSet()) {
                tickHookSet(encounter);
            } else {
                tickFight(encounter);
            }

            if (encounter.finished) forget(it, encounter);
        }
    }

    /**
     * Run out the borrowed window. Nothing is drawn and nothing is played: the bobber being under is
     * the whole prompt, and it stays under on its own until vanilla's countdown ends, which is the
     * same tick this window does.
     */
    private static void tickHookSet(FishingEncounter encounter) {
        if (encounter.stepHookSet()) {
            // Never even tried: the fish spits the hook
            fail(encounter, "the bite was missed");
        }
    }

    private static void tickFight(FishingEncounter encounter) {
        encounter.stepFight();

        MinigameHud.HudSnapshot hudState = HUD_STATE.computeIfAbsent(
            encounter.player.getUUID(), uuid -> new MinigameHud.HudSnapshot());

        if (encounter.justEnteredBobber) {
            playSound(encounter.player, SoundEvents.NOTE_BLOCK_BELL.value(), 0.3F, 1.9F);
        } else if (encounter.justLeftBobber) {
            playSound(encounter.player, SoundEvents.NOTE_BLOCK_BASS.value(), 0.22F, 0.8F);
        }
        if (encounter.justRevealedTreasure) {
            playSound(encounter.player, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.5F, 1.4F);
        }
        // Read before the push: MinigameHud.update is what flips the snapshot's flag
        if (encounter.treasureSecured && !hudState.treasureSecured) {
            playSound(encounter.player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5F, 1.5F);
        }

        MinigameHud.update(encounter, hudState);

        if (encounter.isCaught()) {
            succeed(encounter);
        } else if (encounter.hasEscaped()) {
            fail(encounter, encounter.timedOut() ? "it outlasted you" : "the line went slack");
        }
    }

    private static void forget(Iterator<Map.Entry<UUID, FishingEncounter>> it, FishingEncounter encounter) {
        it.remove();
        HUD_STATE.remove(encounter.player.getUUID());
    }

    // --- Input ---

    /**
     * One rod right-click, meaning whatever the current stage says it means: during the hook set it
     * is the commit, and during the fight it queues a single upward impulse for the next tick.
     *
     * <p>There is no early-click case to handle here, because clicking before the bite never reaches
     * this method: with no encounter open, {@code FishingRodItemMixin} lets the use through and
     * vanilla reels an empty line in, ending the cast. Jumping the gun still costs the bite, it just
     * costs it in vanilla's own words.
     *
     * <p>The click arrives as the ordinary vanilla use packet (see {@code FishingRodItemMixin}), so
     * there is no client mod, no custom packet and no client-reported timing. At most one impulse is
     * spent per tick, which caps the useful click rate at the tick rate: mashing past a workable
     * cadence pins the bobber against the top of the track rather than winning.
     */
    public static void handleReelClick(ServerPlayer player) {
        FishingEncounter encounter = ACTIVE.get(player.getUUID());
        if (encounter == null || encounter.finished) return;

        if (!encounter.inHookSet()) {
            encounter.impulseQueued = true;
            return;
        }

        encounter.beginFight();
        MinigameHud.show(encounter);
        playSound(player, SoundEvents.FISHING_BOBBER_RETRIEVE, 0.7F, 1.4F);
    }

    // --- Outcome ---

    private static void succeed(FishingEncounter encounter) {
        encounter.finished = true;
        ServerPlayer player = encounter.player;
        ServerLevel level = (ServerLevel) player.level();
        FishingHook hook = resolveHook(encounter);
        InteractionHand hand = rodHand(player);

        grantCatch(encounter, level, player, hook, hand);
        if (encounter.treasureSecured) grantTreasure(player, level);

        // Junk has a size on paper only - it is always SMALL - so landing a boot is not a catch.
        if (!encounter.hooked.species().isJunk()) {
            MinedewFishing.FISH_LANDED.trigger(player, encounter.hooked.size());
        }

        if (hook != null && !hook.isRemoved()) hook.discard();
        playSound(player, SoundEvents.PLAYER_LEVELUP, 0.5F, 1.6F);
        MinigameHud.hide(player);

        // The species was hidden for the whole fight; landing it is the reveal
        player.sendOverlayMessage(Component.literal("Landed: " + encounter.hooked.label())
            .withStyle(encounter.hooked.species().isJunk() ? ChatFormatting.GRAY : ChatFormatting.AQUA));
        MinedewFishing.LOGGER.info("[minedew-fishing] {} landed {} after {} ticks{}",
            player.getName().getString(), encounter.hooked.label(), encounter.fightTicks,
            encounter.treasureSecured ? " (treasure secured)" : "");
    }

    private static void fail(FishingEncounter encounter, String reason) {
        encounter.finished = true;
        FishingHook hook = resolveHook(encounter);
        if (hook != null && !hook.isRemoved()) hook.discard();

        playSound(encounter.player, SoundEvents.ITEM_BREAK.value(), 1.0F, 0.8F);
        MinigameHud.hide(encounter.player);
        encounter.player.sendOverlayMessage(
            Component.literal("It got away: " + reason).withStyle(ChatFormatting.GRAY));
        MinedewFishing.LOGGER.info("[minedew-fishing] {} lost {} after {} ticks ({})",
            encounter.player.getName().getString(), encounter.hooked.label(),
            encounter.fightTicks, reason);
    }

    /**
     * Hand over exactly what vanilla's loot table rolled for this bite, plus the size bonus.
     *
     * <p>Deliberately not {@code FishingHook.retrieve()}: the loot was rolled up front so the fight
     * and the payout could agree (a junk fight has to end in junk, see {@code HookedCatch}), and
     * retrieve() would roll the table a second time. Everything retrieve() does around that roll is
     * mirrored here: the item flies to the player, experience drops, the fish stat and the
     * fishing-rod-hooked advancement trigger fire, and the rod takes its point of damage.
     */
    private static void grantCatch(FishingEncounter encounter, ServerLevel level, ServerPlayer player,
                                   FishingHook hook, InteractionHand hand) {
        Vec3 origin = hook != null ? hook.position() : player.position();
        RandomSource random = level.getRandom();

        for (ItemStack stack : encounter.hooked.loot()) {
            spawnTowardsPlayer(level, player, origin, stack.copy());
            if (stack.is(ItemTags.FISHES)) {
                player.awardStat(Stats.FISH_CAUGHT, 1);
            }
        }

        int bonus = bonusPieces(encounter);
        if (bonus > 0) {
            spawnTowardsPlayer(level, player, origin,
                new ItemStack(encounter.hooked.species().getBonusItem(), bonus));
        }

        ExperienceOrb.award(level, player.position(), random.nextInt(6) + 1);

        ItemStack rod = rodStack(player);
        if (rod != null) {
            if (hook != null) {
                CriteriaTriggers.FISHING_ROD_HOOKED.trigger(player, rod, hook, encounter.hooked.loot());
            }
            rod.hurtAndBreak(1, player, hand == InteractionHand.OFF_HAND
                ? net.minecraft.world.entity.EquipmentSlot.OFFHAND
                : net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }
    }

    /**
     * Extra pieces for a bigger catch. Size only: Luck of the Sea already moved the odds on the
     * vanilla roll this rides on top of, and letting it push the bonus too would compound. The
     * counts themselves are on {@code FishSize}: 0 / 1 / 2 / 4 for small / medium / large / trophy.
     */
    private static int bonusPieces(FishingEncounter encounter) {
        FishSpecies species = encounter.hooked.species();
        if (species.isJunk() || species.getBonusItem() == null) return 0;
        return encounter.hooked.size().getBonusPieces();
    }

    private static void spawnTowardsPlayer(ServerLevel level, ServerPlayer player, Vec3 origin, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemEntity item = new ItemEntity(level, origin.x, origin.y, origin.z, stack);
        double dx = player.getX() - origin.x;
        double dy = player.getY() - origin.y;
        double dz = player.getZ() - origin.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        item.setDeltaMovement(dx * 0.1, dy * 0.1 + Math.sqrt(distance) * 0.08, dz * 0.1);
        level.addFreshEntity(item);
    }

    private static void grantTreasure(ServerPlayer player, ServerLevel level) {
        RandomSource random = level.getRandom();
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
        playSound(player, SoundEvents.PLAYER_LEVELUP, 0.7F, 1.8F);
        MinedewFishing.LOGGER.info("[minedew-fishing] {} secured a treasure bonus: {}",
            player.getName().getString(), treasure.getItem());
    }

    // --- Helpers ---

    private static void playSound(ServerPlayer player, SoundEvent sound, float volume, float pitch) {
        ((ServerLevel) player.level()).playSound(null, player.getX(), player.getY(), player.getZ(),
            sound, SoundSource.PLAYERS, volume, pitch);
    }

    private static FishingHook resolveHook(FishingEncounter encounter) {
        Entity entity = encounter.player.level().getEntity(encounter.hookEntityId);
        return entity instanceof FishingHook hook ? hook : null;
    }

    private static ItemStack rodStack(ServerPlayer player) {
        if (player.getMainHandItem().getItem() instanceof FishingRodItem) return player.getMainHandItem();
        if (player.getOffhandItem().getItem() instanceof FishingRodItem) return player.getOffhandItem();
        return null;
    }

    private static InteractionHand rodHand(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof FishingRodItem
            ? InteractionHand.MAIN_HAND
            : InteractionHand.OFF_HAND;
    }
}
