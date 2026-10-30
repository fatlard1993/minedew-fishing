package com.minedew.fishing.fish;

import com.minedew.fishing.MinedewFishing;
import justfatlard.pandorical.api.PandoricalApi;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.animal.fish.TropicalFish.Variant;
import net.minecraft.world.item.DyeColor;
import org.jspecify.annotations.Nullable;

/**
 * How a fish is drawn: the vanilla body it borrows, worn plain, re-skinned, or (for the shapes a
 * tropical fish already has) as one of vanilla's own patterns and colours.
 *
 * <p>A skin is a Pandorical entity model under {@code pandorical/entity_models/fish/<skin>.json},
 * carrying the texture and any stretch; a client without Pandorical sees the plain body.
 *
 * @param skin      the model's name, or null for the body as vanilla draws it
 * @param scale     how much bigger it is drawn than the body it borrows, on top of its size class;
 *                  drawn only, the hitbox is the body's
 * @param variant   a tropical fish's pattern and colours, or null to leave the body's own
 */
public record Look(EntityType<? extends Mob> body, @Nullable String skin, float scale,
                   @Nullable Variant variant) {

    public static Look plain(EntityType<? extends Mob> body) {
        return new Look(body, null, 1F, null);
    }

    public static Look skin(EntityType<? extends Mob> body, String skin) {
        return new Look(body, skin, 1F, null);
    }

    public static Look skin(EntityType<? extends Mob> body, String skin, float scale) {
        return new Look(body, skin, scale, null);
    }

    public static Look tropical(TropicalFish.Pattern pattern, DyeColor base, DyeColor stripes) {
        return new Look(EntityTypes.TROPICAL_FISH, null, 1F, new Variant(pattern, base, stripes));
    }

    /**
     * Dress a mob of {@link #body()} as this fish. Every call sets or clears the skin outright, so a
     * mob that was dressed as something else stops looking like it.
     *
     * @param recolour whether to paint the tropical variant over the mob's own; a wild reef fish
     *                 keeps the colours it spawned with
     */
    public void dress(Mob mob, boolean recolour) {
        if (this.skin != null) {
            PandoricalApi.entityModels().set(mob,
                Identifier.fromNamespaceAndPath(MinedewFishing.MOD_ID, "fish/" + this.skin), null, this.scale);
        } else if (PandoricalApi.entityModels().get(mob) != null) {
            PandoricalApi.entityModels().clear(mob);
        }
        if (recolour && this.variant != null) paint(mob, this.variant);
    }

    static void paint(Mob mob, Variant variant) {
        mob.setComponent(DataComponents.TROPICAL_FISH_PATTERN, variant.pattern());
        mob.setComponent(DataComponents.TROPICAL_FISH_BASE_COLOR, variant.baseColor());
        mob.setComponent(DataComponents.TROPICAL_FISH_PATTERN_COLOR, variant.patternColor());
    }
}
