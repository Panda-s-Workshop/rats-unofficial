package com.github.alexthe666.rats.server.advancements;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.advancements.triggers.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

// 26.1: critereon package renamed to criterion; player predicate now parses via EntityPredicate.ADVANCEMENT_CODEC (see vanilla PlayerTrigger).
// 26.3: triggers moved to advancements.triggers; the player predicate is a loot condition holder (ContextAwarePredicate is gone).
public class BlackDeathSummonedTrigger extends SimpleCriterionTrigger<BlackDeathSummonedTrigger.TriggerInstance> {

	@Override
	public Codec<TriggerInstance> codec() {
		return TriggerInstance.CODEC;
	}

	public void trigger(ServerPlayer player) {
		this.trigger(player, instance -> true);
	}

	public record TriggerInstance(Optional<Holder<LootItemCondition>> player) implements SimpleCriterionTrigger.SimpleInstance {
		public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
				LootItemCondition.CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player)
		).apply(instance, TriggerInstance::new));

		public static TriggerInstance summoned() {
			return new TriggerInstance(Optional.empty());
		}
	}
}
