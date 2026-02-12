package dev.celestelove.whitelister

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import dev.kord.core.Kord
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import dev.kord.rest.builder.interaction.string
import kotlinx.coroutines.withContext
import net.luckperms.api.LuckPermsProvider
import net.luckperms.api.node.types.InheritanceNode
import org.bukkit.plugin.java.JavaPlugin

class DiscordBot(
    private val plugin: JavaPlugin,
    private val token: String,
    private val roleGroups: Map<String, String>,
    private val defaultGroup: String
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

        val member = interaction.data.member.value
        if (member == null) {
            interaction.respondEphemeral { content = "This command can only be used in a server." }
            return
        }

        val memberRoleIds = member.roles.map { it.toString() }

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

    private fun whitelistPlayer(nickname: String, groupName: String): String {
        val server = plugin.server
        val profile = server.createProfile(nickname)
        profile.complete(true)

        val uuid = profile.id ?: return "Could not find Minecraft player **$nickname**."

        val offlinePlayer = server.getOfflinePlayer(uuid)
        offlinePlayer.isWhitelisted = true

        return try {
            val luckPerms = LuckPermsProvider.get()
            val user = luckPerms.userManager.loadUser(uuid).join()
            val group = luckPerms.groupManager.loadGroup(groupName).join().orElse(null)

            if (group == null) {
                "Whitelisted **$nickname**, but LuckPerms group `$groupName` does not exist."
            } else {
                val node = InheritanceNode.builder(group).build()
                user.data().add(node)
                luckPerms.userManager.saveUser(user).join()
                "Whitelisted **$nickname** and added to group `$groupName`."
            }
        } catch (e: Exception) {
            plugin.logger.warning("Failed to assign LuckPerms group: ${e.message}")
            "Whitelisted **$nickname**, but failed to assign LuckPerms group: ${e.message}"
        }
    }

    suspend fun stop() {
        kord?.shutdown()
        kord = null
    }
}
