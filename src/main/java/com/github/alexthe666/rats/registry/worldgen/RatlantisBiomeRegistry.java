package com.github.alexthe666.rats.registry.worldgen;

import com.github.alexthe666.rats.RatsMod;
import com.github.alexthe666.rats.registry.RatlantisEntityRegistry;
import com.github.alexthe666.rats.registry.RatsEntityRegistry;
import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.placement.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.ARGB;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.attribute.AmbientSounds;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public class RatlantisBiomeRegistry {

	//new biome ideas:
	//make the ocean its own biome
	//ratglove fields. No trees, barely any ruins, just ratglove flowers and maybe even compressed rat blocks. Ratlanteans also spawn in abundance here
	//sparser jungle biome
	//ratlantis plains, spawns ruins more frequently. Maybe could also spawn an ancient city-like structure?
	//marble mushroom biome
	public static final ResourceKey<Biome> RATLANTIS = makeKey("ratlantis");

	private static ResourceKey<Biome> makeKey(String name) {
		return ResourceKey.create(Registries.BIOME, Identifier.fromNamespaceAndPath(RatsMod.MODID, name));
	}

	public static void bootstrap(BootstrapContext<Biome> context) {
		HolderGetter<PlacedFeature> features = context.lookup(Registries.PLACED_FEATURE);
		HolderGetter<WorldCarver> carvers = context.lookup(Registries.CARVER);

		context.register(RatlantisBiomeRegistry.RATLANTIS, new Biome.BiomeBuilder()
				.hasPrecipitation(true)
				.downfall(0.5F)
				.temperature(0.55F)
				// 26.3: the creature spawn probability is an environment attribute
				.setAttribute(EnvironmentAttributes.CREATURE_WORLD_GEN_SPAWN_PROBABILITY, 0.4F)
				// 26.1: sky/fog color and ambient mood are environment attributes (as in the shipped biome JSON)
				.setAttribute(EnvironmentAttributes.SKY_COLOR, ARGB.vector3fFromRGB24(0xFFC62A))
				.setAttribute(EnvironmentAttributes.FOG_COLOR, ARGB.vector3fFromRGB24(0xFFDA7F))
				.setAttribute(EnvironmentAttributes.AMBIENT_SOUNDS, AmbientSounds.LEGACY_CAVE_SETTINGS)
				.mobSpawnSettings(new MobSpawnSettings.Builder()
						.addSpawn(RatlantisEntityRegistry.FERAL_RATLANTEAN.get(), MobCategory.MONSTER, 20, count(1, 3))
						.addSpawn(RatlantisEntityRegistry.PIRAT.get(), MobCategory.MONSTER, 50, count(1, 2))
						.addSpawn(RatlantisEntityRegistry.RATLANTEAN_SPIRIT.get(), MobCategory.MONSTER, 15, count(1, 1))
						.addSpawn(RatlantisEntityRegistry.RATLANTEAN_RATBOT.get(), MobCategory.MONSTER, 5, count(1, 1))
						.addSpawn(EntityTypes.PARROT, MobCategory.CREATURE, 40, count(1, 2))
						.addSpawn(EntityTypes.PANDA, MobCategory.CREATURE, 14, count(1, 2))
						.addSpawn(EntityTypes.PUFFERFISH, MobCategory.WATER_AMBIENT, 15, count(1, 3))
						.addSpawn(RatlantisEntityRegistry.RATFISH.get(), MobCategory.WATER_AMBIENT, 45, count(8, 8))
						.addSpawn(EntityTypes.DOLPHIN, MobCategory.WATER_CREATURE, 2, count(1, 2))
						.addSpawn(RatsEntityRegistry.RAT.get(), RatsMod.RATS, 10, count(2, 4))
						.build())
				.generationSettings(new BiomeGenerationSettings.Builder(features, carvers)
						.addCarver(RatlantisDimensionRegistry.RATLANTIS_CAVES)
						.addFeature(GenerationStep.Decoration.FLUID_SPRINGS, MiscOverworldPlacements.SPRING_WATER)
						.addFeature(GenerationStep.Decoration.FLUID_SPRINGS, MiscOverworldPlacements.SPRING_LAVA)
						.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, RatlantisPlacedFeatureRegistry.SMALL_RUINS)
						.addFeature(GenerationStep.Decoration.SURFACE_STRUCTURES, RatlantisPlacedFeatureRegistry.LARGE_RUINS)
						.addFeature(GenerationStep.Decoration.UNDERGROUND_STRUCTURES, CavePlacements.MONSTER_ROOM)
						.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, OrePlacements.ORE_DIRT)
						.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, OrePlacements.ORE_GRAVEL)
						.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, OrePlacements.ORE_GRANITE_UPPER)
						.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, OrePlacements.ORE_DIORITE_UPPER)
						.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, OrePlacements.ORE_ANDESITE_UPPER)
						.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, OrePlacements.ORE_COAL_UPPER)
						.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, OrePlacements.ORE_IRON_MIDDLE)
						.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, OrePlacements.ORE_GOLD_LOWER)
						.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, OrePlacements.ORE_REDSTONE)
						.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, OrePlacements.ORE_DIAMOND_LARGE)
						.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, OrePlacements.ORE_LAPIS)
						.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, MiscOverworldPlacements.DISK_SAND)
						.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, MiscOverworldPlacements.DISK_CLAY)
						.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, MiscOverworldPlacements.DISK_GRAVEL)
						.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, RatlantisPlacedFeatureRegistry.CHEESE_ORE)
						.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, RatlantisPlacedFeatureRegistry.GEM_ORE)
						.addFeature(GenerationStep.Decoration.UNDERGROUND_ORES, RatlantisPlacedFeatureRegistry.ORATCHALCUM_ORE)
						.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, RatlantisPlacedFeatureRegistry.RATGLOVE_FLOWERS)
						.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, RatlantisPlacedFeatureRegistry.MARBLE_PILE)
						.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.PATCH_LARGE_FERN)
						.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.BAMBOO_LIGHT)
						.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.TREES_JUNGLE)
						.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.FLOWER_WARM)
						.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.PATCH_GRASS_JUNGLE)
						.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.BROWN_MUSHROOM_NORMAL)
						.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.RED_MUSHROOM_NORMAL)
						.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.PATCH_SUGAR_CANE)
						.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.VINES)
						.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, VegetationPlacements.PATCH_MELON)
						.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, AquaticPlacements.WARM_OCEAN_VEGETATION)
						.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, AquaticPlacements.SEAGRASS_WARM)
						.addFeature(GenerationStep.Decoration.VEGETAL_DECORATION, AquaticPlacements.SEA_PICKLE)
						.addFeature(GenerationStep.Decoration.TOP_LAYER_MODIFICATION, MiscOverworldPlacements.FREEZE_TOP_LAYER)
						.build())
				.specialEffects(new BiomeSpecialEffects.Builder()
						.foliageColorOverride(2546944)
						.grassColorOverride(8177920)
						.waterColor(4445678)
						.build())
				.build()
		);
	}

	private static IntProvider count(int min, int max) {
		return min == max ? ConstantInt.of(min) : UniformInt.of(min, max);
	}

	public static BiomeSource buildBiomeSource(HolderGetter<Biome> biomes) {
		return MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(ImmutableList.of(
				Pair.of(Climate.parameters(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F), biomes.getOrThrow(RATLANTIS))
		)));
	}
}
