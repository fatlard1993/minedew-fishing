package com.minedew.fishing.fish;

import com.minedew.fishing.MinedewFishing;
import net.minecraft.locale.Language;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * One tropical fish off the reef, by its own pattern and colours, and named the way vanilla names
 * them: one of its 22 named fish (Clownfish, Queen Angelfish...), or else by its pattern and two
 * colours, "Lime-Gray Snooper". Vanilla has always had these names and only ever showed them on a
 * bucket.
 *
 * <p>Fights as {@link Fish#TROPICAL_FISH}, whichever it is: the reef is one family, and the
 * pattern is the reward, not a tell.
 */
public record ReefFish(TropicalFish.Variant variant) implements Quarry {
    /** Vanilla's own odds for a spawned tropical fish being one of the named ones. */
    private static final float NAMED_CHANCE = 0.9F;

    public static ReefFish random(RandomSource random) {
        if (random.nextFloat() < NAMED_CHANCE) {
            return new ReefFish(TropicalFish.COMMON_VARIANTS.get(random.nextInt(TropicalFish.COMMON_VARIANTS.size())));
        }
        TropicalFish.Pattern[] patterns = TropicalFish.Pattern.values();
        DyeColor[] colours = DyeColor.values();
        return new ReefFish(new TropicalFish.Variant(patterns[random.nextInt(patterns.length)],
            colours[random.nextInt(colours.length)], colours[random.nextInt(colours.length)]));
    }

    /** Which of vanilla's named fish this is, or -1 for one named by its pattern. */
    public int named() {
        return TropicalFish.COMMON_VARIANTS.indexOf(this.variant);
    }

    @Override
    public String getDisplayName() {
        Language language = Language.getInstance();
        int named = named();
        if (named >= 0) return language.getOrDefault("entity.minecraft.tropical_fish.predefined." + named);

        String base = language.getOrDefault("color.minecraft." + this.variant.baseColor().getSerializedName());
        String stripes = language.getOrDefault("color.minecraft." + this.variant.patternColor().getSerializedName());
        String pattern = language.getOrDefault("entity.minecraft.tropical_fish.type." + this.variant.pattern().getSerializedName());
        return (base.equals(stripes) ? base : base + "-" + stripes) + " " + pattern;
    }

    /**
     * {@code minedew-fishing:tropical/<name>} for a named one, {@code tropical/clownfish}; the plain
     * tropical fish's own id for the rest, which have no name to collect.
     */
    @Override
    public String catchId() {
        int named = named();
        if (named < 0) return Fish.TROPICAL_FISH.catchId();
        return MinedewFishing.MOD_ID + ":tropical/" + getDisplayName().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    @Override
    public void dressDisplay(Mob shown, FishSize size) {
        Fish.TROPICAL_FISH.dressDisplay(shown, size);
        Look.paint(shown, this.variant);
    }

    @Override public float getBaseSpeed() { return Fish.TROPICAL_FISH.getBaseSpeed(); }
    @Override public float getBaseAggressiveness() { return Fish.TROPICAL_FISH.getBaseAggressiveness(); }
    @Override public FishMovementPattern getPattern() { return Fish.TROPICAL_FISH.getPattern(); }
    @Override public @Nullable FishMovementPattern getAccent() { return Fish.TROPICAL_FISH.getAccent(); }
    @Override public int getAccentPeriodTicks() { return Fish.TROPICAL_FISH.getAccentPeriodTicks(); }
    @Override public int getAccentDurationTicks() { return Fish.TROPICAL_FISH.getAccentDurationTicks(); }
    @Override public Item getBonusItem() { return Fish.TROPICAL_FISH.getBonusItem(); }
    @Override public EntityType<?> getDisplayEntity() { return Fish.TROPICAL_FISH.getDisplayEntity(); }
    @Override public boolean isJunk() { return false; }
}
