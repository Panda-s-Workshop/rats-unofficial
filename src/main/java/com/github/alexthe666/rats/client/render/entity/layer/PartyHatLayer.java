package com.github.alexthe666.rats.client.render.entity.layer;

import com.github.alexthe666.rats.client.render.RatsRenderType;
import com.github.alexthe666.rats.RatsMod;
import com.github.alexthe666.rats.client.model.RatsModelLayers;
import com.github.alexthe666.rats.client.model.entity.PinkieModel;
import com.github.alexthe666.rats.client.model.entity.StaticRatModel;
import com.github.alexthe666.rats.client.model.hats.PartyHatModel;
import com.github.alexthe666.rats.client.render.RatsClientKeys;
import com.github.alexthe666.rats.client.render.RatsEntityModelBridge;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import com.github.alexthe666.rats.server.items.PartyHatItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public class PartyHatLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {
	private final HumanoidModel<?> outerModel;
	private final PartyHatModel partyHat = new PartyHatModel(Minecraft.getInstance().getEntityModels().bakeLayer(RatsModelLayers.PARTY_HAT));
	// 26.1: models pose from a render state at draw time; feed the hat a neutral humanoid state so it stays
	// at the model origin and the PoseStack transform below does the positioning (same trick as RatHelmetLayer).
	private final HumanoidRenderState hatState = new HumanoidRenderState();

	public PartyHatLayer(RenderLayerParent<S, M> parent, HumanoidModel<?> outerModel) {
		super(parent);
		this.outerModel = outerModel;
		// zp5: the cone is authored 8px above its pivot, which floated it a rat-height over rats and most of a block
		// over humanoids. The model resets to its initial pose on every draw, so flatten that pose on this layer's own
		// copy; the brim then sits at the pose stack origin and the transforms below seat it on the head.
		this.partyHat.head.getChild("hat").setInitialPose(PartPose.ZERO);
	}

	@Override
	public void submit(PoseStack stack, SubmitNodeCollector collector, int light, S state, float netHeadYaw, float headPitch) {
		LivingEntity entity = RatsClientKeys.getLiving(state);
		if (entity == null) {
			return;
		}
		ItemStack itemstack = entity.getItemBySlot(EquipmentSlot.HEAD);
		if (entity instanceof TamedRat rat && rat.getRespawnCountdown() > 0) return;
		if (itemstack.getItem() instanceof PartyHatItem hat) {
			stack.pushPose();
			// 26.1: copyPropertiesTo is gone with the render-state split; the outer model was never drawn here, so it just stays hidden.
			this.outerModel.allParts().forEach(part -> part.visible = false); // 26.1: setAllVisible is gone
			if (this.getParentModel() instanceof HumanoidModel<?> human) {
				human.head.translateAndRotate(stack);
				stack.translate(0.0F, -0.5F, 0.0F); // zp5: the top of the 8px head
			} else if (this.getParentModel() instanceof RatsEntityModelBridge<?> bridge) {
				// 26.1: rat renderers wrap their Citadel model in the bridge (and swap to PinkieModel for babies); unwrap to reach the parts.
				boolean positioned = true;
				if (entity.isBaby() && bridge.citadel() instanceof PinkieModel<?> pinkie) {
					pinkie.body.translateRotate(stack);
					stack.translate(0.0D, -0.01D, -0.05D);
					stack.scale(0.65F, 0.65F, 0.65F);
					stack.translate(0.0F, -0.08F, 0.0F); // zp5: the top of the 2px body
				} else if (bridge.citadel() instanceof StaticRatModel<?> rat) {
					rat.body1.translateRotate(stack);
					rat.body2.translateRotate(stack);
					rat.neck.translateRotate(stack);
					rat.head.translateRotate(stack);
					stack.translate(0.0F, -1.5F / 16.0F, 0.0F); // zp5: the top of the 3px head
				} else {
					positioned = false;
				}
				if (positioned) {
					stack.scale(0.75F, 0.75F, 0.75F);
				}
			}
			boolean flag1 = itemstack.hasFoil();
			int i = hat.getColor(itemstack);
			this.submitHat(stack, collector, light, state, flag1, i, Identifier.fromNamespaceAndPath(RatsMod.MODID, "textures/model/hat/party_hat_layer_1.png"));
			i = this.invertColor(hat.getColor(itemstack));
			this.submitHat(stack, collector, light, state, flag1, i, Identifier.fromNamespaceAndPath(RatsMod.MODID, "textures/model/hat/party_hat_layer_2.png"));
			stack.popPose();
		}
	}

	private int invertColor(int color) {
		int a = (color >> 24) & 0xff;
		int r = (color >> 16) & 0xff;
		int g = (color >> 8) & 0xff;
		int b = color & 0xff;

		r = 255 - r;
		g = 255 - g;
		b = 255 - b;
		return (a & 0xff) << 24 | (r & 0xff) << 16 | (g & 0xff) << 8 | (b & 0xff);
	}

	private void submitHat(PoseStack stack, SubmitNodeCollector collector, int light, S state, boolean glint, int color, Identifier texture) {
		int argb = ARGB.color(255, color);
		collector.submitModel(this.partyHat, this.hatState, stack, RenderTypes.armorCutoutNoCull(texture), light, OverlayTexture.NO_OVERLAY, argb, null, state.outlineColor);
		if (glint) {
			// 26.1: ItemRenderer.getArmorFoilBuffer is gone; foil is an extra armorEntityGlint pass (see vanilla EquipmentLayerRenderer).
			collector.submitModel(this.partyHat, this.hatState, stack, RatsRenderType.armorEntityGlint(), light, OverlayTexture.NO_OVERLAY, argb, null, state.outlineColor);
		}
	}
}
