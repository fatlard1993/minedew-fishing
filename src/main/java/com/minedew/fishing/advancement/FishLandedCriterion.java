package com.minedew.fishing.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.minedew.fishing.fish.FishSize;
import java.util.List;
import java.util.Optional;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import org.jspecify.annotations.Nullable;

/**
 * Fires when a fight ends with the fish on the bank, carrying how big it was and which fish.
 *
 * <p>Nothing an inventory trigger can see distinguishes these: a trophy cod and a small cod are
 * both a cod in the end, and the difference lived entirely in the minigame that is now over. The
 * size has to be reported at the moment it still exists.
 *
 * <p>Gated on {@link FishSize#difficulty()} rather than the size's name so an advancement asks for
 * "this hard or harder" and keeps meaning that if a tier is ever added between the named ones.
 *
 * <p>{@code fish} is a list of catch ids (see {@link com.minedew.fishing.fish.Quarry#catchId}), any
 * one of which will do: the angler's log asks for each fish by its own, and "any tropical fish" by
 * all of theirs.
 */
public class FishLandedCriterion extends SimpleCriterionTrigger<FishLandedCriterion.Conditions> {

	@Override
	public Codec<Conditions> codec() {
		return Conditions.CODEC;
	}

	/** @param fish the catch's id, or null for one nothing collects */
	public void trigger(ServerPlayer player, FishSize size, @Nullable String fish) {
		this.trigger(player, conditions -> conditions.matches(size, fish));
	}

	public record Conditions(Optional<Holder<LootItemCondition>> player, Optional<Integer> minDifficulty,
			Optional<List<String>> fish) implements SimpleCriterionTrigger.SimpleInstance {
		public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
				LootItemCondition.CODEC.optionalFieldOf("player").forGetter(Conditions::player),
				Codec.INT.optionalFieldOf("min_difficulty").forGetter(Conditions::minDifficulty),
				Codec.STRING.listOf().optionalFieldOf("fish").forGetter(Conditions::fish)
			).apply(instance, Conditions::new)
		);

		public boolean matches(FishSize size, @Nullable String caught) {
			return this.minDifficulty.map(min -> size.difficulty() >= min).orElse(true)
				&& this.fish.map(wanted -> wanted.contains(caught)).orElse(true);
		}
	}
}
