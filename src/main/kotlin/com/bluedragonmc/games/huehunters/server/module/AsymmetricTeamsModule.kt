package com.bluedragonmc.games.huehunters.server.module

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.event.TeamAssignedEvent
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.minigame.SpectatorModule
import com.bluedragonmc.server.module.minigame.TeamModule
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.manage
import com.bluedragonmc.server.utils.miniMessage
import com.bluedragonmc.server.utils.surroundWithSeparators
import net.kyori.adventure.text.Component
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.network.packet.server.play.TeamsPacket
import net.minestom.server.potion.Potion
import net.minestom.server.potion.PotionEffect
import java.time.Duration
import kotlin.math.ceil

/**
 * This module creates Hiders and Seekers teams and assigns players to them when the game starts.
 * It also provides regeneration to players on the Hiders team, and lowers their max health.
 */
@DependsOn(TeamModule::class)
class AsymmetricTeamsModule(val seekersTeam: TeamModule.Team, val hidersTeam: TeamModule.Team) : GameModule() {
    private val combatStatus = hashMapOf<Player, Int>()
    private lateinit var parent: Game
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        eventNode.addListener(GameStartEvent::class.java) { event ->
            // Make teams when game starts
            val players = ArrayList(parent.players)
            val numSeekers = ceil(players.size / 5.0).toInt()
            logger.debug("$numSeekers seekers:")
            for (i in 1 .. numSeekers) {
                val seeker = players.random()
                logger.debug("${seeker.username} selected")
                players.remove(seeker)
                seekersTeam.addPlayer(seeker)
            }
            players.forEach { player -> hidersTeam.addPlayer(player) }
            parent.getModule<TeamModule>().teams.add(seekersTeam)
            parent.getModule<TeamModule>().teams.add(hidersTeam)
            hidersTeam.players.forEach { player ->
                parent.callEvent(TeamAssignedEvent(parent, player))
                player.sendMessage(miniMessage.deserialize("""
                    <gray>· <p2>You are a <green>Hider<p2>! Avoid the <red>Seekers<p2>, or try to fight back!
                    
                    <gray>· <red>Seekers <p2>can walk through colored blocks by holding the corresponding color, making them appear half-sized.
                    
                    <gray>· <yellow>Right click <p2>the Scaffolding in your hotbar to disguise yourself as a block. <green>Good luck!
                """.trimIndent()).surroundWithSeparators())
                player.getAttribute(Attribute.GENERIC_MAX_HEALTH).baseValue = 10.0
            }
            seekersTeam.players.forEach { player ->
                parent.callEvent(TeamAssignedEvent(parent, player))
                player.sendMessage(miniMessage.deserialize("""
                    <gray>· <p2>You are a <red>Seeker<p2>! <rainbow>Use your colors</rainbow><p2> to track down the <green>Hiders!
                    
                    <gray>· <green>Hiders <p2>can disguise themselves as a block, allowing them to blend in with their surroundings.
                    
                    <gray>· <yellow>Hold <p2>a color in your hotbar to <yellow>hide <p2>nearby blocks of that color, revealing any disguised hiders. <green>Good luck!
                """.trimIndent()).surroundWithSeparators())
                player.addEffect(Potion(PotionEffect.BLINDNESS, 1, 200))
                player.addEffect(Potion(PotionEffect.DARKNESS, 1, 200))
            }
            hidersTeam.register()
            seekersTeam.register()
            hidersTeam.scoreboardTeam.updateNameTagVisibility(TeamsPacket.NameTagVisibility.HIDE_FOR_OTHER_TEAMS)
            seekersTeam.scoreboardTeam.updateNameTagVisibility(TeamsPacket.NameTagVisibility.HIDE_FOR_OTHER_TEAMS)

            // Regen
            parent.players.forEach { combatStatus[it] = 0 }
            MinecraftServer.getSchedulerManager().buildTask {
                for (s in combatStatus) {
                    if (!hidersTeam.players.contains(s.key)) return@buildTask // Regen is for hiders only
                    combatStatus[s.key] = combatStatus.getOrDefault(s.key, 0) + 1
                    if (combatStatus[s.key]!! >= 10) s.key.health += 1.0f
                }
            }.repeat(Duration.ofSeconds(1)).schedule().manage(parent)
        }

        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            if (parent.state == GameState.INGAME) {
                event.player.sendMessage(Component.text("You have joined a game in progress. Stick around to play in the next game!", BRAND_COLOR_PRIMARY_2).surroundWithSeparators())
                parent.getModule<SpectatorModule>().addSpectator(event.player)
            }
        }

        // Regen
        eventNode.addListener(OldCombatModule.PlayerAttackEvent::class.java) { event ->
            val target = event.target
            if (target !is Player) return@addListener
            combatStatus[event.attacker] = 0
            combatStatus[target] = 0
        }
    }

    override fun deinitialize() {
        parent.players.forEach { player ->
            player.getAttribute(Attribute.GENERIC_MAX_HEALTH).baseValue = 20.0
        }
    }
}