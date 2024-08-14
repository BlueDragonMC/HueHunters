package com.bluedragonmc.games.huehunters

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.VersionInfo
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.api.Queue
import com.bluedragonmc.server.utils.GameState
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import java.io.File
import java.net.InetAddress
import java.time.Duration

class StubEnvironment : Environment() {
        override val gameClasses: Collection<String>
            get() = listOf(HueHunters::class.qualifiedName!!)
        override val grpcServerPort: Int = 50051
        override val isDev: Boolean = false
        override val luckPermsHostname: String = ""
        override val mongoConnectionString: String = ""
        override val puffinHostname: String = ""
        override val puffinPort: Int = 50052
        override val queue: Queue = SingleGameQueue()

    class SingleGameQueue : Queue() {
        var selectedMap: String = "Warehouse"
        override fun getMaps(gameType: String): Array<File> {
            return emptyArray()
        }

        fun queue(player: Player) {
            // This queue system is only suitable for HueHunters because the server only runs 1 game at a time
            var validGame: Game? = null
            for (game in Game.games) {
                if (game.state != GameState.ENDING) {
                    validGame = game
                    break
                }
            }
            if (validGame == null) {
                HueHunters(selectedMap).init()
                MinecraftServer.getSchedulerManager().buildTask {
                    queue(player)
                }.delay(Duration.ofSeconds(1)).schedule()
                return
            }
            if (validGame.state == GameState.SERVER_STARTING) {
                MinecraftServer.getSchedulerManager().buildTask {
                    queue(player)
                }.delay(Duration.ofSeconds(1)).schedule()
                return
            }
            MinecraftServer.getSchedulerManager().scheduleNextTick {
                validGame.addPlayer(player)
            }
        }

        override fun queue(player: Player, gameType: CommonTypes.GameType) {
            queue(player)
        }

        override fun randomMap(gameType: String): String? = null

        override fun start() {

        }
    }

    override val versionInfo: VersionInfo
            get() = object: VersionInfo {
                override val BRANCH: String? = null
                override val COMMIT: String? = null
                override val COMMIT_DATE: String? = null
            }

        override suspend fun getServerName(): String = InetAddress.getLocalHost().hostName
}