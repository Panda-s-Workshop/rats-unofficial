package com.github.alexthe666.rats.server.inventory.container;

import com.github.alexthe666.rats.server.block.entity.RatCraftingTableBlockEntity;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;

// 26.3: ItemStackHandler is gone; the table's handlers are transfer-API RatsItemStackHandlers.
// onContentsChanged(int, ItemStack) now also runs after committed transactions (hoppers, pipes, rats).
public class TableItemHandlers {
	public static class BufferHandler extends RatsItemStackHandler implements StackedContentsCompatible {

		private final RatCraftingTableBlockEntity table;

		public BufferHandler(RatCraftingTableBlockEntity table) {
			super(9);
			this.table = table;
		}

		@Override
		protected void onContentsChanged(int slot, ItemStack previousContents) {
			this.table.updateHelper();
			this.table.updateRecipe();
			this.table.setChanged();
		}

		@Override
		public void fillStackedContents(StackedItemContents contents) {
			contents.clear();
			for (ItemStack itemstack : this.stacks) {
				contents.accountSimpleStack(itemstack);
			}
		}
	}

	public static class MatrixHandler extends RatsItemStackHandler {

		private final RatCraftingTableBlockEntity table;

		public MatrixHandler(RatCraftingTableBlockEntity table) {
			super(9);
			this.table = table;
		}

		@Override
		protected void onContentsChanged(int slot, ItemStack previousContents) {
			this.table.updateRecipe();
			this.table.setChanged();
		}
	}

	public static class ResultHandler extends RatsItemStackHandler {

		private final RatCraftingTableBlockEntity table;

		public ResultHandler(RatCraftingTableBlockEntity table) {
			super(1);
			this.table = table;
		}

		@Override
		public boolean isValid(int slot, ItemResource resource) {
			return false;
		}

		// Previously done in extractItem: taking from the result slot re-checks the recipe.
		@Override
		protected void onContentsChanged(int slot, ItemStack previousContents) {
			if (this.getAmountAsInt(slot) < previousContents.getCount()) {
				this.table.updateRecipe();
				this.table.setChanged();
			}
		}
	}
}
