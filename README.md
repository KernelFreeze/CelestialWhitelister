# CelestialWhitelister

A Paper plugin that lets players whitelist themselves through a Discord `/whitelist` slash command. When someone uses the command, the plugin adds them to the Minecraft server whitelist and assigns a LuckPerms group based on their Discord roles.

## Requirements

- Paper 1.21.4+
- [LuckPerms](https://luckperms.net/)
- A Discord bot token ([how to create one](https://discord.com/developers/applications))

## Installation

1. Drop the plugin JAR into your server's `plugins/` folder.
2. Start the server once to generate the default `config.yml`.
3. Edit `plugins/CelestialWhitelister/config.yml` with your Discord bot token and role mappings.
4. Restart the server or run `/celestialwhitelister reload`.

Make sure your Discord bot has been invited to your guild with the `applications.commands` and `bot` scopes.

## How It Works

1. A member runs `/whitelist <nickname>` in your Discord server.
2. The plugin checks whether the member is allowed to use the command (channel and role restrictions, if enabled).
3. It looks up which LuckPerms group the member should receive based on their Discord roles and the `role-groups` mapping.
4. It resolves the Minecraft username to a UUID, adds the player to the server whitelist, and assigns the LuckPerms group.
5. The member receives a private (ephemeral) response in Discord confirming the result.

## Configuration

The config file is located at `plugins/CelestialWhitelister/config.yml`.

```yaml
# Your Discord bot token.
discord-token: "YOUR_BOT_TOKEN_HERE"

# Maps Discord role IDs to LuckPerms group names.
# Players receive the group for the first matching role they have.
role-groups:
  "123456789012345678": "vip"
  "987654321098765432": "member"

# Fallback group when the user has none of the mapped roles.
# Leave empty ("") to deny whitelisting if no role matches.
default-group: "default"

# Restrict the /whitelist command to specific Discord channels.
channel-restriction:
  enabled: false
  channels:
    - "111111111111111111"
    - "222222222222222222"

# Require users to have at least one of these Discord roles to use /whitelist.
# This is checked independently from role-groups.
required-roles:
  enabled: false
  roles:
    - "333333333333333333"
    - "444444444444444444"
```

### Options

| Option | Description |
|---|---|
| `discord-token` | The bot token from the Discord Developer Portal. |
| `role-groups` | A map of Discord role ID to LuckPerms group name. The first role the member has that appears in this map determines their group. |
| `default-group` | The LuckPerms group assigned when no role matches. Set to `""` to reject users with no matching role instead. |
| `channel-restriction.enabled` | When `true`, the `/whitelist` command only works in the listed channels. |
| `channel-restriction.channels` | List of Discord channel IDs where the command is allowed. |
| `required-roles.enabled` | When `true`, members must have at least one of the listed roles to use the command. |
| `required-roles.roles` | List of Discord role IDs required to use `/whitelist`. |

## Commands

### In-game

| Command | Alias | Description |
|---|---|---|
| `/celestialwhitelister reload` | `/cw reload` | Reloads the config and restarts the Discord bot. |

### Discord

| Command | Description |
|---|---|
| `/whitelist <nickname>` | Whitelist a Minecraft player and assign their LuckPerms group. |

## Permissions

| Permission | Description | Default |
|---|---|---|
| `celestialwhitelister.reload` | Allows using `/celestialwhitelister reload`. | OP |

## Building

Requires Java 21+.

```sh
./gradlew build
```

The compiled JAR will be in `build/libs/`.
