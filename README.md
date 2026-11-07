# PetNameFix

Prevents tamed pets from inheriting their owner's name tag properties on the client.

## Requirements

- Paper 26.2 (built against `26.2.build.135-stable`).
- Java 25 or newer.

Version 2.0.0 uses `paper-plugin.yml` and no longer supports Spigot or Minecraft 26.1.
The plugin uses Paper's API for pet ownership checks. Outgoing entity metadata still
requires Minecraft internals, supplied by the official `paperweight-userdev` dev
bundle; no manually installed server JAR is required. This packet integration must
be checked again when upgrading Minecraft.

## Build

```sh
./gradlew build
```

The build runs six packet checks against the Paper dev bundle, including immutable
metadata lists, unrelated metadata, bundles and completion of discarded writes.

Install `build/libs/PetNameFix-2.0.0.jar` in the server's `plugins` directory and restart.
The plugin keeps the existing `PetNameFix` identity and has no configuration or commands.

## Manual verification

On Paper 26.2, use a named, tamed wolf, cat or parrot and an owner whose scoreboard
team changes name tag visibility or color. Check that the pet name does not inherit
those properties, including after reconnecting and leaving/re-entering tracking
range. Check owner interactions with both hands and with another player.
