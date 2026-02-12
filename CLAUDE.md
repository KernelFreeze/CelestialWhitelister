# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CelestialWhitelister is a Paper (Minecraft) plugin written in Kotlin that bridges Discord and Minecraft server whitelisting. Users run a `/whitelist` slash command in Discord with a Minecraft username, and the plugin whitelists the player and assigns them a LuckPerms group based on their Discord roles.

## Build Commands

- **Build:** `./gradlew build` (produces a shadow JAR in `build/libs/`)
- **Run dev server:** `./gradlew runServer` (launches a Paper 1.21.4 test server with the plugin)
- **Clean:** `./gradlew clean`

The build task depends on `shadowJar`, which bundles all runtime dependencies (Kord, Ktor, MCCoroutine, Kotlin stdlib) with relocations to avoid classpath conflicts.

## Architecture

Two source files in `src/main/kotlin/dev/celestelove/whitelister/`:

- **CelestialWhitelister.kt** — Plugin entry point. Extends `SuspendingJavaPlugin` (MCCoroutine). Reads config, validates the Discord token, and launches the Discord bot on an IO dispatcher.
- **DiscordBot.kt** — Manages the Kord Discord client. Registers a global `/whitelist` chat command, handles the interaction by resolving the member's Discord roles to a LuckPerms group (via `role-groups` config mapping), then whitelists the player and assigns the group. Bukkit API calls run on the main thread via `plugin.minecraftDispatcher`.

## Key Dependencies

- **Paper API 1.21.4** (compile-only) — Minecraft server API
- **Kord 0.15.0** — Kotlin Discord bot library
- **MCCoroutine 2.22.0** — Bridges Kotlin coroutines with Bukkit's main thread scheduler
- **LuckPerms API 5.4** (compile-only) — Permission/group management

## Configuration

`src/main/resources/config.yml` — Discord bot token, role-to-LuckPerms-group mappings, and a default group fallback. The `paper-plugin.yml` declares LuckPerms as a required dependency.

## Technical Notes

- Java 21 toolchain target
- Gradle 8.8 with Kotlin DSL
- Shadow JAR relocates all bundled dependencies under `dev.celestelove.whitelister.libs.*`
- `processResources` expands `${version}` in `paper-plugin.yml`
