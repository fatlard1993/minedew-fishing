package com.minedew.fishing.fish;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * What is on the end of the line, as a movement identity. Five of them, matching what vanilla
 * fishing actually hands you: the four fish plus junk.
 *
 * <p>The identity is not invented and not rolled with made-up odds. The mod rolls vanilla's own
 * {@code minecraft:gameplay/fishing} loot table at the moment of the bite and reads the identity
 * back off what came out (see {@link HookedCatch}), so the species distribution, the Luck of the Sea
 * quality shifts, the open-water treasure condition and any datapack edits are all vanilla's, not
 * a copy of vanilla's that can drift. Anything that is not one of the four fish items fights as
 * {@link #JUNK}, including treasure-table pulls: a saddle on the line should feel like a saddle on
 * the line.
 *
 * <p>Each species' pattern (and accent, where it has one) is FIXED. That is the whole point: a
 * player who has fought a few hundred fish should know what they have hooked from the first second
 * of the fight, before the name is ever shown. {@link FishSize} then layers intensity on top.
 *
 * <p>The speed and aggressiveness multipliers are deliberately narrow. Species is supposed to decide
 * how a fight FEELS and size is supposed to decide how HARD it is, and a wide speed spread here
 * breaks that: an earlier 0.90-1.20 spread made a medium pufferfish land 1% of the time for a laggy
 * player while a medium cod landed 61%, which is a difficulty tier hiding inside a flavour knob.
 * Character comes from the pattern's shape, not from how fast it is played.
 */
public enum FishSpecies {
    /** The common catch and the personality everyone learns first: steady, mid-track, short snaps. */
    COD("Cod", 1.00F, 0.90F, FishMovementPattern.MODERATE_DART, null, 0, 0, Items.COD),
    /** Runs and glides: hard lunges up the track, then a smooth stretch while it recovers. */
    SALMON("Salmon", 1.03F, 0.95F, FishMovementPattern.FAST_DART,
        FishMovementPattern.SLOW_SINUSOIDAL, 120, 45, Items.SALMON),
    /** Skittish: never settles anywhere, quick retargets with a tremor on top. */
    TROPICAL_FISH("Tropical Fish", 1.02F, 1.00F, FishMovementPattern.FAST_ERRATIC, null, 0, 0,
        Items.TROPICAL_FISH),
    /** Sluggish and buoyant: holds one depth for a long time, then moves, and rides a little high. */
    PUFFERFISH("Pufferfish", 0.98F, 0.90F, FishMovementPattern.SLOW_FLOATER, null, 0, 0,
        Items.PUFFERFISH),
    /** Not a fish at all: dead weight that snags, lurches, and stops. No size variants. */
    JUNK("Junk", 1.00F, 1.20F, FishMovementPattern.SNAG, null, 0, 0, null);

    private final String displayName;
    private final float baseSpeed;
    private final float baseAggressiveness;
    private final FishMovementPattern pattern;
    private final FishMovementPattern accent;
    private final int accentPeriodTicks;
    private final int accentDurationTicks;
    private final Item bonusItem;

    FishSpecies(String displayName, float baseSpeed, float baseAggressiveness,
                FishMovementPattern pattern, FishMovementPattern accent,
                int accentPeriodTicks, int accentDurationTicks, Item bonusItem) {
        this.displayName = displayName;
        this.baseSpeed = baseSpeed;
        this.baseAggressiveness = baseAggressiveness;
        this.pattern = pattern;
        this.accent = accent;
        this.accentPeriodTicks = accentPeriodTicks;
        this.accentDurationTicks = accentDurationTicks;
        this.bonusItem = bonusItem;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public float getBaseSpeed() {
        return this.baseSpeed;
    }

    public float getBaseAggressiveness() {
        return this.baseAggressiveness;
    }

    public FishMovementPattern getPattern() {
        return this.pattern;
    }

    /** Secondary pattern that periodically takes over, or null for a single-pattern species. */
    public FishMovementPattern getAccent() {
        return this.accent;
    }

    public int getAccentPeriodTicks() {
        return this.accentPeriodTicks;
    }

    public int getAccentDurationTicks() {
        return this.accentDurationTicks;
    }

    /**
     * The item a size bonus pays out in, or null for junk. Cod and salmon are the two the mod
     * reskins into fillets, which is what makes "a bigger fish is more pieces" read naturally;
     * tropical fish and pufferfish are whole creatures and simply come in a bigger haul.
     */
    public Item getBonusItem() {
        return this.bonusItem;
    }

    public boolean isJunk() {
        return this == JUNK;
    }

    /** Reads the identity back off whatever vanilla's loot table produced. */
    public static FishSpecies fromLoot(ItemStack stack) {
        if (stack.is(Items.COD)) return COD;
        if (stack.is(Items.SALMON)) return SALMON;
        if (stack.is(Items.TROPICAL_FISH)) return TROPICAL_FISH;
        if (stack.is(Items.PUFFERFISH)) return PUFFERFISH;
        return JUNK;
    }
}
