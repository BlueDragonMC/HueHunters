package com.bluedragonmc.games.huehunters

import com.bluedragonmc.server.CustomPlayer
import net.minestom.server.MinecraftServer
import net.minestom.server.network.player.PlayerConnection
import java.util.*

class HHPlayer(uuid: UUID, username: String, playerConnection: PlayerConnection) :
    CustomPlayer(uuid, username, playerConnection) {
        init {
            MinecraftServer.getSchedulerManager().scheduleNextTick {
                this.hasPhysics = false // Disable block collision logic to prevent setbacks when walking into X-ray'd blocks
                this.hasCollision = false
            }
        }
}
