package com.bluedragonmc.games.huehunters.server.module

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.config.ConfigModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerTickEvent
import net.minestom.server.instance.block.Block
import net.minestom.server.item.Material
import net.minestom.server.network.packet.server.play.BlockChangePacket
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
            val blockX = playerPos.blockX()
            val newDisappearedBlocks = mutableSetOf<DisappearedBlock>()
            for (x in blockX - radius .. blockX + radius) {
                val blockY = playerPos.blockY()
                for (y in blockY - radius .. blockY + radius) {
                    val blockZ = playerPos.blockZ()
                    for (z in blockZ - radius .. blockZ + radius) {
                        val pos = Pos(x.toDouble(), y.toDouble(), z.toDouble())
                        // Circular radius
                        if (pos.distanceSquared(playerPos) > radius * radius) continue
                        // Color must match holding color to disappear
                        val block = p.instance.getBlock(pos)
                        if (disappearingMaterials.contains(block.registry().material())) {
                            newDisappearedBlocks += DisappearedBlock(pos, block)
                        }
                    }
                }
            }

            // Make old blocks reappear
            val iterator = disappearedBlocks.getOrDefault(p, mutableSetOf()).iterator()
            while (iterator.hasNext()) {
                val block = iterator.next()
                if (!newDisappearedBlocks.contains(block)) {
                    iterator.remove()
                    sendReappearPacket(p, block)
                }
            }

            // Make new blocks disappear
            newDisappearedBlocks.forEach { block ->
                disappear(p, block)
            }
        }
    }

    fun getHoldingColor(player: Player): String? {
        val colors = baseConfigNode.childrenMap()
        for ((color, items) in colors) {
            val materials = items.getList(Material::class.java) ?: continue
            for (item in materials) {
                if (item == player.itemInMainHand.material()) {
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
        sendDisappearPacket(player, block)
    }

    private fun sendDisappearPacket(player: Player, block: DisappearedBlock) {
        player.sendPacket(BlockChangePacket(block.position, Block.AIR))
    }

    private fun sendReappearPacket(player: Player, block: DisappearedBlock) {
        player.sendPacket(BlockChangePacket(block.position, block.block))
    }

    // TODO turn this into an event
    /**
     * Returns `true` if the player should have x-ray, false otherwise
     */
    abstract fun isXrayEnabled(player: Player): Boolean

    data class DisappearedBlock(
        val position: Pos,
        val block: Block,
    )
}