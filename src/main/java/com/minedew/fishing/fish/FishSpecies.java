package com.minedew.fishing.fish;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

/**
 * What vanilla's fishing table handed over: the four fish plus junk.
 *
 * <p>The identity is not invented and not rolled with made-up odds. The mod rolls vanilla's own
 * {@code minecraft:gameplay/fishing} loot table at the moment of the bite and reads the identity
 * back off what came out (see {@link HookedCatch}), so the species distribution, the Luck of the Sea
 * quality shifts, the open-water treasure condition and any datapack edits are all vanilla's, not
 * a copy of vanilla's that can drift. Anything that is not one of the four fish items fights as
 * {@link #JUNK}, including treasure-table pulls: a saddle on the line should feel like a saddle on
 * the line.
 *
 * <p>A fish roll is then narrowed by the water to the {@link Fish} it really is, which is what it
 * fights as. As a quarry, each of the four fish here is its plain {@link Fish}: cod fights as cod.
 */
public enum FishSpecies implements Quarry {
    COD(Items.COD, EntityTypes.COD),
    SALMON(Items.SALMON, EntityTypes.SALMON),
    TROPICAL_FISH(Items.TROPICAL_FISH, EntityTypes.TROPICAL_FISH),
    PUFFERFISH(Items.PUFFERFISH, EntityTypes.PUFFERFISH),
    /** Not a fish at all: dead weight that snags, lurches, and stops. No size variants. */
    JUNK(null, null);

    private final @Nullable Item bonusItem;
    private final @Nullable EntityType<?> displayEntity;

    FishSpecies(@Nullable Item bonusItem, @Nullable EntityType<?> displayEntity) {
        this.bonusItem = bonusItem;
        this.displayEntity = displayEntity;
    }

    @Override
    public String getDisplayName() {
        return isJunk() ? "Junk" : Fish.plain(this).getDisplayName();
    }

    @Override
    public float getBaseSpeed() {
        return isJunk() ? 1.00F : Fish.plain(this).getBaseSpeed();
    }

    @Override
    public float getBaseAggressiveness() {
        return isJunk() ? 1.20F : Fish.plain(this).getBaseAggressiveness();
    }

    @Override
    public FishMovementPattern getPattern() {
        return isJunk() ? FishMovementPattern.SNAG : Fish.plain(this).getPattern();
    }

    @Override
    public @Nullable FishMovementPattern getAccent() {
        return isJunk() ? null : Fish.plain(this).getAccent();
    }

    @Override
    public int getAccentPeriodTicks() {
        return isJunk() ? 0 : Fish.plain(this).getAccentPeriodTicks();
    }

    @Override
    public int getAccentDurationTicks() {
        return isJunk() ? 0 : Fish.plain(this).getAccentDurationTicks();
    }

    /**
     * The item a size bonus pays out in, or null for junk. Cod and salmon are the two the mod
     * reskins into fillets, which is what makes "a bigger fish is more pieces" read naturally;
     * tropical fish and pufferfish are whole creatures and simply come in a bigger haul.
     */
    @Override
    public @Nullable Item getBonusItem() {
        return this.bonusItem;
    }

    /**
     * The vanilla mob flung to the player as the landing animation (scaled by
     * {@link FishSize#getDisplayScale()}), or null for junk, which flies as its item. Purely
     * theater: the payout is items either way, delivered when the flight lands.
     */
    @Override
    public @Nullable EntityType<?> getDisplayEntity() {
        return this.displayEntity;
    }

    @Override
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

    /** The vanilla fish a mob of this type is, or null for any other mob. */
    public static @Nullable FishSpecies ofBody(EntityType<?> type) {
        if (type == EntityTypes.COD) return COD;
        if (type == EntityTypes.SALMON) return SALMON;
        if (type == EntityTypes.TROPICAL_FISH) return TROPICAL_FISH;
        if (type == EntityTypes.PUFFERFISH) return PUFFERFISH;
        return null;
    }
}
