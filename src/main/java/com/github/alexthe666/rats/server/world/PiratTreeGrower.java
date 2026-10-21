package com.github.alexthe666.rats.server.world;

import com.github.alexthe666.rats.registry.worldgen.RatlantisConfiguredFeatureRegistry;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.level.block.grower.TreeGrower;

// 1.21: AbstractMegaTreeGrower / AbstractTreeGrower were collapsed into the final TreeGrower record.
// Expose a singleton instance that callers (SaplingBlock ctor) can use in place of the previous subclass instance.
public final class PiratTreeGrower {
	// 26.3: TreeGrower takes weighted lists of tree features (regular, mega, with flowers) plus the shortest tree.
	// 26.1's secondary variants were all empty, so the 0.5 secondary chance never mattered.
	public static final TreeGrower INSTANCE = new TreeGrower(
			"rats:pirat",
			WeightedList.of(RatlantisConfiguredFeatureRegistry.GHOST_PIRAT_TREE),
			WeightedList.of(RatlantisConfiguredFeatureRegistry.LARGE_GHOST_PIRAT_TREE),
			WeightedList.of(),
			RatlantisConfiguredFeatureRegistry.GHOST_PIRAT_TREE
	);

	private PiratTreeGrower() {
	}
}
