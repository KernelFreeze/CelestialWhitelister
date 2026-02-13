package dev.celestelove.whitelister

import dev.kord.common.entity.Snowflake
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.util.UUID

data class WhitelistEntry(
    val discordId: ULong,
    val minecraftNickname: String,
    val luckpermsGroup: String,
    val uuid: UUID,
)

object WhitelistEntries : Table("whitelist_entries") {
    val discordId = ulong("discord_id")
    val minecraftNickname = text("minecraft_nickname")
    val luckpermsGroup = text("luckperms_group")
    val uuid = uuid("uuid")
    override val primaryKey = PrimaryKey(discordId)
}

class WhitelistDatabase(dataFolder: File) : AutoCloseable {
    private val database: Database

    init {
        dataFolder.mkdirs()
        val dbFile = dataFolder.resolve("whitelist.db")
        database = Database.connect("jdbc:sqlite:${dbFile.absolutePath}", driver = "org.sqlite.JDBC")
        transaction(database) {
            SchemaUtils.create(WhitelistEntries)
        }
    }

    fun getEntry(discordId: Snowflake): WhitelistEntry? = transaction(database) {
        WhitelistEntries.selectAll().where { WhitelistEntries.discordId eq discordId.value }.singleOrNull()?.let {
                WhitelistEntry(
                    discordId = it[WhitelistEntries.discordId],
                    minecraftNickname = it[WhitelistEntries.minecraftNickname],
                    luckpermsGroup = it[WhitelistEntries.luckpermsGroup],
                    uuid = it[WhitelistEntries.uuid],
                )
            }
    }

    fun insertEntry(discordId: Snowflake, nickname: String, group: String, uuid: UUID) {
        transaction(database) {
            WhitelistEntries.insert {
                it[WhitelistEntries.discordId] = discordId.value
                it[minecraftNickname] = nickname
                it[luckpermsGroup] = group
                it[WhitelistEntries.uuid] = uuid
            }
        }
    }

    fun updateEntry(discordId: Snowflake, nickname: String, group: String, uuid: UUID) {
        transaction(database) {
            WhitelistEntries.update({ WhitelistEntries.discordId eq discordId.value }) {
                it[minecraftNickname] = nickname
                it[luckpermsGroup] = group
                it[WhitelistEntries.uuid] = uuid
            }
        }
    }

    override fun close() {
        database.connector().close()
    }
}
