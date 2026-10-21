package com.github.alexthe666.rats.server.items;

import com.github.alexthe666.rats.registry.RatsToolMaterialRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.DataMapHooks;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class RatlantisToolItem {

	// 26.1: PickaxeItem is gone — pickaxes are plain Items configured via Properties.pickaxe(material, damage, speed).
	public static class Pickaxe extends Item {

		public Pickaxe(Item.Properties properties) {
			super(properties.pickaxe(RatsToolMaterialRegistry.RATLANTIS, 1.0F, -2.8F));
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
			tooltip.accept(Component.translatable(this.getDescriptionId() + ".desc0").withStyle(ChatFormatting.YELLOW));
			tooltip.accept(Component.translatable(this.getDescriptionId() + ".desc1").withStyle(ChatFormatting.GRAY));
		}

		@Override
		public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity living) {
			if (state.is(BlockTags.BASE_STONE_OVERWORLD)) {
				if (!level.isClientSide() && state.getDestroySpeed(level, pos) != 0.0F) {
					stack.hurtAndBreak(0, living, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
				}
				return true;
			} else {
				return super.mineBlock(stack, level, state, pos, living);
			}
		}
	}

	// 26.3: AxeItem, ShovelItem and HoeItem are gone — tools are plain Items configured via Properties.axe/shovel/hoe,
	// which also attach the vanilla block transformer (stripping, path flattening, tilling) as a component.
	public static class Axe extends Item {
		public Axe(Item.Properties properties) {
			super(properties.axe(RatsToolMaterialRegistry.RATLANTIS, 5.0F, -3.0F));
		}

		@Override
		public float getDestroySpeed(ItemStack stack, BlockState state) {
			if (state.is(BlockTags.LEAVES)) {
				// 1.21: AxeItem.speed field removed. Use the material's mining speed directly so leaves get
				// the same fast-mine bonus they had in 1.20.1, regardless of whether vanilla considers
				// the axe preferred for leaves.
				return RatsToolMaterialRegistry.RATLANTIS.speed() * 1.5F;
			}
			return super.getDestroySpeed(stack, state);
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
			tooltip.accept(Component.translatable(this.getDescriptionId() + ".desc0").withStyle(ChatFormatting.YELLOW));
			tooltip.accept(Component.translatable(this.getDescriptionId() + ".desc1").withStyle(ChatFormatting.GRAY));
		}

		@Override
		public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity living) {
			if (state.is(BlockTags.LEAVES)) {
				if (!level.isClientSide() && state.getDestroySpeed(level, pos) != 0.0F) {
					stack.hurtAndBreak(0, living, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
				}
				return true;
			} else {
				return super.mineBlock(stack, level, state, pos, living);
			}
		}
	}

	public static class Shovel extends Item {
		public Shovel(Item.Properties properties) {
			super(properties.shovel(RatsToolMaterialRegistry.RATLANTIS, 1.5F, -3.0F));
		}

		@Override
		public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity living) {
			if (state.is(BlockTags.SAND)) {
				if (!level.isClientSide() && state.getDestroySpeed(level, pos) != 0.0F) {
					stack.hurtAndBreak(0, living, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
				}
				return true;
			} else {
				return super.mineBlock(stack, level, state, pos, living);
			}
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
			tooltip.accept(Component.translatable(this.getDescriptionId() + ".desc0").withStyle(ChatFormatting.YELLOW));
			tooltip.accept(Component.translatable(this.getDescriptionId() + ".desc1").withStyle(ChatFormatting.GRAY));
		}
	}

	public static class Hoe extends Item {
		public Hoe(Item.Properties properties) {
			super(properties.hoe(RatsToolMaterialRegistry.RATLANTIS, -3.0F, 0.0F));
		}

		@Override
		public InteractionResult useOn(UseOnContext context) {
			// 26.3: HOE_TILL/getToolModifiedState and HoeItem.changeIntoState are gone; tilling is the hoe's
			// minecraft:block_transformer component. Evaluate its target state for each block of the 3x3 area and
			// apply it like HoeItem.changeIntoState did (one durability point for the whole use, as before).
			Holder<BlockTransformer> transformer = context.getItemInHand().get(DataComponents.BLOCK_TRANSFORMER);
			if (transformer == null) {
				return InteractionResult.PASS;
			}
			boolean tilledAny = false;
			Level level = context.getLevel();
			for (int x = -1; x <= 1; x++) {
				for (int z = -1; z <= 1; z++) {
					BlockPos pos = context.getClickedPos().offset(x, 0, z);
					BlockState toolModifiedState = getTransformedState(transformer, context.getClickedFace(), level, pos);
					if (toolModifiedState != null) {
						Player player = context.getPlayer();
						level.playSound(player, pos, SoundEvents.HOE_TILL.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
						if (!level.isClientSide()) {
							level.setBlock(pos, toolModifiedState, 11);
							level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, toolModifiedState));
							tilledAny = true;
						}
					}
				}
			}

			if (tilledAny && context.getPlayer() != null) {
				context.getItemInHand().hurtAndBreak(1, context.getPlayer(), context.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? net.minecraft.world.entity.EquipmentSlot.MAINHAND : net.minecraft.world.entity.EquipmentSlot.OFFHAND);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		}


		@Nullable
		private static BlockState getTransformedState(Holder<BlockTransformer> transformer, Direction face, Level level, BlockPos pos) {
			for (BlockTransformer.BlockTransformData data : DataMapHooks.getAllTransformers(transformer)) {
				if (!data.disallowedFaces().contains(face)) {
					BlockState state = data.blockStateProvider().value().getOptionalState(level, level.getRandom(), pos);
					if (state != null) {
						return data.updateFromNeighbors() ? Block.updateFromNeighbourShapes(state, level, pos) : state;
					}
				}
			}
			return null;
		}

		@Override
		public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
			super.appendHoverText(stack, context, display, tooltip, flag);
			tooltip.accept(Component.translatable(this.getDescriptionId() + ".desc0").withStyle(ChatFormatting.YELLOW));
			tooltip.accept(Component.translatable(this.getDescriptionId() + ".desc1").withStyle(ChatFormatting.GRAY));
		}
	}
}
