# Minedew Fishing

A Fabric mod that replaces the moment a fish bites your line with a Stardew Valley-inspired fishing
minigame: set the hook, then fight the fish on a bar. Runs entirely server-side and renders through
Pandorical.

## How It Works

When a fish bites (detected from vanilla's own bite signal, not a heuristic), an encounter begins.

### 1. Set the hook

Exactly the way you already do it: the bobber splashes and goes under, so right-click.

There is no extra prompt for this and there is not meant to be. The window is open for as long as
the bobber is under, because it *is* vanilla's own bite window, so "click while it is under" is the
whole rule and it is one you already know. Click before the bite and you reel in an empty line; let
the bite lapse and it is gone. There is a short grace period after the bobber pops back up, which is
where the mod's network tolerance lives.

### 2. Fight it on the bar

A water column appears with two things in it: a **marker**, swimming up and down on its own, and
your **bobber**, a bar you keep under it.

Take a second to look at it if you want one. The fight does not start until your first click: until
then the bar hangs where it is, the meter does not move, and the only thing happening is the fish
showing you how it swims.

The bobber is driven by **click impulses**. Every right-click gives it one kick upward; gravity pulls
it down between clicks. Tap faster to rise, ease off to sink. It is deliberately not a held input:
impulses are events that can arrive whenever the network delivers them, which is what makes a
server-authoritative minigame playable at all.

Mashing does not help. At most one impulse is spent per tick, so past a workable cadence extra
clicks do nothing except pin the bobber against the top of the track.

Beside the column is the **catch meter**. It fills while the marker is inside your bobber and drains
while it is not. Fill it and the fish is yours. Empty it and it is gone.

### 3. Treasure, if you want it

Some fights surface a **treasure chest** partway in, at a fixed spot on the track, with its own meter
that only fills while your bobber covers it.

That is the whole gamble: every moment you spend on the chest is a moment the catch meter is
draining. And the chest only pays out if you land the fish too. Going for it is a real decision, not
free loot.

## What's On The Line

Five things bite, matching exactly what vanilla fishing hands you: **cod**, **salmon**, **tropical
fish**, **pufferfish**, and **junk**. Which one you get is not invented odds; the mod rolls
Minecraft's own fishing loot table at the moment of the bite and reads the identity off the result,
so Luck of the Sea, open-water treasure, biome-specific junk and any datapack edits all apply
normally.

Each species has a **fixed movement signature**, and its name is hidden for the whole fight. Only the
difficulty stars are shown. Learning to recognize a salmon's lunge-and-glide, or a pufferfish's long
buoyant holds, or the unmistakable dead-weight lurch of junk, is the actual skill the mod is about.
The name is the reward at the end.

Difficulty comes from **size**, not species. Every fish comes in Small, Medium, Large and Trophy, and
the bigger classes get a smaller bar, a longer fight, and a thrash that announces itself no matter
what is on the line. Rain, deep water and night all push toward the bigger classes. A bigger fish
also pays out more.

### Fillets

Cod and salmon, raw and cooked, are reskinned as **fillets**. It is there so the size reward reads
right: a trophy fish paying out five whole cod looks like five fish, while five fillets look like one
big fish cut up, which is what actually happened. Vanilla clients see the ordinary items.

## Tips

- The hook set is the only timed moment, and it is vanilla's: watch the bobber, not the screen
- Nothing is lost by taking a moment to read the fish before your first click. The fight is waiting
  for you, not running without you
- Tapping at a steady rate holds the bobber level. Learn where that rate is and everything else is a
  nudge away from it
- Leaving the rod alone will not land you a fish, and neither will an autoclicker. Both were measured
- Difficulty stars tell you how big it is before you commit. The species you have to read

## Pandorical

Minedew Fishing is server-authoritative and entirely server-side. The whole state machine (hook-set
timing, fish motion, bobber physics, meter, treasure, outcome) runs on the server, and no
minedew-fishing jar is ever needed on a client:

- The player's click is the ordinary rod right-click, which the server already receives as a vanilla
  use packet. The mod observes it server-side during an encounter and cancels vanilla reeling. There
  is no custom packet and no client-reported timing.
- The fight renders as a **Pandorical HUD overlay** driven entirely by declarative updates pushed
  from the server, including Pandorical's `particle_burst` component for the chest's capture ring,
  which the client animates itself off a phase clock rather than being fed per-tick. The hook set
  renders nothing at all: vanilla's own splash and dipped bobber are the prompt.

**The Pandorical mod is required client-side.** If a connecting player doesn't have Pandorical, this
mod leaves their fishing hook alone entirely and vanilla's normal fishing behavior applies unmodified
for that player. There is no fallback minigame or reduced-feature mode.

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`); connecting clients need only Pandorical. Version targets live in `gradle.properties` (Minecraft, loader, Fabric API) and `fabric.mod.json` (Java).

## Development

Architecture, the bestiary, tuning invariants, and testing: see [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT, see [LICENSE](LICENSE).

## Credits

- Inspired by [Stardew Valley](https://www.stardewvalley.net/) by ConcernedApe
- Built with [Fabric](https://fabricmc.net/)
- Created by justfatlard
