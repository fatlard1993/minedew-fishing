# Minedew Fishing

A Fabric mod for Minecraft 1.21.4 that replaces the vanilla fishing system with a Stardew Valley-style fishing minigame.

## Features

### Engaging Fishing Minigame
- **Vertical bar UI** that appears when fishing
- **Moving fish** with position-based AI
- **Capture zone** that you must keep the fish in
- **Progress bar** that fills when successful, depletes when fish escapes
- **Click/hold mechanics** for intuitive gameplay

### Diverse Fish Types
The mod includes 13 different fish species across 4 difficulty tiers:

#### Easy Fish (★)
- **Carp** - Slow, gentle movement
- **Sunfish** - Calm and predictable
- **Sardine** - Easy to catch

#### Medium Fish (★★)
- **Bass** - Occasional quick jumps
- **Trout** - Moderate speed with jumps
- **Salmon** - Smooth but fast directional changes
- **Catfish** - Steady but challenging

#### Hard Fish (★★★)
- **Pike** - Fast and erratic movements
- **Tuna** - Very quick direction changes
- **Sturgeon** - Unpredictable patterns

#### Legendary Fish (★★★★)
- **Crimsonfish** - Chaotic movement (Ocean, Rainy weather)
- **Glacierfish** - Teleport-like behavior (Deep Ocean, Night)
- **Mutant Carp** - Extreme chaos (River, Rainy weather)

### Dynamic Fish Spawning
Fish availability depends on:
- **Biome** - Different fish in oceans, rivers, and lakes
- **Weather** - Legendary fish appear during rain
- **Time of Day** - Some fish only appear at night
- **Location** - Deep ocean has different fish than shallow water

### Movement Patterns
Each fish has unique AI behavior:
- **Slow Sinusoidal** - Gentle wave-like movement
- **Moderate Jumpy** - Calm with sudden bursts
- **Moderate Dart** - Fast directional changes
- **Fast Erratic** - Highly unpredictable
- **Fast Dart** - Lightning-quick movements
- **Legendary Chaos** - Extreme unpredictability
- **Legendary Teleport** - Instant position changes

### Visual & Audio Feedback
- **Smooth animations** for fish and capture bar
- **Sound effects** for:
  - Fish splash when minigame starts
  - Button click when moving capture bar
  - Bell chime when fish enters capture zone
  - Level up sound on successful catch
  - Retrieve sound on failure
- **Pulsing effects** on UI elements
- **Real-time progress tracking**

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.4
2. Install [Fabric API](https://modrinth.com/mod/fabric-api)
3. Download the latest release of Minedew Fishing
4. Place the `.jar` file in your `mods` folder

## How to Play

1. **Cast your fishing rod** into water like normal
2. **Wait for the minigame** to appear (automatic when fish bites)
3. **Click or hold** to move the green capture bar up
4. **Release** to let the bar fall down
5. **Keep the fish** (orange icon) inside the green capture zone
6. **Fill the progress bar** to 100% to catch the fish
7. **Don't let progress reach 0%** or the fish escapes!

## Building from Source

```bash
git clone https://github.com/yourusername/minedew-fishing.git
cd minedew-fishing
./gradlew build
```

The compiled mod will be in `build/libs/`.

## Development

### Project Structure
```
src/
├── main/
│   ├── java/com/minedew/fishing/
│   │   ├── MinedewFishing.java          # Main mod class
│   │   ├── MinedewFishingClient.java    # Client initializer
│   │   ├── fish/
│   │   │   ├── FishType.java            # Fish definitions
│   │   │   ├── FishMovementPattern.java # Movement types
│   │   │   └── FishBehavior.java        # AI logic
│   │   └── network/
│   │       ├── FishingMinigameStartPayload.java
│   │       └── FishingMinigameEndPayload.java
│   └── resources/
│       ├── fabric.mod.json
│       └── minedew-fishing.mixins.json
└── client/
    └── java/com/minedew/fishing/
        ├── client/
        │   ├── MinigameManager.java     # Core minigame logic
        │   ├── MinigameState.java       # Game state
        │   └── MinigameRenderer.java    # UI rendering
        └── mixin/
            ├── FishingBobberEntityMixin.java
            ├── FishingRodItemMixin.java
            ├── InGameHudMixin.java
            └── MinecraftClientMixin.java
```

### Technologies Used
- **Fabric Loader** 0.16.9
- **Fabric API** 0.114.0+1.21.4
- **Minecraft** 1.21.4
- **Java** 21
- **Mixin** for code injection

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Credits

- Inspired by [Stardew Valley](https://www.stardewvalley.net/) by ConcernedApe
- Built with [Fabric](https://fabricmc.net/)
- Created by justfatlard

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## Support

If you encounter any issues or have suggestions, please open an issue on GitHub.
