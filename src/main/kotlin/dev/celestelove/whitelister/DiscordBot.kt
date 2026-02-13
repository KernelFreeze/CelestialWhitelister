package dev.celestelove.whitelister

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import com.google.gson.Gson
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.string
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
    private val forceOnlineUUIDs: Boolean
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

        kord.on<ChatInputCommandInteractionCreateEvent> {
            handleWhitelist(this)
        }

        plugin.logger.info("Discord bot connected!")
        kord.login()
    }

    private suspend fun handleWhitelist(event: ChatInputCommandInteractionCreateEvent) {
        val interaction = event.interaction
        if (interaction.command.rootName != "whitelist") return

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
                "Whitelisted $nickname, but LuckPerms group $groupName does not exist."
            } else {
                val node = InheritanceNode.builder(group).build()
                user.data().add(node)
                luckPerms.userManager.saveUser(user).join()

                CelestialWhitelister.LOGGER.info { "Added **$nickname** to group `$groupName`." }
                "Whitelisted $nickname and added to group $groupName."
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to assign LuckPerms group: ${e.message}")
            "Whitelisted $nickname, but failed to assign LuckPerms group: ${e.message}"
        }
    }

    suspend fun stop() {
        kord?.shutdown()
        kord = null
    }
}
