package dev.celestelove.whitelister

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.string
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.node.types.InheritanceNode
import org.bukkit.plugin.java.JavaPlugin
import java.net.HttpURLConnection
import java.net.URI
import java.util.UUID

class DiscordBot(
    private val plugin: JavaPlugin,
    private val token: String,
    private val roleGroups: Map<String, String>,
    private val defaultGroup: String,
    private val allowedChannels: Set<String>,
    private val requiredRoles: Set<String>,
    private val onlineMode: Boolean,
    private val forceOnlineUUIDs: Boolean,
    private val oneTimeWhitelist: Boolean,
    private val database: WhitelistDatabase?,
) {
    private var kord: Kord? = null

    suspend fun start() {
        val kord = Kord(token)
        this.kord = kord

        kord.createGlobalChatInputCommand(
            name = "whitelist",
            description = "Whitelist a Minecraft player on the server"
        ) {
            string("nickname", "The Minecraft username to whitelist") {
                required = true
            }
        }

        kord.createGlobalChatInputCommand(
            name = "changenick",
            description = "Change your whitelisted Minecraft nickname"
        ) {
            string("nickname", "The new Minecraft username") {
                required = true
            }
        }

        kord.on<ChatInputCommandInteractionCreateEvent> {
            when (interaction.command.rootName) {
                "whitelist" -> handleWhitelist(this)
                "changenick" -> handleChangeNick(this)
            }
        }

        plugin.logger.info("Discord bot connected!")
        kord.login()
    }

    private suspend fun handleWhitelist(event: ChatInputCommandInteractionCreateEvent) {
        val interaction = event.interaction

        val nickname = interaction.command.strings["nickname"]!!

        // Channel restriction check
        if (allowedChannels.isNotEmpty()) {
            val channelId = interaction.channelId.toString()
            if (channelId !in allowedChannels) {
                interaction.respondEphemeral {
                    content = "The /whitelist command cannot be used in this channel."
                }
                return
            }
        }

        val member = interaction.data.member.value
        if (member == null) {
            interaction.respondEphemeral { content = "This command can only be used in a server." }
            return
        }

        val discordId = interaction.user.id

        // One-time whitelist check
        if (oneTimeWhitelist && database != null) {
            val existing = database.getEntry(discordId)
            if (existing != null) {
                interaction.respondEphemeral {
                    content = "You have already whitelisted **${existing.minecraftNickname}**. Use /changenick to change your nickname."
                }
                return
            }
        }

        val memberRoleIds = member.roles.map { it.toString() }

        // Required roles check
        if (requiredRoles.isNotEmpty()) {
            if (memberRoleIds.none { it in requiredRoles }) {
                interaction.respondEphemeral {
                    content = "You don't have the required role to use this command."
                }
                return
            }
        }

        // Find the matching LuckPerms group from the member's Discord roles
        val matchedGroup = memberRoleIds.firstNotNullOfOrNull { roleId ->
            roleGroups[roleId]
        } ?: defaultGroup.ifEmpty { null }

        if (matchedGroup == null) {
            interaction.respondEphemeral {
                content = "You don't have a Discord role that permits whitelisting."
            }
            return
        }

        // Run Bukkit API calls on the main thread via MCCoroutine
        val result = withContext(plugin.minecraftDispatcher) {
            whitelistPlayer(nickname, matchedGroup)
        }

        // Record in database if whitelist succeeded (message contains "Whitelisted")
        if (oneTimeWhitelist && database != null && result.startsWith("Whitelisted")) {
            val uuid = withContext(plugin.minecraftDispatcher) { resolveUUID(nickname) }
            if (uuid != null) {
                database.insertEntry(discordId, nickname, matchedGroup, uuid)
            }
        }

        interaction.respondEphemeral { content = result }
    }

    private suspend fun handleChangeNick(event: ChatInputCommandInteractionCreateEvent) {
        val interaction = event.interaction

        val newNickname = interaction.command.strings["nickname"]!!

        // Channel restriction check
        if (allowedChannels.isNotEmpty()) {
            val channelId = interaction.channelId.toString()
            if (channelId !in allowedChannels) {
                interaction.respondEphemeral {
                    content = "The /changenick command cannot be used in this channel."
                }
                return
            }
        }

        val member = interaction.data.member.value
        if (member == null) {
            interaction.respondEphemeral { content = "This command can only be used in a server." }
            return
        }

        val discordId = interaction.user.id

        if (database == null) {
            interaction.respondEphemeral {
                content = "The nickname change feature is not enabled."
            }
            return
        }

        val existing = database.getEntry(discordId)
        if (existing == null) {
            interaction.respondEphemeral {
                content = "You haven't whitelisted a player yet. Use /whitelist first."
            }
            return
        }

        if (existing.minecraftNickname.equals(newNickname, ignoreCase = true)) {
            interaction.respondEphemeral {
                content = "That's already your whitelisted nickname."
            }
            return
        }

        val memberRoleIds = member.roles.map { it.toString() }

        // Required roles check
        if (requiredRoles.isNotEmpty()) {
            if (memberRoleIds.none { it in requiredRoles }) {
                interaction.respondEphemeral {
                    content = "You don't have the required role to use this command."
                }
                return
            }
        }

        // Resolve the new group from current Discord roles
        val newGroup = memberRoleIds.firstNotNullOfOrNull { roleId ->
            roleGroups[roleId]
        } ?: defaultGroup.ifEmpty { null }

        if (newGroup == null) {
            interaction.respondEphemeral {
                content = "You don't have a Discord role that permits whitelisting."
            }
            return
        }

        val result = withContext(plugin.minecraftDispatcher) {
            changeWhitelistedPlayer(existing, newNickname, newGroup)
        }

        if (result.startsWith("Changed")) {
            val newUuid = withContext(plugin.minecraftDispatcher) { resolveUUID(newNickname) }
            if (newUuid != null) {
                database.updateEntry(discordId, newNickname, newGroup, newUuid)
            }
        }

        interaction.respondEphemeral { content = result }
    }

    @Serializable
    data class MojangProfile(val id: String, val name: String) {
        fun uuid(): UUID {
            val formatted = id.replaceFirst(
                Regex("(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})"),
                "$1-$2-$3-$4-$5"
            )
            return UUID.fromString(formatted)
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun getOnlineUUID(name: String): UUID? {
        val url = URI("https://api.mojang.com/users/profiles/minecraft/$name").toURL()
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"

        if (connection.responseCode != 200) return null

        val body = connection.inputStream.bufferedReader().readText()
        val profile = json.decodeFromString<MojangProfile>(body)
        return profile.uuid()
    }

    fun getOfflineUUID(name: String): UUID? {
        val server = plugin.server

        val profile = server.createProfile(name)
        profile.complete(true, false)

        return profile.id
    }

    private fun resolveUUID(nickname: String): UUID? {
        return if (onlineMode) {
            getOnlineUUID(nickname)
        } else if (forceOnlineUUIDs) {
            getOnlineUUID(nickname) ?: getOfflineUUID(nickname)
        } else {
            getOfflineUUID(nickname)
        }
    }

    private fun whitelistPlayer(nickname: String, groupName: String): String {
        val server = plugin.server
        val uuid = resolveUUID(nickname) ?: return "Could not find Minecraft player **$nickname**."

        val offlinePlayer = server.getOfflinePlayer(uuid)
        offlinePlayer.isWhitelisted = true

        CelestialWhitelister.LOGGER.info { "'$nickname' (UUID: $uuid was whitelisted." }

        return try {
            val luckPerms = LuckPermsProvider.get()
            val user = luckPerms.userManager.loadUser(uuid).join()
            val group = luckPerms.groupManager.loadGroup(groupName).join().orElse(null)

            if (group == null) {
                CelestialWhitelister.LOGGER.error { "$groupName does not exist. Can't add the whitelisted user to that group." }
                "Whitelisted **$nickname**, but LuckPerms group `$groupName` does not exist."
            } else {
                val node = InheritanceNode.builder(group).build()
                user.data().add(node)
                luckPerms.userManager.saveUser(user).join()

                CelestialWhitelister.LOGGER.info { "Added $nickname to group $groupName." }
                "Whitelisted **$nickname** and added to group `$groupName`."
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to assign LuckPerms group: ${e.message}")
            "Whitelisted **$nickname**, but failed to assign LuckPerms group: `${e.message}`"
        }
    }

    private fun changeWhitelistedPlayer(existing: WhitelistEntry, newNickname: String, newGroup: String): String {
        val server = plugin.server

        // Remove old player from whitelist and LuckPerms group
        val oldPlayer = server.getOfflinePlayer(existing.uuid)
        oldPlayer.isWhitelisted = false

        try {
            val luckPerms = LuckPermsProvider.get()
            val oldUser = luckPerms.userManager.loadUser(existing.uuid).join()
            val oldGroup = luckPerms.groupManager.loadGroup(existing.luckpermsGroup).join().orElse(null)
            if (oldGroup != null) {
                val oldNode = InheritanceNode.builder(oldGroup).build()
                oldUser.data().remove(oldNode)
                luckPerms.userManager.saveUser(oldUser).join()
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to remove old LuckPerms group: ${e.message}")
        }

        CelestialWhitelister.LOGGER.info { "Removed '${existing.minecraftNickname}' (UUID: ${existing.uuid}) from whitelist." }

        // Add new player
        val newUuid = resolveUUID(newNickname)
            ?: return "Removed old player from whitelist, but could not find Minecraft player **$newNickname**."

        val newPlayer = server.getOfflinePlayer(newUuid)
        newPlayer.isWhitelisted = true

        CelestialWhitelister.LOGGER.info { "'$newNickname' (UUID: $newUuid) was whitelisted." }

        return try {
            val luckPerms = LuckPermsProvider.get()
            val user = luckPerms.userManager.loadUser(newUuid).join()
            val group = luckPerms.groupManager.loadGroup(newGroup).join().orElse(null)

            if (group == null) {
                CelestialWhitelister.LOGGER.error { "$newGroup does not exist. Can't add the whitelisted user to that group." }
                "Changed nickname to $newNickname, but LuckPerms group $newGroup does not exist."
            } else {
                val node = InheritanceNode.builder(group).build()
                user.data().add(node)
                luckPerms.userManager.saveUser(user).join()

                CelestialWhitelister.LOGGER.info { "Added '$newNickname' to group '$newGroup'." }
                "Changed whitelisted player from ${existing.minecraftNickname} to $newNickname (group: $newGroup)."
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to assign LuckPerms group: ${e.message}")
            "Changed nickname to $newNickname, but failed to assign LuckPerms group: ${e.message}"
        }
    }

    suspend fun stop() {
        kord?.shutdown()
        kord = null
    }
}
