# Continue Button Continued

A lightweight **Fabric client-side mod** that adds a compact **Continue** button to Minecraft's main menu.

Continue Button Continued remembers the last singleplayer world or multiplayer server you joined, then lets you jump
back in with one click from the title screen.

![Continue Button Continued screenshot](screenshot.png)

## Features

- Adds a compact **Continue** button to the main menu.
- Opens the last played singleplayer world automatically.
- Reconnects to the last joined multiplayer server automatically.
- Falls back to the world/server selection screen when the saved target is missing.
- Shows helpful tooltips for the saved world or server.
- Pings the last multiplayer server before showing its status tooltip.
- Migrates legacy `continuebutton` configuration to `continuebuttoncontinued` when possible.
- Client-side only; servers do not need to install this mod.
- Includes translations for multiple languages.

## Compatibility

| Component | Version |
| --- | --- |
| Minecraft | `26.1.2` |
| Java | `25` or newer |
| Fabric Loader | `0.19.3` or newer |
| Fabric API | `0.154.0+26.1.2` or compatible |
| Gradle | `9.6.1` wrapper |

This branch targets Minecraft `26.1.2`, which uses Fabric's non-remapped 26.1+ development workflow. The Gradle build
uses `net.fabricmc.fabric-loom`, not `net.fabricmc.fabric-loom-remap`.

## Installation

1. Install Fabric Loader for Minecraft `26.1.2`.
2. Install Fabric API for Minecraft `26.1.2`.
3. Put the Continue Button Continued `.jar` file into your `.minecraft/mods` folder.
4. Start the game and use the **Continue** button on the main menu.

## Configuration

The mod stores its last target in:

```text
.minecraft/config/continuebuttoncontinued/config.properties
```

Older Continue Button installations used:

```text
.minecraft/config/continuebutton/config.properties
```

On first launch, Continue Button Continued attempts to copy the legacy configuration into the new location so existing
users can keep their last saved world or server.

## Building from source

Use Java 25 and the included Gradle wrapper.

```bash
./gradlew clean build
```

On Windows:

```powershell
.\gradlew.bat clean build
```

The built mod jar will be generated in:

```text
build/libs/
```

## Development notes

- Minecraft `26.1.2` uses the 26.1+ Fabric workflow, so this project does not declare Yarn mappings.
- Fabric dependencies are managed through `gradle/libs.versions.toml`.
- The main-menu injection targets `TitleScreen#init` instead of a private menu-building method, which makes the layout
  logic less fragile across future Minecraft updates.
- The mod keeps its config path stable as `continuebuttoncontinued` and still migrates the old `continuebutton` config
  when possible.

## Credits

Continue Button Continued is a continued fork of **[Continue Button](https://github.com/umollu/continue-button)** by
**umollu**.

Maintained by **Likos-Lupus** and **Chiloven945**.

## License

This project is licensed under the MIT License.

The original Continue Button copyright notice is preserved in [`LICENSE`](LICENSE), and the continued project copyright
notice has been added there as well.
