package com.bluedragonmc.games.huehunters.server.command

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.command.BlueDragonCommand
import com.bluedragonmc.server.event.GameStartEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class StartCommand(name: String) : BlueDragonCommand(name = name, block = {
    syntax {
        val game = Game.Companion.findGame(player)
        if (game == null) {
            player.sendMessage(Component.text("You are not in a game", NamedTextColor.RED))
            return@syntax
        }
        game.callEvent(GameStartEvent(game))
        sender.sendMessage(Component.text("Game started successfully", NamedTextColor.GREEN))
    }
})