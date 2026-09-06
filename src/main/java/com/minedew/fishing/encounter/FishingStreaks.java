package com.minedew.fishing.encounter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * How many fish in a row each player has lost.
 *
 * <p>The fight is tuned so a first-timer lands most easy fish, and still somebody has a bad run,
 * and a bad run is where people put the rod down. So every fish that gets away makes the next
 * one a little kinder: a wider bar, and better odds of a smaller fish, up to a ceiling. A fish
 * landed clears it. Junk counts for nothing either way: a boot lost is a snag, not a fight.
 *
 * <p>Kept across restarts with the world, because the run that matters is the one that ended
 * with logging off in disgust.
 */
public final class FishingStreaks extends SavedData {
    private record Entry(UUID player, int misses) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("player").forGetter(Entry::player),
            Codec.INT.fieldOf("misses").forGetter(Entry::misses)
        ).apply(instance, Entry::new));
    }

    private record Data(List<Entry> entries) {
        static final Codec<Data> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Entry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(Data::entries)
        ).apply(instance, Data::new));
    }

    public static final Codec<FishingStreaks> CODEC = Data.CODEC.xmap(FishingStreaks::fromData, FishingStreaks::toData);

    private static final SavedDataType<FishingStreaks> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("minedew-fishing", "streaks"), FishingStreaks::new, CODEC, DataFixTypes.LEVEL);

    private final Map<UUID, Integer> misses = new HashMap<>();

    public static FishingStreaks get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Fish lost in a row, capped where the kindness stops growing. */
    public int misses(UUID player) {
        return this.misses.getOrDefault(player, 0);
    }

    /** One more got away. @return the run so far */
    public int lost(UUID player) {
        int run = Math.min(MinigameTuning.BACKOFF_MAX_MISSES, misses(player) + 1);
        this.misses.put(player, run);
        this.setDirty();
        return run;
    }

    /** A fish landed: the run is over. */
    public void landed(UUID player) {
        if (this.misses.remove(player) != null) this.setDirty();
    }

    private static FishingStreaks fromData(Data data) {
        FishingStreaks streaks = new FishingStreaks();
        for (Entry entry : data.entries()) streaks.misses.put(entry.player(), entry.misses());
        return streaks;
    }

    private Data toData() {
        List<Entry> entries = new java.util.ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : this.misses.entrySet()) {
            if (entry.getValue() > 0) entries.add(new Entry(entry.getKey(), entry.getValue()));
        }
        return new Data(entries);
    }
}
