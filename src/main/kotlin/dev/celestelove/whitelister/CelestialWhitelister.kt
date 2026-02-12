package dev.celestelove.whitelister

import com.github.shynixn.mccoroutine.bukkit.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.bukkit.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CelestialWhitelister : SuspendingJavaPlugin() {

    private var discordBot: DiscordBot? = null

    override suspend fun onEnableAsync() {
        saveDefaultConfig()

        val token = config.getString("discord-token")
        if (token.isNullOrBlank() || token == "YOUR_BOT_TOKEN_HERE") {
            logger.severe("Discord bot token is not configured! Please set it in config.yml")
            server.pluginManager.disablePlugin(this)
            return
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

        logger.info("Loaded ${roleGroups.size} role-to-group mappings")

        val bot = DiscordBot(this, token, roleGroups, defaultGroup)
        discordBot = bot

        // Start the Discord bot on an IO thread since kord.login() blocks
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
    }

    override suspend fun onDisableAsync() {
        discordBot?.stop()
        logger.info("CelestialWhitelister disabled")
    }
}
