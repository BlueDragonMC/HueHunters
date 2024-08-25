package com.bluedragonmc.games.huehunters.server.command

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.command.BlueDragonCommand
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.utils.GameState
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

class StartCommand(name: String) : BlueDragonCommand(name = name, block = {
    syntax {
        val game = Game.Companion.findGame(player)
        if (game == null) {
            player.sendMessage(Component.text("You are not in a game", NamedTextColor.RED))
            return@syntax
        }
        if (game.state == GameState.WAITING || game.state == GameState.STARTING) {
            game.state = GameState.INGAME
            game.callEvent(GameStartEvent(game))
            sender.sendMessage(Component.text("Game started successfully", NamedTextColor.GREEN))
        } else {
            sender.sendMessage(Component.text("This game has already been started!", NamedTextColor.RED))
        }
    }
})