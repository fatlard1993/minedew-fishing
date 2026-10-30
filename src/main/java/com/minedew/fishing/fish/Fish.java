package com.minedew.fishing.fish;

import com.minedew.fishing.MinedewFishing;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.Predicate;

import static com.minedew.fishing.fish.FishMovementPattern.*;

/**
 * Every fish that can be caught, and where.
 *
 * <p>Which fish bit is two questions. Vanilla's fishing table answers the first, as it always has:
 * cod, salmon, pufferfish or tropical fish, at vanilla's odds with Luck of the Sea and any datapack
 * edits. That decides the plate - a cod roll cuts into whitefish fillets, a salmon roll into oily -
 * so a fish here is only ever the closer look at one of those, and lean fish come of the cod roll,
 * oily fish of the salmon. The water answers the second: of the fish that roll could be, which live
 * where the bobber landed, at this hour, in this weather, at this depth. When none do, it is the
 * vanilla fish itself, so a cast never comes up empty for want of a match.
 *
 * <p>The same table dresses the fish swimming in the world (see {@link WildFish}), which is why each
 * one borrows the body of the vanilla fish it rolls from: a walleye in a river is a cod there,
 * re-skinned, and drops whitefish like one.
 *
 * <p>How each fights is its family's pattern and its own accent, every one run through tune_sim.py
 * and kept inside the band the vanilla four already set: for a good player a medium fish lands about
 * as often as a medium cod or pufferfish, and a bar left parked or an autoclicker lands next to
 * none. The strength trims the family's aggressiveness to get it there, and stays within a tenth.
 *
 * <p>That narrowness is the rule, not timidity. Species is supposed to decide how a fight FEELS and
 * size how HARD it is, and a wide speed spread breaks that: an earlier 0.90-1.20 spread made a
 * medium pufferfish land 1% of the time for a laggy player while a medium cod landed 61%, which is
 * a difficulty tier hiding inside a flavour knob. Character comes from the pattern's shape.
 */
public enum Fish implements Quarry {
    // --- Whitefish: what a cod roll can be ---
    COD("Cod", FishFamily.GROUNDFISH, FishSpecies.COD, Look.plain(EntityTypes.COD), 60,
        w -> w.ocean() && !w.warm() && w.deep(6)),
    LINGCOD("Lingcod", FishFamily.GROUNDFISH, FishSpecies.COD, Look.skin(EntityTypes.COD, "lingcod", 1.15F), 30,
        w -> w.ocean() && w.cold() && w.deep(12),
        BOTTOM_GRIND, 220, 45, 1.00F),
    LARGEMOUTH_BASS("Largemouth Bass", FishFamily.SUNFISHES, FishSpecies.COD, Look.skin(EntityTypes.COD, "largemouth_bass", 1.1F), 40,
        w -> w.still() && !w.cold() && !w.night() && w.shallow(6),
        AMBUSH, 220, 30, 1.10F),
    SMALLMOUTH_BASS("Smallmouth Bass", FishFamily.SUNFISHES, FishSpecies.COD, Look.skin(EntityTypes.COD, "smallmouth_bass"), 35,
        w -> (w.river() || w.lake()) && !w.warm() && !w.frozen() && w.clear(),
        FAST_DART, 160, 30, 1.05F),
    PERCH("Perch", FishFamily.PERCHES, FishSpecies.COD, Look.skin(EntityTypes.COD, "perch", 0.9F), 40,
        w -> (w.river() || w.lake()) && w.cold(),
        SCHOOL, 160, 70, 0.95F),
    WALLEYE("Walleye", FishFamily.PERCHES, FishSpecies.COD, Look.skin(EntityTypes.COD, "walleye", 1.1F), 30,
        w -> (w.river() || w.lake()) && w.dim() && w.deep(4),
        SLOW_FLOATER, 160, 20, 1.00F),
    CARP("Carp", FishFamily.CARPS, FishSpecies.COD, Look.skin(EntityTypes.COD, "carp", 1.15F), 40,
        Waters::still,
        BOTTOM_GRIND, 220, 70, 1.10F),
    CHUB("Chub", FishFamily.CARPS, FishSpecies.COD, Look.skin(EntityTypes.COD, "chub", 0.9F), 40,
        w -> (w.river() || w.lake()) && w.mountains(),
        FAST_DART, 220, 45, 0.95F),
    BREAM("Bream", FishFamily.CARPS, FishSpecies.COD, Look.skin(EntityTypes.COD, "bream"), 35,
        w -> w.river() && w.night(),
        SLOW_FLOATER, 120, 45, 1.05F),
    CATFISH("Catfish", FishFamily.CATFISHES, FishSpecies.COD, Look.skin(EntityTypes.COD, "catfish", 1.2F), 30,
        w -> (w.river() || w.swamp()) && (w.raining() || w.night()) && w.deep(3),
        AMBUSH, 160, 45, 1.00F),
    BULLHEAD("Bullhead", FishFamily.CATFISHES, FishSpecies.COD, Look.skin(EntityTypes.COD, "bullhead", 0.9F), 30,
        w -> w.still() && w.shallow(5),
        MODERATE_DART, 220, 45, 1.10F),
    PIKE("Pike", FishFamily.PIKES, FishSpecies.COD, Look.skin(EntityTypes.COD, "pike", 1.1F), 15,
        w -> (w.river() || w.lake()) && !w.warm() && w.shallow(6),
        FAST_DART, 220, 30, 1.00F),
    STURGEON("Sturgeon", FishFamily.STURGEONS, FishSpecies.COD, Look.skin(EntityTypes.COD, "sturgeon", 1.35F), 6,
        w -> w.river() && w.cold() && w.deep(6),
        LEAP, 220, 45, 0.90F),
    TILAPIA("Tilapia", FishFamily.CICHLIDS, FishSpecies.COD, Look.skin(EntityTypes.COD, "tilapia"), 40,
        w -> w.freshwater() && w.warm()),
    BLIND_CAVE_FISH("Blind Cave Fish", FishFamily.CHARACINS, FishSpecies.COD, Look.skin(EntityTypes.COD, "blind_cave_fish", 0.85F), 100,
        Waters::cave,
        SLOW_FLOATER, 160, 45, 0.95F),

    // --- Oily fish: what a salmon roll can be ---
    SALMON("Salmon", FishFamily.SALMONIDS, FishSpecies.SALMON, Look.plain(EntityTypes.SALMON), 50,
        w -> (w.river() && !w.warm()) || (w.ocean() && w.cold()),
        SLOW_SINUSOIDAL, 120, 45, 1.00F),
    RAINBOW_TROUT("Rainbow Trout", FishFamily.SALMONIDS, FishSpecies.SALMON, Look.skin(EntityTypes.SALMON, "rainbow_trout", 0.9F), 35,
        w -> (w.river() || w.lake()) && !w.warm() && !w.frozen() && w.clear() && w.day(),
        SLOW_SINUSOIDAL, 220, 70, 1.05F),
    TIGER_TROUT("Tiger Trout", FishFamily.SALMONIDS, FishSpecies.SALMON, Look.skin(EntityTypes.SALMON, "tiger_trout", 0.9F), 20,
        w -> (w.river() || w.lake()) && w.cold(),
        SLOW_SINUSOIDAL, 180, 60, 0.95F),
    SARDINE("Sardine", FishFamily.HERRINGS, FishSpecies.SALMON, Look.skin(EntityTypes.SALMON, "sardine", 0.6F), 40,
        w -> w.ocean() && !w.cold() && w.day() && w.shallow(16)),
    ANCHOVY("Anchovy", FishFamily.HERRINGS, FishSpecies.SALMON, Look.skin(EntityTypes.SALMON, "anchovy", 0.5F), 35,
        w -> w.ocean() && !w.frozen() && w.shallow(10),
        FAST_ERRATIC, 220, 30, 0.95F),
    HERRING("Herring", FishFamily.HERRINGS, FishSpecies.SALMON, Look.skin(EntityTypes.SALMON, "herring", 0.7F), 40,
        w -> w.ocean() && w.cold(),
        MODERATE_DART, 220, 30, 1.05F),
    SHAD("Shad", FishFamily.HERRINGS, FishSpecies.SALMON, Look.skin(EntityTypes.SALMON, "shad", 0.75F), 35,
        w -> w.river() && w.raining(),
        LEAP, 160, 30, 0.90F),
    TUNA("Tuna", FishFamily.TUNAS, FishSpecies.SALMON, Look.skin(EntityTypes.SALMON, "tuna", 1.4F), 15,
        w -> w.ocean() && !w.cold() && w.day() && w.deep(20),
        FAST_DART, 220, 30, 0.95F),
    ALBACORE("Albacore", FishFamily.TUNAS, FishSpecies.SALMON, Look.skin(EntityTypes.SALMON, "albacore", 1.15F), 30,
        w -> w.ocean() && !w.frozen() && w.twilight() && w.deep(12),
        SNAP, 120, 70, 1.10F),
    EEL("Eel", FishFamily.EELS, FishSpecies.SALMON, Look.skin(EntityTypes.SALMON, "eel"), 30,
        w -> (w.ocean() || w.swamp()) && (w.night() || w.raining()),
        AMBUSH, 160, 30, 0.90F),
    DORADO("Dorado", FishFamily.CHARACINS, FishSpecies.SALMON, Look.skin(EntityTypes.SALMON, "dorado", 1.05F), 40,
        w -> (w.river() || w.lake()) && (w.jungle() || w.warm()) && w.day(),
        RUN, 220, 70, 0.95F),

    // --- What a pufferfish roll can be: the ones that sting, or look like they might ---
    PUFFERFISH("Pufferfish", FishFamily.PUFFERS, FishSpecies.PUFFERFISH, Look.plain(EntityTypes.PUFFERFISH), 60,
        w -> w.ocean() && !w.cold() && w.shallow(14)),
    BLOBFISH("Blobfish", FishFamily.SCULPINS, FishSpecies.PUFFERFISH, Look.skin(EntityTypes.PUFFERFISH, "blobfish", 1.2F), 40,
        w -> w.ocean() && w.night() && w.deep(24)),
    STONEFISH("Stonefish", FishFamily.SCORPIONFISHES, FishSpecies.PUFFERFISH, Look.skin(EntityTypes.PUFFERFISH, "stonefish"), 30,
        w -> w.cave() || (w.ocean() && w.warm() && w.shallow(8))),

    // --- What a tropical fish roll can be ---
    /** The reef: one of vanilla's own tropical fish, named for its pattern when it is caught. */
    TROPICAL_FISH("Tropical Fish", FishFamily.REEF, FishSpecies.TROPICAL_FISH, Look.plain(EntityTypes.TROPICAL_FISH), 80,
        Waters::ocean),
    SUNFISH("Sunfish", FishFamily.SUNFISHES, FishSpecies.TROPICAL_FISH,
        Look.tropical(TropicalFish.Pattern.GLITTER, DyeColor.GREEN, DyeColor.ORANGE), 60,
        w -> (w.river() || w.lake()) && w.temperate() && w.shallow(8),
        FAST_ERRATIC, 220, 30, 0.95F),
    BLUE_DISCUS("Blue Discus", FishFamily.CICHLIDS, FishSpecies.TROPICAL_FISH,
        Look.tropical(TropicalFish.Pattern.STRIPEY, DyeColor.LIGHT_BLUE, DyeColor.BLUE), 50,
        w -> w.freshwater() && (w.warm() || w.jungle()),
        SLOW_FLOATER, 220, 30, 0.95F),
    LIONFISH("Lionfish", FishFamily.SCORPIONFISHES, FishSpecies.TROPICAL_FISH,
        Look.tropical(TropicalFish.Pattern.STRIPEY, DyeColor.WHITE, DyeColor.BROWN), 25,
        w -> w.ocean() && w.warm(),
        SLOW_FLOATER, 160, 45, 1.10F);

    private final String displayName;
    private final FishFamily family;
    private final FishSpecies rolledFrom;
    private final Look look;
    private final int weight;
    private final Predicate<Waters> where;
    private final @Nullable FishMovementPattern accent;
    private final int accentPeriodTicks;
    private final int accentDurationTicks;
    private final float strength;

    Fish(String displayName, FishFamily family, FishSpecies rolledFrom, Look look, int weight, Predicate<Waters> where) {
        this(displayName, family, rolledFrom, look, weight, where, null, 0, 0, 1F);
    }

    Fish(String displayName, FishFamily family, FishSpecies rolledFrom, Look look, int weight, Predicate<Waters> where,
         @Nullable FishMovementPattern accent, int accentPeriodTicks, int accentDurationTicks, float strength) {
        this.displayName = displayName;
        this.family = family;
        this.rolledFrom = rolledFrom;
        this.look = look;
        this.weight = weight;
        this.where = where;
        this.accent = accent;
        this.accentPeriodTicks = accentPeriodTicks;
        this.accentDurationTicks = accentDurationTicks;
        this.strength = strength;
    }

    /**
     * What bit, given what vanilla's table rolled and where. A reef fish comes back as the one
     * individual on the line, with its own pattern and name.
     */
    public static Quarry bite(FishSpecies rolled, Waters waters, RandomSource random) {
        Fish fish = pick(rolled, waters, random);
        return fish == TROPICAL_FISH ? ReefFish.random(random) : fish;
    }

    /** Which fish a vanilla fish swimming here is. */
    public static Fish wild(FishSpecies body, Waters waters, RandomSource random) {
        return pick(body, waters, random);
    }

    private static Fish pick(FishSpecies rolled, Waters waters, RandomSource random) {
        int total = 0;
        for (Fish fish : values()) {
            if (fish.rolledFrom == rolled && fish.where.test(waters)) total += fish.weight;
        }
        if (total == 0) return plain(rolled);

        int roll = random.nextInt(total);
        for (Fish fish : values()) {
            if (fish.rolledFrom != rolled || !fish.where.test(waters)) continue;
            roll -= fish.weight;
            if (roll < 0) return fish;
        }
        return plain(rolled);
    }

    /** The vanilla fish itself, for when the water has nothing more particular to offer. */
    public static Fish plain(FishSpecies rolled) {
        return switch (rolled) {
            case SALMON -> SALMON;
            case PUFFERFISH -> PUFFERFISH;
            case TROPICAL_FISH -> TROPICAL_FISH;
            default -> COD;
        };
    }

    /** Null for anything that is not one. */
    public static @Nullable Fish byId(String id) {
        for (Fish fish : values()) {
            if (fish.id().equals(id)) return fish;
        }
        return null;
    }

    /**
     * Whether it lives anywhere in this kind of water: the water's kind held, and every other
     * condition tried in turn.
     */
    public boolean foundIn(Waters.Kind kind) {
        int[] depths = {1, 3, 5, 8, 12, 20, Waters.MEASURED};
        for (Waters.Temperature temperature : Waters.Temperature.values()) {
            for (Waters.Hour hour : Waters.Hour.values()) {
                for (int depth : depths) {
                    for (int flags = 0; flags < 16; flags++) {
                        Waters waters = new Waters(kind, temperature, depth, (flags & 1) != 0, (flags & 2) != 0,
                            (flags & 4) != 0, hour, (flags & 8) != 0);
                        if (this.where.test(waters)) return true;
                    }
                }
            }
        }
        return false;
    }

    /** The name it is kept and asked for by, e.g. {@code largemouth_bass}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public FishFamily getFamily() {
        return this.family;
    }

    /** The vanilla fish this one is the closer look at; it decides the fillet and the body. */
    public FishSpecies getRolledFrom() {
        return this.rolledFrom;
    }

    public Look getLook() {
        return this.look;
    }

    @Override
    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public float getBaseSpeed() {
        return this.family.getBaseSpeed();
    }

    @Override
    public float getBaseAggressiveness() {
        return this.family.getBaseAggressiveness() * this.strength;
    }

    @Override
    public FishMovementPattern getPattern() {
        return this.family.getPattern();
    }

    @Override
    public @Nullable FishMovementPattern getAccent() {
        return this.accent;
    }

    @Override
    public int getAccentPeriodTicks() {
        return this.accentPeriodTicks;
    }

    @Override
    public int getAccentDurationTicks() {
        return this.accentDurationTicks;
    }

    @Override
    public Item getBonusItem() {
        return this.rolledFrom.getBonusItem();
    }

    @Override
    public EntityType<?> getDisplayEntity() {
        return this.look.body();
    }

    @Override
    public boolean isJunk() {
        return false;
    }

    @Override
    public void dressDisplay(net.minecraft.world.entity.Mob shown, FishSize size) {
        this.look.dress(shown, true);
    }

    @Override
    public String catchId() {
        return MinedewFishing.MOD_ID + ":" + id();
    }
}
