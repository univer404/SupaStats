# SupaStats

SupaStats converts the supplied statistics Skript into a Paper/Purpur plugin for Minecraft 1.21.11.

## Features

- `/statistics [player]` and `/stats [player]`
- Configurable statistics GUI; edit rows/title in `config.yml` and item slots/materials/names/lore in `gui.yml`
- Toggle the GUI using `settings.gui-enabled` in `config.yml`
- Tracks kills, deaths, placed blocks, broken blocks, and mobs killed
- YAML persistence in `stats.yml` and cached offline placeholders in `offline.yml`
- PlaceholderAPI: `%skstatistics_kills%`, `%skstatistics_deaths%`, `%skstatistics_blocks_placed%`, `%skstatistics_blocks_broken%`, `%skstatistics_mobs_killed%`
- RGB/MiniMessage colors and legacy ampersand colors; italics are disabled by default
- Java 21; targets Paper/Purpur 1.21.11

## Dependencies

- Paper or Purpur 1.21.11
- PlaceholderAPI (optional for custom stats placeholders; required for money, shards, playtime and sell values)

## Install

1. Download `SupaStats.jar` from the GitHub Actions artifact after a successful build.
2. Put it in the server's `plugins` folder.
3. Start the server to generate configuration files.
4. Edit `plugins/SupaStats/config.yml` and `gui.yml`, then restart the server.

Permission `supastats.view` defaults to true. `supastats.view.others` defaults to operators.

## Build locally

Use Java 21 and Maven:

```sh
mvn clean package
```

The JAR is created at `target/SupaStats.jar`.

## Notes

Offline money/shards/playtime/spending values are snapshots from the player's last quit because PlaceholderAPI expansions generally require an online player context. They refresh on quit and clear on join, matching the supplied Skript's behavior.
