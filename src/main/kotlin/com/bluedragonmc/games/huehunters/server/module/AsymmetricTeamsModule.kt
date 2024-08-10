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
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent

@DependsOn(TeamModule::class)
class AsymmetricTeamsModule(val seekersTeam: TeamModule.Team, val hidersTeam: TeamModule.Team) : GameModule() {
    private lateinit var parent: Game
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        eventNode.addListener(GameStartEvent::class.java) { event ->
            // Make teams when game starts
            val players = mutableListOf(*parent.players.toTypedArray())
            val seeker = players.random()
            players.remove(seeker)
            seekersTeam.addPlayer(seeker)
            players.forEach { player -> hidersTeam.addPlayer(player) }
            parent.getModule<TeamModule>().teams.add(seekersTeam)
            parent.getModule<TeamModule>().teams.add(hidersTeam)
            seeker.sendMessage(Component.text("You are a Seeker!"))
            hidersTeam.players.forEach { player ->
                player.sendMessage("You are a Hider! Avoid the Seekers, or try to fight back!")
            }
        }
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            if (parent.state == GameState.INGAME) {
                event.player.sendMessage(Component.text("You have joined a game in progress. Stick around to play in the next game!", BRAND_COLOR_PRIMARY_2).surroundWithSeparators())
                parent.getModule<SpectatorModule>().addSpectator(event.player)
            }
        }
    }
}