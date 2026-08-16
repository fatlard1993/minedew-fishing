package com.minedew.fishing.fish;

import com.minedew.fishing.access.MinedewFishingHookAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;

/**
 * One bite, resolved up front: what vanilla's loot table actually produced, which movement identity
 * that makes it, and how big it is.
 *
 * <p>The loot is rolled at the moment of the bite rather than at the end of the fight, which is what
 * lets the fight and the payout agree. Reading the identity off a real roll of
 * {@code minecraft:gameplay/fishing} (85 fish / 10 junk / 5 open-water treasure, and within fish
 * 60 cod / 25 salmon / 13 pufferfish / 2 tropical, all verified against the snapshot's own tables)
 * means the mod inherits Luck of the Sea's quality shifts, the open-water condition, biome-specific
 * junk and any datapack edits for free, instead of maintaining a copy of the odds that can silently
 * drift from vanilla's.
 *
 * @param species what it fights like
 * @param size    how hard it fights; junk has no size variants
 * @param loot    exactly what the player gets if they land it
 */
public record HookedCatch(FishSpecies species, FishSize size, List<ItemStack> loot) {
    /** Junk is quick and annoying rather than hard, so it always fights at the easiest tier. */
    public static final int JUNK_DIFFICULTY = 1;

    public int difficulty() {
        return this.species.isJunk() ? JUNK_DIFFICULTY : this.size.difficulty();
    }

    /** What the player is told they landed, once they have landed it. */
    public String label() {
        if (this.species.isJunk()) {
            return this.loot.isEmpty()
                ? this.species.getDisplayName()
                : this.loot.getFirst().getHoverName().getString();
        }
        return this.size.getDisplayName() + " " + this.species.getDisplayName();
    }

    /** Roll vanilla's fishing table for this hook and classify the result. */
    public static HookedCatch roll(ServerLevel level, ServerPlayer player, FishingHook hook, ItemStack rod) {
        float luck = ((MinedewFishingHookAccess) hook).minedew$luck() + player.getLuck();
        LootParams params = new LootParams.Builder(level)
            .withParameter(LootContextParams.ORIGIN, hook.position())
            .withParameter(LootContextParams.TOOL, rod)
            .withParameter(LootContextParams.THIS_ENTITY, hook)
            .withLuck(luck)
            .create(LootContextParamSets.FISHING);

        LootTable table = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING);
        List<ItemStack> loot = List.copyOf(table.getRandomItems(params));

        FishSpecies species = loot.isEmpty() ? FishSpecies.JUNK : FishSpecies.fromLoot(loot.getFirst());

        RandomSource random = level.getRandom();
        BlockPos pos = hook.blockPosition();
        boolean deepWater = level.getBiome(pos).is(BiomeTags.IS_DEEP_OCEAN);
        long timeOfDay = level.getOverworldClockTime() % 24000L;
        boolean night = timeOfDay >= 13000L && timeOfDay <= 23000L;
        FishSize size = species.isJunk()
            ? FishSize.SMALL
            : FishSize.roll(random, level.isRaining(), deepWater, night);

        return new HookedCatch(species, size, loot);
    }
}
