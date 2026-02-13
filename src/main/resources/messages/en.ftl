# Discord command responses
whitelist-wrong-channel = The /whitelist command cannot be used in this channel.
changenick-wrong-channel = The /changenick command cannot be used in this channel.
command-guild-only = This command can only be used in a server.
already-whitelisted = You have already whitelisted **{ $nickname }**. Use /changenick to change your nickname.
missing-required-role = You don't have the required role to use this command.
no-whitelisting-role = You don't have a Discord role that permits whitelisting.
changenick-not-enabled = The nickname change feature is not enabled.
no-existing-whitelist = You haven't whitelisted a player yet. Use /whitelist first.
same-nickname = That's already your whitelisted nickname.

# Whitelist results
player-not-found = Could not find Minecraft player **{ $nickname }**.
whitelist-success = Whitelisted **{ $nickname }** and added to group `{ $group }`.
whitelist-success-group-missing = Whitelisted **{ $nickname }**, but LuckPerms group `{ $group }` does not exist.
whitelist-success-group-error = Whitelisted **{ $nickname }**, but failed to assign LuckPerms group: `{ $error }`.

# Change nick results
changenick-old-not-found = Removed old player from whitelist, but could not find Minecraft player **{ $nickname }**.
changenick-success = Changed whitelisted player from { $oldNickname } to { $newNickname } (group: { $group }).
changenick-success-group-missing = Changed nickname to { $nickname }, but LuckPerms group { $group } does not exist.
changenick-success-group-error = Changed nickname to { $nickname }, but failed to assign LuckPerms group: { $error }.

# In-game messages
reload-start = Reloading CelestialWhitelister...
reload-success = CelestialWhitelister reloaded successfully!
reload-failure = Failed to reload. Check console for errors.
