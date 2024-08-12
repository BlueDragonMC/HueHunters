package com.bluedragonmc.games.huehunters

import com.bluedragonmc.games.huehunters.server.module.AsymmetricTeamsModule
import com.bluedragonmc.games.huehunters.server.module.BlockDisguisesModule
import com.bluedragonmc.games.huehunters.server.module.BlockReplacerModule
import com.bluedragonmc.games.huehunters.server.module.ColorXrayModule
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.event.GameStateChangedEvent
import com.bluedragonmc.server.event.TeamAssignedEvent
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.gameplay.ActionBarModule
import com.bluedragonmc.server.module.gameplay.InventoryPermissionsModule
import com.bluedragonmc.server.module.gameplay.SidebarModule
import com.bluedragonmc.server.module.gameplay.WorldPermissionsModule
import com.bluedragonmc.server.module.instance.InstanceContainerModule
import com.bluedragonmc.server.module.map.AnvilFileMapProviderModule
import com.bluedragonmc.server.module.minigame.*
import com.bluedragonmc.server.module.vanilla.DoorsModule
import com.bluedragonmc.server.module.vanilla.FallDamageModule
import com.bluedragonmc.server.utils.*
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.sound.SoundEvent
import java.nio.file.Paths
import java.time.Duration

class HueHunters(mapName: String) : Game("HueHunters", mapName) {
    override fun initialize() {

        var timeRemaining: Int? = null

        onGameStart {
            timeRemaining = 60 * 4 // 4 minutes
        }

        val hidersTeam = TeamModule.Team(
            name = Component.text("Hiders", NamedTextColor.GREEN),
            players = mutableListOf(),
            allowFriendlyFire = false
        )
        val helpersTeam = TeamModule.Team(
            name = Component.text("Seekers' Helpers", NamedTextColor.RED),
            players = mutableListOf(),
            allowFriendlyFire = false
        )
        val seekersTeam = TeamModule.Team(
            name = Component.text("Seekers", NamedTextColor.DARK_RED),
            players = mutableListOf(),
            allowFriendlyFire = false
        )

        use(ActionBarModule())
        use(AnvilFileMapProviderModule(Paths.get("worlds/$name/$mapName")))
        use(ConfigModule("huehunters.yml"))
        use(DoorsModule())
        use(FallDamageModule())
        use(InstanceContainerModule()) { module ->
            use(BlockReplacerModule(getOwnedInstances()))
        }
        use(InventoryPermissionsModule(allowDropItem = false, allowMoveItem = false))
        use(MOTDModule(Component.text("Hiders disguised as blocks must avoid\n")))
        use(OldCombatModule(allowDamage = true, allowKnockback = true))
        use(PlayerResetModule(defaultGameMode = GameMode.ADVENTURE))
        use(SidebarModule(title = name))
        val binding = getModule<SidebarModule>().bind {
            val statusLine = when (state) {
                GameState.SERVER_STARTING, GameState.WAITING -> {
                    Component.text("Waiting for players...", NamedTextColor.YELLOW)
                }

                GameState.STARTING -> {
                    Component.text("Starting soon!", NamedTextColor.YELLOW)
                }

                GameState.INGAME -> {
                    if (timeRemaining == null) getSpacer()
                    else {
                        val minutes = (timeRemaining!! / 60).toString().padStart(2, '0')
                        val seconds = (timeRemaining!! % 60).toString().padStart(2, '0')
                        Component.text("$minutes:$seconds remaining", NamedTextColor.YELLOW)
                    }
                }

                GameState.ENDING -> {
                    Component.text("Game over!", NamedTextColor.RED)
                }
            }

            val order = listOf(seekersTeam, helpersTeam, hidersTeam)
            val playerLines = players.sortedBy { player ->
                order.indexOf(getModule<TeamModule>().getTeam(player)).let {
                    // Push players that aren't in a team to the end of the list
                    if (it == -1) order.size else it
                }
            }.map { player ->
                player.displayName ?: player.name.withColor(
                    getModule<TeamModule>().getTeam(player)?.name?.color() ?: NamedTextColor.GRAY
                )
            }

            listOf(
                getSpacer(),
                statusLine,
                getSpacer(),
                *playerLines.toTypedArray(),
                getSpacer()
            )
        }
        handleEvent<PlayerSpawnEvent> { binding.update() }
        handleEvent<GameStateChangedEvent> { binding.update() }
        MinecraftServer.getSchedulerManager().buildTask {
            if (state == GameState.INGAME) binding.update()
        }.repeat(Duration.ofMillis(500)).schedule().manage(this)

        MinecraftServer.getSchedulerManager().buildTask {
            if (timeRemaining != null) {
                if (timeRemaining!! <= 0) {
                    // When time runs out, the hiders win
                    getModule<WinModule>().declareWinner(hidersTeam)
                } else {
                    timeRemaining = timeRemaining!! - 1
                }
            }
        }.repeat(Duration.ofSeconds(1)).schedule().manage(this)

        handleEvent<PlayerDeathEvent> { event ->
            if (getModule<TeamModule>().getTeam(event.player) == hidersTeam) {
                hidersTeam.removePlayer(event.player)

                if (hidersTeam.players.isEmpty()) {
                    MinecraftServer.getSchedulerManager().scheduleNextTick {
                        getModule<WinModule>().declareWinner(seekersTeam)
                    }
                    return@handleEvent
                }

                helpersTeam.addPlayer(event.player)
                event.player.inventory.clear()
                event.player.getAttribute(Attribute.GENERIC_SCALE).baseValue = 0.5
                event.player.sendMessage(Component.text("\nYou are now a hunter's helper! Clicking on a\nhider marks them for the hunter to find.\n", NamedTextColor.YELLOW))
            }
        }

        handleEvent<OldCombatModule.PlayerAttackEvent> { event ->
            if (event.target is Player && getModule<TeamModule>().getTeam(event.player) == helpersTeam && getModule<TeamModule>().getTeam(event.target as Player) == hidersTeam) {
                event.isCancelled = true
                val disguise = getModule<BlockDisguisesModule>().disguises[event.target as Player] ?: return@handleEvent
                if (!disguise.displayEntity.isGlowing) {
                    (event.target as Player).playSound(Sound.sound(SoundEvent.ENCHANT_THORNS_HIT, Sound.Source.HOSTILE, 1f, 1f))
                    (event.target as Player).sendMessage(Component.text("You have been tagged! ", NamedTextColor.DARK_RED, TextDecoration.BOLD) + Component.text("You are visible to all seekers for 5 seconds.", NamedTextColor.RED).decoration(TextDecoration.BOLD, false))
                }
                disguise.displayEntity.isGlowing = true
                MinecraftServer.getSchedulerManager().buildTask {
                    disguise.displayEntity.isGlowing = false
                }.delay(Duration.ofSeconds(5)).schedule().manage(this)
            }
        }

        use(SpawnpointModule(SpawnpointModule.TestSpawnpointProvider(Pos(0.0, 68.0, 7.0))))
        use(SpectatorModule(spectateOnDeath = false, spectateOnLeave = true))
        use(TeamModule(autoTeams = false, allowFriendlyFire = false)) { module -> module.teams.addAll(listOf(seekersTeam, helpersTeam, hidersTeam))}
        use(TimedRespawnModule(seconds = 5))
        use(VoidDeathModule(0.0, respawnMode = false))
        use(CustomDeathMessageModule())
        use(WinModule(winCondition = WinModule.WinCondition.LAST_TEAM_ALIVE))
        use(
            WorldPermissionsModule(
                allowBlockBreak = false,
                allowBlockPlace = false,
                allowBreakMap = false,
                allowBlockInteract = true
            )
        )

        // Game-specific modules
        use(AsymmetricTeamsModule(hidersTeam = hidersTeam, seekersTeam = seekersTeam))
        use(object : ColorXrayModule(radius = 5) {
            override fun isXrayEnabled(player: Player): Boolean {
                return seekersTeam.players.contains(player)
            }
        })
        val useDisguiseItem = ItemStack.builder(Material.SCAFFOLDING)
            .customName(Component.text("Change Block (right-click)").noItalic())
            .build()
        use(BlockDisguisesModule(useDisguiseItem = useDisguiseItem))

        eventNode.addListener(GameStartEvent::class.java) {
            state = GameState.INGAME
        }

        eventNode.addListener(TeamAssignedEvent::class.java) { event ->
            val player = event.player
            val teamName = getModule<TeamModule>().getTeam(event.player)?.name
            if (teamName == seekersTeam.name) {
                player.inventory.addItemStack(ItemStack.of(Material.RED_CONCRETE))
                player.inventory.addItemStack(ItemStack.of(Material.ORANGE_CONCRETE))
                player.inventory.addItemStack(ItemStack.of(Material.BLUE_CONCRETE))
                player.inventory.addItemStack(ItemStack.of(Material.GREEN_CONCRETE))
                player.inventory.addItemStack(ItemStack.of(Material.WHITE_CONCRETE))
                player.inventory.addItemStack(ItemStack.of(Material.BROWN_CONCRETE))
                player.inventory.addItemStack(ItemStack.of(Material.YELLOW_CONCRETE))
                player.inventory.addItemStack(ItemStack.of(Material.PURPLE_CONCRETE))
            } else if (teamName == hidersTeam.name) {
                player.inventory.addItemStack(useDisguiseItem)
            }
        }
    }
}