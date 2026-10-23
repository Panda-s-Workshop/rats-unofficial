package com.github.alexthe666.rats.gametest;

import com.github.alexthe666.rats.RatsMod;
import com.github.alexthe666.rats.registry.RatsEntityRegistry;
import com.github.alexthe666.rats.registry.RatsItemRegistry;
import com.github.alexthe666.rats.server.entity.rat.TamedRat;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * zp5: dev-only hat check, off unless {@code -Drats.hatshots=true} ({@code ./gradlew runHatShots}). In a copy of the
 * GameTest world it lines up a tamed rat, a baby, a rat in an iron helmet (where the helmet layer seats a hat), a zombie
 * and an armor stand, all in party hats but the helmet rat, screenshots each from the front, the side and
 * three-quarters into {@code run/hatshots/screenshots/hats/} and quits.
 */
@EventBusSubscriber(modid = RatsMod.MODID, value = Dist.CLIENT)
public final class HatShots {
	private static final boolean ENABLED = Boolean.getBoolean("rats.hatshots");
	private static final String TAG = System.getProperty("rats.hatshots.tag", "");
	private static final double Y = 100;

	private record Subject(String name, double x, double z, double look, double distance) {
	}

	private record Shot(String name, int delay, Consumer<ServerPlayer> setUp) {
	}

	private static final List<Subject> SUBJECTS = List.of(
			new Subject("rat", 0.5, -4.5, 0.25, 1.1),
			new Subject("baby_rat", 3.5, -4.5, 0.15, 0.8),
			new Subject("helmet_rat", 6.5, -4.5, 0.25, 1.1),
			new Subject("zombie", -3.5, -4.5, 1.6, 2.2),
			new Subject("armor_stand", -7.5, -4.5, 1.6, 2.2));

	private static int ticks = -1;
	private static int wait;
	private static int index;
	private static List<Shot> shots;
	private static volatile float camYaw = 180, camPitch = 8;

	private HatShots() {
	}

	@SubscribeEvent
	public static void onClientTick(ClientTickEvent.Post event) {
		if (!ENABLED) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.options.pauseOnLostFocus = false;
		if (minecraft.player == null || minecraft.level == null || minecraft.getSingleplayerServer() == null) {
			return;
		}
		ticks++;
		minecraft.player.setYRot(camYaw);
		minecraft.player.setXRot(camPitch);
		minecraft.player.yHeadRot = minecraft.player.yHeadRotO = camYaw;
		if (minecraft.gui.screen() != null) {
			minecraft.gui.setScreen(null);
		}
		if (ticks == 40) {
			setUp(minecraft);
			return;
		}
		if (ticks < 140 || shots == null) {
			return;
		}
		if (wait > 0) {
			wait--;
			return;
		}
		if (index > 0) {
			Shot taken = shots.get(index - 1);
			Screenshot.grab(minecraft.gameDirectory, "hats/" + TAG + taken.name() + ".png", minecraft.gameRenderer.mainRenderTarget(), 1, message -> {
			});
		}
		if (index >= shots.size()) {
			RatsMod.LOGGER.info("[hatshots] done, {} shots", shots.size());
			minecraft.stop();
			return;
		}
		Shot shot = shots.get(index++);
		onServer(minecraft, shot.setUp());
		wait = shot.delay();
	}

	private static void setUp(Minecraft minecraft) {
		shots = new ArrayList<>();
		shots.add(new Shot("overview", 30, player -> {
			for (String command : List.of("fill -10 99 -10 10 99 2 minecraft:smooth_stone", "fill -10 100 -10 10 106 2 minecraft:air", "kill @e[type=!player]")) {
				run(player, command);
			}
			spawnAll(player);
			camYaw = 180;
			camPitch = 20;
			player.teleportTo(player.level(), 0.0, Y + 1.5, 2.0, Set.of(), 180, 20, true);
		}));
		for (Subject subject : SUBJECTS) {
			shots.add(new Shot(subject.name() + "__front", 15, player -> view(player, subject, 0)));
			shots.add(new Shot(subject.name() + "__side", 12, player -> view(player, subject, 90)));
			shots.add(new Shot(subject.name() + "__34", 12, player -> view(player, subject, 35)));
		}
		new File(minecraft.gameDirectory, "screenshots/hats").mkdirs();
		if (!minecraft.gui.hud.isHidden()) {
			minecraft.gui.hud.toggle();
		}
		minecraft.options.setCameraType(CameraType.FIRST_PERSON);
		onServer(minecraft, player -> {
			// the stage's chunks load once the player is there; the first shot builds it
			for (String command : List.of("time set noon", "weather clear", "gamemode spectator @a", "tp @a 0.5 101.5 2.0 180 20")) {
				run(player, command);
			}
		});
	}

	private static void spawnAll(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		for (Subject subject : SUBJECTS) {
			LivingEntity entity = switch (subject.name()) {
				case "zombie" -> EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
				case "armor_stand" -> EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
				default -> RatsEntityRegistry.TAMED_RAT.get().create(level, EntitySpawnReason.COMMAND);
			};
			if (entity instanceof Mob mob) {
				mob.setNoAi(true);
				mob.setPersistenceRequired();
			}
			if (entity instanceof TamedRat rat && subject.name().equals("baby_rat")) {
				rat.setAge(-24000);
			}
			entity.setItemSlot(EquipmentSlot.HEAD, new ItemStack(subject.name().equals("helmet_rat") ? Items.IRON_HELMET : RatsItemRegistry.PARTY_HAT.get()));
			entity.snapTo(subject.x(), Y, subject.z(), 0, 0);
			entity.setYBodyRot(0);
			entity.setYHeadRot(0);
			level.addFreshEntity(entity);
		}
	}

	/** Puts the camera {@code angle} degrees around the subject (0 = in front, 90 = its left side); subjects face south. */
	private static void view(ServerPlayer player, Subject subject, double angle) {
		double a = Math.toRadians(angle);
		double cx = subject.x() + subject.distance() * Math.sin(a), cz = subject.z() + subject.distance() * Math.cos(a);
		double eye = Y + subject.look() + subject.distance() * 0.25;
		float yaw = (float) Math.toDegrees(Math.atan2(Math.sin(a), -Math.cos(a)));
		float pitch = (float) Math.toDegrees(Math.atan2(eye - (Y + subject.look()), subject.distance()));
		camYaw = yaw;
		camPitch = pitch;
		player.teleportTo(player.level(), cx, eye - player.getEyeHeight(), cz, Set.of(), yaw, pitch, true);
	}

	private static void run(ServerPlayer player, String command) {
		var server = ((ServerLevel) player.level()).getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
	}

	private static void onServer(Minecraft minecraft, Consumer<ServerPlayer> action) {
		IntegratedServer server = minecraft.getSingleplayerServer();
		server.execute(() -> action.accept(server.getPlayerList().getPlayers().getFirst()));
	}
}
