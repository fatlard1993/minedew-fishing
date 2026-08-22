package com.minedew.fishing.integration;

import java.util.List;
import justfatlard.village_quests.api.LessonApi;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A fisherman teaching how to read what is on the line.
 *
 * <p>Registered with Village Quests when that mod is present. The minigame's
 * whole skill is recognising a species from how it swims before the name is
 * ever shown, so the lessons ask for one species at a time and hand back that
 * species' signature -- which is a thing a player would otherwise learn by
 * losing a few hundred fish.
 *
 * <p>Every number quoted below is from {@code MinigameTuning} and the fish
 * enums rather than the README, including the two the README rounds off: the
 * free look before a fight is forty ticks, not indefinite, and the level-flight
 * tapping rate is about twice a second.
 *
 * <p>This class must only be touched behind a mod-loaded check. It refers to
 * Village Quests types directly, so loading it without that mod present throws.
 */
public final class VillageQuestsLessons {
	private VillageQuestsLessons() {}

	public static void register() {
		LessonApi.register(new LessonApi.Craft(
			"minedew-fishing:angling",
			"fisherman",
			LessonApi.Policy.standard(),
			lessons(),
			new LessonApi.Openings(
				LessonApi.lines(
					"{former} is gone. They had you part-way through, and half-taught is worse than not started. I'll finish it if you want. ",
					"You were going out with {former}, weren't you. I'd not have said anything while they were here. Now: ",
					"*makes room on the bench* {former}'s student. I know about where they'd got you. "),
				LessonApi.lines(
					"Water's right for the next one, when you've the time.",
					"*doesn't look up* Next one whenever. It'll keep. Fish will too.",
					"There's another when you want it. No hurry -- there never is, with this."),
				LessonApi.lines(
					"{former} is gone. Their rod is still leaning where they left it. Nobody has moved it.",
					"You'll have heard about {former}. *long pause* They were teaching you to read them, weren't they."),
				LessonApi.lines(
					"You watch the bobber and not the bar. That's the tell. People who've been taught watch the bobber.",
					"*nods at your hands* You don't mash. Took me a season to stop.",
					"Somebody taught you properly. {mentor}? Thought so. They start everyone on cod.")),
			new LessonApi.Hooks() {
				@Override
				public void onGraduate(ServerPlayer player, ServerLevel world, LessonApi.Teacher teacher) {
					teacher.give(new ItemStack(Items.FISHING_ROD));
					teacher.says("Take the spare. Mine is older than you are and I am not parting with it.");
					teacher.laterInTheVillage("Someone was out on the end of the jetty before light, not casting. "
						+ "Just watching the water where the current turns.", 0);
				}
			}));
	}

	private static List<LessonApi.Lesson> lessons() {
		return List.of(
			new LessonApi.Lesson(
				"You want to learn this properly. Right. Bring me a cod -- one you pulled in yourself, and I will know, because the ones "
					+ "in the barrel are mine. Cod first. Everyone starts on cod.",
				"catch a cod for {name}",
				"Click while the bobber is under. That's it. That's the only timed part.",
				"*takes it without looking* Good. Now the only bit of this that is on a clock: the bobber dips, you click. That is the same "
					+ "moment you have always clicked -- nothing new was added there and nothing new was meant to be. Click before it goes "
					+ "under and you pull in an empty line. Let it come back up and it is gone.",
				"After that there is no clock at all, only the bar. And cod is the one to learn the feel on: steady, sits about the middle, "
					+ "short little snaps. Once you know cod you know when something is not cod.",
				Items.COD, stack -> stack.is(Items.COD), 6),

			new LessonApi.Lesson(
				"Salmon next. They fight differently and I want you to feel it rather than be told it. -- I will tell you afterwards. "
					+ "Feel it first.",
				"catch a salmon for {name}",
				"One click, one kick up. Gravity does the rest. Twice a second holds it level.",
				"*weighs it in one hand* Now. The bar is not something you hold up, it is something you tap up. Every click is one kick "
					+ "upward and it sinks between them. About twice a second keeps it level -- find that rate and everything else in the "
					+ "fight is a nudge either side of it.",
				"And mashing does nothing. Not 'not much' -- nothing. It will only take one click a tick however many you give it, so past "
					+ "a workable rate the extra ones are thrown away and you have pinned yourself against the top of the track. Salmon "
					+ "punishes that: it lunges hard up the track, then glides while it gets its breath. You want to be sinking when it glides.",
				Items.SALMON, stack -> stack.is(Items.SALMON), 6),

			new LessonApi.Lesson(
				"Pufferfish. Nastiest of the four to read, so we do it while you still expect to lose some.",
				"catch a pufferfish for {name}",
				"You get about two seconds before it starts. Spend them watching.",
				"*sets it down carefully, points at it* Here is the thing nobody uses. When the fight comes up, it is not running yet. The "
					+ "bar hangs, the meter sits still, and the fish swims about showing you exactly what it is -- for about two seconds, "
					+ "and your first click ends it early. Two seconds of watching is worth more than two seconds of clicking.",
				"Which is how you catch this one. A pufferfish holds a depth for a long while, does nothing, then moves -- and it rides a "
					+ "little higher than the rest. If it hangs still and high while you are watching, you have got one, and you can set "
					+ "your rate before you have lost any of the meter.",
				Items.PUFFERFISH, stack -> stack.is(Items.PUFFERFISH), 8),

			new LessonApi.Lesson(
				"Tropical fish now. Warm water, out past the coast, and take a bucket if you want to keep one whole. -- One on the line "
					+ "will do for me.",
				"catch a tropical fish for {name}",
				"The stars are the size. They are never the species.",
				"*almost laughs* That one is a mess to hold on to. Never settles anywhere, keeps changing its mind, and there is a shake "
					+ "on top of the movement. Nothing else does that. -- Which matters, because you are never told what you have hooked. "
					+ "The name comes at the end. The stars come at the start, and the stars are the size, not the species.",
				"So the size is the half you are given and the species is the half you have to read. And the size is not luck alone: rain, "
					+ "deep water and night each push it bigger, and they stack. Fish a deep spot at night in the rain and you are asking "
					+ "for the big ones on purpose. Whether that is clever depends on how good you have got.",
				Items.TROPICAL_FISH, stack -> stack.is(Items.TROPICAL_FISH), 8),

			new LessonApi.Lesson(
				"Last thing, and it is not a fish. Bring me a nautilus shell. You will not find one by fishing hard -- you find one by "
					+ "fishing in open water and being patient about it. Off the shore, nothing over your head. Go on.",
				"catch a nautilus shell for {name} -- open water, no roof",
				"Anything that isn't a fish fights like junk. Dead weight, snag, stop.",
				"*turns it over, pleased* There. And you will have noticed it did not fight like anything you had met. Everything that is "
					+ "not one of the four fights the same way -- dead weight that snags, lurches, and stops. A boot does that. So does a "
					+ "saddle. So does this, and this is worth having.",
				"One more and then I will leave you alone. About one fight in five puts a chest up on the track, at a fixed spot, and it "
					+ "stays put. Covering it costs you the best part of two seconds -- two seconds your catch meter is going down, and it "
					+ "pays nothing at all unless you land the fish as well. Grab for it the moment it appears and you will lose both. Wait "
					+ "until the fish drifts across it and take them together. That is the whole trick, and it is the only part of this I "
					+ "cannot teach you by telling you.",
				Items.NAUTILUS_SHELL, stack -> stack.is(Items.NAUTILUS_SHELL), 12));
	}
}
