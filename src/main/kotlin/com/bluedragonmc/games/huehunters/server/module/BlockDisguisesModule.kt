package com.bluedragonmc.games.huehunters.server.module

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.event.TeamAssignedEvent
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.minigame.SpectatorModule
import com.bluedragonmc.server.module.minigame.TeamModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.metadata.display.BlockDisplayMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag

// TODO make this a standard module in BlueDragon's common library

@DependsOn(ColorXrayModule::class)
class BlockDisguisesModule(val hidersTeam: TeamModule.Team) : GameModule() {
    private lateinit var parent: Game
    val disguises = hashMapOf<Player, Entity>()
    val TAG_DISGUISE_OWNER = Tag.UUID("disguise_owner_uuid")
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        // HueHunters-specific
        eventNode.addListener(TeamAssignedEvent::class.java) { event ->
            disguisePlayerIfAllowed(event.player)
        }
        eventNode.addListener(PlayerChangeHeldSlotEvent::class.java) { event ->
            disguisePlayerIfAllowed(event.player)
        }

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            val disguise = disguises[event.player] ?: return@addListener
            disguise.pose = event.player.pose
            disguise.teleport(event.newPosition)
        }

        eventNode.addListener(PlayerDeathEvent::class.java) { event ->
            undisguisePlayer(event.player)
        }
        eventNode.addListener(PlayerLeaveGameEvent::class.java) { event ->
            undisguisePlayer(event.player)
        }
        eventNode.addListener(SpectatorModule.StartSpectatingEvent::class.java) { event ->
            undisguisePlayer(event.player)
        }

        // Forward attack events
        eventNode.addListener(OldCombatModule.PlayerAttackEvent::class.java) { event ->
            val target = event.target as? LivingEntity ?: return@addListener
            if (target in disguises.values) {
                event.isCancelled = true
                val owner = getOwner(target)
                parent.callEvent(
                    EntityAttackEvent(
                        event.attacker,
                        owner ?: return@addListener
                    )
                )
            }
        }

        // Play disguise damage sound when its owner takes damage
        eventNode.addListener(EntityDamageEvent::class.java) { event ->
            val player = event.entity as? Player ?: return@addListener
            val disguise = disguises[player] ?: return@addListener
            event.instance.playSound(
                Sound.sound(
                SoundEvent.ENTITY_PLAYER_HURT ?: return@addListener,
                Sound.Source.PLAYER,
                1.0f,
                1.0f
            ), disguise.position)
        }
    }

    override fun deinitialize() {
        while (disguises.keys.size > 0) {
            undisguisePlayer(disguises.keys.first())
        }
    }

    /**
     * Calls [disguisePlayerFromHand] only if the player is on the [hidersTeam].
     */
    private fun disguisePlayerIfAllowed(player: Player) {
        if (parent.getModule<TeamModule>().getTeam(player) == hidersTeam) {
            println("Player is on hiders team")
            disguisePlayerFromHand(player)
        }
    }

    /**
     * Turns the player into a random block whose color matches the item in the player's hand.
     */
    private fun disguisePlayerFromHand(player: Player) {
        disguises[player]?.remove()
        val colorXrayModule = parent.getModule<ColorXrayModule>()
        val color = colorXrayModule.getHoldingColor(player)
        if (color == null) {
            undisguisePlayer(player)
            return
        }
        val blocks = colorXrayModule.getColorBlocks(color)
        if (blocks == null) {
            undisguisePlayer(player)
            return
        }
        disguisePlayer(player, blocks.random().block())
    }

    private fun disguisePlayer(player: Player, block: Block) {
        val entity = Entity(EntityType.BLOCK_DISPLAY)
        val meta = entity.entityMeta as BlockDisplayMeta
        meta.setBlockState(block)
        disguisePlayer(player, entity)
    }

    private fun disguisePlayer(player: Player, disguise: Entity) {
        disguise.updateViewableRule { player.uuid != it.uuid }
        player.getAttribute(Attribute.GENERIC_SCALE).baseValue = 0.5 // Allows the player to fit through 1 block gaps
        player.isAutoViewable = false
        disguise.entityMeta.isHasNoGravity = true
        disguise.customName = player.name
        disguise.isCustomNameVisible = true
        disguise.setTag(TAG_DISGUISE_OWNER, player.uuid)
        disguise.setInstance(player.instance ?: return, player.position)
        if (disguise is LivingEntity) disguise.isInvulnerable = true
        disguises[player] = disguise
    }

    /**
     * Returns the player's disguise if they are disguised, otherwise just returns the player.
     */
    private fun getDisguiseOrPlayer(player: Player): Entity = disguises[player] ?: player

    private fun undisguisePlayer(player: Player) {
        if (!isDisguised(player)) return
        disguises[player]!!.remove()
        player.getAttribute(Attribute.GENERIC_SCALE).baseValue = 1.0
        player.isAutoViewable = true
        disguises.remove(player)
    }

    private fun isDisguised(player: Player): Boolean = disguises.containsKey(player)

    /**
     * Get the player using the disguise.
     */
    private fun getOwner(disguise: Entity): Player? = MinecraftServer.getConnectionManager().getOnlinePlayerByUuid(disguise.getTag(TAG_DISGUISE_OWNER))
}