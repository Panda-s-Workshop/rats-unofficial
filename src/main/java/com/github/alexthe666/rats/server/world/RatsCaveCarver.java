package com.github.alexthe666.rats.server.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.CarverOutput;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.carver.CaveWorldCarver;
import net.minecraft.world.level.levelgen.carver.WorldCarver;

// 26.3: carvers are records implementing WorldCarver (they are their own configured instance) and CaveWorldCarver is a
// final record, so this delegates to it. The 26.1 class only cleared the carver's `liquids` set; carvers no longer
// read fluids at all (the aquifer decides what fills a carved block), so the cave behaviour is unchanged.
// The shipped Ratlantis carver JSON uses minecraft:cave; this type stays registered under its old id.
public record RatsCaveCarver(CaveWorldCarver cave) implements WorldCarver {
	public static final MapCodec<RatsCaveCarver> CODEC = CaveWorldCarver.MAP_CODEC.xmap(RatsCaveCarver::new, RatsCaveCarver::cave);

	@Override
	public int getRange() {
		return this.cave.getRange();
	}

	@Override
	public boolean carve(WorldGenerationContext context, RandomSource random, ChunkPos chunkPos, ChunkPos sourceChunkPos, CarverOutput output) {
		return this.cave.carve(context, random, chunkPos, sourceChunkPos, output);
	}

	@Override
	public boolean isStartChunk(RandomSource random) {
		return this.cave.isStartChunk(random);
	}

	@Override
	public MapCodec<RatsCaveCarver> codec() {
		return CODEC;
	}
}
