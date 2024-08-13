package com.bluedragonmc.games.huehunters.server.command

import com.bluedragonmc.games.huehunters.StubEnvironment
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.api.Environment
import com.bluedragonmc.server.command.BlueDragonCommand
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.noBold
import com.bluedragonmc.server.utils.plus
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.command.builder.arguments.ArgumentWord
import java.util.*

class MapCommand(name: String) : BlueDragonCommand(name = name, block = {
    val validMaps = listOf("Warehouse", "Shipping")

    val queue = Environment.queue as StubEnvironment.SingleGameQueue

    val mapName = ArgumentWord("mapName").from(*validMaps.toTypedArray())

    syntax {
        val node = Game.findGame(player)?.getModuleOrNull<ConfigModule>()?.getConfig()?.node("world")
        if (node == null) {
            sender.sendMessage(queue.selectedMap)
            return@syntax
        }
        val name = node.node("name")?.string ?: "Untitled"
        val description = node.node("description")?.string ?: "An awesome map!"
        val author = node.node("author")?.string ?: "BlueDragon Build Team"
        sender.sendMessage(
            Component.translatable(
                "module.motd.map",
                BRAND_COLOR_PRIMARY_2,
                // Map name
                Component.text(name, BRAND_COLOR_PRIMARY_1, TextDecoration.BOLD)
                    .hoverEvent(
                        HoverEvent.showText(
                            Component.text(
                                name,
                                BRAND_COLOR_PRIMARY_1,
                                TextDecoration.BOLD
                            ) + Component.newline() + Component.text(
                                description,
                                NamedTextColor.GRAY
                            ).noBold()
                        )
                    ),
                // Map builder
                Component.text(author, BRAND_COLOR_PRIMARY_1)
            )
        )
        if (name != queue.selectedMap) {
            sender.sendMessage(Component.text("Next game map: ", BRAND_COLOR_PRIMARY_2) + Component.text(queue.selectedMap, BRAND_COLOR_PRIMARY_1))
        }
    }

    syntax(mapName) {
        val game = Game.findGame(player)
        val newMapName = get(mapName)
        if (game == null) {
            sender.sendMessage(Component.text("You are not in a game!", NamedTextColor.RED))
            return@syntax
        }
        if (queue.selectedMap.equals(newMapName, ignoreCase = true)) {
            sender.sendMessage(Component.text("This map is already selected!", NamedTextColor.RED))
            return@syntax
        }
        if (!validMaps.contains(get(mapName))) {
            sender.sendMessage(Component.text("Invalid map name!", NamedTextColor.RED))
            return@syntax
        }
        queue.selectedMap = newMapName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() } // Capitalize first letter
        if (game.state == GameState.INGAME) {
            sender.sendMessage(Component.text("Map will be set to ${queue.selectedMap} after this game.", NamedTextColor.GREEN))
        } else {
            game.endGame(queueAllPlayers = true)
            sender.sendMessage(Component.text("Map changed to ${queue.selectedMap}.", NamedTextColor.GREEN))
        }
    }
})