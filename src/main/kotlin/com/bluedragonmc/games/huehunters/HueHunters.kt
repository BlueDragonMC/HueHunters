package com.bluedragonmc.games.huehunters

import com.bluedragonmc.games.huehunters.server.module.AsymmetricTeamsModule
import com.bluedragonmc.games.huehunters.server.module.ColorXrayModule
import net.minestom.server.coordinate.Pos

import com.bluedragonmc.server.Game
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
import net.minestom.server.entity.Player
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
//        use(PlayerResetModule(defaultGameMode = GameMode.ADVENTURE))
        use(SidebarModule(title = name))
        use(SpawnpointModule(SpawnpointModule.TestSpawnpointProvider(Pos(122.5, 119.0, -12.0))))
        use(SpectatorModule(spectateOnDeath = false, spectateOnLeave = true))
        use(TeamModule(autoTeams = false, allowFriendlyFire = false))
        use(TimedRespawnModule(seconds = 5))
        use(VoidDeathModule(0.0, respawnMode = false))
        use(WinModule(winCondition = WinModule.WinCondition.LAST_TEAM_ALIVE))
        use(WorldPermissionsModule(allowBlockBreak = false, allowBlockPlace = false, allowBreakMap = false, allowBlockInteract = true))


        use(AsymmetricTeamsModule())
        use(object: ColorXrayModule(radius = 5) {
            override fun isXrayEnabled(player: Player): Boolean {
                return true
            }
        })
    }
}