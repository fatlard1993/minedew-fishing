# Minedew Fishing

A Fabric mod that replaces the moment a fish bites your line with a Stardew Valley-inspired "telegraph and commit" fishing minigame, run entirely server-side and rendered through Pandorical.

## How It Works

When a fish bites (detected from vanilla's own bite/nibble signal, not a heuristic), a fishing encounter begins:

1. A short **tell** phase plays: a colored ring appears around your reticle, telegraphing that a commit window is coming.
2. The ring then flips into a **commit** window and the prompt reads **"CLICK!"**; right-click (use your fishing rod) during this window to land the round.
3. Clicking too early (during the tell) or too late (after the window plus a brief grace period) counts as a miss.
4. This repeats for **2 to 5 rounds**, depending on the fish's difficulty. You can afford up to 2 misses across the whole encounter; a 3rd miss loses the fish. There's also an overall 20-second timeout as a safety net.
5. Land enough rounds and the fish is yours: the mod completes the catch through vanilla's own retrieval logic, so loot tables, enchantments (Luck of the Sea, Lure), etc. all work normally.

Higher-difficulty fish shorten both the tell and commit windows, so rounds come at you faster.

### Feint Rounds

The toughest (legendary-tier) fish can occasionally throw a **feint**: a round that looks exactly like a normal tell/commit cycle, but you must *not* click through it; clicking during a feint's commit window counts as a miss. Correctly waiting one out costs you nothing; it's a "read the fish" moment on top of the normal rounds.

### Treasure

Every encounter has a chance to carry a bonus **treasure round**, flagged in the HUD with a gold-tinted ring and a "treasure on the line" hint. Landing that specific round on top of a successful catch grants an extra reward (an item ranging from a handful of lapis or iron up to a diamond), independent of the normal fish drop. Treasure rounds are never feints.

### Fish

13 fish species across 4 difficulty tiers, each with its own display name and difficulty rating (reflected in tell/commit window length and feint eligibility):

- **Easy**: Carp, Sunfish, Sardine
- **Medium**: Bass, Trout, Salmon, Catfish
- **Hard**: Pike, Tuna, Sturgeon
- **Legendary**: Crimsonfish, Glacierfish, Mutant Carp (feint-eligible)

Which fish you get depends on your current biome, whether it's raining, and the time of day.

### Tips

- Easier fish (★) give you generous tell/commit windows, good for learning the rhythm
- Harder fish shorten both windows, so rounds come faster
- Only legendary (★★★★) fish can throw feints
- A gold-tinted ring means the encounter is carrying a treasure round

## Requirements

- Targets the Minecraft, Fabric Loader, and Fabric API versions declared in this mod's `gradle.properties`; check there for the exact currently-supported version
- Java version as declared in `fabric.mod.json`'s `depends` block
- Pandorical (required, see below)

## Pandorical

Minedew Fishing is server-authoritative and entirely server-side: a `FishingEncounterManager` on the server runs the entire round state machine (phase timing, hit/miss/feint/treasure resolution, misses, timeout). No minedew-fishing jar is ever needed on a client:

- The player's click is the ordinary rod right-click, which the server already receives as a vanilla use packet; the mod observes it server-side during an encounter and cancels vanilla reeling. Latency tolerance comes from the grace phase after each commit window, not from any client-reported timing.
- The on-screen encounter (ring, prompt text, progress pips, fish name/difficulty, flash feedback) renders as a **Pandorical HUD overlay**, driven entirely by declarative updates pushed from the server (including Pandorical's `particle_burst` component for the orbiting ring and its animated geometry/color updates for phase and outcome changes).

**The Pandorical mod is required client-side.** If a connecting player doesn't have Pandorical, this mod leaves their fishing hook alone entirely and vanilla's normal fishing behavior applies unmodified for that player. There is no fallback minigame or reduced-feature mode.

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`). Connecting clients need only Pandorical.

## Development

Architecture, mechanics reference, tuning knobs, and testing: see [DEVELOPMENT.md](DEVELOPMENT.md).

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Credits

- Inspired by [Stardew Valley](https://www.stardewvalley.net/) by ConcernedApe
- Built with [Fabric](https://fabricmc.net/)
- Created by justfatlard
