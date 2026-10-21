package com.github.alexthe666.rats.server.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;

import java.util.Map;

public record RatlantisRuinConfiguration(Map<Identifier, Float> ruins, Identifier defaultRuin,
										 Holder<StructureProcessorList> processor) {
	// 26.3: FeatureConfiguration is gone; the config is inlined into the feature JSON, so this is a MapCodec now.
	public static final MapCodec<RatlantisRuinConfiguration> CODEC = RecordCodecBuilder.mapCodec(instance ->
			instance.group(
							Codec.unboundedMap(Identifier.CODEC, Codec.FLOAT).fieldOf("ruins").forGetter(config -> config.ruins),
							Identifier.CODEC.fieldOf("fallback_ruin").forGetter(config -> config.defaultRuin),
							StructureProcessorType.LIST_CODEC.fieldOf("processor").orElse(null).forGetter(config -> config.processor))
					.apply(instance, RatlantisRuinConfiguration::new));
}
