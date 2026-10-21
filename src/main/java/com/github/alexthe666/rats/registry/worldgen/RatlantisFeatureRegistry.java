package com.github.alexthe666.rats.registry.worldgen;

import com.github.alexthe666.rats.RatsMod;
import com.github.alexthe666.rats.server.world.*;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class RatlantisFeatureRegistry {
	// 26.3: carvers, features and structure processors are records/interfaces whose type registries hold their MapCodecs
	// (worldgen/carver_type, worldgen/feature_type, worldgen/structure_processor).
	public static final DeferredRegister<MapCodec<? extends WorldCarver>> CARVERS = DeferredRegister.create(Registries.CARVER_TYPE, RatsMod.MODID);
	public static final DeferredRegister<MapCodec<? extends Feature>> FEATURES = DeferredRegister.create(Registries.FEATURE_TYPE, RatsMod.MODID);
	public static final DeferredRegister<MapCodec<? extends StructureProcessor>> PROCESSORS = DeferredRegister.create(Registries.STRUCTURE_PROCESSOR, RatsMod.MODID);
	public static final DeferredRegister<TrunkPlacerType<?>> TRUNK_PLACERS = DeferredRegister.create(Registries.TRUNK_PLACER_TYPE, RatsMod.MODID);

	public static final DeferredHolder<MapCodec<? extends WorldCarver>, MapCodec<RatsCaveCarver>> RATLANTIS_CAVES = CARVERS.register("ratlantis_caves", () -> RatsCaveCarver.CODEC);

	public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<RatlantisRuinFeature>> RATLANTIS_RUIN = FEATURES.register("ratlantis_ruin", () -> RatlantisRuinFeature.CODEC);
	public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<MarblePileFeature>> MARBLE_PILE = FEATURES.register("marble_pile", () -> MarblePileFeature.CODEC);

	public static final DeferredHolder<MapCodec<? extends StructureProcessor>, MapCodec<CopyInputStateRuleProcessor>> COPY_STATE = PROCESSORS.register("copy_state", () -> CopyInputStateRuleProcessor.CODEC);
	public static final DeferredHolder<TrunkPlacerType<?>, TrunkPlacerType<ThickBranchingTrunkPlacer>> THICK_BASE_BRANCHING_TRUNK_PLACER = TRUNK_PLACERS.register("thick_branching_trunk_placer", () -> new TrunkPlacerType<ThickBranchingTrunkPlacer>(ThickBranchingTrunkPlacer.CODEC));
}
