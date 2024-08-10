package com.bluedragonmc.games.huehunters.server.module

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.utils.component1
import com.bluedragonmc.server.utils.component2
import com.bluedragonmc.server.utils.component3
import com.bluedragonmc.server.utils.toBlockVec
import net.minestom.server.MinecraftServer
import net.minestom.server.coordinate.Pos
import net.minestom.server.coordinate.Vec
import net.minestom.server.entity.Entity
import net.minestom.server.entity.EntityType
import net.minestom.server.entity.Metadata
import net.minestom.server.entity.Player
import net.minestom.server.entity.metadata.display.AbstractDisplayMeta
import net.minestom.server.entity.metadata.display.BlockDisplayMeta
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.BlockChangePacket
import net.minestom.server.network.packet.server.play.EntityMetaDataPacket
import net.minestom.server.timer.TaskSchedule
import org.spongepowered.configurate.ConfigurationNode

abstract class ColorXrayModule(val radius: Int = 5) : GameModule() {
    private val disappearedBlocks = hashMapOf<Player, MutableSet<DisappearedBlock>>()
    private lateinit var baseConfigNode: ConfigurationNode
    final override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerTickEvent::class.java) { event ->
            val p = event.player
            if (!isXrayEnabled(p)) return@addListener
            val playerPos = p.position
            // Keys are colors, values are lists of blocks
            baseConfigNode = parent.getModule<ConfigModule>().getConfig().node("xray")

            // get the color of what you are holding
            val holdingColor = getHoldingColor(p) ?: return@addListener

            // All the blocks in the same color that you're holding - these blocks should disappear
            val disappearingMaterials = getColorBlocks(holdingColor) ?: return@addListener

            // Figure out which blocks should be invisible
            val blocksInRadius = mutableListOf<Pos>()

            val (blockX, blockY, blockZ) = playerPos.toBlockVec()
            for (x in blockX.toInt() - radius .. blockX.toInt() + radius) {
                for (y in blockY.toInt() - radius .. blockY.toInt() + radius) {
                    for (z in blockZ.toInt() - radius .. blockZ.toInt() + radius) {
                        val pos = Pos(x.toDouble(), y.toDouble(), z.toDouble())
                        // Circular radius
                        if (pos.distanceSquared(playerPos) > radius * radius) continue
                        // Color must match holding color to disappear
                        val block = p.instance.getBlock(pos)
                        if (disappearingMaterials.contains(block.registry().material())) {
                            blocksInRadius += pos
                        }
                    }
                }
            }

            // Make blocks outside the radius reappear
            disappearedBlocks[p]?.removeIf {
                if (it.position.distanceSquared(playerPos) > radius * radius || !disappearingMaterials.contains(it.block.registry().material())) {
                    playReappearEffect(p, it)
                    return@removeIf true
                }
                false
            }

            // Make blocks that have just entered the radius disappear
            for (blockPos in blocksInRadius) {
                if (!disappearedBlocks.containsKey(p) || disappearedBlocks[p]!!.none { it.position == blockPos }) {
                    disappear(p, DisappearedBlock(blockPos, p.instance.getBlock(blockPos), p))
                }
            }
        }
    }

    fun getHoldingColor(player: Player, heldSlot: Byte = player.heldSlot): String? {
        val colors = baseConfigNode.childrenMap()
        for ((color, items) in colors) {
            val materials = items.getList(Material::class.java) ?: continue
            for (item in materials) {
                if (item == player.inventory.getItemStack(heldSlot.toInt()).material()) {
                    return color.toString()
                }
            }
        }
        return null
    }

    /**
     * Returns all the blocks associated with the given color, based on the config file.
     */
    fun getColorBlocks(color: String) = baseConfigNode.node(color).getList(Material::class.java)

    fun disappear(player: Player, block: DisappearedBlock) {
        if (!disappearedBlocks.containsKey(player)) disappearedBlocks[player] = mutableSetOf()
        val blockSet = disappearedBlocks[player]!!
        if (blockSet.contains(block)) return
        blockSet += block
        playDisappearEffect(player, block)
    }

    private val EFFECT_DURATION = 5 // ticks?
    private val SMALL_SIZE = 0.4

    private fun playDisappearEffect(player: Player, block: DisappearedBlock) {
        player.instance.setBlock(block.position, Block.BARRIER)
        player.sendPacket(BlockChangePacket(block.position, Block.AIR))

        val temp = Entity(EntityType.BLOCK_DISPLAY)
        val meta = temp.entityMeta as BlockDisplayMeta
        meta.isHasNoGravity = true
        meta.setBlockState(block.block)
        meta.scale = Vec.ONE
        meta.transformationInterpolationStartDelta = -1
        meta.transformationInterpolationDuration = EFFECT_DURATION

        temp.setInstance(player.instance, block.position)
        MinecraftServer.getSchedulerManager().scheduleNextTick {
            meta.setNotifyAboutChanges(false)
            meta.translation = Vec(0.5 - SMALL_SIZE / 2, 0.5 - SMALL_SIZE / 2, 0.5 - SMALL_SIZE / 2)
            meta.scale = Vec(SMALL_SIZE, SMALL_SIZE, SMALL_SIZE)
            meta.setNotifyAboutChanges(true)
            player.sendPacket(EntityMetaDataPacket(temp.entityId, mapOf(AbstractDisplayMeta.OFFSET + 4 to Metadata.Vector3(Vec.ZERO))))
        }

        block.displayEntity = temp
    }

    private fun playReappearEffect(player: Player, block: DisappearedBlock) {
        if (block.displayEntity != null) {
            val meta = block.displayEntity!!.entityMeta as BlockDisplayMeta
            meta.setNotifyAboutChanges(false)
            meta.scale = Vec.ONE
            meta.transformationInterpolationStartDelta = -1
            meta.transformationInterpolationDuration = EFFECT_DURATION
            meta.translation = Vec.ZERO
            meta.setNotifyAboutChanges(true)
        }

        MinecraftServer.getSchedulerManager().buildTask {
            block.displayEntity?.remove()
            player.instance.setBlock(block.position, block.block)
        }.delay(TaskSchedule.tick(EFFECT_DURATION)).schedule()
    }

    // TODO turn this into an event
    /**
     * Returns `true` if the player should have x-ray, false otherwise
     */
    abstract fun isXrayEnabled(player: Player): Boolean

    data class DisappearedBlock(
        val position: Pos,
        val block: Block,
        val owner: Player,
    ) {
        var displayEntity: Entity? = null
    }
}