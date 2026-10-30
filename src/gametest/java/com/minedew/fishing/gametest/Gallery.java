package com.minedew.fishing.gametest;

import com.minedew.fishing.advancement.FishLandedCriterion;
import com.minedew.fishing.encounter.FishingEncounter;
import com.minedew.fishing.encounter.FishingEncounterManager;
import com.minedew.fishing.fish.Fish;
import com.minedew.fishing.fish.FishSize;
import com.minedew.fishing.fish.FishSpecies;
import com.minedew.fishing.fish.HookedCatch;
import com.minedew.fishing.fish.ReefFish;
import com.minedew.fishing.fish.Waters;
import com.minedew.fishing.fish.WildFish;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.triggers.Criterion;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.fish.Cod;
import net.minecraft.world.entity.animal.fish.Pufferfish;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every fish, dressed, in rows on a sand bank; and one held up on the line after it is landed. And
 * on the way, that the angler's log asks for every one of them, and that a fish let loose in the
 * world becomes the water's own.
 *
 * <p>The skins are pixels on somebody else's UV layout and the hold is a mob turned side-on to a
 * camera, and neither can be judged anywhere but in a picture. Run it under xvfb-run, as the
 * showcase is; the frames land in build/run/clientGameTest/screenshots.
 */
public final class Gallery implements FabricClientGameTest {

	private static final int WIDTH = 1920;
	private static final int HEIGHT = 1080;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isAvailable(connection.getServerPlayer()));

			server.runOnServer(Gallery::everyFishIsLogged);

			server.runCommand("gamerule advance_time false");
			server.runCommand("gamerule advance_weather false");
			server.runCommand("weather clear");
			server.runCommand("time set 6000");
			server.runCommand("gamemode spectator @a");

			BlockPos origin = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			int x = origin.getX();
			int y = origin.getY() + 6;
			int z = origin.getZ();

			server.runCommand("fill %d %d %d %d %d %d minecraft:air".formatted(x - 14, y, z - 14, x + 14, y + 8, z + 6));
			server.runCommand("fill %d %d %d %d %d %d minecraft:sand".formatted(x - 14, y - 1, z - 14, x + 14, y - 1, z + 6));

			List<List<Fish>> rows = List.of(
				Arrays.stream(Fish.values()).filter(f -> f.getRolledFrom() == FishSpecies.COD).toList(),
				Arrays.stream(Fish.values()).filter(f -> f.getRolledFrom() == FishSpecies.SALMON).toList(),
				Arrays.stream(Fish.values()).filter(f -> f.getRolledFrom() == FishSpecies.PUFFERFISH
					|| f.getRolledFrom() == FishSpecies.TROPICAL_FISH).toList());
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				for (int row = 0; row < rows.size(); row++) {
					List<Fish> fish = rows.get(row);
					for (int i = 0; i < fish.size(); i++) {
						double fx = x + 0.5 + (i - (fish.size() - 1) / 2.0) * 1.3;
						show(level, fish.get(i), fx, y + 0.4, z - 2.5 - row * 2.2);
					}
				}
			});
			look(server, x + 0.5, y + 0.6, z + 3.0, x + 0.5, y + 0.4, z - 4.0);
			context.waitTicks(60);
			shoot(context, "gallery");

			showOff(context, server, connection, x, origin.getY(), z);
		}
	}

	/**
	 * The log's two challenges are written out by hand, and a fish added to {@link Fish} without
	 * them is a fish nobody can finish the log for. Checked against the live server, where the
	 * names come from, including the reef's, which are vanilla's own words.
	 */
	private static void everyFishIsLogged(MinecraftServer server) {
		Set<String> wanted = new HashSet<>();
		criteria(server, "angler/every_fish").forEach(c -> wanted.addAll(fishOf(c)));
		for (Fish fish : Fish.values()) {
			if (!wanted.contains(fish.catchId())) throw new AssertionError(fish + " is missing from angler/every_fish");
		}
		Set<String> reef = new HashSet<>();
		criteria(server, "angler/reef_survey").forEach(c -> reef.addAll(fishOf(c)));
		for (TropicalFish.Variant variant : TropicalFish.COMMON_VARIANTS) {
			String id = new ReefFish(variant).catchId();
			if (!reef.contains(id)) throw new AssertionError(id + " is missing from angler/reef_survey");
		}
	}

	private static Collection<Criterion<?>> criteria(MinecraftServer server, String path) {
		AdvancementHolder holder = server.getAdvancements().get(Identifier.fromNamespaceAndPath("minedew-fishing", path));
		if (holder == null) throw new AssertionError("no advancement minedew-fishing:" + path);
		return holder.value().criteria().values();
	}

	private static List<String> fishOf(Criterion<?> criterion) {
		return criterion.triggerInstance() instanceof FishLandedCriterion.Conditions conditions
			? conditions.fish().orElse(List.of())
			: List.of();
	}

	/** One fish, held still where it is put and turned side-on to the camera. */
	private static void show(ServerLevel level, Fish fish, double x, double y, double z) {
		if (!(fish.getLook().body().create(level, EntitySpawnReason.COMMAND) instanceof Mob mob)) return;
		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.setPermanentlyInvulnerable(true);
		mob.setPersistenceRequired();
		mob.setPos(x, y, z);
		mob.setYRot(90F);
		mob.setYBodyRot(90F);
		mob.setYHeadRot(90F);
		level.addFreshEntity(mob);
		fish.getLook().dress(mob, true);
		// Stood up as it is when it is held on show, so it is the flank that is photographed
		PandoricalApi.animations().play(mob, mob instanceof Pufferfish ? "minedew-fishing:dangle_puffer" : "minedew-fishing:dangle", true);
	}

	/** A fish landed the way a player lands one, photographed while it is held up on show. */
	private void showOff(ClientGameTestContext context, TestServerContext server, TestServerConnection connection,
			int x, int y, int z) {
		server.runCommand("gamemode survival @a");
		for (int strip = 0; strip < 3; strip++) {
			int near = z - 6 - strip * 6;
			server.runCommand("fill %d %d %d %d %d %d minecraft:water"
				.formatted(x - 10, y - 3, near - 5, x + 10, y - 1, near));
		}
		server.runCommand("fill %d %d %d %d %d %d minecraft:sand"
			.formatted(x - 10, y - 1, z - 5, x + 10, y - 1, z + 4));
		server.runCommand("item replace entity @a weapon.mainhand with minecraft:fishing_rod");
		look(server, x + 0.5, y, z + 2.5, x + 0.5, y + 0.6, z - 8.0);
		context.waitTicks(40);

		server.runOnServer(s -> wildFishTakeTheirWater(s.overworld(), x, y, z));

		server.runOnServer(s -> {
			ServerLevel level = s.overworld();
			ServerPlayer player = connection.getServerPlayer();
			FishingHook hook = new FishingHook(player, level, 0, 0);
			hook.setPos(x + 0.5, y - 0.9, z - 10.5);
			level.addFreshEntity(hook);
			HookedCatch hooked = new HookedCatch(Fish.RAINBOW_TROUT, FishSize.LARGE, List.of(new ItemStack(Items.SALMON)));
			FishingEncounterManager.startEncounter(player, hook, hooked, 40);
		});
		context.waitTicks(4);
		server.runOnServer(s -> FishingEncounterManager.handleReelClick(connection.getServerPlayer()));
		context.waitTicks(10);
		// Won outright: the fight is the showcase's picture, the hold is this one's
		server.runOnServer(s -> land(connection.getServerPlayer()));
		context.waitTicks(30);
		shoot(context, "the-show");
	}

	/**
	 * A plain cod let loose in the pond becomes one of the pond's own fish the moment it is in the
	 * world, wears that fish's look, and is still that fish after a trip through a bucket.
	 */
	private static void wildFishTakeTheirWater(ServerLevel level, int x, int y, int z) {
		Cod cod = EntityTypes.COD.create(level, EntitySpawnReason.COMMAND);
		cod.setPos(x + 0.5, y - 2, z - 9.5);
		level.addFreshEntity(cod);

		Fish fish = Fish.byId(cod.getAttached(WildFish.KIND));
		if (fish == null || fish.getRolledFrom() != FishSpecies.COD || fish == Fish.COD) {
			throw new AssertionError("a cod in a pond should be a pond fish, was " + fish);
		}
		if (fish.getLook().skin() != null && PandoricalApi.entityModels().get(cod) == null) {
			throw new AssertionError(fish + " in the pond is not wearing its skin");
		}

		ItemStack bucket = new ItemStack(Items.COD_BUCKET);
		cod.saveToBucketTag(bucket);
		Cod poured = EntityTypes.COD.create(level, EntitySpawnReason.BUCKET);
		poured.loadFromBucketTag(bucket.get(DataComponents.BUCKET_ENTITY_DATA).copyTag());
		if (!fish.id().equals(poured.getAttached(WildFish.KIND))) {
			throw new AssertionError(fish + " came out of the bucket as " + poured.getAttached(WildFish.KIND));
		}
		cod.discard();

		// What the fisherman's freshwater lesson accepts is read off the fish's own conditions
		Set<Fish> fresh = EnumSet.noneOf(Fish.class);
		for (Fish each : Fish.values()) {
			if (each.foundIn(Waters.Kind.RIVER) || each.foundIn(Waters.Kind.LAKE) || each.foundIn(Waters.Kind.SWAMP)) fresh.add(each);
		}
		if (!fresh.containsAll(EnumSet.of(Fish.CARP, Fish.PERCH, Fish.PIKE, Fish.SUNFISH, Fish.EEL, Fish.SALMON))
				|| fresh.contains(Fish.COD) || fresh.contains(Fish.HERRING) || fresh.contains(Fish.BLIND_CAVE_FISH)) {
			throw new AssertionError("freshwater fish read off their conditions wrongly: " + fresh);
		}
	}

	@SuppressWarnings("unchecked")
	private static void land(ServerPlayer player) {
		try {
			var active = FishingEncounterManager.class.getDeclaredField("ACTIVE");
			active.setAccessible(true);
			FishingEncounter encounter = ((Map<UUID, FishingEncounter>) active.get(null)).get(player.getUUID());
			Method succeed = FishingEncounterManager.class.getDeclaredMethod("succeed", FishingEncounter.class);
			succeed.setAccessible(true);
			succeed.invoke(null, encounter);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("could not land the fish", e);
		}
	}

	/** Stand the camera at one place and point it at another; the y is the feet. */
	private static void look(TestServerContext server, double x, double y, double z,
			double atX, double atY, double atZ) {
		double dx = atX - x;
		double dy = atY - (y + 1.62);
		double dz = atZ - z;
		double yaw = -Math.toDegrees(Math.atan2(dx, dz));
		double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		server.runCommand("tp @a %.2f %.2f %.2f %.1f %.1f".formatted(x, y, z, yaw, pitch));
	}

	private static void shoot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name)
			.withSize(WIDTH, HEIGHT)
			.disableCounterPrefix());
	}
}
