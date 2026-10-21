package com.github.alexthe666.rats.client.render.block;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.StandingSignRenderer;

/**
 * 26.3: sign boards are baked block models now (see the pirat sign blockstates and models); the block entity
 * renderer only draws the text, so the old {@code submitSign} override (which drew the board with the
 * entityTranslucent render type) has nothing left to hook. The translucent board comes from the block model's
 * render type instead.
 */
public class PiratSignRenderer extends StandingSignRenderer {

	public PiratSignRenderer(BlockEntityRendererProvider.Context context) {
		super(context);
	}
}
