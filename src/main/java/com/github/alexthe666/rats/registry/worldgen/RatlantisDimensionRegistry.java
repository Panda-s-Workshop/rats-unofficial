package com.github.alexthe666.rats.registry.worldgen;

import com.github.alexthe666.rats.RatsMod;
import com.google.common.collect.ImmutableList;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.data.worldgen.biome.OverworldBiomes;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TimelineTags;
import net.minecraft.util.ARGB;
import net.minecraft.util.valueproviders.TrapezoidFloat;
import net.minecraft.util.valueproviders.UniformFloat;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.util.valueproviders.VeryBiasedToBottomInt;
import net.minecraft.world.attribute.AmbientSounds;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributeMap;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.timeline.Timeline;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.carver.CaveWorldCarver;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;
import net.minecraft.world.level.levelgen.material.MaterialRules;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.placement.CaveSurface;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.List;
import java.util.Optional;

public class RatlantisDimensionRegistry {
	public static final Identifier DIMENSION = Identifier.fromNamespaceAndPath(RatsMod.MODID, "ratlantis");
	public static final ResourceKey<Level> DIMENSION_KEY = ResourceKey.create(Registries.DIMENSION, DIMENSION);

	// 26.3: configured carvers became plain carvers (worldgen/carver).
	public static final ResourceKey<WorldCarver> RATLANTIS_CAVES = ResourceKey.create(Registries.CARVER, Identifier.fromNamespaceAndPath(RatsMod.MODID, "ratlantis_caves"));
	public static final ResourceKey<NoiseGeneratorSettings> RATLANTIS_NOISE_GEN = ResourceKey.create(Registries.NOISE_SETTINGS, Identifier.fromNamespaceAndPath(RatsMod.MODID, "ratlantis_noise_gen"));
	// 26.3: the surface rule moved out of the noise settings into its own registry (worldgen/material_rule).
	public static final ResourceKey<MaterialRule> RATLANTIS_MATERIAL_RULE = ResourceKey.create(Registries.MATERIAL_RULE, Identifier.fromNamespaceAndPath(RatsMod.MODID, "ratlantis_noise_gen"));
	public static final ResourceKey<DimensionType> RATLANTIS_DIM_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, Identifier.fromNamespaceAndPath(RatsMod.MODID, "ratlantis_type"));
	public static final ResourceKey<LevelStem> RATLANTIS_LEVEL_STEM = ResourceKey.create(Registries.LEVEL_STEM, DIMENSION);

	private static final ResourceKey<DensityFunction> SHIFT_X = vanillaFunction("shift_x");
	private static final ResourceKey<DensityFunction> SHIFT_Z = vanillaFunction("shift_z");
	// 26.3: the old end_islands density function is vanilla's end/islands (centre island plus end_outer_islands).
	private static final ResourceKey<DensityFunction> END_ISLANDS = vanillaFunction("end/islands");

	private static ResourceKey<DensityFunction> vanillaFunction(String name) {
		return ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.withDefaultNamespace(name));
	}

	// 26.1: DimensionType gained skybox/cardinal lighting/environment attributes/timelines/clocks;
	// values mirror the vanilla overworld bootstrap with Ratlantis' old flags (natural, bed works,
	// 0..256 world height, no fixed time).
	private static DimensionType ratlantisType(HolderGetter<Block> blocks, HolderGetter<Timeline> timelines, HolderGetter<WorldClock> clocks) {
		EnvironmentAttributeMap attributes = EnvironmentAttributeMap.builder()
				.set(EnvironmentAttributes.FOG_COLOR, ARGB.vector3fFromRGB24(-4138753))
				.set(EnvironmentAttributes.SKY_COLOR, ARGB.vector3fFromRGB24(OverworldBiomes.calculateSkyColor(0.8F)))
				.set(EnvironmentAttributes.AMBIENT_LIGHT_COLOR, ARGB.vector3fFromRGB24(-16119286))
				.set(EnvironmentAttributes.CLOUD_COLOR, ARGB.vector4fFromARGB32(ARGB.white(0.8F)))
				.set(EnvironmentAttributes.CLOUD_HEIGHT, 192.33F)
				.set(EnvironmentAttributes.BED_RULE, BedRule.CAN_SLEEP_WHEN_DARK)
				.set(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, false)
				.set(EnvironmentAttributes.NETHER_PORTAL_SPAWNS_PIGLINS, false)
				.set(EnvironmentAttributes.AMBIENT_SOUNDS, AmbientSounds.LEGACY_CAVE_SETTINGS)
				.build();
		return new DimensionType(
				false, //has fixed time
				true, //has skylight
				false, //has ceiling
				false, //has ender dragon fight
				1.0D, //coordinate scale
				0, //min y
				256, //height
				256, //logical height
				blocks.getOrThrow(BlockTags.INFINIBURN_OVERWORLD),
				0.0F, //ambient light
				new DimensionType.MonsterSettings(UniformInt.of(0, 7), 7),
				DimensionType.Skybox.OVERWORLD,
				CardinalLighting.Type.DEFAULT,
				attributes,
				timelines.getOrThrow(TimelineTags.IN_OVERWORLD),
				Optional.of(clocks.getOrThrow(WorldClocks.OVERWORLD))
		);
	}

	// 26.3: noise cell sizes moved from NoiseSettings into the interpolated density function (26.1 used
	// size_horizontal 1 / size_vertical 2, i.e. 4x8 block cells); "preliminary_surface_level" became
	// "chunk_surface_level"; barrier/fluid/lava noises moved into the aquifer config; cache_2d is cache.
	public static NoiseGeneratorSettings ratlantisNoise(HolderGetter<DensityFunction> functions, HolderGetter<NormalNoise> noises, HolderGetter<MaterialRule> materialRules) {
		DensityFunction densityfunction = NoiseRouterData.getFunction(functions, SHIFT_X);
		DensityFunction densityfunction1 = NoiseRouterData.getFunction(functions, SHIFT_Z);
		DensityFunction endIslands = NoiseRouterData.getFunction(functions, END_ISLANDS);
		// kept exactly as 26.1 shipped it (the old initial-density expression, which evaluates to about y = -1)
		DensityFunction surfaceLevel = DensityFunctions.add(
				DensityFunctions.constant(-0.234275F),
				DensityFunctions.mul(
						DensityFunctions.yClampedGradient(16, 32, 0.0F, 1.0F),
						DensityFunctions.add(
								DensityFunctions.constant(0.234375F),
								DensityFunctions.add(
										DensityFunctions.constant(-23.4375F),
										DensityFunctions.mul(
												DensityFunctions.yClampedGradient(0, 312, 1.0F, 0.0F),
												DensityFunctions.add(
														DensityFunctions.constant(23.4375F),
														DensityFunctions.add(
																DensityFunctions.constant(-0.703125F),
																DensityFunctions.cache(endIslands)
														)
												)
										)
								))
				)
		);
		return new NoiseGeneratorSettings(
				NoiseSettings.create(0, 256),
				Blocks.STONE.defaultBlockState(),
				Blocks.WATER.defaultBlockState(),
				new NoiseRouter(
						DensityFunctions.shiftedNoise2d(densityfunction, densityfunction1, 0.25D, noises.getOrThrow(Noises.TEMPERATURE)), //temperature
						DensityFunctions.shiftedNoise2d(densityfunction, densityfunction1, 0.25D, noises.getOrThrow(Noises.VEGETATION)), //vegetation
						DensityFunctions.zero(), //continents
						DensityFunctions.cache(endIslands), //erosion
						DensityFunctions.zero(), //depth
						DensityFunctions.zero(), //ridges
						surfaceLevel, //chunk surface level
						DensityFunctions.mul(
								DensityFunctions.constant(0.64F),
								DensityFunctions.interpolated(
										DensityFunctions.blendDensity(
												DensityFunctions.add(
														DensityFunctions.constant(0.554375F),
														DensityFunctions.mul(
																DensityFunctions.yClampedGradient(0, 87, 0.25F, 3.6F),
																DensityFunctions.add(
																		DensityFunctions.constant(1.234375F),
																		DensityFunctions.add(
																				DensityFunctions.constant(-2.75F),
																				DensityFunctions.mul(
																						DensityFunctions.yClampedGradient(0, 175, 1.0F, 0.0F),
																						DensityFunctions.add(
																								DensityFunctions.constant(1.75F),
																								NoiseRouterData.getFunction(functions, NoiseRouterData.AMPLIFIED_OVERWORLD_FUNCTIONS.slopedCheese())
																						)
																				)
																		)
																)
														)
												)
										), 4, 8
								)
						).squeeze() //final density
				),
				materialRules.getOrThrow(RATLANTIS_MATERIAL_RULE),
				List.of(),
				63,
				false,
				// aquifers were enabled with all-zero barrier/fluid/lava noises and no deep-dark exclusion
				Optional.of(new Aquifer.Config(
						DensityFunctions.zero(), //barrier
						DensityFunctions.zero(), //fluid level floodedness
						DensityFunctions.zero(), //fluid level spread
						DensityFunctions.zero(), //lava
						DensityFunctions.zero(), //exclusion
						surfaceLevel //surface level
				)),
				false,
				NoiseGeneratorSettings.DebugFunctions.EMPTY
		);
	}

	public static void bootstrapNoise(BootstrapContext<NoiseGeneratorSettings> context) {
		context.register(RATLANTIS_NOISE_GEN, ratlantisNoise(context.lookup(Registries.DENSITY_FUNCTION), context.lookup(Registries.NOISE), context.lookup(Registries.MATERIAL_RULE)));
	}

	public static void bootstrapMaterialRule(BootstrapContext<MaterialRule> context) {
		context.register(RATLANTIS_MATERIAL_RULE, createSurfaceRules());
	}

	public static void bootstrapType(BootstrapContext<DimensionType> context) {
		context.register(RATLANTIS_DIM_TYPE, ratlantisType(context.lookup(Registries.BLOCK), context.lookup(Registries.TIMELINE), context.lookup(Registries.WORLD_CLOCK)));
	}

	// 26.3: the cave carver is a record with its config inline. Count and thickness were hard-coded in 26.1's
	// CaveWorldCarver and are now explicit (same values as vanilla's minecraft:cave). The replaceable tag, the lava
	// level (26.1: the bottom, i.e. no lava) and the debug settings are gone: carvers skip #uncarvable blocks and the
	// aquifer decides what fills a carved block (no lava above y -54).
	public static void bootstrapCarver(BootstrapContext<WorldCarver> context) {
		context.register(RATLANTIS_CAVES, new CaveWorldCarver(
				0.15F,
				UniformHeight.of(VerticalAnchor.aboveBottom(8), VerticalAnchor.absolute(180)),
				VeryBiasedToBottomInt.of(0, 14),
				TrapezoidFloat.of(0.0F, 3.0F, 1.0F),
				true,
				UniformFloat.of(0.1F, 0.9F),
				UniformFloat.of(0.7F, 1.4F),
				UniformFloat.of(0.8F, 1.3F),
				net.minecraft.util.valueproviders.ConstantFloat.of(1.0F),
				UniformFloat.of(-1.0F, -0.4F)));
	}

	public static void bootstrapLevelStem(BootstrapContext<LevelStem> context) {
		HolderGetter<Biome> biomeRegistry = context.lookup(Registries.BIOME);
		HolderGetter<DimensionType> dimTypes = context.lookup(Registries.DIMENSION_TYPE);
		HolderGetter<NoiseGeneratorSettings> noiseGenSettings = context.lookup(Registries.NOISE_SETTINGS);
		context.register(RATLANTIS_LEVEL_STEM, new LevelStem(dimTypes.getOrThrow(RatlantisDimensionRegistry.RATLANTIS_DIM_TYPE), new NoiseBasedChunkGenerator(RatlantisBiomeRegistry.buildBiomeSource(biomeRegistry), noiseGenSettings.getOrThrow(RatlantisDimensionRegistry.RATLANTIS_NOISE_GEN))));
	}

	// 26.3: surface rules are material rules; the old SurfaceRules.ON_FLOOR / ON_CEILING / UNDER_FLOOR constants are
	// these stone depth checks (vanilla registers them as minecraft:on_floor etc. material conditions).
	private static final MaterialCondition ON_FLOOR = MaterialRules.stoneDepthCheck(0, false, CaveSurface.FLOOR);
	private static final MaterialCondition ON_CEILING = MaterialRules.stoneDepthCheck(0, false, CaveSurface.CEILING);
	private static final MaterialCondition UNDER_FLOOR = MaterialRules.stoneDepthCheck(0, true, CaveSurface.FLOOR);

	private static final MaterialRule BEDROCK = makeStateRule(Blocks.BEDROCK);
	private static final MaterialRule DIRT = makeStateRule(Blocks.DIRT);
	private static final MaterialRule GRASS_BLOCK = makeStateRule(Blocks.GRASS_BLOCK);
	private static final MaterialRule SAND = makeStateRule(Blocks.SAND);
	private static final MaterialRule SANDSTONE = makeStateRule(Blocks.SANDSTONE);

	private static MaterialRule makeStateRule(Block block) {
		return MaterialRules.state(block.defaultBlockState());
	}

	private static MaterialRule createSurfaceRules() {
		MaterialRule overworldLike = MaterialRules.sequence(
				MaterialRules.ifTrue(ON_FLOOR,
						MaterialRules.sequence(
								MaterialRules.sequence(
										MaterialRules.ifTrue(ON_CEILING, SANDSTONE),
										MaterialRules.ifTrue(MaterialRules.waterBlockCheck(-1, 0), GRASS_BLOCK), SAND),
								MaterialRules.ifTrue(MaterialRules.waterBlockCheck(-1, 0),
										MaterialRules.sequence(GRASS_BLOCK)))),
				MaterialRules.ifTrue(MaterialRules.waterStartCheck(-6, -1),
						MaterialRules.sequence(
								MaterialRules.ifTrue(UNDER_FLOOR, DIRT))));

		ImmutableList.Builder<MaterialRule> builder = ImmutableList.builder();

		builder.add(MaterialRules.ifTrue(MaterialRules.verticalGradient("bedrock_floor", VerticalAnchor.bottom(), VerticalAnchor.aboveBottom(5)), BEDROCK));
		builder.add(overworldLike);
		return MaterialRules.sequence(builder.build().toArray(MaterialRule[]::new));
	}
}
