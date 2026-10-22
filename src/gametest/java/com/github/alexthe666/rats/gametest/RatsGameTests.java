package com.github.alexthe666.rats.gametest;

import com.github.alexthe666.rats.RatsMod;
import com.github.alexthe666.rats.registry.RatsEntityRegistry;
import com.github.alexthe666.rats.registry.RatsItemRegistry;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.EntityProcessor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.function.Consumer;

/**
 * zp5: dev-only GameTests (the gametest source set is not in the jar). Run with {@code ./gradlew runGameTestServer}.
 */
@EventBusSubscriber(modid = RatsMod.MODID)
public final class RatsGameTests {

	private static final Identifier SAVE_RELOAD = Identifier.fromNamespaceAndPath(RatsMod.MODID, "tamed_rat_save_reload");
	/** A 5 x 5 x 5 stone-floored arena (from ZP5's zp5-compat). */
	private static final Identifier ARENA = Identifier.fromNamespaceAndPath(RatsMod.MODID, "arena");

	private RatsGameTests() {
	}

	@SubscribeEvent
	public static void registerFunctions(RegisterEvent event) {
		event.register(Registries.TEST_FUNCTION, SAVE_RELOAD, () -> (Consumer<GameTestHelper>) RatsGameTests::saveReload);
	}

	@SubscribeEvent
	public static void registerTests(RegisterGameTestsEvent event) {
		Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(Identifier.fromNamespaceAndPath(RatsMod.MODID, "default"));
		event.registerTest(SAVE_RELOAD, new FunctionGameTestInstance(ResourceKey.create(Registries.TEST_FUNCTION, SAVE_RELOAD), new TestData<>(environment, ARENA, 100, 0, true)));
	}

	/**
	 * A tamed rat saved and loaded the way a chunk does keeps its Cheese Staff faces and its health, including health
	 * above the base max that only its health upgrade allows. A player changing an upgrade still heals it to full.
	 */
	private static void saveReload(GameTestHelper helper) {
		TamedRat rat = helper.spawn(RatsEntityRegistry.TAMED_RAT.get(), new BlockPos(2, 2, 2));
		rat.setItemSlot(EquipmentSlot.LEGS, new ItemStack(RatsItemRegistry.RAT_UPGRADE_HEALTH.get()));
		double baseMax = rat.getAttributeBaseValue(Attributes.MAX_HEALTH);
		helper.assertTrue(rat.getMaxHealth() > baseMax + 10.0F, "the health upgrade raises max health, got " + rat.getMaxHealth());
		float health = (float) baseMax + 5.0F;
		rat.setHealth(health);
		rat.depositFacing = Direction.NORTH;
		rat.pickupFacing = Direction.EAST;

		TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
		helper.assertTrue(rat.save(output), "the rat saves");
		rat.discard();
		Entity loaded = EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), output.buildResult()),
				helper.getLevel(), EntitySpawnReason.LOAD, EntityProcessor.NOP);
		helper.assertTrue(loaded instanceof TamedRat, "the rat loads, got " + loaded);
		TamedRat back = (TamedRat) loaded;

		helper.assertValueEqual(back.depositFacing, Direction.NORTH, "deposit facing");
		helper.assertValueEqual(back.pickupFacing, Direction.EAST, "pickup facing");
		helper.assertValueEqual(back.getHealth(), health, "health after reload (max " + back.getMaxHealth() + ")");

		back.setHealth(1.0F);
		back.setItemSlot(EquipmentSlot.FEET, new ItemStack(RatsItemRegistry.RAT_UPGRADE_SPEED.get()));
		helper.assertValueEqual(back.getHealth(), back.getMaxHealth(), "health after a player adds an upgrade");
		back.discard();
		helper.succeed();
	}
}
