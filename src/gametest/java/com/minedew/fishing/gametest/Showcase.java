package com.minedew.fishing.gametest;

import com.minedew.fishing.encounter.FishingEncounterManager;
import com.minedew.fishing.fish.FishSize;
import com.minedew.fishing.fish.FishSpecies;
import com.minedew.fishing.fish.HookedCatch;
import justfatlard.pandorical.api.PandoricalApi;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerConnection;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * The pictures for the readme and the mod page: the fight on the bar, which is the whole mod and
 * cannot be described in a sentence.
 *
 * <p>The encounter is started through {@link FishingEncounterManager}, the call vanilla's own bite
 * makes, so what is photographed is the fight a player is in. Run it under xvfb-run; the frames
 * land in build/run/clientGameTest/screenshots.
 */
public final class Showcase implements FabricClientGameTest {

	private static final int WIDTH = 1920;
	private static final int HEIGHT = 1080;

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			TestServerContext server = world.getServer();
			TestServerConnection connection = world.getConnection();
			connection.waitForChunksRender();
			server.waitFor(s -> PandoricalApi.isAvailable(connection.getServerPlayer()));

			server.runCommand("gamerule doDaylightCycle false");
			server.runCommand("gamerule doWeatherCycle false");
			server.runCommand("weather clear");
			server.runCommand("time set 1000");
			server.runCommand("gamemode survival @a");
			server.runCommand("recipe give @a *");

			BlockPos origin = server.computeOnServer(s -> connection.getServerPlayer().blockPosition());
			int x = origin.getX();
			int y = origin.getY();
			int z = origin.getZ();

			// A pond in front of the bank the camera stands on, filled in strips: one fill is
			// capped at 32768 blocks, and the water has to stop short of the camera or the picture
			// is taken from the bottom of it.
			for (int strip = 0; strip < 4; strip++) {
				int near = z - 6 - strip * 6;
				server.runCommand("fill %d %d %d %d %d %d minecraft:water"
					.formatted(x - 14, y - 3, near - 5, x + 14, y - 1, near));
			}
			server.runCommand("fill %d %d %d %d %d %d minecraft:sand"
				.formatted(x - 14, y - 1, z - 5, x + 14, y - 1, z + 4));
			server.runCommand("item replace entity @a weapon.mainhand with minecraft:fishing_rod");
			look(server, x + 0.5, y, z + 2.5, x + 0.5, y - 1.0, z - 16.0);
			context.waitTicks(220);

			// And the fight itself: a trophy salmon on the line, a few seconds in, which is where
			// the bar is worth a picture.
			server.runOnServer(s -> {
				ServerLevel level = s.overworld();
				ServerPlayer player = connection.getServerPlayer();
				FishingHook hook = new FishingHook(player, level, 0, 0);
				hook.setPos(x + 0.5, y - 0.9, z - 12.5);
				level.addFreshEntity(hook);
				HookedCatch hooked = new HookedCatch(FishSpecies.SALMON, FishSize.TROPHY,
					List.of(new ItemStack(Items.SALMON)));
				FishingEncounterManager.startEncounter(player, hook, hooked, 40);
			});
			// Set the hook, the way a player does: the click that answers the bite. Left to itself
			// the bite lapses in forty ticks and the picture is of the line going slack.
			context.waitTicks(4);
			server.runOnServer(s -> FishingEncounterManager.handleReelClick(connection.getServerPlayer()));
			// Far enough into the fight that the marker has moved and the meter has something in it.
			context.waitTicks(50);
			shoot(context, "the-fight");
		}
	}

	/** Stand the camera at one place and point it at another; the y is the feet. */
	private void look(TestServerContext server, double x, double y, double z,
			double atX, double atY, double atZ) {
		double dx = atX - x;
		double dy = atY - (y + 1.62);
		double dz = atZ - z;
		double yaw = -Math.toDegrees(Math.atan2(dx, dz));
		double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		server.runCommand("tp @a %.2f %.2f %.2f %.1f %.1f".formatted(x, y, z, yaw, pitch));
	}

	private void shoot(ClientGameTestContext context, String name) {
		context.takeScreenshot(TestScreenshotOptions.of(name)
			.withSize(WIDTH, HEIGHT)
			.disableCounterPrefix());
	}
}
