package com.bluedragonmc.games.huehunters.server.module

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_1
import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.combat.OldCombatModule
import com.bluedragonmc.server.module.minigame.SpectatorModule
import com.bluedragonmc.server.utils.isFullCube
import com.bluedragonmc.server.utils.plus
import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.minestom.server.coordinate.Point
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Player
import net.minestom.server.entity.attribute.Attribute
import net.minestom.server.entity.metadata.display.BlockDisplayMeta
import net.minestom.server.entity.metadata.other.InteractionMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.entity.EntityAttackEvent
import net.minestom.server.event.entity.EntityDamageEvent
import net.minestom.server.event.player.PlayerDeathEvent
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.event.player.PlayerUseItemOnBlockEvent
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import net.minestom.server.item.ItemStack
import net.minestom.server.sound.SoundEvent

// TODO make this a standard module in BlueDragon's common library

// TODO: if we need to make movement smoother, we can combine a block display + an interaction entity (https://minecraft.wiki/w/Interaction)
// the block display has no hitbox, but an Interaction does

@DependsOn(ColorXrayModule::class)
class BlockDisguisesModule(val useDisguiseItem: ItemStack) : GameModule() {
    companion object {
        /**
         * How long you have to stand still before your disguise snaps, in ticks
         */
        private const val SNAP_DELAY_TICKS = 30L
    }
    private lateinit var parent: Game
    val disguises = hashMapOf<Player, DamageableBlockEntity>()
    private val moveTimer = hashMapOf<Player, Long>() // Tracks the time that each player has not moved
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        // HueHunters-specific
        eventNode.addListener(PlayerUseItemOnBlockEvent::class.java) { event ->
            val itemStack = event.player.itemInMainHand
            if (!itemStack.isSimilar(useDisguiseItem)) return@addListener
            val block = event.player.instance.getBlock(event.position)
            if (!parent.getModule<ColorXrayModule>().getDisappearableBlocks().contains(block.registry().material()) || !block.isFullCube()) {
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
            disguisePlayer(event.player, block)
        }
        eventNode.addListener(PlayerTickEvent::class.java) { event ->
            val newTime = moveTimer.getOrDefault(event.player, 0) + 1
            moveTimer[event.player] = newTime
            if (newTime >= SNAP_DELAY_TICKS) {
                val disguise = disguises[event.player]
                if (disguise?.snap == false) disguise.snap = true
            }
        }

        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            if (event.newPosition.samePoint(event.player.position)) return@addListener
            moveTimer[event.player] = 0L
            disguises[event.player]?.snap = false
            val disguise = disguises[event.player] ?: return@addListener
            disguise.displayEntity.pose = event.player.pose
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
            for (disguise in disguises.values) {
                if (target == disguise.interactionEntity) {
                    event.isCancelled = true
                    val owner = disguise.owner
                    parent.callEvent(
                        EntityAttackEvent(
                            event.attacker,
                            owner ?: return@addListener
                        )
                    )
                }
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
            ), disguise.owner.position)
        }
    }

    override fun deinitialize() {
        while (disguises.keys.size > 0) {
            undisguisePlayer(disguises.keys.first())
        }
        parent.players.forEach { player ->
            player.getAttribute(Attribute.GENERIC_SCALE).baseValue = 1.0
        }
    }

    fun disguisePlayer(player: Player, block: Block) {
        player.sendMessage(Component.text("You are now a ", BRAND_COLOR_PRIMARY_2) + Component.translatable(block.registry().translationKey(), BRAND_COLOR_PRIMARY_1))
        player.playSound(Sound.sound(
            SoundEvent.ENTITY_BREEZE_JUMP,
            Sound.Source.PLAYER,
            0.5f,
            0.7f
        ))
        val wasGlowing = disguises[player]?.displayEntity?.isGlowing == true
        if (isDisguised(player)) {
            disguises[player]!!.setBlock(block)
            return
        }
        val entity = DamageableBlockEntity(player)
        entity.setBlock(block)
        disguisePlayer(player, entity)
        entity.displayEntity.isGlowing = wasGlowing
    }

    private fun disguisePlayer(player: Player, disguise: DamageableBlockEntity) {
        disguises[player]?.remove()
        player.getAttribute(Attribute.GENERIC_SCALE).baseValue = 0.5 // Allows the player to fit through 1 block gaps
        player.isAutoViewable = false
        disguise.setInstance(player.instance ?: return, player.position)
        disguises[player] = disguise
    }

    private fun undisguisePlayer(player: Player) {
        if (!isDisguised(player)) return
        disguises[player]!!.remove()
        player.getAttribute(Attribute.GENERIC_SCALE).baseValue = 1.0
        player.isAutoViewable = true
        disguises.remove(player)
    }

    private fun isDisguised(player: Player): Boolean = disguises.containsKey(player)
}

/**
 * Combines a DisplayEntity and InteractionEntity to make a block display that can be attacked
 */
data class DamageableBlockEntity(
    val owner: Player,
    val displayEntity: Entity = Entity(EntityType.BLOCK_DISPLAY),
    val interactionEntity: Entity = Entity(EntityType.INTERACTION),
) {
    private val displayMeta: BlockDisplayMeta get() { return displayEntity.entityMeta as BlockDisplayMeta }
    private val interactionMeta: InteractionMeta get() { return interactionEntity.entityMeta as InteractionMeta }

    init {
        displayMeta.isHasNoGravity = true
        interactionMeta.isHasNoGravity = true
        interactionEntity.customName = owner.name
        interactionEntity.isCustomNameVisible = false

        // The display entity is left visible to the owner
        interactionEntity.updateViewableRule { owner.uuid != it.uuid }
    }

    /**
     * If snap is enabled, future calls to `teleport` will move the entity to the center of its block.
     */
    var snap: Boolean = false
        set(value) {
            field = value
            teleport(owner.position)
        }

    fun setBlock(block: Block) {
        displayMeta.setBlockState(block)
        // TODO assign height and width based on block shape
        interactionMeta.height = 1f
        interactionMeta.width = 1f
    }

    fun teleport(pos: Pos) {
        val newPos =
            if (snap) Pos(pos.blockX().toDouble(), pos.blockY().toDouble(), pos.blockZ().toDouble(), 0f, 0f)
            else Pos(pos.x - 0.5, pos.y, pos.z - 0.5, 0f, 0f)
        if (snap) {
            if (owner.instance.getBlock(newPos).compare(displayMeta.blockStateId)) {
                // Prevent players from snapping inside identical blocks
                owner.sendMessage(Component.text("You can't snap to this block!", NamedTextColor.RED))
                owner.playSound(Sound.sound(SoundEvent.BLOCK_NOTE_BLOCK_PLING, Sound.Source.PLAYER, 1.0f, 0.5f))
                return
            }
            owner.playSound(Sound.sound(SoundEvent.ENTITY_BREEZE_IDLE_GROUND, Sound.Source.PLAYER, 1.0f, 1.7f))
            owner.sendActionBar(Component.text("You have snapped to your current block.", BRAND_COLOR_PRIMARY_2))
        }
        displayMeta.setNotifyAboutChanges(false)
        displayMeta.posRotInterpolationDuration = 1
        displayMeta.transformationInterpolationStartDelta = -1
        displayMeta.setNotifyAboutChanges(true)
        displayEntity.teleport(newPos)
        val hitboxPos = Pos(newPos.x + 0.5, newPos.y, newPos.z + 0.5)
        interactionEntity.teleport(hitboxPos)
    }

    fun setInstance(instance: Instance, spawnPosition: Point) {
        displayEntity.setInstance(instance, spawnPosition)
        interactionEntity.setInstance(instance, spawnPosition)
    }

    fun remove() {
        displayEntity.remove()
        interactionEntity.remove()
    }
}