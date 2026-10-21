package com.github.alexthe666.rats.registry;

import com.github.alexthe666.rats.RatsMod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;

// 26.1: the capability surface switched from IItemHandler/IEnergyStorage/IFluidHandler to the
// transaction-based transfer API (ResourceHandler<ItemResource/FluidResource> / EnergyHandler).
// These registrations are load-bearing for the mod's OWN AI: RatDepositGoal resolves
// Capabilities.Item/Fluid/Energy.BLOCK on its deposit target, so a quarry rat can't hand its ore
// to the quarry and a milkmaid rat can't drain into the curdler unless the Rats BEs expose them.
@EventBusSubscriber(modid = RatsMod.MODID)
public final class RatsCapabilities {

	private RatsCapabilities() {
	}

	@SubscribeEvent
	public static void register(RegisterCapabilitiesEvent event) {
		// WorldlyContainer-backed BEs expose their sided container semantics through the
		// vanilla-wrapper; no internal migration needed.
		event.registerBlockEntity(
				Capabilities.Item.BLOCK,
				RatsBlockEntityRegistry.RAT_QUARRY.get(),
				WorldlyContainerWrapper::new);
		event.registerBlockEntity(
				Capabilities.Item.BLOCK,
				RatsBlockEntityRegistry.AUTO_CURDLER.get(),
				WorldlyContainerWrapper::new);
		event.registerBlockEntity(
				Capabilities.Item.BLOCK,
				RatsBlockEntityRegistry.UPGRADE_COMBINER.get(),
				WorldlyContainerWrapper::new);

		// The curdler's milk tank (migrated to FluidStacksResourceHandler for the new API).
		event.registerBlockEntity(
				Capabilities.Fluid.BLOCK,
				RatsBlockEntityRegistry.AUTO_CURDLER.get(),
				(be, side) -> be.getTank());

		// The cage wheel's generated RF (migrated to SimpleEnergyHandler).
		event.registerBlockEntity(
				Capabilities.Energy.BLOCK,
				RatsBlockEntityRegistry.RAT_CAGE_WHEEL.get(),
				(be, side) -> be.energyStorage);

		// 26.3: the crafting table's handlers are transfer-API RatsItemStackHandlers now, so it is exposed again
		// like before the 26.1 port: the result from below, the input buffer from every other side.
		event.registerBlockEntity(
				Capabilities.Item.BLOCK,
				RatsBlockEntityRegistry.RAT_CRAFTING_TABLE.get(),
				(be, side) -> be.itemHandler(side));

		// The tamed rat's own inventory (hands and armour slots), as before the 26.1 port.
		event.registerEntity(
				Capabilities.Item.ENTITY,
				RatsEntityRegistry.TAMED_RAT.get(),
				(rat, ctx) -> rat.itemHandler);
	}
}
