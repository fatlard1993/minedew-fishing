package com.minedew.fishing.fish;

import com.minedew.fishing.MinedewFishing;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.fish.AbstractFish;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.biome.Biomes;
import org.jspecify.annotations.Nullable;

/**
 * The fish in the world are the fish on the line: a cod swimming in a cold river is a perch, and
 * looks it.
 *
 * <p>Each vanilla fish is asked, the first time it loads, which {@link Fish} it is, by the same
 * table and the same water a bobber there would be; the answer is kept on it for good, and comes
 * along in a bucket. It is always one that borrows its own body, so what it drops when killed is
 * still what the line would have paid: a walleye is a cod underneath and cuts into whitefish.
 *
 * <p>Vanilla puts fish in the sea and salmon in rivers and nothing in a pond, which leaves most of
 * the freshwater table with no body to wear. So cod are added to rivers, swamps, jungles and the
 * ponds of forests, taiga and plains, salmon to swamps and jungles, and tropical fish to jungle
 * water and temperate rivers, where they are the sunfish and the discus.
 */
public final class WildFish {
    private WildFish() {}

    /** Which fish a vanilla fish is, by {@link Fish#id()}. */
    public static final AttachmentType<String> KIND = AttachmentRegistry.<String>builder()
        .persistent(Codec.STRING)
        .buildAndRegister(Identifier.fromNamespaceAndPath(MinedewFishing.MOD_ID, "fish"));

    private static final String BUCKET_KEY = MinedewFishing.MOD_ID + ":fish";

    public static void register() {
        ServerEntityEvents.ENTITY_LOAD.register(WildFish::onLoad);

        BiomeModifications.addSpawn(BiomeSelectors.tag(BiomeTags.IS_RIVER), MobCategory.WATER_AMBIENT, EntityTypes.COD, 4, 1, 3);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.SWAMP, Biomes.MANGROVE_SWAMP),
            MobCategory.WATER_AMBIENT, EntityTypes.COD, 6, 1, 3);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.SWAMP, Biomes.MANGROVE_SWAMP),
            MobCategory.WATER_AMBIENT, EntityTypes.SALMON, 3, 1, 2);
        BiomeModifications.addSpawn(BiomeSelectors.tag(BiomeTags.IS_JUNGLE), MobCategory.WATER_AMBIENT, EntityTypes.COD, 4, 1, 3);
        BiomeModifications.addSpawn(BiomeSelectors.tag(BiomeTags.IS_JUNGLE), MobCategory.WATER_AMBIENT, EntityTypes.SALMON, 3, 1, 3);
        BiomeModifications.addSpawn(BiomeSelectors.tag(BiomeTags.IS_JUNGLE), MobCategory.WATER_AMBIENT, EntityTypes.TROPICAL_FISH, 4, 1, 3);
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(Biomes.RIVER), MobCategory.WATER_AMBIENT, EntityTypes.TROPICAL_FISH, 2, 1, 2);
        BiomeModifications.addSpawn(BiomeSelectors.tag(BiomeTags.IS_FOREST).or(BiomeSelectors.tag(BiomeTags.IS_TAIGA))
            .or(BiomeSelectors.includeByKey(Biomes.PLAINS)), MobCategory.WATER_AMBIENT, EntityTypes.COD, 3, 1, 2);
    }

    private static void onLoad(Entity entity, ServerLevel level) {
        // Left alone without AI: the landing animation's fish is dressed by its catch, and a fish a
        // map-maker froze in place was put there as it is
        if (!(entity instanceof Mob mob) || mob.isNoAi()) return;
        FishSpecies body = FishSpecies.ofBody(entity.getType());
        if (body == null) return;

        String kept = mob.getAttached(KIND);
        Fish fish = kept != null ? Fish.byId(kept) : null;
        boolean fresh = fish == null || fish.getRolledFrom() != body;
        if (fresh) {
            fish = Fish.wild(body, Waters.at(level, mob.blockPosition()), level.getRandom());
            mob.setAttached(KIND, fish.id());
        }
        // Only a fish just named is painted: one that has been a sunfish since it spawned already is
        fish.getLook().dress(mob, fresh);
    }

    public static void toBucket(AbstractFish fish, ItemStack bucket) {
        String kind = fish.getAttached(KIND);
        if (kind != null) {
            CustomData.update(DataComponents.BUCKET_ENTITY_DATA, bucket, tag -> tag.putString(BUCKET_KEY, kind));
        }
    }

    /** The fish living in a bucket, or null for an empty bucket, anything else, or one from before. */
    public static @Nullable Fish inBucket(ItemStack bucket) {
        CustomData data = bucket.get(DataComponents.BUCKET_ENTITY_DATA);
        if (data == null) return null;
        return data.copyTag().getString(BUCKET_KEY).map(Fish::byId).orElse(null);
    }

    public static void fromBucket(AbstractFish fish, CompoundTag tag) {
        tag.getString(BUCKET_KEY).ifPresent(kind -> fish.setAttached(KIND, kind));
    }
}
