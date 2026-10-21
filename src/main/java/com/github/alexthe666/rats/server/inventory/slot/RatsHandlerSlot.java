package com.github.alexthe666.rats.server.inventory.slot;

import com.github.alexthe666.rats.server.inventory.container.RatsItemStackHandler;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * 26.3: replacement for NeoForge's removed {@code SlotItemHandler}, over a {@link RatsItemStackHandler}.
 * NeoForge's own {@code ResourceHandlerSlot} makes {@code set}/{@code setChanged} final, which the crafting table
 * slots override, so this keeps the old SlotItemHandler behaviour (live stack in {@link #getItem()}).
 */
public class RatsHandlerSlot extends Slot {
	private static final Container EMPTY_INVENTORY = new SimpleContainer(0);
	private final RatsItemStackHandler itemHandler;
	private final int index;

	public RatsHandlerSlot(RatsItemStackHandler itemHandler, int index, int xPosition, int yPosition) {
		super(EMPTY_INVENTORY, index, xPosition, yPosition);
		this.itemHandler = itemHandler;
		this.index = index;
	}

	@Override
	public boolean mayPlace(ItemStack stack) {
		return !stack.isEmpty() && this.itemHandler.isItemValid(this.index, stack);
	}

	@Override
	public ItemStack getItem() {
		return this.itemHandler.getStackInSlot(this.index);
	}

	@Override
	public void set(ItemStack stack) {
		this.itemHandler.setStackInSlot(this.index, stack);
		this.setChanged();
	}

	@Override
	public void onQuickCraft(ItemStack oldStackIn, ItemStack newStackIn) {
	}

	@Override
	public int getMaxStackSize() {
		return this.itemHandler.getSlotLimit(this.index);
	}

	@Override
	public int getMaxStackSize(ItemStack stack) {
		return Math.min(this.getMaxStackSize(), stack.getMaxStackSize());
	}

	@Override
	public boolean mayPickup(Player player) {
		return !this.itemHandler.extractItem(this.index, 1, true).isEmpty();
	}

	@Override
	public ItemStack remove(int amount) {
		return this.itemHandler.extractItem(this.index, amount, false);
	}

	public RatsItemStackHandler getItemHandler() {
		return this.itemHandler;
	}

	@Override
	public boolean isSameInventory(Slot other) {
		return other instanceof RatsHandlerSlot slot && slot.itemHandler == this.itemHandler;
	}
}
