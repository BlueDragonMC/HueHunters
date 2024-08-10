package com.bluedragonmc.games.huehunters.server.module

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.minigame.SpectatorModule
import com.bluedragonmc.server.module.minigame.TeamModule
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.surroundWithSeparators
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent

@DependsOn(TeamModule::class)
class AsymmetricTeamsModule : GameModule() {
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(GameStartEvent::class.java) { event ->
            // Make teams when game starts
            val players = mutableListOf(*parent.players.toTypedArray())
            val seeker = players.random()
            players.remove(seeker)
            val seekersTeam = TeamModule.Team(
                name = Component.text("Seekers", NamedTextColor.RED),
                players = mutableListOf(seeker),
                allowFriendlyFire = false
            )
            val hidersTeam = TeamModule.Team(
                name = Component.text("Hiders", NamedTextColor.GREEN),
                players = players,
                allowFriendlyFire = false
            )
            parent.getModule<TeamModule>().teams.add(seekersTeam)
            parent.getModule<TeamModule>().teams.add(hidersTeam)
            seeker.sendMessage(Component.text("You are a Seeker!"))
            players.forEach { it.sendMessage("You are a Hider! Avoid the Seekers, or try to fight back!") }
        }
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            if (parent.state == GameState.INGAME) {
                event.player.sendMessage(Component.text("You have joined a game in progress. Stick around to play in the next game!", BRAND_COLOR_PRIMARY_2).surroundWithSeparators())
                parent.getModule<SpectatorModule>().addSpectator(event.player)
            }
        }
    }

}