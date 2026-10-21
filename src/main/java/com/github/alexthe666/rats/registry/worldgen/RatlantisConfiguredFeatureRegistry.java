package com.github.alexthe666.rats.registry.worldgen;

import com.github.alexthe666.rats.RatsMod;
import com.github.alexthe666.rats.registry.RatlantisBlockRegistry;
import com.github.alexthe666.rats.server.world.MarblePileFeature;
import com.github.alexthe666.rats.server.world.RatlantisRuinConfiguration;
import com.github.alexthe666.rats.server.world.RatlantisRuinFeature;
import com.github.alexthe666.rats.server.world.ThickBranchingTrunkPlacer;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.BlockStateProviders;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.SimpleBlockFeature;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.CherryFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.TagMatchTest;

import java.util.Map;

public class RatlantisConfiguredFeatureRegistry {
	public static final ResourceKey<Feature> GHOST_PIRAT_TREE = registerKey("ghost_pirat_tree");
	public static final ResourceKey<Feature> LARGE_GHOST_PIRAT_TREE = registerKey("large_ghost_pirat_tree");
	public static final ResourceKey<Feature> RATGLOVE_FLOWERS = registerKey("ratglove_flowers");
	public static final ResourceKey<Feature> MARBLE_PILE = registerKey("marble_pile");
	public static final ResourceKey<Feature> SMALL_RUINS = registerKey("small_ruins");
	public static final ResourceKey<Feature> LARGE_RUINS = registerKey("large_ruins");
	public static final ResourceKey<Feature> CHEESE_ORE = registerKey("cheese_ore");
	public static final ResourceKey<Feature> GEM_ORE = registerKey("ratlantean_gem_ore");
	public static final ResourceKey<Feature> ORATCHALCUM_ORE = registerKey("oratchalcum_ore");

	public static ResourceKey<Feature> registerKey(String name) {
		return ResourceKey.create(Registries.FEATURE, Identifier.fromNamespaceAndPath(RatsMod.MODID, name));
	}

	// PORT-NOTE: 1.21 forbids touching DeferredHolder.get() at class-load time. PiratTreeGrower.INSTANCE
	// references LARGE_GHOST_PIRAT_TREE during BLOCK registration (via SaplingBlock ctor), which forces
	// this class's <clinit> mid-block-registration. Constructing TreeConfiguration as static fields
	// would try to .get() PIRAT_LOG / PIRAT_LEAVES before they're bound. Build the configs inside
	// bootstrap() instead — that runs at data-gen / world-load time, well after registries are bound.

	// 26.3: configured features are gone; a Feature record is its own configured instance (worldgen/feature).
	public static void bootstrap(BootstrapContext<Feature> context) {
		HolderGetter<StructureProcessorList> processors = context.lookup(Registries.PROCESSOR_LIST);
		Holder<BlockStateProvider> belowTrunkProvider = context.lookup(Registries.BLOCK_STATE_PROVIDER).getOrThrow(BlockStateProviders.SOIL_BENEATH_TREE);
		TreeFeature ghostPiratTreeConfig = new TreeFeature.Builder(
				BlockStateProvider.of(RatlantisBlockRegistry.PIRAT_LOG.get()),
				new ThickBranchingTrunkPlacer(5, 1, 1, 1, 1, 2),
				BlockStateProvider.of(RatlantisBlockRegistry.PIRAT_LEAVES.get()),
				new CherryFoliagePlacer(ConstantInt.of(4), ConstantInt.of(0), ConstantInt.of(5), 0.25F, 0.5F, 0.5F, 0.75F),
				new TwoLayersFeatureSize(1, 0, 2),
				belowTrunkProvider).ignoreVines().build();
		TreeFeature largeGhostPiratTreeConfig = new TreeFeature.Builder(
				BlockStateProvider.of(RatlantisBlockRegistry.PIRAT_LOG.get()),
				new ThickBranchingTrunkPlacer(8, 1, 1, 2, 2, 4),
				BlockStateProvider.of(RatlantisBlockRegistry.PIRAT_LEAVES.get()),
				new CherryFoliagePlacer(ConstantInt.of(4), ConstantInt.of(0), ConstantInt.of(5), 0.25F, 0.5F, 0.5F, 0.75F),
				new TwoLayersFeatureSize(1, 0, 2),
				belowTrunkProvider).ignoreVines().build();
		context.register(GHOST_PIRAT_TREE, ghostPiratTreeConfig);
		context.register(LARGE_GHOST_PIRAT_TREE, largeGhostPiratTreeConfig);
		// 26.1: Feature.FLOWER/RANDOM_PATCH were removed; vanilla flowers are SIMPLE_BLOCK configured features
		// with the patch count/spread handled by the placed feature (see vanilla flower_default.json).
		context.register(RATGLOVE_FLOWERS, new SimpleBlockFeature(BlockStateProvider.of(RatlantisBlockRegistry.RATGLOVE_FLOWER.get())));
		context.register(MARBLE_PILE, new MarblePileFeature());
		context.register(SMALL_RUINS, new RatlantisRuinFeature(new RatlantisRuinConfiguration(Map.of(ruinLocation("cheese_statuette"), 0.35F, ruinLocation("marble_giant_cheese"), 0.1F, ruinLocation("marble_hut"), 0.35F, ruinLocation("marble_pillar_collection"), 0.2F, ruinLocation("marble_pillar_leaning"), 0.25F, ruinLocation("marble_rat_head"), 0.15F, ruinLocation("marble_rat_lincoln"), 0.12F, ruinLocation("marble_small_aquaduct"), 0.3F, ruinLocation("marble_thin_tower"), 0.4F, ruinLocation("marble_tower"), 0.25F), ruinLocation("marble_pillar"), processors.getOrThrow(RatlantisStructureRegistry.RUIN_RUINS))));
		context.register(LARGE_RUINS, new RatlantisRuinFeature(new RatlantisRuinConfiguration(Map.of(ruinLocation("marble_forum"), 0.2F, ruinLocation("marble_palace"), 0.15F, ruinLocation("marble_rat_colossus"), 0.05F, ruinLocation("marble_rat_sphinx"), 0.1F, ruinLocation("marble_temple"), 0.2F), ruinLocation("marble_large_aquaduct"), processors.getOrThrow(RatlantisStructureRegistry.RUIN_RUINS))));
		context.register(CHEESE_ORE, new OreFeature(new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES), RatlantisBlockRegistry.CHEESE_ORE.get().defaultBlockState(), 5));
		context.register(GEM_ORE, new OreFeature(new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES), RatlantisBlockRegistry.RATLANTEAN_GEM_ORE.get().defaultBlockState(), 4));
		context.register(ORATCHALCUM_ORE, new OreFeature(new TagMatchTest(BlockTags.STONE_ORE_REPLACEABLES), RatlantisBlockRegistry.ORATCHALCUM_ORE.get().defaultBlockState(), 3));
	}

	private static Identifier ruinLocation(String name) {
		return Identifier.fromNamespaceAndPath(RatsMod.MODID, name);
	}
}
