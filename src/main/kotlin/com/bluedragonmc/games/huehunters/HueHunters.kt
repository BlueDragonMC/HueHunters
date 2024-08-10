package com.bluedragonmc.games.huehunters

import com.bluedragonmc.games.huehunters.server.module.AsymmetricTeamsModule
import com.bluedragonmc.games.huehunters.server.module.BlockDisguisesModule
import com.bluedragonmc.games.huehunters.server.module.ColorXrayModule
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
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
import com.bluedragonmc.server.module.vanilla.NaturalRegenerationModule
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
        use(InstanceContainerModule())
        use(InventoryPermissionsModule(allowDropItem = false, allowMoveItem = false))
        use(MOTDModule(Component.text("Hiders disguised as blocks must avoid\n")))
        use(NaturalRegenerationModule())
        use(OldCombatModule(allowDamage = true, allowKnockback = true))
        use(PlayerResetModule(defaultGameMode = GameMode.ADVENTURE))
        use(SidebarModule(title = name))
        use(SpawnpointModule(SpawnpointModule.TestSpawnpointProvider(Pos(122.5, 119.0, -12.0))))
        use(SpectatorModule(spectateOnDeath = false, spectateOnLeave = true))
        use(TeamModule(autoTeams = false, allowFriendlyFire = false))
        use(TimedRespawnModule(seconds = 5))
        use(VoidDeathModule(0.0, respawnMode = false))
        use(WinModule(winCondition = WinModule.WinCondition.LAST_TEAM_ALIVE))
        use(WorldPermissionsModule(allowBlockBreak = false, allowBlockPlace = false, allowBreakMap = false, allowBlockInteract = true))

        // Game-specific modules

        eventNode.addListener(GameStartEvent::class.java) { event ->
            players.forEach { player ->
                player.inventory.addItemStack(ItemStack.of(Material.BROWN_TERRACOTTA))
                player.inventory.addItemStack(ItemStack.of(Material.LIME_TERRACOTTA))
                player.inventory.addItemStack(ItemStack.of(Material.WARPED_PLANKS))
                player.inventory.addItemStack(ItemStack.of(Material.CHERRY_LOG))
                player.inventory.addItemStack(ItemStack.of(Material.BRICKS))
                player.inventory.addItemStack(ItemStack.of(Material.WAXED_COPPER_BLOCK))
                player.inventory.addItemStack(ItemStack.of(Material.HAY_BLOCK))
                player.inventory.addItemStack(ItemStack.of(Material.WHITE_TERRACOTTA))
            }
        }

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
        use(BlockDisguisesModule(hidersTeam = hidersTeam))
    }
}