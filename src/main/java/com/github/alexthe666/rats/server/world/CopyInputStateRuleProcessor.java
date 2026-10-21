package com.github.alexthe666.rats.server.world;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// 26.3: StructureProcessor is an interface and its registry holds the MapCodec directly (no StructureProcessorType).
public class CopyInputStateRuleProcessor implements StructureProcessor {

	public static final MapCodec<CopyInputStateRuleProcessor> CODEC = ProcessorRule.CODEC.listOf().fieldOf("rules").xmap(CopyInputStateRuleProcessor::new, config -> config.rules);

	private final ImmutableList<ProcessorRule> rules;

	public CopyInputStateRuleProcessor(List<? extends ProcessorRule> list) {
		this.rules = ImmutableList.copyOf(list);
	}

	@Override
	public StructureTemplate.StructureBlockInfo process(LevelReader reader, BlockPos oldPos, BlockPos newPos, StructureTemplate.StructureBlockInfo oldInfo, StructureTemplate.StructureBlockInfo newInfo, StructurePlaceSettings settings, @Nullable StructureTemplate template) {
		RandomSource randomsource = RandomSource.create(Mth.getSeed(newInfo.pos()));
		for (ProcessorRule processorrule : this.rules) {
			// 26.3: the location predicate now reads the world state itself (testAgainstWorldState).
			if (processorrule.test(reader, newInfo.state(), oldInfo.pos(), newInfo.pos(), newPos, randomsource)) {
				return new StructureTemplate.StructureBlockInfo(newInfo.pos(), processorrule.getOutputState().getBlock().withPropertiesOf(oldInfo.state()), processorrule.getOutputTag(randomsource, newInfo.nbt()));
			}
		}

		return newInfo;
	}

	@Override
	public MapCodec<CopyInputStateRuleProcessor> codec() {
		return CODEC;
	}
}
