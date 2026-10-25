# SupaStats

SupaStats converts the supplied statistics Skript into a Paper/Purpur plugin for Minecraft 1.21.11.

## Features

- `/statistics [player]` and `/stats [player]`
- Configurable statistics GUI; edit rows/title in `config.yml` and item slots/materials/names/lore in `gui.yml`
- Toggle the GUI using `settings.gui-enabled` in `config.yml`
- Tracks kills, deaths, placed blocks, broken blocks, and mobs killed
- YAML persistence in `stats.yml` and cached offline placeholders in `offline.yml`
- PlaceholderAPI: `%statistics_kills%`, `%statistics_deaths%`, `%statistics_blocks_placed%`, `%statistics_blocks_broken%`, `%statistics_mobs_killed%`
- RGB/MiniMessage colors and legacy ampersand colors; italics are disabled by default
- Java 21; targets Paper/Purpur 1.21.11

## Dependencies

- Paper or Purpur 1.21.11
- Vault and a Vault-compatible economy plugin (required for money balance)
- PlaceholderAPI (optional; used for shards and playtime)

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

Money balance is read through Vault and the active economy provider. SellGUI and ShopGUI placeholders are not used. Shards and playtime still use PlaceholderAPI. Offline playtime/shards values are snapshots from the player's last quit because PlaceholderAPI expansions generally require an online player context. They refresh on quit and clear on join. Playtime uses `%statistic_time_played%`.
