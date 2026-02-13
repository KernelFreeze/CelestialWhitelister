package dev.celestelove.whitelister

import com.charleskorn.kaml.Yaml
import com.github.shynixn.mccoroutine.bukkit.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.bukkit.launch
import com.mojang.brigadier.Command
import io.github.oshai.kotlinlogging.KotlinLogging
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender

@Suppress("UnstableApiUsage")
class CelestialWhitelister : SuspendingJavaPlugin() {
    companion object {
        val LOGGER = KotlinLogging.logger {}
    }

    private var discordBot: DiscordBot? = null
    private var whitelistDatabase: WhitelistDatabase? = null

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
                "Celestial Whitelister admin commands",
                listOf("cw")
            )
        }
    }

    private suspend fun performReload(sender: CommandSender) {
        discordBot?.stop()
        discordBot = null
        whitelistDatabase?.close()
        whitelistDatabase = null
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

    private fun loadConfig(): PluginConfig {
        val configText = dataFolder.resolve("config.yml").readText()
        return Yaml.default.decodeFromString(PluginConfig.serializer(), configText)
    }

    private fun startBot(): Boolean {
        val pluginConfig = try {
            loadConfig()
        } catch (e: Exception) {
            logger.severe("Failed to parse config.yml: ${e.message}")
            return false
        }

        val token = pluginConfig.discordToken
        if (token.isBlank() || token == "YOUR_BOT_TOKEN_HERE") {
            logger.severe("Discord bot token is not configured! Please set it in config.yml")
            return false
        }

        val roleGroups = pluginConfig.roleGroups
        val defaultGroup = pluginConfig.defaultGroup

        val allowedChannels = if (pluginConfig.channelRestriction.enabled) {
            pluginConfig.channelRestriction.channels.toSet()
        } else {
            emptySet()
        }

        val requiredRoles = if (pluginConfig.requiredRoles.enabled) {
            pluginConfig.requiredRoles.roles.toSet()
        } else {
            emptySet()
        }

        val onlineMode = server.onlineMode
        val forceOnlineUUIDs = pluginConfig.forceOnlineUuids
        val oneTimeWhitelist = pluginConfig.oneTimeWhitelist

        val database = if (oneTimeWhitelist) {
            try {
                WhitelistDatabase(dataFolder).also {
                    whitelistDatabase = it
                }
            } catch (e: Exception) {
                logger.severe("Failed to initialize whitelist database: ${e.message}")
                return false
            }
        } else {
            null
        }

        logger.info("Loaded ${roleGroups.size} role-to-group mappings")
        if (allowedChannels.isNotEmpty()) logger.info("Channel restriction enabled: ${allowedChannels.size} channel(s)")
        if (requiredRoles.isNotEmpty()) logger.info("Role requirement enabled: ${requiredRoles.size} role(s)")
        if (oneTimeWhitelist) logger.info("One-time whitelist restriction enabled")
        logger.info("Server online-mode: $onlineMode, force-online-uuids: $forceOnlineUUIDs")

        val bot = DiscordBot(this, token, roleGroups, defaultGroup, allowedChannels, requiredRoles, onlineMode, forceOnlineUUIDs, oneTimeWhitelist, database)
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
        whitelistDatabase?.close()
        logger.info("CelestialWhitelister disabled")
    }
}
