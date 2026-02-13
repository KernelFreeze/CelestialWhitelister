package dev.celestelove.whitelister

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PluginConfig(
    @SerialName("discord-token") val discordToken: String = "YOUR_BOT_TOKEN_HERE",
    @SerialName("role-groups") val roleGroups: Map<String, String> = emptyMap(),
    @SerialName("default-group") val defaultGroup: String = "default",
    @SerialName("force-online-uuids") val forceOnlineUuids: Boolean = false,
    @SerialName("channel-restriction") val channelRestriction: ChannelRestriction = ChannelRestriction(),
    @SerialName("required-roles") val requiredRoles: RequiredRoles = RequiredRoles(),
)

@Serializable
data class ChannelRestriction(
    val enabled: Boolean = false,
    val channels: List<String> = emptyList(),
)

@Serializable
data class RequiredRoles(
    val enabled: Boolean = false,
    val roles: List<String> = emptyList(),
)
