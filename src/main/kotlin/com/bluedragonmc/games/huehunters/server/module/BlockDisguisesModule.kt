package com.bluedragonmc.games.huehunters.server.module

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.minigame.SpectatorModule
import com.bluedragonmc.server.utils.plus
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.LivingEntity
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.metadata.other.FallingBlockMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerUseItemOnBlockEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag

// TODO make this a standard module in BlueDragon's common library

// TODO: if we need to make movement smoother, we can combine a block display + an interaction entity (https://minecraft.wiki/w/Interaction)
// the block display has no hitbox, but an Interaction does

@DependsOn(ColorXrayModule::class)
class BlockDisguisesModule(val useDisguiseItem: ItemStack) : GameModule() {
    private lateinit var parent: Game
    val disguises = hashMapOf<Player, Entity>()
    val TAG_DISGUISE_OWNER = Tag.UUID("disguise_owner_uuid")
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        // HueHunters-specific
        eventNode.addListener(PlayerUseItemOnBlockEvent::class.java) { event ->
            println("HERE1")
//            if (!event.itemStack.isSimilar(useDisguiseItem)) return@addListener TODO fix
            println("HERE2")
            val block = event.player.instance.getBlock(event.position)
            if (!parent.getModule<ColorXrayModule>().getDisappearableBlocks().contains(block.registry().material())) {
                event.player.sendMessage(
                    Component.text(
                        "You can't disguise as ",
                        NamedTextColor.RED
                    ) + Component.translatable(block.registry().translationKey(), NamedTextColor.RED) + Component.text(
                        "!",
                        NamedTextColor.RED
                    )
                )
                return@addListener
            }
            println("HERE3")
            disguisePlayer(event.player, block)
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
            val target = event.target
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

    private fun disguisePlayer(player: Player, block: Block) {
        player.sendMessage(Component.text("You are now a ") + Component.translatable(block.registry().translationKey()))
        val entity = Entity(EntityType.FALLING_BLOCK)
        val meta = entity.entityMeta as FallingBlockMeta
        meta.block = (block)
        disguisePlayer(player, entity)
    }

    private fun disguisePlayer(player: Player, disguise: Entity) {
        disguises[player]?.remove()
        disguise.updateViewableRule { player.uuid != it.uuid }
        player.getAttribute(Attribute.GENERIC_SCALE).baseValue = 0.5 // Allows the player to fit through 1 block gaps
        player.isAutoViewable = false
        disguise.entityMeta.isHasNoGravity = true
        disguise.customName = player.name
        disguise.isCustomNameVisible = false
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