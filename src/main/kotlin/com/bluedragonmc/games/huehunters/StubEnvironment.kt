package com.bluedragonmc.games.huehunters

import com.bluedragonmc.api.grpc.CommonTypes
import com.bluedragonmc.server.VersionInfo
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.api.Queue
import net.minestom.server.entity.Player
import java.io.File
import java.net.InetAddress

class StubEnvironment : Environment() {
        override val gameClasses: Collection<String>
            get() = listOf(HueHunters::class.qualifiedName!!)
        override val grpcServerPort: Int = 50051
        override val isDev: Boolean = false
        override val luckPermsHostname: String = ""
        override val mongoConnectionString: String = ""
        override val puffinHostname: String = ""
        override val puffinPort: Int = 50052
        override val queue: Queue = object: Queue(){
            override fun getMaps(gameType: String): Array<File> {
                return emptyArray()
            }

            override fun queue(player: Player, gameType: CommonTypes.GameType) {

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