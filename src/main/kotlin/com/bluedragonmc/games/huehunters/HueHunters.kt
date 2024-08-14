package com.bluedragonmc.games.huehunters

import com.bluedragonmc.games.huehunters.server.module.*
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.event.GameStateChangedEvent
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.event.TeamAssignedEvent
import com.bluedragonmc.server.module.combat.CustomDeathMessageModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.gameplay.InventoryPermissionsModule
import com.bluedragonmc.server.module.gameplay.SidebarModule
import com.bluedragonmc.server.module.gameplay.WorldPermissionsModule
import com.bluedragonmc.server.module.instance.CustomGeneratorInstanceModule
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
import net.kyori.adventure.title.Title
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.item.ItemComponent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.TeamsPacket
import net.minestom.server.sound.SoundEvent
import java.nio.file.Paths
import java.time.Duration

class HueHunters(mapName: String) : Game("HueHunters", mapName) {
    override fun initialize() {

        var timeRemaining: Int? = null

        onGameStart {
            timeRemaining = 150 // 2:30 minutes
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
        helpersTeam.register()
        helpersTeam.scoreboardTeam.updateNameTagVisibility(TeamsPacket.NameTagVisibility.HIDE_FOR_OTHER_TEAMS)
        val seekersTeam = TeamModule.Team(
            name = Component.text("Seekers", NamedTextColor.DARK_RED),
            players = mutableListOf(),
            allowFriendlyFire = false
        )

        use(AnvilFileMapProviderModule(Paths.get("worlds/$name/$mapName"), CustomGeneratorInstanceModule.getFullbrightDimension()))
        use(ConfigModule()) { module ->
            val floorHeight = module.getConfig().node("world", "floorHeight").getDouble(63.0)
            use(VoidSoundModule(threshold = floorHeight))
            use(VoidDeathModule(threshold = floorHeight - 200, respawnMode = false))
        }
        use(DoorsModule())
        use(FallDamageModule())
        use(InstanceContainerModule()) { module ->
            use(BlockReplacerModule(getOwnedInstances()))
        }
        use(InventoryPermissionsModule(allowDropItem = false, allowMoveItem = false))
        use(MOTDModule(Component.text("Hiders disguised as blocks must avoid\nthe Hunters, who can use the colors in their inventory\nto bend reality and make real blocks disappear.")))
        use(OldCombatModule(allowDamage = true, allowKnockback = true))
        use(PlayerResetModule(defaultGameMode = GameMode.ADVENTURE))
        use(SidebarModule(title = name))
        use(VoteStartModule())
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
                var color = getModule<TeamModule>().getTeam(player)?.name?.color()
                if (color == null && (state == GameState.WAITING || state == GameState.STARTING)) {
                    color = if(getModule<VoteStartModule>().hasVoted(player)) NamedTextColor.GREEN else NamedTextColor.GRAY
                }
                player.displayName ?: player.name.withColor(color ?: NamedTextColor.GRAY)
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
            if (state != GameState.ENDING) binding.update()
        }.repeat(Duration.ofMillis(500)).schedule().manage(this)

        var minuteWarned = false
        MinecraftServer.getSchedulerManager().buildTask {
            if (timeRemaining != null) {
                if (timeRemaining!! <= 1) {
                    // When time runs out, the hiders win
                    getModule<WinModule>().declareWinner(hidersTeam)
                } else {
                    timeRemaining = timeRemaining!! - 1
                    if (timeRemaining!! <= 60 && !minuteWarned) {
                        minuteWarned = true
                        showTitle(Title.title(
                            Component.text(timeRemaining.toString(), BRAND_COLOR_PRIMARY_1),
                            Component.text("seconds remaining", BRAND_COLOR_PRIMARY_2),
                            Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(1))
                        ))
                        playSound(Sound.sound(
                            SoundEvent.BLOCK_BEACON_POWER_SELECT,
                            Sound.Source.RECORD,
                            1.0f,
                            1.0f
                        )
                        )
                    } else if (timeRemaining!! in 1..10) {
                        showTitle(Title.title(
                            Component.text(timeRemaining.toString(), BRAND_COLOR_PRIMARY_1),
                            Component.empty(),
                            Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(1))
                        ))
                        playSound(Sound.sound(
                            SoundEvent.BLOCK_NOTE_BLOCK_BASS,
                            Sound.Source.BLOCK,
                            1.0f,
                            1.5f
                        ))
                    }
                }
                binding.update()
            }
        }.repeat(Duration.ofSeconds(1)).schedule().manage(this)

        handleEvent<PlayerDeathEvent> { event ->
            val team = getModule<TeamModule>().getTeam(event.player)
            if (team == hidersTeam) {
                val timeAdded = 20 / seekersTeam.players.size
                timeRemaining = timeRemaining!! + timeAdded
                this.sendActionBar(Component.text("$timeAdded seconds have been added to the game clock.", BRAND_COLOR_PRIMARY_2))
                this.playSound(Sound.sound(SoundEvent.BLOCK_VAULT_ACTIVATE, Sound.Source.PLAYER, 1.0f, 1.0f))
                hidersTeam.removePlayer(event.player)

                if (hidersTeam.players.isEmpty()) {
                    MinecraftServer.getSchedulerManager().scheduleNextTick {
                        for (player in ArrayList(helpersTeam.players)) {
                            // Switch them to the seekers team so that they see the victory title
                            helpersTeam.removePlayer(player)
                            seekersTeam.addPlayer(player)
                        }
                        getModule<WinModule>().declareWinner(seekersTeam)
                    }
                    return@handleEvent
                }

                helpersTeam.addPlayer(event.player)
                event.player.inventory.clear()
                event.player.sendMessage(Component.text("\nYou are now a hunter's helper! Clicking on a\nhider marks them for the hunter to find.\n", NamedTextColor.YELLOW))
                MinecraftServer.getSchedulerManager().buildTask {
                    event.player.getAttribute(Attribute.GENERIC_SCALE).baseValue = 0.5
                    event.player.getAttribute(Attribute.GENERIC_MAX_HEALTH).baseValue = 6.0
                }.delay(Duration.ofSeconds(5)).schedule().manage(this)
            } else if (team == seekersTeam) {
                if (timeRemaining != null) {
                    val timeRemoved = 40 / seekersTeam.players.size
                    timeRemaining = timeRemaining!! - timeRemoved
                    this.sendActionBar(Component.text("$timeRemoved seconds have been removed from the game clock.", BRAND_COLOR_PRIMARY_2))
                    this.playSound(Sound.sound(SoundEvent.BLOCK_VAULT_DEACTIVATE, Sound.Source.PLAYER, 1.0f, 1.0f))
                }
            }
        }

        handleEvent<OldCombatModule.PlayerAttackEvent> { event ->
            val attackerTeam = getModule<TeamModule>().getTeam(event.player)
            val targetTeam = getModule<TeamModule>().getTeam(event.target as? Player ?: return@handleEvent)
            if (attackerTeam == helpersTeam && targetTeam == hidersTeam) {
                event.isCancelled = true
                val disguise = getModule<BlockDisguisesModule>().disguises[event.target as Player] ?: return@handleEvent
                if (!disguise.displayEntity.isGlowing) {
                    (event.target as Player).playSound(Sound.sound(SoundEvent.ENCHANT_THORNS_HIT, Sound.Source.HOSTILE, 1f, 1f))
                    (event.target as Player).sendMessage(Component.text("You have been tagged! ", NamedTextColor.DARK_RED, TextDecoration.BOLD) + Component.text("You are visible to all seekers for 5 seconds.", NamedTextColor.RED).decoration(TextDecoration.BOLD, false))
                }
                seekersTeam.playSound(Sound.sound(SoundEvent.ENCHANT_THORNS_HIT, Sound.Source.PLAYER, 1f, 1f))
                seekersTeam.sendActionBar(Component.text("A hider has been tagged by one of your helpers!", BRAND_COLOR_PRIMARY_2))
                disguise.displayEntity.isGlowing = true
                MinecraftServer.getSchedulerManager().buildTask {
                    disguise.displayEntity.isGlowing = false
                }.delay(Duration.ofSeconds(7)).schedule().manage(this)
            }
            if (attackerTeam == helpersTeam && targetTeam == seekersTeam) {
                event.isCancelled = true
            }
            if (attackerTeam == seekersTeam && targetTeam == helpersTeam) {
                event.isCancelled = true
            }
        }

        use(SpawnpointModule(SpawnpointModule.ConfigSpawnpointProvider(allowRandomOrder = true)))
        use(SpectatorModule(spectateOnDeath = false, spectateOnLeave = true))
        use(TeamModule(autoTeams = false, allowFriendlyFire = false)) { module -> module.teams.addAll(listOf(seekersTeam, helpersTeam, hidersTeam))}
        use(TimedRespawnModule(seconds = 1))
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
            .set(ItemComponent.ITEM_NAME, Component.text("Change Block ", NamedTextColor.AQUA) + Component.text("(Right click)", NamedTextColor.GRAY))
            .build()
        use(BlockDisguisesModule(useDisguiseItem = useDisguiseItem))

        eventNode.addListener(GameStartEvent::class.java) {
            state = GameState.INGAME
        }

        val disguisesModule = getModule<BlockDisguisesModule>()
        val concreteBlocks = getModule<ColorXrayModule>().getDisappearableBlocks().filter { it.name().contains("concrete") }

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
                disguisesModule.disguisePlayer(player, concreteBlocks.random().block())
            }
        }

        eventNode.addListener(PlayerLeaveGameEvent::class.java) { event ->
            MinecraftServer.getSchedulerManager().scheduleNextTick {
                // After one tick, the player that left should have been removed from their team
                // Check for a win
                if (hidersTeam.players.size == 0) {
                    for (helper in ArrayList(helpersTeam.players)) {
                        // Move helpers to the seekers team so they see the "VICTORY!" message
                        helpersTeam.removePlayer(helper)
                        seekersTeam.addPlayer(helper)
                    }
                    getModule<WinModule>().declareWinner(seekersTeam)
                } else if (seekersTeam.players.size == 0) { // The last/only seeker left
                    getModule<WinModule>().declareWinner(hidersTeam)
                }
            }
        }
    }
}