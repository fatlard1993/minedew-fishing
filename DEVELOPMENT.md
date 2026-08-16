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
- **Output** is a declarative Pandorical HUD push. The client only interpolates between the values
  the server sends.

If a connecting player has no Pandorical client, `startEncounter` deliberately does nothing and the
hook is left entirely to vanilla fishing. There is no fallback minigame.

### Component Map

**Initialization**
- `MinedewFishing`: registers the server tick hook, the fillet `overrideVanillaItem` reskins, and
  the mod's asset bundle. That is the whole entrypoint.

**Fish** (`fish/`)
- `FishSpecies`: the five movement identities. Not rolled with invented odds: `HookedCatch` rolls
  vanilla's own `minecraft:gameplay/fishing` table and reads the identity back off the result
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

**HUD** (`hud/`)
- `HookSetHud`: the hook-set ring and prompt
- `MinigameHud`: the fight overlay (track, marker, bobber, catch gauge, treasure chest and its ring)

**Mixins** (`mixin/`)
- `FishingHookMixin`: bite detection off vanilla's own `nibble` countdown (the same signal vanilla
  uses to gate its catch roll), not a bobber-physics heuristic; starts and aborts encounters with the
  hook's lifecycle
- `FishingRodItemMixin`: observes the rod use as minigame input, then cancels it so vanilla's
  `retrieve()` cannot end the cast with an immediate real catch

**Access** (`access/MinedewFishingHookAccess.java`)
- Duck interface implemented by the hook mixin, exposing the hook's private Luck of the Sea value so
  the mod's own loot roll matches the roll vanilla would have made. Lives outside the `mixin`
  package on purpose: Sponge Mixin rejects non-mixin classes in a package a mixin config claims

## Mechanics Reference

An encounter is two stages.

**Hook set.** One telegraphed timing window: `TELL` (a ring winds up; clicking now loses the bite),
`COMMIT` (click), then `GRACE`, a few ticks of network slack after the window visually closes.
Letting it lapse loses the bite too. Tell and commit lengths shorten with difficulty.

**Fight.** The bar game. The marker swims under its species' pattern; the player taps the rod to keep
a bobber bar under it (one click, one upward impulse, gravity between clicks, one impulse per tick
maximum); the catch meter fills while the two overlap and drains while they do not. Full is a catch,
empty is an escape, and there is a 45 s timeout as a safety net.

A fight may also carry a **treasure chest**, which surfaces at a fixed spot partway in and has its
own meter that only fills while the bobber covers it. Time spent on the chest is time not spent on
the fish. It is only kept if the fish is also landed.

The species name is never shown during the fight; only the difficulty stars are. It is revealed on
the catch.

## Bestiary

Five identities, exactly matching what vanilla fishing hands you. Species decides how a fight
**feels**; size decides how **hard** it is. The two are deliberately separate axes, and the species
speed/aggression multipliers are kept narrow so a species never becomes a difficulty tier in
disguise.

| Species | Pattern | Signature | What it feels like |
|---|---|---|---|
| **Cod** | `MODERATE_DART` | Retargets every 22-45 ticks, moderate burst, settles hard | The baseline. Moves to a new depth, sits there, moves again. If you can read anything, you can read cod |
| **Salmon** | `FAST_DART` + `SLOW_SINUSOIDAL` accent | Retargets every 20-40 ticks with the biggest burst of any fish; every ~6 s it drops into a narrow mid-track glide for ~2 s | Runs and glides. Hard lunges, then a stretch where it is suddenly easy while it recovers |
| **Tropical Fish** | `FAST_ERRATIC` | Retargets every 18-34 ticks, shortest hold of any fish, faint per-tick tremor | Skittish. Never settles for long, and never quite sits still even when it does |
| **Pufferfish** | `SLOW_FLOATER` | Retargets every 26-46 ticks, longest holds, rides slightly high | Sluggish and buoyant. Long dead holds and unhurried moves, but it makes you work up-track |
| **Junk** | `SNAG` | Retargets every 22-45 ticks with an extreme settle damping and a low bias | Not a fish. Dead pauses, a sudden short lurch, a dead stop, dragging low. A second of this and you know you have hooked garbage |

Junk has no size variants and always fights at tier 1, with a faster-filling meter, so a boot is
quick and annoying rather than hard.

### Size tiers

| Size | Tier | Bobber | Fill time | Break-even duty | Thrash | Bonus pieces |
|---|---|---|---|---|---|---|
| Small | ★ | 0.200 | 60 ticks | 48% | none | 0 |
| Medium | ★★ | 0.185 | 72 ticks | 48% | none | 1 |
| Large | ★★★ | 0.170 | 84 ticks | 49% | 16 ticks every 170 | 2 |
| Trophy | ★★★★ | 0.165 | 88 ticks | 49% | 20 ticks every 190 | 4 |

Size is rolled per bite, weighted 50/30/15/5, with rain, deep water and night all pushing toward the
bigger classes. `TROPHY_THRASH` is layered over the species' own pattern for the two big classes: a
big one announces itself no matter what species it is.

## Tuning

All numbers live in `MinigameTuning`. Read its class doc before changing any of them: it states the
three invariants that hold the game together, and each one has been broken during tuning and
measured doing damage.

Briefly:

1. **The fish must not outrun the bobber**, where the bound is the bobber's *sustained climb rate*
   `D/(1-D) * (CLICK_IMPULSE*r/20 - BOBBER_GRAVITY)`, not `BOBBER_TERMINAL_SPEED` (clicking never
   reaches that).
2. **The fish must actually traverse the track.** A fish too slow to cover `FISH_MIN_JUMP` within
   its retarget interval drifts around mid-track, which is exactly what a parked bobber covers.
   Slow fish are not easy fish, they are free fish.
3. **The meter's break-even duty cycle `drain / (gain + drain)` must sit in roughly 46-52%.** Below
   it a parked bobber wins on its own; above it not even perfect tracking sustains it.

### Where to edit

**Fish feel**: `FishMovementPattern` (per-pattern knobs) and `FishSpecies` (which pattern, and the
narrow speed/aggression multipliers).

**Difficulty**: `MinigameTuning`'s `BOBBER_SIZE_BY_DIFFICULTY`, `CATCH_TICKS_BY_DIFFICULTY` and
`PROGRESS_DRAIN_BY_DIFFICULTY`, plus `FishSize`'s thrash timings. The bobber height is the strongest
lever and also the most dangerous, because the bar's height is how much of the track a player who
stops playing covers for free.

**Pacing of the hook set**: `HOOK_TELL_TICKS`, `HOOK_COMMIT_TICKS`, `HOOK_GRACE_TICKS`.

**HUD**: `hud/MinigameHud.java` and `hud/HookSetHud.java`. Pixel geometry there mirrors
`generate_textures.py`, which draws each texture at exactly the size it is blitted at; change both
together.

## Testing

### Headless difficulty simulation

Difficulty is tuned against a simulation, not by eye. The harness drives the real `FishingEncounter`
/ `FishMotion` / `MinigameTuning` classes (constructed with a null player, which those classes never
touch) with scripted players, so what it measures is what ships.

It needs three things to be worth anything:

- **A cadence-modulating controller, not a bang-bang one.** The game asks the player to modulate tap
  rate; a bot that clicks every tick it is below the fish oscillates, and will score the slow bots
  above the fast ones.
- **A parking bot.** Sweep a fixed bobber height and report the best result. A tracking bot alone
  cannot tell you the game is winnable without playing it, and that is the failure mode this design
  is most prone to.
- **Latency and fumbles as the skill axis**, since a server-authoritative minigame lives or dies on
  how it degrades over a round trip.

Rebuild classes and run against the Loom-resolved runtime classpath; `Bootstrap.bootStrap()` is
needed before touching `FishSpecies`, because its constants reference `Items`.

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
- A bobber parked at mid-track still lands a minority of the easiest fish (measured up to 36% on
  small salmon). Driving that to zero costs more in fairness to real players than it is worth

## License

MIT License; see the LICENSE file.
