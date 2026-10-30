package com.minedew.fishing.integration;

import com.minedew.fishing.fish.Fish;
import com.minedew.fishing.fish.FishFamily;
import com.minedew.fishing.fish.Waters;
import com.minedew.fishing.fish.WildFish;
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
 * whole skill is recognising a fish from how it swims before the name is ever
 * shown, so the first lessons ask for one of the plain fish at a time and hand
 * back its signature -- which is a thing a player would otherwise learn by
 * losing a few hundred fish. The two after them are what the plain fish cannot
 * teach: that the water decides which fish bites, and that kin move alike. Both
 * ask for a fish alive in a bucket, because a bucket remembers which fish it
 * holds and a fillet does not.
 *
 * <p>Village Quests counts a student's progress in lessons done, so new lessons
 * go before the last: anyone part-way through meets them next, and anyone who
 * graduated stays graduated.
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
					"There's another when you want it. No hurry. There never is, with this."),
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
					teacher.says("Take the spare. Mine is older than you are and I am not parting with it. And keep count of what you "
						+ "land. There are more fish in this world than either of us has met.");
					teacher.laterInTheVillage("Someone was out on the end of the jetty before light, not casting. "
						+ "Just watching the water where the current turns.", 0);
				}
			}));
	}

	private static List<LessonApi.Lesson> lessons() {
		return List.of(
			new LessonApi.Lesson(
				"You want to learn this properly. Right. Bring me a cod. Out of the sea, mind: a river hands you its own fish, and "
					+ "you will learn the wrong one. One you pulled in yourself, and I will know, because the ones in the barrel are mine. "
					+ "Cod first. Everyone starts on cod.",
				"catch a cod for {name}",
				"Click while the bobber is under. That's it. That's the only timed part.",
				"*takes it without looking* Good. Now the only bit of this that is on a clock: the bobber dips, you click. That is the same "
					+ "moment you have always clicked. Nothing new was added there and nothing new was meant to be. Click before it goes "
					+ "under and you pull in an empty line. Let it come back up and it is gone.",
				"After that there is no clock at all, only the bar. And cod is the one to learn the feel on: steady, sits about the middle, "
					+ "short little snaps. Once you know cod you know when something is not cod.",
				Items.COD, stack -> stack.is(Items.COD), 6),

			new LessonApi.Lesson(
				"Salmon next. Rivers, or the cold sea. They fight differently and I want you to feel it rather than be told it. I will "
					+ "tell you afterwards. Feel it first.",
				"catch a salmon for {name}",
				"One click, one kick up. Gravity does the rest. Twice a second holds it level.",
				"*weighs it in one hand* Now. The bar is not something you hold up, it is something you tap up. Every click is one kick "
					+ "upward and it sinks between them. About twice a second keeps it level. Find that rate and everything else in the "
					+ "fight is a nudge either side of it.",
				"And mashing does nothing. Not 'not much'. Nothing. It will only take one click a tick however many you give it, so past "
					+ "a workable rate the extra ones are thrown away and you have pinned yourself against the top of the track. Salmon "
					+ "punishes that: it lunges hard up the track, then glides while it gets its breath. You want to be sinking when it glides.",
				Items.SALMON, stack -> stack.is(Items.SALMON), 6),

			new LessonApi.Lesson(
				"Pufferfish. Warm sea, not deep. Nastiest of the plain ones to read, so we do it while you still expect to lose some.",
				"catch a pufferfish for {name}",
				"The first few seconds cannot cost you. Spend them watching.",
				"*sets it down carefully, points at it* Here is the thing nobody uses. When the fight comes up, the meter cannot fall "
					+ "below where it started for the first few seconds, whatever you do. So spend them watching the fish instead of "
					+ "chasing it: it shows you exactly what it is, and four seconds of watching is worth more than four seconds of clicking.",
				"Which is how you catch this one. A pufferfish holds a depth for a long while, does nothing, then moves. And it rides a "
					+ "little higher than the rest. If it hangs still and high while you are watching, you have got one, and you can set "
					+ "your rate before you have lost any of the meter.",
				Items.PUFFERFISH, stack -> stack.is(Items.PUFFERFISH), 8),

			new LessonApi.Lesson(
				"Tropical fish now. Warm water, out past the coast, and take a bucket if you want to keep one whole. One on the line "
					+ "will do for me.",
				"catch a tropical fish for {name}",
				"Nothing up there tells you what you hooked. Read it or guess.",
				"*almost laughs* That one is a mess to hold on to. Never settles anywhere, keeps changing its mind, and there is a shake "
					+ "on top of the movement. Nothing else does that. Which matters, because the bar tells you nothing at all. Not the "
					+ "name, not the size. The name comes at the end, and everything before it you read off how the thing moves and how "
					+ "hard it pulls. With these the name is worth waiting for: the reef has two dozen of them with names of their own.",
				"And do not wait on the weather for a big one. Better than one in five is already big in flat sunshine. Rain, deep water "
					+ "and night each tilt it further, and they stack, so fishing a deep spot at night in the rain is asking for them on "
					+ "purpose. It is a thumb on the scale, not a gate. The big ones turn up whenever they like.",
				Items.TROPICAL_FISH, stack -> stack.is(Items.TROPICAL_FISH), 8),

			new LessonApi.Lesson(
				"Something different. Bring me a fish out of fresh water, alive, in a bucket. Not a cod and not a salmon: one of the "
					+ "river's own, or a pond's. Scoop it, do not hook it.",
				"bring {name} a freshwater fish in a bucket",
				"The rod says what kind. The water says which one. Cast somewhere else.",
				"*tips the bucket and looks in for a long moment* Here is the thing nobody tells you. The rod decides what kind of fish "
					+ "it is: lean, oily, the stinging kind, the reef kind. The water decides which. Lean out of the sea is a cod. Lean out "
					+ "of a cold river is a perch, out of a pond in the sun a bass, out of anywhere under rock that pale blind thing. Same "
					+ "cut on the board, different fish on the line.",
				"So if you want a particular one you do not cast harder, you cast somewhere else. Colder water or warmer. After dark, or "
					+ "at dawn. In the rain. And how deep: pike and bass sit in the shallows, catfish and walleye down in the holes, and "
					+ "the big sea fish want water you could drop a tower into. The ones swimming about out there are the same fish, too. "
					+ "Whatever is in the water there is what bites there.",
				Items.COD_BUCKET, VillageQuestsLessons::freshwaterFish, 8),

			new LessonApi.Lesson(
				"Out to the cold sea for this one. Bring me one of the little silver ones, alive, in a bucket: a herring, or anything "
					+ "of its kind. They go about in crowds. You will see them.",
				"bring {name} a herring, or its kin, in a bucket",
				"Kin move alike. Learn the family and you know the fish half-way.",
				"*holds the bucket up to the light* A herring. And a sardine, an anchovy, a shad: all one family, and on the line they "
					+ "all do the same thing. Nervy. Little turns, a flinch now and then, like one fish out of a crowd that has lost the "
					+ "crowd. Every family has its way. Bass jump. Carp cruise and never quite stop. Pike and catfish sit like stones and "
					+ "then go. An eel never stops flowing at all.",
				"Then each one has a habit of its own on top. A walleye hangs dead in the water now and then. A smallmouth makes a run. "
					+ "Learn the family first, then the habit, and by the time the name comes up you will have known it a while. None of "
					+ "them is harder than a cod, mind. Different is not harder. Big is harder.",
				Items.SALMON_BUCKET, VillageQuestsLessons::herringKin, 8),

			new LessonApi.Lesson(
				"Last thing, and it is not a fish. Bring me a nautilus shell. You will not find one by fishing hard. You find one by "
					+ "fishing in open water and being patient about it. Off the shore, nothing over your head. Go on.",
				"catch a nautilus shell for {name}: open water, no roof",
				"Anything that isn't a fish fights like junk. Dead weight, snag, stop.",
				"*turns it over, pleased* There. And you will have noticed it did not fight like anything you had met. Everything that is "
					+ "not a fish fights the same way: dead weight that snags, lurches, and stops. A boot does that. So does a "
					+ "saddle. So does this, and this is worth having.",
				"One more and then I will leave you alone. About one fight in five puts a chest up on the track, at a fixed spot, and it "
					+ "stays put. Covering it costs you the best part of two seconds, two seconds your catch meter is going down, and it "
					+ "pays nothing at all unless you land the fish as well. Grab for it the moment it appears and you will lose both. Wait "
					+ "until the fish drifts across it and take them together. That is the whole trick, and it is the only part of this I "
					+ "cannot teach you by telling you.",
				Items.NAUTILUS_SHELL, stack -> stack.is(Items.NAUTILUS_SHELL), 12));
	}

	/** One of the water's own: a fish of a river, a pond or a swamp, and not just the plain one. */
	private static boolean freshwaterFish(ItemStack stack) {
		Fish fish = WildFish.inBucket(stack);
		return fish != null && fish != Fish.plain(fish.getRolledFrom())
			&& (fish.foundIn(Waters.Kind.RIVER) || fish.foundIn(Waters.Kind.LAKE) || fish.foundIn(Waters.Kind.SWAMP));
	}

	private static boolean herringKin(ItemStack stack) {
		Fish fish = WildFish.inBucket(stack);
		return fish != null && fish.getFamily() == FishFamily.HERRINGS;
	}
}
