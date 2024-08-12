package com.bluedragonmc.games.huehunters

import com.bluedragonmc.games.huehunters.server.module.AsymmetricTeamsModule
import com.bluedragonmc.games.huehunters.server.module.BlockDisguisesModule
import com.bluedragonmc.games.huehunters.server.module.BlockReplacerModule
import com.bluedragonmc.games.huehunters.server.module.ColorXrayModule
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.event.TeamAssignedEvent
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
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.noItalic
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.GameMode
import net.minestom.server.entity.Player
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.nio.file.Paths

class HueHunters(mapName: String) : Game("HueHunters", mapName) {
    override fun initialize() {
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
        use(SpawnpointModule(SpawnpointModule.TestSpawnpointProvider(Pos(0.0, 68.0, 7.0))))
        use(SpectatorModule(spectateOnDeath = false, spectateOnLeave = true))
        use(TeamModule(autoTeams = false, allowFriendlyFire = false))
        use(TimedRespawnModule(seconds = 5))
        use(VoidDeathModule(0.0, respawnMode = false))
        use(WinModule(winCondition = WinModule.WinCondition.LAST_TEAM_ALIVE))
        use(WorldPermissionsModule(allowBlockBreak = false, allowBlockPlace = false, allowBreakMap = false, allowBlockInteract = true))

        // Game-specific modules
        val hidersTeam = TeamModule.Team(
            name = Component.text("Hiders", NamedTextColor.GREEN),
            players = mutableListOf(),
            allowFriendlyFire = false
        )
        val seekersTeam = TeamModule.Team(
            name = Component.text("Seekers", NamedTextColor.RED),
            players = mutableListOf(),
            allowFriendlyFire = false
        )
        use(AsymmetricTeamsModule(hidersTeam = hidersTeam, seekersTeam = seekersTeam))
        use(object: ColorXrayModule(radius = 5) {
            override fun isXrayEnabled(player: Player): Boolean {
                return seekersTeam.players.contains(player)
            }
        })
        val useDisguiseItem = ItemStack.builder(Material.SCAFFOLDING)
            .customName(Component.text("Change Block (right-click)").noItalic())
            .build()
        use(BlockDisguisesModule(useDisguiseItem = useDisguiseItem))

        eventNode.addListener(GameStartEvent::class.java) { event ->
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