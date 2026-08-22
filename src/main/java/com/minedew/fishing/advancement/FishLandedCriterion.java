package com.minedew.fishing.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.minedew.fishing.fish.FishSize;
import java.util.Optional;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Fires when a fight ends with the fish on the bank, carrying how big it was.
 *
 * <p>Nothing an inventory trigger can see distinguishes these: a trophy cod and a small cod are
 * both a cod in the end, and the difference lived entirely in the minigame that is now over. The
 * size has to be reported at the moment it still exists.
 *
 * <p>Gated on {@link FishSize#difficulty()} rather than the size's name so an advancement asks for
 * "this hard or harder" and keeps meaning that if a tier is ever added between the named ones.
 */
public class FishLandedCriterion extends SimpleCriterionTrigger<FishLandedCriterion.Conditions> {

	@Override
	public Codec<Conditions> codec() {
		return Conditions.CODEC;
	}

	public void trigger(ServerPlayer player, FishSize size) {
		this.trigger(player, conditions -> conditions.matches(size));
	}

	public record Conditions(Optional<Holder<LootItemCondition>> player, Optional<Integer> minDifficulty)
			implements SimpleCriterionTrigger.SimpleInstance {
		public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(
			instance -> instance.group(
				LootItemCondition.CODEC.optionalFieldOf("player").forGetter(Conditions::player),
				Codec.INT.optionalFieldOf("min_difficulty").forGetter(Conditions::minDifficulty)
			).apply(instance, Conditions::new)
		);

		public boolean matches(FishSize size) {
			return this.minDifficulty.map(min -> size.difficulty() >= min).orElse(true);
		}
	}
}
