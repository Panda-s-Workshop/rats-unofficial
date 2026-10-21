package com.github.alexthe666.rats.server.world;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;

/**
 * 26.3: stand-in for the removed FeaturePlaceContext. A Feature is now a record that is its own configured instance
 * and is placed through {@code place(level, chunkGenerator, random, origin)}; Rats' features wrap those arguments
 * plus their config in this record so their generation code reads the same as before.
 *
 * @param config the feature's config, or null for features without one
 */
public record RatsFeatureContext<C>(WorldGenLevel level, ChunkGenerator chunkGenerator, RandomSource random, BlockPos origin, C config) {
}
