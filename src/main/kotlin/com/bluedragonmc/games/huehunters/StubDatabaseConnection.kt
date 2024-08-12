package com.bluedragonmc.games.huehunters

import com.bluedragonmc.server.CustomPlayer
import com.bluedragonmc.server.api.DatabaseConnection
import com.bluedragonmc.server.model.GameDocument
import com.bluedragonmc.server.model.PlayerDocument
import net.minestom.server.entity.Player
import java.util.*
import kotlin.reflect.KMutableProperty

class StubDatabaseConnection : DatabaseConnection {
    override suspend fun getPlayerDocument(username: String): PlayerDocument? {
        return null
    }

    override suspend fun getPlayerDocument(uuid: UUID): PlayerDocument? {
        return null
    }

    override suspend fun getPlayerDocument(player: Player): PlayerDocument {
        TODO("Not yet implemented")
    }

    override suspend fun getPlayerForPunishmentId(id: String): PlayerDocument? {
        return null
    }

    override fun loadDataDocument(player: CustomPlayer) {
        TODO("Not yet implemented")
    }

    override suspend fun logGame(game: GameDocument) {

    }

    override suspend fun rankPlayersByStatistic(key: String, sortCriteria: String, limit: Int): List<PlayerDocument> {
        TODO("Not yet implemented")
    }

    override suspend fun <T> updatePlayer(playerUuid: String, field: KMutableProperty<T>, value: T) {

    }
}