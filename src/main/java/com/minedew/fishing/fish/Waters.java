package com.minedew.fishing.fish;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * The water a fish was found in, as far as which fish it is cares: what kind of water, how warm,
 * how deep, the hour and the weather. Read once, where the bobber sits or the fish spawned.
 *
 * <p>Minecraft has no seasons, so the cold-water fish that are a winter catch elsewhere are a
 * frozen-biome catch here.
 *
 * @param depth water blocks in the column, surface to bed, at most {@link #MEASURED}
 */
public record Waters(Kind kind, Temperature temperature, int depth, boolean deepOcean,
                     boolean mountains, boolean jungle, Hour hour, boolean raining) {

    public enum Kind { OCEAN, RIVER, SWAMP, LAKE, CAVE }

    public enum Temperature { FROZEN, COLD, TEMPERATE, WARM }

    public enum Hour { DAWN, DAY, DUSK, NIGHT }

    /** Deeper than this reads as bottomless; nothing asks for more. */
    public static final int MEASURED = 32;

    /** Deep enough to count as deep water for the size roll, wherever it is. */
    public static final int DEEP = 16;

    public static Waters at(ServerLevel level, BlockPos pos) {
        BlockPos top = pos;
        for (int i = 0; i < MEASURED && level.getFluidState(top.above()).is(FluidTags.WATER); i++) {
            top = top.above();
        }
        int depth = 0;
        for (BlockPos at = top; depth < MEASURED && level.getFluidState(at).is(FluidTags.WATER); at = at.below()) {
            depth++;
        }

        Holder<Biome> biome = level.getBiome(top);
        // Sky light is raw, not the hour's: zero means rock overhead, not night
        boolean underground = level.getBrightness(LightLayer.SKY, top.above()) == 0;
        Kind kind = underground ? Kind.CAVE
            : biome.is(BiomeTags.IS_OCEAN) ? Kind.OCEAN
            : biome.is(BiomeTags.IS_RIVER) ? Kind.RIVER
            : biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP) ? Kind.SWAMP
            : Kind.LAKE;

        long time = level.getOverworldClockTime() % 24000L;
        Hour hour = time >= 23000L || time < 1000L ? Hour.DAWN
            : time < 11500L ? Hour.DAY
            : time < 13000L ? Hour.DUSK
            : Hour.NIGHT;

        return new Waters(kind, temperature(biome), depth, biome.is(BiomeTags.IS_DEEP_OCEAN),
            biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL), biome.is(BiomeTags.IS_JUNGLE),
            hour, level.isRaining());
    }

    /**
     * Every ocean has the same base temperature, so the sea is read off its name; on land the
     * biome's own number is the honest answer.
     */
    private static Temperature temperature(Holder<Biome> biome) {
        if (is(biome, Biomes.FROZEN_OCEAN, Biomes.DEEP_FROZEN_OCEAN, Biomes.FROZEN_RIVER)) return Temperature.FROZEN;
        if (is(biome, Biomes.COLD_OCEAN, Biomes.DEEP_COLD_OCEAN)) return Temperature.COLD;
        if (is(biome, Biomes.WARM_OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN)) return Temperature.WARM;
        if (biome.is(BiomeTags.IS_OCEAN)) return Temperature.TEMPERATE;

        float base = biome.value().getBaseTemperature();
        if (base < 0.15F) return Temperature.FROZEN;
        if (base < 0.45F) return Temperature.COLD;
        if (base < 0.95F) return Temperature.TEMPERATE;
        return Temperature.WARM;
    }

    @SafeVarargs
    private static boolean is(Holder<Biome> biome, ResourceKey<Biome>... keys) {
        for (ResourceKey<Biome> key : keys) {
            if (biome.is(key)) return true;
        }
        return false;
    }

    public boolean ocean() { return this.kind == Kind.OCEAN; }
    public boolean river() { return this.kind == Kind.RIVER; }
    public boolean swamp() { return this.kind == Kind.SWAMP; }
    public boolean lake() { return this.kind == Kind.LAKE; }
    public boolean cave() { return this.kind == Kind.CAVE; }
    /** Rivers, lakes and swamps: anything under the sky that is not the sea. */
    public boolean freshwater() { return river() || lake() || swamp(); }
    /** Still water: lakes, ponds and swamps. */
    public boolean still() { return lake() || swamp(); }

    public boolean frozen() { return this.temperature == Temperature.FROZEN; }
    /** Cold or frozen. */
    public boolean cold() { return this.temperature.ordinal() <= Temperature.COLD.ordinal(); }
    public boolean temperate() { return this.temperature == Temperature.TEMPERATE; }
    public boolean warm() { return this.temperature == Temperature.WARM; }

    public boolean day() { return this.hour == Hour.DAY; }
    public boolean night() { return this.hour == Hour.NIGHT; }
    /** Dawn or dusk. */
    public boolean twilight() { return this.hour == Hour.DAWN || this.hour == Hour.DUSK; }
    /** Night, twilight, or a sky dark with rain. */
    public boolean dim() { return !day() || this.raining; }
    public boolean clear() { return !this.raining; }

    /** At most this many blocks of water under the surface. */
    public boolean shallow(int blocks) { return this.depth <= blocks; }
    /** At least this many blocks of water under the surface. */
    public boolean deep(int blocks) { return this.depth >= blocks; }
}
