# Minedew Fishing

A Fabric mod that replaces the moment a fish bites your line with a Stardew Valley-inspired fishing
minigame: set the hook, then fight the fish on a bar. Runs entirely server-side and renders through
Pandorical.

## How It Works

When a fish bites (detected from vanilla's own bite signal, not a heuristic), an encounter begins.

### 1. Set the hook

A ring winds up around your reticle, then snaps bright and the prompt reads **SET!**. Right-click in
that window to set the hook.

Click too early and you have jumped the gun; let the window lapse and the bite is gone. Either way
the fish spits the hook and the cast is over, so there is one thing to get right before the fight
even starts. There is a short grace period after the window visually closes, which is where the
mod's network tolerance lives.

### 2. Fight it on the bar

A water column appears with two things in it: a **marker**, swimming up and down on its own, and
your **bobber**, a bar you keep under it.

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

- The hook set is the only pure reflex moment; the fight rewards smooth cadence over fast hands
- Tapping at a steady rate holds the bobber level. Learn where that rate is and everything else is a
  nudge away from it
- Leaving the rod alone will not land you a fish, and neither will an autoclicker. Both were measured
- Difficulty stars tell you how big it is before you commit. The species you have to read

## Requirements

- Targets the Minecraft, Fabric Loader, and Fabric API versions declared in this mod's
  `gradle.properties`; check there for the exact currently-supported version
- Java version as declared in `fabric.mod.json`'s `depends` block
- Pandorical (required, see below)

## Pandorical

Minedew Fishing is server-authoritative and entirely server-side. The whole state machine (hook-set
timing, fish motion, bobber physics, meter, treasure, outcome) runs on the server, and no
minedew-fishing jar is ever needed on a client:

- The player's click is the ordinary rod right-click, which the server already receives as a vanilla
  use packet. The mod observes it server-side during an encounter and cancels vanilla reeling. There
  is no custom packet and no client-reported timing.
- The encounter renders as a **Pandorical HUD overlay** driven entirely by declarative updates pushed
  from the server, including Pandorical's `particle_burst` component for the hook-set ring and the
  chest's capture ring, which the client animates itself off a phase clock rather than being fed
  per-tick.

**The Pandorical mod is required client-side.** If a connecting player doesn't have Pandorical, this
mod leaves their fishing hook alone entirely and vanilla's normal fishing behavior applies unmodified
for that player. There is no fallback minigame or reduced-feature mode.

## Installation

Install server-side alongside its declared dependencies (see `fabric.mod.json`). Connecting clients
need only Pandorical.

## Development

Architecture, the bestiary, tuning invariants, and testing: see [DEVELOPMENT.md](DEVELOPMENT.md).

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Credits

- Inspired by [Stardew Valley](https://www.stardewvalley.net/) by ConcernedApe
- Built with [Fabric](https://fabricmc.net/)
- Created by justfatlard
