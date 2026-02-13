# Contributing to Celestial Whitelister

## Tech Stack

- **Language:** Kotlin (plugin logic) + Java (classloader)
- **Platform:** Paper 1.21.4 (`paper-api`)
- **Build:** Gradle with Kotlin DSL
- **Java target:** 21
- **Discord library:** [Kord](https://github.com/kordlib/kord) 0.15.0
- **Permissions:** [LuckPerms API](https://luckperms.net/) 5.4
- **Async:** [MCCoroutine](https://github.com/Shynixn/MCCoroutine) 2.22.0 (bridges Kotlin coroutines with the Bukkit scheduler)

## Project Structure

```
src/
  main/
    java/dev/celestelove/whitelister/
      CelestialWhitelisterClassLoader.java   # Paper PluginLoader — resolves runtime deps
    kotlin/dev/celestelove/whitelister/
      CelestialWhitelister.kt                # Plugin entry point — lifecycle, config, commands
      DiscordBot.kt                          # Discord bot — slash command, whitelist logic
    resources/
      paper-plugin.yml                       # Paper plugin descriptor
      config.yml                             # Default config shipped in the JAR

build.gradle.kts       # Build script, dependency declarations, paper-libraries generation
settings.gradle.kts    # Root project name
```

There are only three source files. Each one has a single, well-scoped responsibility described below.

## Source Files

### `CelestialWhitelisterClassLoader.java`

Paper's `PluginLoader` implementation. Written in Java because the Kotlin stdlib is not yet on the classpath when this class runs.

At load time it reads `paper-libraries.txt` (a generated file baked into the JAR at build time) and feeds each Maven coordinate to a `MavenLibraryResolver`. Paper then downloads the artifacts from Maven Central and adds them to the plugin's classpath. All transitive dependencies are excluded (`EXCLUDE_ALL`) to avoid conflicts with Paper internals — only the exact artifacts listed in the file are loaded.

This is the mechanism that brings Kord, MCCoroutine, and the Kotlin stdlib onto the classpath before the Kotlin-based plugin class is instantiated.

### `CelestialWhitelister.kt`

The main plugin class. Extends `SuspendingJavaPlugin` from MCCoroutine, which means lifecycle methods (`onEnableAsync`, `onDisableAsync`) are suspending functions that run inside a coroutine scope tied to the plugin.

**Startup (`onEnableAsync`):**
1. Saves the default `config.yml` if it doesn't exist.
2. Registers the `/celestialwhitelister reload` command (alias `/cw`) using Paper's Brigadier lifecycle API.
3. Reads config values (token, role mappings, channel/role restrictions) and constructs a `DiscordBot`.
4. Launches the bot on `Dispatchers.IO`. If the token is missing or still the placeholder, the plugin disables itself.

**Reload (`performReload`):**
Stops the running bot, calls `reloadConfig()` to re-read the YAML from disk, then starts a fresh `DiscordBot` with the new values.

**Shutdown (`onDisableAsync`):**
Stops the bot gracefully via `kord.shutdown()`.

### `DiscordBot.kt`

Contains all Discord interaction and whitelisting logic.

**`start()`** — Connects to Discord with Kord, registers a global `/whitelist` slash command with a required `nickname` string parameter, and installs an event handler for incoming command interactions.

**`handleWhitelist(event)`** — The event handler. Runs these checks in order:
1. **Channel restriction** — If `allowedChannels` is non-empty, reject commands from unlisted channels.
2. **Guild membership** — The command must come from a guild (not a DM).
3. **Required roles** — If `requiredRoles` is non-empty, the member must have at least one.
4. **Group resolution** — Iterates the member's Discord role IDs and returns the first match found in `roleGroups`. Falls back to `defaultGroup`. If both are empty, the request is denied.
5. **Whitelisting** — Dispatches to the main Minecraft thread via `plugin.minecraftDispatcher` and calls `whitelistPlayer`.

**`whitelistPlayer(nickname, groupName)`** — Runs on the main thread. Resolves the Minecraft username to a UUID via `server.createProfile().complete()`, sets `isWhitelisted = true` on the `OfflinePlayer`, then loads the LuckPerms user and adds an `InheritanceNode` for the resolved group. Returns a human-readable result string that is sent back as an ephemeral Discord response.

**`stop()`** — Calls `kord.shutdown()` and clears the reference.

## Build Pipeline

The build has a custom `generatePaperLibraries` task that resolves the `paperLibrary` configuration and writes every artifact's Maven coordinate (`group:name:version`) to `build/generated-resources/paper-libraries.txt`. The `processResources` task depends on it, so the file is always up to date and included in the JAR. This is what `CelestialWhitelisterClassLoader` reads at runtime.

`processResources` also expands `${version}` in `paper-plugin.yml` so the plugin version stays in sync with `build.gradle.kts`.

### Useful Gradle tasks

```sh
./gradlew build          # Compile and package the JAR
./gradlew runServer      # Start a local Paper 1.21.4 dev server with the plugin
```

## Threading Model

- **IO dispatcher** — The Discord bot (`kord.login()`) blocks on a long-lived websocket, so it runs on `Dispatchers.IO`.
- **Minecraft main thread** — All Bukkit/Paper API calls (`createProfile`, `isWhitelisted`, `getOfflinePlayer`) and LuckPerms user operations happen on `plugin.minecraftDispatcher`, which MCCoroutine routes to the server's main tick thread.
- **Kord event handler** — `handleWhitelist` starts on Kord's coroutine context, then uses `withContext(plugin.minecraftDispatcher)` to switch to the main thread for the Minecraft operations before returning to respond ephemerally.

## Adding a New Feature — General Guidance

- **New config option:** Add it to `resources/config.yml` with a comment, read it in `CelestialWhitelister.startBot()`, and pass it through to `DiscordBot` via the constructor.
- **New Discord command:** Register it in `DiscordBot.start()` alongside the existing `/whitelist` command and add a handler in the `kord.on<ChatInputCommandInteractionCreateEvent>` block (dispatch on `interaction.command.rootName`).
- **New in-game command:** Add a `.then(Commands.literal("subcommand") ...)` branch inside `registerCommands()` in `CelestialWhitelister.kt`.
- **New runtime dependency:** Add it as a `paperLibrary(...)` dependency in `build.gradle.kts`. The classloader pipeline will pick it up automatically.
