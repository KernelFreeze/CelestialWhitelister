package dev.celestelove.whitelister

import com.github.shynixn.mccoroutine.bukkit.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.bukkit.launch
import com.mojang.brigadier.Command
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender

@Suppress("UnstableApiUsage")
class CelestialWhitelister : SuspendingJavaPlugin() {

    private var discordBot: DiscordBot? = null

    override suspend fun onEnableAsync() {
        saveDefaultConfig()
        registerCommands()

        if (!startBot()) {
            server.pluginManager.disablePlugin(this)
            return
        }
    }

    private fun registerCommands() {
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { event ->
            val commands = event.registrar()
            commands.register(
                Commands.literal("celestialwhitelister")
                    .requires { it.sender.hasPermission("celestialwhitelister.reload") }
                    .then(
                        Commands.literal("reload")
                            .executes { ctx ->
                                val sender = ctx.source.sender
                                sender.sendMessage(
                                    Component.text("Reloading CelestialWhitelister...", NamedTextColor.YELLOW)
                                )
                                this@CelestialWhitelister.launch {
                                    performReload(sender)
                                }
                                Command.SINGLE_SUCCESS
                            }
                    )
                    .build(),
                "CelestialWhitelister admin commands",
                listOf("cw")
            )
        }
    }

    private suspend fun performReload(sender: CommandSender) {
        discordBot?.stop()
        discordBot = null
        reloadConfig()

        if (startBot()) {
            sender.sendMessage(
                Component.text("CelestialWhitelister reloaded successfully!", NamedTextColor.GREEN)
            )
        } else {
            sender.sendMessage(
                Component.text("Failed to reload. Check console for errors.", NamedTextColor.RED)
            )
        }
    }

    private fun startBot(): Boolean {
        val token = config.getString("discord-token")
        if (token.isNullOrBlank() || token == "YOUR_BOT_TOKEN_HERE") {
            logger.severe("Discord bot token is not configured! Please set it in config.yml")
            return false
        }

        val roleGroups = mutableMapOf<String, String>()
        val roleSection = config.getConfigurationSection("role-groups")
        if (roleSection != null) {
            for (key in roleSection.getKeys(false)) {
                val group = roleSection.getString(key)
                if (group != null) {
                    roleGroups[key] = group
                }
            }
        }

        val defaultGroup = config.getString("default-group", "default") ?: "default"

        val allowedChannels = if (config.getBoolean("channel-restriction.enabled", false)) {
            config.getStringList("channel-restriction.channels").toSet()
        } else {
            emptySet()
        }

        val requiredRoles = if (config.getBoolean("required-roles.enabled", false)) {
            config.getStringList("required-roles.roles").toSet()
        } else {
            emptySet()
        }

        logger.info("Loaded ${roleGroups.size} role-to-group mappings")
        if (allowedChannels.isNotEmpty()) logger.info("Channel restriction enabled: ${allowedChannels.size} channel(s)")
        if (requiredRoles.isNotEmpty()) logger.info("Role requirement enabled: ${requiredRoles.size} role(s)")

        val bot = DiscordBot(this, token, roleGroups, defaultGroup, allowedChannels, requiredRoles)
        discordBot = bot

        this.launch {
            withContext(Dispatchers.IO) {
                try {
                    bot.start()
                } catch (e: Exception) {
                    logger.severe("Failed to start Discord bot: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        return true
    }

    override suspend fun onDisableAsync() {
        discordBot?.stop()
        logger.info("CelestialWhitelister disabled")
    }
}
