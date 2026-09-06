package com.minedew.fishing.encounter;

import com.minedew.fishing.MinedewFishing;
import com.minedew.fishing.fish.FishSpecies;
import com.minedew.fishing.fish.HookedCatch;
import com.minedew.fishing.hud.MinigameHud;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.util.Prediction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
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
    /**
     * Game time until which each player's rod clicks are swallowed, armed whenever an encounter
     * ends. See {@link #shouldSwallowRecast}.
     */
    private static final Map<UUID, Long> RECAST_GUARD_UNTIL = new ConcurrentHashMap<>();

    /** Landed catches still mid-air between the water and the player; see {@link CatchFlight}. */
    private static final List<CatchFlight> CATCH_FLIGHTS = new ArrayList<>();
    /** How long the landing animation may run before the haul is handed over regardless. */
    private static final int CATCH_FLIGHT_TIMEOUT_TICKS = 60;
    /**
     * Within this range of the player the flight counts as arrived.
     *
     * <p>Close enough to read as reaching you. It was nearly two blocks, which is a fish
     * blinking out at arm's length rather than being caught; the step below is clamped to the
     * distance remaining, so a tight radius is no longer something a fast fish can skip over.
     */
    private static final double CATCH_FLIGHT_ARRIVAL_RADIUS = 0.9;
    /**
     * Reel-in speed band, blocks per tick. Speed is distance-proportional between these, so a far
     * catch hustles and a near one decelerates as it closes. Overshoot is prevented by the step
     * being clamped to the distance remaining rather than by the floor being small.
     */
    /** How far above the fish the treasure rides. */
    private static final double ESCORT_RISE = 0.55;
    private static final double CATCH_FLIGHT_MIN_SPEED = 0.3;
    private static final double CATCH_FLIGHT_MAX_SPEED = 0.8;

    public static boolean isPlayerInEncounter(UUID uuid) {
        return ACTIVE.containsKey(uuid);
    }

    /**
     * Whether the minigame overlay is on this player's screen right now.
     *
     * <p>Narrower than {@link #isPlayerInEncounter} on purpose. An encounter exists from the moment
     * something bites, but the overlay only goes up when the fight starts, and the hook-set window
     * before that shows nothing at all. Anything giving up screen space to the minigame wants this
     * question and not the other one, or it would clear the screen for a bite nobody struck at.
     */
    public static boolean isMinigameHudUp(UUID uuid) {
        FishingEncounter encounter = ACTIVE.get(uuid);
        return encounter != null && !encounter.finished && !encounter.inHookSet();
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
        if (!CATCH_FLIGHTS.isEmpty()) {
            CATCH_FLIGHTS.removeIf(CatchFlight::tick);
        }
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

        // The fish coming onto the line and darting off it, in the sounds of the water: a reel
        // taking up slack as it comes into the bar, a splash of tail as it leaves. Note blocks
        // said the same thing and sounded like a game telling you so.
        if (encounter.justEnteredBobber) {
            playSound(encounter.player, SoundEvents.FISHING_BOBBER_RETRIEVE, 0.35F, 1.6F);
        } else if (encounter.justLeftBobber) {
            playSound(encounter.player, SoundEvents.FISH_SWIM, 0.55F, 0.9F);
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
    /**
     * True while this click belongs to the tapping cadence that just finished a fight rather than
     * to a new intent. The fight ends mid-cadence, so without this the player's next in-flight tap
     * reaches vanilla with no hook out and throws the line straight back. Every swallowed click
     * re-arms the guard, so what ends it is a genuine pause: the next cast is the first click that
     * comes {@link MinigameTuning#RECAST_GUARD_TICKS} after the tapping stops.
     */
    public static boolean shouldSwallowRecast(ServerPlayer player) {
        Long until = RECAST_GUARD_UNTIL.get(player.getUUID());
        if (until == null) return false;

        long now = player.level().getGameTime();
        if (now >= until) {
            RECAST_GUARD_UNTIL.remove(player.getUUID());
            return false;
        }
        RECAST_GUARD_UNTIL.put(player.getUUID(), now + MinigameTuning.RECAST_GUARD_TICKS);
        return true;
    }

    private static void armRecastGuard(ServerPlayer player) {
        RECAST_GUARD_UNTIL.put(player.getUUID(),
            player.level().getGameTime() + MinigameTuning.RECAST_GUARD_TICKS);
    }

    public static void handleReelClick(ServerPlayer player) {
        FishingEncounter encounter = ACTIVE.get(player.getUUID());
        if (encounter == null || encounter.finished) return;

        if (!encounter.inHookSet()) {
            encounter.impulseQueued = true;
            return;
        }

        encounter.beginFight();
        MinigameHud.show(encounter);

        // Named, because the meter opening a little further along is not something a player can
        // see they earned - the bar has no memory of where it usually starts, and a head start
        // nobody attributes to their own hand reads as the fish having been easy.
        if (encounter.strikeBonus > 0F) {
            player.sendOverlayMessage(Component.literal(String.format("Clean strike!  +%d%%",
                Math.round(encounter.strikeBonus * 100))).withStyle(ChatFormatting.GREEN));
            playSound(player, SoundEvents.EXPERIENCE_ORB_PICKUP, 0.6F, 1.6F);
        }

        playSound(player, SoundEvents.FISHING_BOBBER_RETRIEVE, 0.7F, 1.4F);
    }

    // --- Outcome ---

    private static void succeed(FishingEncounter encounter) {
        encounter.finished = true;
        ServerPlayer player = encounter.player;
        ServerLevel level = (ServerLevel) player.level();
        FishingHook hook = resolveHook(encounter);
        InteractionHand hand = rodHand(player);

        // Rolled here rather than granted here: the treasure rides the catch (see grantCatch), so
        // it arrives in the same moment and by the same path as the fish it was won alongside.
        ItemStack treasure = encounter.treasureSecured ? rollTreasure(level.getRandom()) : null;
        grantCatch(encounter, level, player, hook, hand, treasure);

        // Junk has a size on paper only - it is always SMALL - so landing a boot is not a catch.
        if (!encounter.hooked.species().isJunk()) {
            MinedewFishing.FISH_LANDED.trigger(player, encounter.hooked.size());
        }

        if (hook != null && !hook.isRemoved()) hook.discard();
        armRecastGuard(player);
        // One sound, pitched up when the chest came with it. Two were played before - this one and
        // the treasure's own, in the same tick, both PLAYER_LEVELUP - and simultaneous copies of a
        // sound do not read as two events, they read as one muddy one. So the treasure is heard by
        // this note being higher, not by a second note underneath it.
        playSound(player, SoundEvents.PLAYER_LEVELUP, 0.5F, treasure != null ? 1.9F : 1.6F);
        MinigameHud.hide(player);

        // The species was hidden for the whole fight; landing it is the reveal - and so is the
        // treasure, which used to arrive in the inventory named by nothing at all. A chest you
        // fought a whole second minigame for is worth being told you won.
        MutableComponent reveal = Component.literal("Landed: " + encounter.hooked.label())
            .withStyle(encounter.hooked.species().isJunk() ? ChatFormatting.GRAY : ChatFormatting.AQUA);
        if (treasure != null) {
            reveal.append(Component.literal("  +  ").withStyle(ChatFormatting.DARK_GRAY));
            if (treasure.getCount() > 1) {
                reveal.append(Component.literal(treasure.getCount() + "x ").withStyle(ChatFormatting.GOLD));
            }
            reveal.append(treasure.getHoverName().copy().withStyle(ChatFormatting.GOLD));
        }
        player.sendOverlayMessage(reveal);
        MinedewFishing.LOGGER.info("[minedew-fishing] {} landed {} after {} ticks{}",
            player.getName().getString(), encounter.hooked.label(), encounter.fightTicks,
            treasure != null ? " (treasure secured: " + treasure.getItem() + ")" : "");
    }

    private static void fail(FishingEncounter encounter, String reason) {
        encounter.finished = true;
        FishingHook hook = resolveHook(encounter);
        if (hook != null && !hook.isRemoved()) hook.discard();
        armRecastGuard(encounter.player);

        playSound(encounter.player, SoundEvents.ITEM_BREAK.value(), 1.0F, 0.8F);
        MinigameHud.hide(encounter.player);
        encounter.player.sendOverlayMessage(
            Component.literal("It got away: " + reason).withStyle(ChatFormatting.GRAY));

        // A fish that got away still taught something: a little experience, and the next one
        // comes a little kinder. Junk is neither.
        if (!encounter.hooked.species().isJunk()) {
            ServerLevel level = (ServerLevel) encounter.player.level();
            ExperienceOrb.award(level, encounter.player.position(),
                MinigameTuning.XP_LOST_BY_DIFFICULTY[encounter.difficulty - 1]);
            FishingStreaks.get(level.getServer()).lost(encounter.player.getUUID());
        }
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
     * mirrored here: the catch flies to the player (as the living fish, see
     * {@link #launchCatchFlight}; junk as its item), experience drops, the fish stat and the
     * fishing-rod-hooked advancement trigger fire, and the rod takes its point of damage.
     */
    private static void grantCatch(FishingEncounter encounter, ServerLevel level, ServerPlayer player,
                                   FishingHook hook, InteractionHand hand, ItemStack treasure) {
        Vec3 origin = hook != null ? hook.position() : player.position();
        RandomSource random = level.getRandom();

        List<ItemStack> payload = new ArrayList<>();
        for (ItemStack stack : encounter.hooked.loot()) {
            payload.add(stack.copy());
            if (stack.is(ItemTags.FISHES)) {
                player.awardStat(Stats.FISH_CAUGHT, 1);
            }
        }
        int bonus = bonusPieces(encounter);
        if (bonus > 0) {
            payload.add(new ItemStack(encounter.hooked.species().getBonusItem(), bonus));
        }
        // In the payload, so the chest's contents are subject to the same single-delivery promise
        // as the fish: they fly in together, and a player who logs out mid-flight drops both
        // rather than keeping one and losing the other.
        if (treasure != null) payload.add(treasure);

        if (!launchCatchFlight(encounter, level, player, origin, payload, treasure)) {
            // No mob to show (junk, or the spawn failed): the haul flies as items, vanilla-style
            for (ItemStack stack : payload) {
                spawnTowardsPlayer(level, player, origin, stack);
            }
        }

        // Experience by the size of the fish, steeply: a trophy is worth a night of smalls.
        int xp;
        if (encounter.hooked.species().isJunk()) {
            xp = MinigameTuning.XP_LANDED_JUNK;
        } else {
            xp = MinigameTuning.XP_LANDED_BY_SIZE[Mth.clamp(encounter.hooked.size().difficulty(), 1, 4) - 1]
                + random.nextInt(MinigameTuning.XP_LANDED_SPREAD + 1);
            FishingStreaks.get(level.getServer()).landed(player.getUUID());
        }
        if (treasure != null) xp += MinigameTuning.XP_TREASURE;
        ExperienceOrb.award(level, player.position(), xp);

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
     * counts themselves are on {@code FishSize}: 1 / 2 / 3 / 5 for small / medium / large / trophy,
     * landing at 2 / 3 / 4 / 6 in hand once the loot's own piece is counted.
     */
    private static int bonusPieces(FishingEncounter encounter) {
        FishSpecies species = encounter.hooked.species();
        if (species.isJunk() || species.getBonusItem() == null) return 0;
        return encounter.hooked.size().getBonusPieces();
    }

    /**
     * The landing animation: the catch flies out of the water as the real vanilla mob, scaled by
     * its size class, and the haul is handed over when it reaches the player. Seeing a trophy
     * salmon come at you IS the reveal; the overlay text just puts a name on it.
     *
     * <p>False when this catch has no mob to show (junk) or the spawn failed; the caller falls
     * back to flinging the items themselves.
     */
    private static boolean launchCatchFlight(FishingEncounter encounter, ServerLevel level,
                                             ServerPlayer player, Vec3 origin, List<ItemStack> payload,
                                             ItemStack treasure) {
        EntityType<?> type = encounter.hooked.species().getDisplayEntity();
        if (type == null) return false;
        if (!(type.create(level, EntitySpawnReason.MOB_SUMMONED) instanceof Mob fish)) return false;

        fish.setPos(origin.x, origin.y, origin.z);
        // Theater, not a mob: it cannot die mid-arc, and the flight ends by discard, never despawn.
        // AI and gravity are off because the line owns the motion: a one-time fling (the first cut
        // of this) left a live mob to its own physics, and mob drag plus fish AI meant anything but
        // a short cast landed in the water and swam away until the timeout delivered off-screen.
        fish.setPermanentlyInvulnerable(true);
        fish.setPersistenceRequired();
        fish.setNoAi(true);
        fish.setNoGravity(true);
        // And nothing to bump into. The line reels the catch over the bank, the boat and the
        // lip of whatever you are stood on, and a mob that collides with those stops dead
        // against one and never arrives - which is what "it does not come all the way back"
        // was: pushed into a block, going nowhere, until the timeout handed the fish over
        // from wherever it had stuck.
        fish.noPhysics = true;
        AttributeInstance scale = fish.getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(encounter.hooked.size().getDisplayScale());
        }

        // No opening fling: the flight is stepped by position from the first tick (see
        // CatchFlight.steer), and a velocity set here would be carried by nothing.

        if (!level.addFreshEntity(fish)) return false;

        // A fish out of water flops, and this one cannot: its AI is off so the line can own the
        // motion, and vanilla's flopping is AI. Played rather than simulated, so it thrashes all
        // the way in instead of gliding at you like a thrown brick.
        justfatlard.pandorical.api.PandoricalApi.animations()
            .play(fish, "minedew-fishing:flop", true);
        CATCH_FLIGHTS.add(new CatchFlight(fish, player, payload, spawnEscort(level, origin, treasure)));
        return true;
    }

    /**
     * The treasure, riding in on the line beside the fish.
     *
     * <p>Shown rather than merely granted, because the chest is fought for separately and won
     * separately and then arrived by appearing in the inventory - the one part of the encounter
     * with no moment of its own. What you land ought to come out of the water where you can see it.
     *
     * <p>Display only, and it must stay that way: the real stack is delivered by the payload, so an
     * escort that anybody could pick up would hand out the treasure twice. It carries a copy, it is
     * set never to be picked up, and it is discarded on every path out of the flight. Its lifetime
     * is left alone deliberately - an escort orphaned by a crash expires the way any dropped item
     * does, rather than sitting on the water forever holding something nobody can take.
     *
     * @return null when there is no treasure to show
     */
    private static ItemEntity spawnEscort(ServerLevel level, Vec3 origin, ItemStack treasure) {
        if (treasure == null || treasure.isEmpty()) return null;

        ItemEntity escort = new ItemEntity(level, origin.x, origin.y, origin.z, treasure.copy());
        escort.setNeverPickUp();
        escort.setNoGravity(true);
        escort.noPhysics = true;
        escort.setDeltaMovement(Vec3.ZERO);

        return level.addFreshEntity(escort) ? escort : null;
    }

    /**
     * One landed catch mid-air between the water and the player. The mob is the animation and the
     * payload is the truth: whatever happens to the animation (arrival, timeout, the mob somehow
     * vanishing, the player logging out), the payload is delivered exactly once.
     */
    private static final class CatchFlight {
        private final Mob fish;
        private final ServerPlayer player;
        private final List<ItemStack> payload;
        /** The treasure shown flying in beside the fish; null when the catch won none. */
        private final ItemEntity escort;
        private int ticksLeft = CATCH_FLIGHT_TIMEOUT_TICKS;

        CatchFlight(Mob fish, ServerPlayer player, List<ItemStack> payload, ItemEntity escort) {
            this.fish = fish;
            this.player = player;
            this.payload = payload;
            this.escort = escort;
        }

        /** Rides just above the catch, close enough to read as one haul and clear of the fish. */
        private void placeEscort() {
            if (this.escort == null || this.escort.isRemoved()) return;
            this.escort.setPos(this.fish.getX(), this.fish.getY() + ESCORT_RISE, this.fish.getZ());
            this.escort.setDeltaMovement(Vec3.ZERO);
        }

        private void dropEscort() {
            if (this.escort != null && !this.escort.isRemoved()) this.escort.discard();
        }

        /** One tick; true when the flight is over and the haul delivered. */
        boolean tick() {
            if (this.player.isRemoved()) {
                // Player gone mid-flight: the haul drops where the fish is rather than vanishing
                for (ItemStack stack : this.payload) {
                    this.fish.level().addFreshEntity(new ItemEntity(this.fish.level(),
                        this.fish.getX(), this.fish.getY(), this.fish.getZ(), stack));
                }
                this.fish.discard();
                dropEscort();
                return true;
            }

            // Measured against the point it is actually flying at, not the player's feet: the
            // two differ by the aim height, so a radius tighter than that would never be met and
            // the fish would hover at the player's chest until the timeout gave up for it.
            boolean arrived = this.fish.position().distanceTo(aimPoint()) < CATCH_FLIGHT_ARRIVAL_RADIUS;
            if (!arrived && !this.fish.isRemoved() && --this.ticksLeft > 0) {
                steer();
                placeEscort();
                return false;
            }

            if (!this.fish.isRemoved()) this.fish.discard();
            dropEscort();
            for (ItemStack stack : this.payload) {
                if (!this.player.getInventory().add(stack)) {
                    this.player.drop(stack, false, Prediction.SERVER_ONLY);
                }
            }
            playSound(this.player, SoundEvents.ITEM_PICKUP, 0.4F, 1.0F);
            return true;
        }

        /**
         * The line reels it in: velocity is re-aimed at the player every tick, so the flight
         * arrives no matter what drag did to the last tick's motion or where the player walked
         * meanwhile. Runs from END_SERVER_TICK, so the value set here is exactly what the mob's
         * next movement step applies.
         */
        /** Chest height rather than feet: a catch arrives at the hands that reeled it in. */
        private Vec3 aimPoint() {
            return this.player.position().add(0, this.player.getBbHeight() * 0.5, 0);
        }

        private void steer() {
            Vec3 to = aimPoint().subtract(this.fish.position());
            double distance = to.length();
            if (distance < 1.0E-4) return;

            // Never further than what is left, so it cannot sail past and have to come back:
            // that overshoot is what the arrival radius had to be widened to hide.
            double speed = Math.min(distance, Mth.clamp(distance * 0.25,
                CATCH_FLIGHT_MIN_SPEED, CATCH_FLIGHT_MAX_SPEED));
            Vec3 step = to.scale(speed / distance);

            // MOVED, not pushed. Setting a velocity asks the entity's own movement to carry it,
            // and this one has no AI - which is the state map-makers use precisely because it
            // holds a mob still. The velocity was set faithfully every tick and applied by
            // nothing, so the catch sat exactly where it was spawned, at the bobber, until the
            // timeout handed the fish over from the water's surface.
            //
            // Position is the honest way to say it anyway: this is theatre on a fixed path, and
            // the client interpolates between the positions it is sent, so the flight is smooth
            // without the engine being involved at all.
            this.fish.setPos(this.fish.getX() + step.x,
                this.fish.getY() + step.y,
                this.fish.getZ() + step.z);
            // Zero, so that if its movement ever does run it cannot travel the step twice
            this.fish.setDeltaMovement(Vec3.ZERO);
        }
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

    /** What the chest held. Rolling only - delivery is the catch payload's job. */
    private static ItemStack rollTreasure(RandomSource random) {
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

        return treasure;
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
