package com.github.alexthe666.rats.server.inventory.container;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandlerUtil;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemStacksResourceHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * 26.3: NeoForge removed {@code ItemStackHandler}/{@code IItemHandlerModifiable}. This is the transfer-API
 * {@link ItemStacksResourceHandler} (so it can be exposed through {@code Capabilities.Item.BLOCK} and serialized
 * with {@code serialize/deserialize}) plus the stack-based helpers the Rats code used on the old handler.
 * <p>
 * Override {@link #onContentsChanged(int, ItemStack)} for change callbacks: it runs after {@link #setStackInSlot}
 * and after every committed transaction that touched a slot (hoppers, pipes, {@link #insertItem}, {@link #extractItem}).
 */
public class RatsItemStackHandler extends ItemStacksResourceHandler {

	public RatsItemStackHandler(int size) {
		super(size);
	}

	public int getSlots() {
		return this.size();
	}

	/**
	 * The stack stored in the slot. Like the old {@code ItemStackHandler#getStackInSlot} this is the live stack:
	 * callers that change it directly must report the change themselves.
	 */
	public ItemStack getStackInSlot(int slot) {
		return this.stacks.get(slot);
	}

	/** Replaces the slot's contents (no validity check, like {@code IItemHandlerModifiable#setStackInSlot}). */
	public void setStackInSlot(int slot, ItemStack stack) {
		if (stack.isEmpty()) {
			this.set(slot, ItemResource.EMPTY, 0);
		} else {
			this.set(slot, ItemResource.of(stack), stack.getCount());
		}
	}

	public int getSlotLimit(int slot) {
		return this.getCapacityAsInt(slot, ItemResource.EMPTY);
	}

	public boolean isItemValid(int slot, ItemStack stack) {
		return !stack.isEmpty() && this.isValid(slot, ItemResource.of(stack));
	}

	/** {@code IItemHandler#insertItem}: inserts into one slot and returns what did not fit. */
	public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		try (Transaction tx = Transaction.openRoot()) {
			int inserted = this.insert(slot, ItemResource.of(stack), stack.getCount(), tx);
			if (!simulate) {
				tx.commit();
			}
			return inserted == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - inserted);
		}
	}

	/** {@code IItemHandler#extractItem}: extracts up to {@code amount} from one slot and returns it. */
	public ItemStack extractItem(int slot, int amount, boolean simulate) {
		ItemResource resource = this.getResource(slot);
		if (resource.isEmpty() || amount <= 0) {
			return ItemStack.EMPTY;
		}
		try (Transaction tx = Transaction.openRoot()) {
			int extracted = this.extract(slot, resource, amount, tx);
			if (!simulate) {
				tx.commit();
			}
			return extracted == 0 ? ItemStack.EMPTY : resource.toStack(extracted);
		}
	}

	/** {@code ItemHandlerHelper#insertItemStacked}: fills matching stacks first, then empty slots; returns the remainder. */
	public ItemStack insertItemStacked(ItemStack stack, boolean simulate) {
		return insertStacked(this, stack, simulate, null);
	}

	/** {@code ItemHandlerHelper#insertItemStacked} for any item resource handler. */
	public static ItemStack insertStacked(net.neoforged.neoforge.transfer.ResourceHandler<ItemResource> handler, ItemStack stack, boolean simulate, @Nullable TransactionContext transaction) {
		if (stack.isEmpty()) {
			return ItemStack.EMPTY;
		}
		try (Transaction tx = Transaction.open(transaction)) {
			int inserted = ResourceHandlerUtil.insertStacking(handler, ItemResource.of(stack), stack.getCount(), tx);
			if (!simulate) {
				tx.commit();
			}
			return inserted == stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - inserted);
		}
	}
}
