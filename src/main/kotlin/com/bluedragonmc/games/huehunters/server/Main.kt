package com.bluedragonmc.games.huehunters.server

import com.bluedragonmc.games.huehunters.HHPlayer
import com.bluedragonmc.games.huehunters.HueHunters
import com.bluedragonmc.games.huehunters.StubDatabaseConnection
import com.bluedragonmc.games.huehunters.StubEnvironment
import com.bluedragonmc.games.huehunters.server.command.StartCommand
import com.bluedragonmc.server.ALT_COLOR_1
import com.bluedragonmc.server.VersionInfo
import com.bluedragonmc.server.api.*
import com.bluedragonmc.server.api.Queue
import com.bluedragonmc.server.module.minigame.SpawnpointModule
import com.bluedragonmc.server.service.Database
import com.bluedragonmc.server.service.Messaging
import com.bluedragonmc.server.service.Permissions
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

private val logger = LoggerFactory.getLogger("HueHuntersMain")

fun main() {
    logger.info("Hello!")
    val server = MinecraftServer.init()
    val globalEventHandler = MinecraftServer.getGlobalEventHandler()

    MinecraftServer.getConnectionManager().setPlayerProvider(::HHPlayer)

    Messaging.initializeIncoming(IncomingRPCHandlerStub())
    Messaging.initializeOutgoing(OutgoingRPCHandlerStub())
    Database.initialize(StubDatabaseConnection())

    Environment.setEnvironment(StubEnvironment())

    GlobalTranslation.hook()

    Permissions.initialize(object: PermissionManager {
        override fun getMetadata(player: UUID): PlayerMeta = PlayerMeta(
            prefix = Component.empty(),
            suffix = Component.empty(),
            primaryGroup = "default",
            rankColor = ALT_COLOR_1
        )

        override fun hasPermission(player: UUID, node: String): Boolean = true

    })

    MinecraftServer.getCommandManager().register(StartCommand("start"))

    val game = HueHunters(mapName = "Warehouse")
    game.init()

    globalEventHandler.addListener(AsyncPlayerConfigurationEvent::class.java) { event ->
        event.spawningInstance = game.getInstance()
    }

    globalEventHandler.addListener(PlayerSpawnEvent::class.java) { event ->
        if (event.instance == game.getInstance() && !game.players.contains(event.player)) {
            game.addPlayer(event.player, false)
            event.player.teleport(game.getModule<SpawnpointModule>().spawnpointProvider.getSpawnpoint(event.player))
        }
    }

    server.start("0.0.0.0", 25565)
}