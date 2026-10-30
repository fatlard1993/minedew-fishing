# Minedew Fishing - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Setup

Prerequisites: a JDK matching the Java version declared in `fabric.mod.json`, and Git.

```bash
git clone https://github.com/fatlard1993/minedew-fishing.git
cd minedew-fishing
./gradlew build        # output in build/libs/
./gradlew runClient    # dev client
./gradlew runServer    # dev server
```

## Architecture

The minigame is **server-authoritative and entirely server-side**. `FishingEncounterManager` owns
every live encounter and drives it from the server tick; the client runs no game logic at all. There
is no client entrypoint, no client jar, and no custom packet in either direction.

- **Input** is the ordinary vanilla rod right-click, observed server-side in `FishingRodItemMixin`.
  During the hook set it is the commit; during the fight it queues one upward impulse.
- **Output** is a declarative Pandorical HUD push, for the fight only. The hook set draws nothing:
  vanilla's own splash and dipped bobber are the cue, and the window is vanilla's own `nibble`
  countdown, so the prompt and the mechanic are the same object.
- The client only interpolates between the values the server sends.

If a connecting player has no Pandorical client, `startEncounter` deliberately does nothing and the
hook is left entirely to vanilla fishing. There is no fallback minigame.

### Component Map

**Initialization**
- `MinedewFishing`: registers the `fish_landed` advancement trigger, the difficulty attachment, the
  config and mods-menu setting, `/fishing`, the server tick hook, the wild fish, the fillet
  `overrideVanillaItem` reskins, the mod's asset bundle, and the Village Quests lessons when that
  mod is loaded

**Difficulty setting**
- `FishingDifficulty`: `EASIEST`/`EASY`/`NORMAL`/`HARD`, each a set of multipliers on bar height,
  meter drain, fish speed and pattern jitter, layered over the size tier. The per-player choice is a
  persistent attachment (copied on death); absent means the server's
- `FishingConfig`: `config/minedew-fishing.properties` (`difficulty`, default `normal`) and the
  ops-only Pandorical `serverGroup` choice backed by it. A change from the menu or the command is
  written back to the file, replacing the key's line in place
- `FishingCommands`: `/fishing difficulty` (anyone, reports), `... server <level>` and
  `... player <players> <level|server>` (both `LEVEL_GAMEMASTERS`)

**Advancements** (`advancement/`)
- `FishLandedCriterion`: `minedew-fishing:fish_landed`, fired on a won fight (never junk) with an
  optional `min_difficulty` condition and an optional `fish` list of catch ids, any of which will do.
  `big_one` asks for 3, `trophy` for 4
- The Angler's Log (`advancement/angler/`): a tab with one advancement per family, one per rare solo
  fish, and the `every_fish` and `reef_survey` challenges. Written by hand; the `Gallery` game test
  fails if a fish in `Fish` or one of vanilla's named tropical fish is missing from the challenges

**API** (`api/`)
- `MinedewFishingApi`: `addBiter` lets another mod take a bite before vanilla's table is rolled.
  Biters are asked in order and the first non-null `Quarry` wins; a biter that throws is logged once
  and skipped after that. The size is still rolled here, so difficulty works the same

**Integration** (`integration/`)
- `VillageQuestsLessons`: the fisherman's seven-lesson craft. The two bucket lessons read the fish
  out of the bucket (`WildFish.inBucket`), the freshwater one through `Fish.foundIn`, so it follows
  the fish's own conditions. New lessons go before the last: Village Quests counts progress in
  lessons done. Refers to Village Quests types directly, so it is only reached behind the
  `isModLoaded` check

**Fish** (`fish/`)
- `Quarry`: what the fight and the payout read off the thing on the line. `FishSpecies` is the
  vanilla implementation; another mod's comes in through `MinedewFishingApi`. Two hooks with
  defaults: `dressDisplay` dresses the landing animation's mob (an overlay, a model), and
  `landAlive` lets a catch come up alive instead of being landed, in which case nothing is handed
  over but a chest won alongside it and the reveal reads "Surfaced" instead of "Landed"
- `FishSpecies`: what vanilla's table handed over (cod, salmon, tropical fish, pufferfish, junk).
  Not rolled with invented odds: `HookedCatch` rolls vanilla's own `minecraft:gameplay/fishing`
  table and reads the identity back off the result. Junk fights as itself; the four fish defer to
  their plain `Fish`
- `Fish`: every catchable fish, each tied to the vanilla roll it narrows (which decides its fillet
  and the vanilla body it borrows), a weight, and a `Waters` predicate. `Fish.bite` picks among the
  fish of the rolled kind that live where the bobber is, falling back to the vanilla fish itself
- `FishFamily`: kin, and so movement: each family's pattern, base speed and aggressiveness. A fish
  adds an accent and a strength trim of at most a tenth
- `Waters`: the water a fish was found in - kind (ocean, river, swamp, lake, cave), temperature,
  depth measured down the column, mountains, jungle, the hour, the weather
- `Look`: a fish's body, its Pandorical skin (an entity model under
  `pandorical/entity_models/fish/`, from `generate_fish_textures.py`) and draw scale, or a
  tropical-fish variant
- `ReefFish`: one tropical fish off the reef, by its own variant, named as vanilla names them
- `WildFish`: the identity of fish swimming in the world, as a persistent attachment rolled on first
  load from the same table, carried through buckets by `AbstractFishMixin`; also the freshwater
  spawns that give rivers, swamps and ponds fish to dress
- `FishSize`: `SMALL`/`MEDIUM`/`LARGE`/`TROPHY`, rolled per bite. This is where difficulty comes
  from; it also sets the size bonus payout
- `FishMovementPattern`: the per-pattern motion knobs (retarget interval, pull, burst, jitter,
  settle damping, target bias). Data only
- `FishMotion`: runs one fight's motion at 20 Hz off those knobs, layering primary pattern, species
  accent, and the size class's thrash
- `HookedCatch`: one resolved bite (species, size, and the exact loot that will be handed over)

**Encounter** (`encounter/`)
- `FishingEncounterManager`: lifecycle, tick loop, click dispatch, outcome resolution, payout
- `FishingEncounter`: per-player state for one encounter, and the hook-set and fight step functions
- `MinigameTuning`: every feel knob, plus the invariants that constrain them
- `FishingStreaks`: fish lost in a row per player, as world `SavedData`; drives the back-off (a
  wider bar and a smaller-fish tilt per miss, capped at `BACKOFF_MAX_MISSES`)

**HUD** (`hud/`)
- `MinigameHud`: the fight overlay (track, marker, bobber, catch gauge, treasure chest and its ring).
  The only overlay there is; the hook set deliberately has none

**Mixins** (`mixin/`)
- `FishingHookMixin`: bite detection off vanilla's own `nibble` countdown (the same signal vanilla
  uses to gate its catch roll), not a bobber-physics heuristic; hands that countdown to the encounter
  as the hook-set window, holds it up for the duration of a fight so the line stays taut and vanilla
  cannot start a second bite underneath one, and starts and aborts encounters with the hook's
  lifecycle
- `FishingRodItemMixin`: observes the rod use as minigame input, then cancels it so vanilla's
  `retrieve()` cannot end the cast with an immediate real catch
- `AbstractFishMixin`: carries a wild fish's identity through a bucket and back

**Access** (`access/MinedewFishingHookAccess.java`)
- Duck interface implemented by the hook mixin, exposing the hook's private Luck of the Sea value so
  the mod's own loot roll matches the roll vanilla would have made. Lives outside the `mixin`
  package on purpose: Sponge Mixin rejects non-mixin classes in a package a mixin config claims

## Mechanics Reference

An encounter is two stages.

**Hook set.** One timing window with no overlay at all, opening on the tick of the bite: `COMMIT`
(vanilla has just played its splash and pulled the bobber under, so click), then `GRACE`, a few ticks
of network slack after the bobber pops back up. Letting it lapse loses the bite. Clicking *before*
the bite never reaches the mod at all: with no encounter open, vanilla reels an empty line in and the
cast is over, so jumping the gun still costs the bite without the mod saying a word about it.

The window length is not a constant of ours. It is vanilla's `nibble` roll for that bite (20-40
ticks), read off the hook in `FishingHookMixin` at the moment vanilla sets it, which is the same tick
it plays the splash and flips `DATA_BITING`. That is what pulls the bobber under and keeps it under
for exactly that many ticks, so the window is open precisely while the cue is showing. Difficulty
does not shorten it: the difficulty is the fight.

The one thing the hook set's timing does buy is a head start. A strike within `HOOK_SET_CLEAN_TICKS`
(6) of the bite opens the meter `STRIKE_PROGRESS_BONUS_MAX` (0.15) above `PROGRESS_START`, tapering
to nothing at `HOOK_SET_LATE_TICKS` (18), and the action bar names it ("Clean strike!"). Measured in
ticks since the bite, not as a fraction of the window, so a long `nibble` roll does not pay the same
reaction more. It sits above the opening floor rather than raising it, so an unused head start drains
back to where everyone else began.

**Fight.** The bar game. The marker swims under its species' pattern; the player taps the rod to keep
a bobber bar under it (one click, one upward impulse, gravity between clicks, one impulse per tick
maximum); the catch meter fills while the two overlap and drains while they do not. Full is a catch,
empty is an escape, and there is a 45 s timeout as a safety net.

A landed catch flies to the player as the real vanilla fish mob, dressed as the fish it is and
scaled by its size class (`FishSize.getDisplayScale`, 0.6x to 2.2x), and is held up in front of them
for `SHOW_TICKS`, side-on and dangling, before the haul is handed over
(`FishingEncounterManager.CatchFlight`). Out of the water vanilla's cod, salmon and tropical fish
renderers lay the fish on its side; the `dangle` animation turns the model's root back a quarter
turn and moves it back by the model's height so the flank faces the player (`dangle_puffer` for the
pufferfish body, which stays upright). Junk flies as its item, vanilla-style.

The fight starts on the tick after the hook-set click: that click is the proof the player is
present, so there is no further wait. (An earlier build additionally held the bar and the clock
until a first *fight* click, and it read as the game failing to start.) Reaction time is bought by
the opening floor; see the opening buffer under Tuning.

A fight may also carry a **treasure chest**, which surfaces at a fixed spot partway in and has its
own meter that only fills while the bobber covers it. Time spent on the chest is time not spent on
the fish. It is only kept if the fish is also landed.

The overlay names nothing and rates nothing during the fight: not the species, not the size. Both
are read off how the thing moves and how hard it pulls, and the name is revealed on the catch.

The size used to show as difficulty stars. They were honest, and they were genuinely useful while
the fight was being tuned - then in play they became a verdict handed down before the first click,
and one-star fights got abandoned rather than finished. A grade a player can act on before they
have started is a grade that decides for them.

## Bestiary

Vanilla's table picks the kind (cod, salmon, tropical fish, pufferfish, junk); the water picks the
fish (`Fish`). Species decides how a fight **feels**; size decides how **hard** it is. The two are
deliberately separate axes, and the speed/aggression multipliers are kept narrow so a species never
becomes a difficulty tier in disguise.

The vanilla four keep their old signatures, unchanged:

| Species | Pattern | Signature | What it feels like |
|---|---|---|---|
| **Cod** | `MODERATE_DART` | Retargets every 22-45 ticks, moderate burst, settles hard | The baseline. Moves to a new depth, sits there, moves again. If you can read anything, you can read cod |
| **Salmon** | `FAST_DART` + `SLOW_SINUSOIDAL` accent | Retargets every 20-40 ticks with the biggest burst of any fish; every ~6 s it drops into a slow mid-track sweep for ~2 s | Runs and glides. Hard lunges, then a stretch where it is suddenly easy while it recovers |
| **Tropical Fish** | `FAST_ERRATIC` | Retargets every 18-34 ticks, shortest hold of any fish, faint per-tick tremor | Skittish. Never settles for long, and never quite sits still even when it does |
| **Pufferfish** | `SLOW_FLOATER` | Retargets every 26-46 ticks, longest holds, rides slightly high | Sluggish and buoyant. Long dead holds and unhurried moves, but it makes you work up-track |
| **Junk** | `SNAG` | Retargets every 22-45 ticks with an extreme settle damping and a low bias | Not a fish. Dead pauses, a sudden short lurch, a dead stop, dragging low. A second of this and you know you have hooked garbage |

Every other fish swims its family's pattern (`FishFamily`) with an accent of its own:

| Family | Pattern | Fish |
|---|---|---|
| Groundfish | `MODERATE_DART` | Cod, Lingcod |
| Sunfishes | `LEAP` | Largemouth Bass, Smallmouth Bass, Sunfish |
| Perches | `STALK` | Perch, Walleye |
| Carps | `CRUISE` | Carp, Chub, Bream |
| Catfishes | `BOTTOM_GRIND` | Catfish, Bullhead |
| Pikes | `AMBUSH` | Pike |
| Sturgeons | `BOTTOM_GRIND` | Sturgeon |
| Cichlids | `TERRITORIAL` | Tilapia, Blue Discus |
| Characins | `SNAP` | Dorado, Blind Cave Fish |
| Salmonids | `FAST_DART` | Salmon, Rainbow Trout, Tiger Trout |
| Herrings | `SCHOOL` | Sardine, Anchovy, Herring, Shad |
| Tunas | `RUN` | Tuna, Albacore |
| Eels | `SERPENTINE` | Eel |
| Scorpionfishes | `LURK` | Stonefish, Lionfish |
| Puffers | `SLOW_FLOATER` | Pufferfish |
| Sculpins | `SLUMP` | Blobfish |
| Reef Fish | `FAST_ERRATIC` | the reef's tropical fish |

Each family pattern was tuned alone in `tune_sim.py` until a medium fish landed for a good player
about as often as a medium cod or pufferfish, and each fish's accent timing and strength searched
until the fish as a whole did too: for a good player every medium fish lands 48-66% (cod 54,
pufferfish 68), a sloppy one 4-11%, a parked bar 0-2% and an autoclicker never. The table in
`tune_sim.py` mirrors `Fish`; keep the two together.

Two things the tuning turned up. A sine primary slow enough to read as gliding is one a bar can sit
on (95% for a sloppy player), so `CRUISE` and `SERPENTINE` glide by retargeting with almost no
settle instead. And an accent switch must not re-roll the retarget clock between two retargeting
layers: it used to, which made every accent a pause and every accented fish 25-35 points easier
than its family. `FishMotion` keeps the move already due; a thrash's onset and the end of a sine
glide still re-roll, which leaves the vanilla four exactly as they were.

Anything another mod adds through `MinedewFishingApi` brings its own row. Check its signature
against these with `tune_sim.py` before shipping it: the accent combinations are not
intuitive, and a primary plus a gentler accent can come out far easier than either alone.

Junk has no size variants and always fights at tier 1, with a faster-filling meter, so a boot is
quick and annoying rather than hard.

### Size tiers

| Size | Tier | Bobber | Fill time | Break-even duty | Thrash | Bonus pieces |
|---|---|---|---|---|---|---|
| Small | ★ | 0.200 | 75 ticks | 48% | none | 2 |
| Medium | ★★ | 0.185 | 92 ticks | 48% | none | 3 |
| Large | ★★★ | 0.170 | 110 ticks | 49% | 16 ticks every 170 | 4 |
| Trophy | ★★★★ | 0.165 | 120 ticks | 50% | 20 ticks every 190 | 6 |

Size is rolled per bite, weighted 50/30/15/5, with rain, deep water (a deep ocean, or at least
`Waters.DEEP` blocks of it under the bobber anywhere) and night all pushing toward the bigger
classes. `TROPHY_THRASH` is layered over the species' own pattern for the two big classes: a
big one announces itself no matter what species it is.

## Tuning

All numbers live in `MinigameTuning`. Read its class doc before changing any of them: it states the
three invariants that hold the game together, and each one has been broken during tuning and
measured doing damage.

Briefly:

1. **The fish must not outrun the bobber**, where the bound is the bobber's *sustained climb rate*
   `D/(1-D) * (CLICK_IMPULSE*r/20 - BOBBER_GRAVITY)`, not `BOBBER_TERMINAL_SPEED` (clicking never
   reaches that). Downhill, the bound is transient rather than sustained: a fall builds through the
   damping's time constant while a burst is instant, which is what `FISH_PULL_GRAVITY_MULT` exists
   to cover — gravity doubles while the fish is below the bar, and never while the bar covers it,
   so the hover cadence stays untouched.
2. **The fish must actually traverse the track.** A fish too slow to cover `FISH_MIN_JUMP` within
   its retarget interval drifts around mid-track, which is exactly what a parked bobber covers.
   Slow fish are not easy fish, they are free fish.
3. **The meter's break-even duty cycle `drain / (gain + drain)` must sit in roughly 46-52%.** Below
   it a parked bobber wins on its own; above it not even perfect tracking sustains it.

### The opening buffer

Separate from all three invariants, and do not confuse it with them. Break-even governs a fight
already in progress; the opening buffer governs whether the player was ever in it. It is two knobs:

| Knob | What it does |
|---|---|
| `PROGRESS_START` | Meter the fight opens with, and the floor it cannot fall through while the opening lasts |
| `OPENING_FLOOR_TICKS` | How long that floor lasts, in fight ticks |

Learned measuring these, the hard way:

- **Buy reaction time in ticks, not in progress.** `PROGRESS_START` is a permanent credit toward the
  win and is worth exactly as much to a bar nobody is holding. Taking `OPENING_FLOOR_TICKS` from 6 to
  35 cost the camping bot almost nothing; taking `PROGRESS_START` from 0.10 to 0.30 instead put a
  parked bobber back at 43-53% on the easy tiers.
- **A wait-for-first-click hold is not worth its feel cost.** One shipped for a while: bar hung,
  clock stopped, meter frozen until the first fight click. It measured well (a stopped clock buys
  nothing for camping, and it made catch rates flat out to two seconds of reaction time) but it
  played as the game failing to start, because the hook-set click had already proven the player
  present. Removing it cost the simulated profiles ~15% relative on catch rate and the parking bot
  nothing; if reaction time ever needs buying again, buy it in `OPENING_FLOOR_TICKS`.

A floor rather than a drain freeze, for the same reason: a freeze makes points won during the opening
permanent, and a parked bar collects those too.

### Where to edit

**Fish feel**: `FishMovementPattern` (per-pattern knobs) and `FishSpecies` (which pattern, and the
narrow speed/aggression multipliers).

**Difficulty**: `MinigameTuning`'s `BOBBER_SIZE_BY_DIFFICULTY`, `CATCH_TICKS_BY_DIFFICULTY` and
`PROGRESS_DRAIN_BY_DIFFICULTY`, plus `FishSize`'s thrash timings. The bobber height is the strongest
lever and also the most dangerous, because the bar's height is how much of the track a player who
stops playing covers for free.

**The difficulty setting**: `FishingDifficulty`'s multipliers, applied on top of the tier. Its class
doc has the rules: drain is only ever eased, never raised (above ~52% break-even nobody sustains a
fish), so Hard is a narrower bar and a quicker fish; and the fish stays slower than the bobber at
every level. Easiest deliberately lets a parked bar land the smaller fish.

**Reaction time**: the opening buffer above, never the difficulty knobs.

**Pacing of the hook set**: `HOOK_GRACE_TICKS` only. The window itself is vanilla's, on purpose.
The clean-strike head start is `HOOK_SET_CLEAN_TICKS`, `HOOK_SET_LATE_TICKS` and
`STRIKE_PROGRESS_BONUS_MAX`; read the `PROGRESS_START` note before raising the last.

**HUD**: `hud/MinigameHud.java`. Pixel geometry there mirrors `generate_textures.py`, which draws
each texture at exactly the size it is blitted at; change both together.

**Fish skins**: `generate_fish_textures.py` writes every skin's texture and Pandorical entity model
from one table, painted onto the vanilla cod, salmon and pufferfish UV layouts. Stretches are given
about each part's own vanilla pivot, because Pandorical scales a part's pivot along with the part.
The names are the ones `Fish` passes to `Look.skin`; add a fish in both places.

**Where fish live**: the `Waters` predicate and weight on each `Fish`. The Angler's Log descriptions
hint at those conditions in words, so a changed condition wants its hint changed too.

## Testing

### Headless difficulty simulation

Difficulty is tuned against a simulation, not by eye. The current instrument is `tune_sim.py`, a
Python mirror of `FishingEncounter` / `FishMotion` / `MinigameTuning` (the original Java-classpath
harness was not kept). Being a mirror, it measures what ships only while it agrees with the Java
constants and stepping order — it has drifted once already, and a whole physics scale-up shipped
unmeasured because of it. Any change to the constants or the step functions goes into both files in
the same commit.

It needs four things to be worth anything:

- **An orientation delay on every profile.** Ticks at the start of the fight during which the bot
  does not click at all, because a player has to notice the overlay, find the marker and start a
  cadence: 250 ms for the ideal profile, 600 ms for the good one, a full second for the sloppy one.
  This is not a refinement, it is the difference between measuring the game and measuring nothing. A
  bot that starts tracking on tick 0 is blind to the entire opening, which is how an opening buffer
  worth 625 ms shipped and was unplayable on contact with a human. Report catch rate as a *curve*
  against this delay, not just at the profile's value: what you want to see is flat.
- **A cadence-modulating controller, not a bang-bang one.** The game asks the player to modulate tap
  rate; a bot that clicks every tick it is below the fish oscillates, and will score the slow bots
  above the fast ones.
- **A parking bot.** Sweep a fixed bobber height and report the best result, plus the two degenerate
  ends of that sweep by name (never clicking, and clicking every tick). A tracking bot alone cannot
  tell you the game is winnable without playing it, and that is the failure mode this design is most
  prone to.
- **Latency and fumbles as the skill axis**, since a server-authoritative minigame lives or dies on
  how it degrades over a round trip.

Run it from a Python REPL: `import tune_sim; tune_sim.rate("salmon", "large", tune_sim.GOOD)`,
with `park=True` for the parking bot. No Minecraft classpath involved, which is the whole reason it
survives where the Java harness did not — and also why the sync rule above is load-bearing.

Every named fish is in its table too (`FAMILIES` and `FISH`, mirroring `FishFamily` and `Fish`), and
`tune_sim.rate("walleye", "medium", tune_sim.GOOD)` measures one.

### Game tests

Client game tests, which open a window, so run them under xvfb:

```bash
xvfb-run -a -s "-screen 0 2048x1152x24" ./gradlew runClientGameTest
```

- `Showcase`: the fight, as the README's picture
- `Gallery`: fails if a fish in `Fish`, or one of vanilla's named tropical fish, is missing from the
  Angler's Log challenges; checks a cod let loose in a pond becomes a pond fish, wears its skin and
  keeps it through a bucket; and photographs every fish dressed (`gallery.png`) and a landed one on
  show (`the-show.png`), which is the only way to judge the skins and the hold

Frames land in `build/run/clientGameTest/screenshots`.

### In game

`./gradlew runClient`, single-player world with Pandorical installed client-side, `/give @s
fishing_rod`, find water, cast.

Debug logging: `MinedewFishing.LOGGER`.

**Mixin not applying**: check `src/main/resources/minedew-fishing.mixins.json`, package placement,
and that method signatures still match the current target class (`javap` the remapped Minecraft jar
if in doubt); then `./gradlew clean build`.

**No minigame appears**: confirm Pandorical is loaded on the connecting client; without it the
encounter never starts.

## Known Limitations

- No fallback experience for players without Pandorical (intentional)
- The difficulty numbers come from scripted players. They bound the game (a parking bot cannot win,
  a laggy player can still play) but they cannot tell you whether it *feels* good, whether the
  motion is legible on screen, or whether the tap cadence is comfortable on a real hand
- The simulated treasure-chest cost is a worst case: the bot abandons the fish the instant the chest
  surfaces, where a player can wait until the fish is already near it
- A bobber parked at mid-track still lands a minority of the easiest fish (measured up to 32% on
  small salmon, 14% on small pufferfish, under 10% everywhere else). Driving that to zero costs more
  in fairness to real players than it is worth
- The hook-set window is inherited from vanilla's `nibble` roll, so it varies 20-40 ticks from bite
  to bite and nothing in the mod can tighten it for a harder fish. That is the trade for having the
  cue and the window be the same object, and it is the right trade: the alternative is a telegraph
  the player has to learn on top of one they already know

## License

MIT License; see the LICENSE file.

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).
