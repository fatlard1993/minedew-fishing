# Quick Start Guide - Minedew Fishing

## For Players

### Installation
1. Download and install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.4
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) 0.114.0+ for 1.21.4
3. Build this mod (see Development Setup) or download the release
4. Place both `.jar` files in your `.minecraft/mods` folder
5. Launch Minecraft with the Fabric profile

### How to Play
1. **Cast your fishing rod** into any body of water
2. **Wait a few seconds** - the minigame will automatically appear when a fish bites
3. **Control the green bar**:
   - Click or hold to make it rise
   - Release to let it fall
4. **Keep the fish icon** (orange) inside the green bar
5. **Watch the progress bar** on the right:
   - Fills when fish is captured
   - Drains when fish escapes
6. **Win condition**: Fill progress to 100%
7. **Lose condition**: Progress reaches 0% (after 5 seconds of gameplay)

### Tips
- Easy fish (★) move slowly - great for learning
- Hard fish (★★★) dart around - requires quick reflexes
- Legendary fish (★★★★) are extremely challenging
- Rain spawns legendary fish in oceans and rivers
- Night time spawns special fish in deep oceans
- Different biomes have different fish

---

## For Developers

### Development Setup

#### Prerequisites
- Java Development Kit (JDK) 21
- Git

#### Clone and Build
```bash
# Clone the repository
git clone https://github.com/yourusername/minedew-fishing.git
cd minedew-fishing

# Build the mod
./gradlew build

# Output will be in build/libs/minedew-fishing-1.0.0.jar
```

#### Run in Development
```bash
# Run Minecraft client with mod loaded
./gradlew runClient

# Run dedicated server with mod loaded
./gradlew runServer
```

#### Generate Minecraft Sources (for IDE)
```bash
# Generate sources for IntelliJ IDEA
./gradlew genSources

# Then import as Gradle project in your IDE
```

### Project Structure
```
src/
├── main/                          # Common code (both sides)
│   ├── java/
│   │   └── com/minedew/fishing/
│   │       ├── MinedewFishing.java              # Main mod initializer
│   │       ├── MinedewFishingClient.java        # Client initializer
│   │       ├── fish/                            # Fish system
│   │       │   ├── FishType.java               # Fish definitions & spawning
│   │       │   ├── FishMovementPattern.java    # Movement types
│   │       │   └── FishBehavior.java           # Fish AI logic
│   │       ├── client/                          # Client-side minigame
│   │       │   ├── MinigameState.java          # Game state & physics
│   │       │   ├── MinigameManager.java        # Controller & input
│   │       │   └── MinigameRenderer.java       # UI rendering
│   │       └── network/                         # Network packets
│   │           ├── FishingMinigameStartPayload.java
│   │           └── FishingMinigameEndPayload.java
│   └── resources/
│       ├── fabric.mod.json                      # Mod metadata
│       ├── minedew-fishing.mixins.json         # Mixin config
│       └── assets/minedew-fishing/
│           └── lang/en_us.json                 # Translations
└── client/                        # Client-only code
    └── java/com/minedew/fishing/mixin/
        ├── FishingBobberEntityMixin.java       # Intercepts fishing logic
        ├── FishingRodItemMixin.java            # Prevents vanilla rod behavior
        ├── InGameHudMixin.java                 # Renders UI overlay
        └── MinecraftClientMixin.java           # Updates minigame logic
```

### Key Files to Edit

#### Adding a New Fish
Edit `src/main/java/com/minedew/fishing/fish/FishType.java`:
```java
// Add to enum
NEW_FISH("New Fish", 2, 0.7f, 0.5f, FishMovementPattern.MODERATE_JUMPY),

// Add to getRandomFish() method for biome spawning
if (biome.isIn(BiomeTags.IS_OCEAN)) {
    availableFish.add(NEW_FISH);
}
```

#### Creating a New Movement Pattern
1. Add to `FishMovementPattern.java`:
   ```java
   MY_CUSTOM_PATTERN
   ```

2. Add handler in `FishBehavior.java`:
   ```java
   case MY_CUSTOM_PATTERN:
       updateMyCustomPattern();
       break;
   ```

3. Implement the update method:
   ```java
   private void updateMyCustomPattern() {
       // Your custom fish movement logic
   }
   ```

#### Adjusting Physics
Edit constants in `MinigameState.java`:
```java
private static final float CAPTURE_BAR_HEIGHT = 0.15f;       // Bar size
private static final float BAR_RISE_ACCELERATION = 0.003f;   // Rise speed
private static final float BAR_GRAVITY = 0.002f;             // Fall speed
private static final float PROGRESS_GAIN_RATE = 0.008f;      // Win speed
private static final float PROGRESS_LOSS_RATE = 0.012f;      // Lose speed
```

#### Customizing UI
Edit `MinigameRenderer.java`:
- Change colors (COLOR_* constants)
- Adjust positions (BAR_X_OFFSET, etc.)
- Modify rendering logic in `render()` method

### Testing

#### Local Testing
```bash
# Run client
./gradlew runClient

# Test in single-player world
# 1. Create new world
# 2. Get fishing rod: /give @s fishing_rod
# 3. Find water and test fishing
```

#### Debug Logging
Add to any class:
```java
import com.minedew.fishing.MinedewFishing;

MinedewFishing.LOGGER.info("Debug message");
MinedewFishing.LOGGER.error("Error message");
```

### Common Issues

#### Mixin Not Applying
- Check `src/main/resources/minedew-fishing.mixins.json`
- Ensure mixin class is in correct package
- Verify method signatures match target class
- Run `./gradlew clean build`

#### Gradle Build Fails
```bash
# Clear caches
./gradlew clean
rm -rf .gradle build

# Rebuild
./gradlew build
```

#### IDE Not Recognizing Minecraft Classes
```bash
# Regenerate sources
./gradlew cleanIdea
./gradlew genSources
./gradlew idea

# Then reimport project
```

### Build Commands

```bash
# Clean build
./gradlew clean build

# Build without tests
./gradlew build -x test

# Run client
./gradlew runClient

# Run server
./gradlew runServer

# Generate sources
./gradlew genSources

# Publish to maven local
./gradlew publishToMavenLocal
```

### Publishing

#### Before Release
1. Update version in `gradle.properties`
2. Update changelog
3. Test thoroughly
4. Build release: `./gradlew build`

#### GitHub Release
1. Create git tag: `git tag v1.0.0`
2. Push tag: `git push origin v1.0.0`
3. Upload `build/libs/minedew-fishing-1.0.0.jar` to release

### Contributing

#### Code Style
- Follow existing patterns
- Use descriptive variable names
- Add comments for complex logic
- Keep methods focused and small
- No magic numbers - use constants

#### Pull Request Checklist
- [ ] Code builds without errors
- [ ] Tested in-game
- [ ] No console errors/warnings
- [ ] Follows existing code style
- [ ] Updated documentation if needed

### Support & Resources

- [Fabric Wiki](https://fabricmc.net/wiki/)
- [Fabric Discord](https://discord.gg/v6v4pMv)
- [Minecraft Mappings](https://fabricmc.net/wiki/tutorial:mappings)
- [Mixin Documentation](https://github.com/SpongePowered/Mixin/wiki)

### License
MIT License - see LICENSE file
