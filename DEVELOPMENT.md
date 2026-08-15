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

The minigame is **server-authoritative**: `FishingEncounterManager` on the server owns the entire state machine (phase timing, hit/miss/feint/treasure resolution, misses, timeout). The client never simulates game logic; it renders what the server declares (via Pandorical HUD) and predicts its own click locally for instant sound feedback before the authoritative result arrives.

If a connecting player has no Pandorical client, `startEncounter` deliberately does nothing and the hook is left entirely to vanilla fishing. There is no fallback minigame.

### Component Map

**Initialization**
- `MinedewFishing` (common): registers network payload types, the `FishingClickC2S` receiver (delegates to `FishingEncounterManager.handleClick`), and the server tick hook
- `MinedewFishingClient`: thin; registers receivers for the three S2C payloads and forwards them to `ClientEncounterState`, no rendering of its own

**Fish** (`fish/`)
- `FishType`: 13 species across 4 difficulty tiers; difficulty (1-4) drives round count, window lengths, and feint eligibility. `getRandomFish(biome, isRaining, timeOfDay, random)` selects by biome tag (ocean/river/deep ocean/other), rain (unlocks a legendary in ocean/river), and time of day (Glacierfish at night in deep ocean)
- `FishMovementPattern`: vestigial enum tag on each `FishType`; not read anywhere, no behavioral effect

**Encounter** (`encounter/`)
- `FishingEncounterManager`: the state machine and single source of truth; starts/aborts encounters, ticks phase timers, resolves clicks server-side using the client's self-reported elapsed time for latency tolerance, applies outcomes, and pushes all HUD updates
- `FishingEncounter`: per-player state for one in-progress encounter (phase, round/miss counters, timing, feint/treasure flags); server-only

**Client prediction** (`client/ClientEncounterState.java`)
- A plain data holder (no `net.minecraft.client.*` imports) mirroring the most recently server-declared phase, so a click can be judged locally for optimistic sound feedback; the server is always final

**Network** (`network/`)
- `FishingPhaseS2C`: phase transition (`tell`/`commit`), duration, feint flag
- `FishingClickResultS2C`: per-round hit/miss outcome
- `FishingEncounterEndS2C`: encounter finished (caught/escaped, treasure secured or not)
- `FishingClickC2S`: the one thing the client sends: "I clicked, and here's how long I believe it's been since the phase began"

**Mixins** (`mixin/`)
- `FishingHookMixin`: bite detection off vanilla's own `nibble` countdown on `FishingHook` (the same signal vanilla uses to gate its catch roll), not a bobber-physics heuristic; starts/aborts encounters with the hook's lifecycle
- `FishingRodItemMixin` (both sides): cancels the vanilla rod-reel interaction while an encounter owns the cast, so a stray right-click can't bypass the minigame into an immediate vanilla catch
- `FishingRodItemClientMixin` (client only): optimistic local click feedback, sends `FishingClickC2S`

**Access** (`access/MinedewFishingHookAccess.java`)
- Implemented by the hook mixin; lets `FishingEncounterManager` force the hook's nibble state to 1 on success so vanilla's own `retrieve()` completes the catch (loot table, Luck of the Sea/Lure all run as normal)

## Mechanics Reference

Each round: **TELL** (colored ring telegraphs; clicking is a miss) → **COMMIT** (prompt flips to "CLICK!"; a click is a hit unless the round is a feint) → **GRACE** (~300ms of tolerance before the round scores as an automatic miss, or on a feint, a successful "read").

- Window durations scale with difficulty: tell 0.50s (diff 1) down to 0.35s (diff 4); commit 0.80s down to 0.45s
- A full encounter needs `difficulty + 1` successful rounds (2-5), tolerates 2 misses, and has a 20-second overall timeout
- Feints: difficulty-4 fish only, ~30% chance per eligible round, never on the treasure round; must be waited out without clicking
- Treasure: each encounter independently has a 15-25% chance to designate one round (chosen up front) as a treasure round; landing it on top of a successful catch grants a bonus item (common materials up to a diamond)
- Success forces the hook's nibble state and calls vanilla `retrieve()`; failure (3rd miss or timeout) discards the hook and plays a break sound

## HUD

The overlay is a single Pandorical HUD (`minedew-fishing:encounter`) anchored above the reticle: a `particle_burst` ring whose color/speed/radius track phase and round type (blue tell → green commit; gold treasure; purple feint), a flash sprite tinting on each outcome (green hit, red miss, white read), text components for fish name, difficulty stars, prompt, and progress pips, and an optional "treasure on the line" hint. All of it is declarative component updates pushed from the server.

## Where to Edit

**Adding a fish**: `fish/FishType.java`; add an enum entry with display name and difficulty, then wire it into the biome/weather/time selection logic in the same file.

**Tuning pacing**: the tables at the top of `FishingEncounterManager.java`:

```java
private static final int[] TELL_TICKS = {10, 9, 8, 7};       // difficulty 1..4
private static final int[] COMMIT_TICKS = {16, 14, 12, 9};   // difficulty 1..4
private static final int MAX_MISSES = 2;
private static final int OVERALL_TIMEOUT_TICKS = 400; // 20s safety net
private static final float FEINT_CHANCE = 0.30F;
```

These are deliberately generous (a genre shift from twitch bar-tracking to "read the tell, commit in the window") and not extensively playtested at every tier; tune here if it plays too easy or too hard.

**Customizing the HUD**: `showHud` / `updateHudForPhase` / `flash` in `FishingEncounterManager.java`, built with Pandorical's `HudBuilder` / `ComponentBuilder` API.

## Testing

`./gradlew runClient`, single-player world with Pandorical installed client-side, `/give @s fishing_rod`, find water, cast.

Debug logging: `MinedewFishing.LOGGER.info(...)`.

**Mixin not applying**: check `src/main/resources/minedew-fishing.mixins.json`, package placement, and that method signatures still match the current target class (`javap` the remapped Minecraft jar if in doubt); then `./gradlew clean build`.

**No minigame appears**: confirm Pandorical is loaded on the connecting client; without it the encounter never starts.

## Known Limitations

- No fallback experience for players without Pandorical (intentional)
- `FishMovementPattern` is vestigial, no behavioral effect
- Pacing constants are generous and unverified by extensive playtesting

## License

MIT License; see the LICENSE file.
