package com.bluedragonmc.games.huehunters.server.module

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.DependsOn
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.module.config.ConfigModule
import com.bluedragonmc.server.module.instance.InstanceModule
import net.minestom.server.coordinate.Pos
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.instance.Chunk
import net.minestom.server.instance.Instance
import net.minestom.server.instance.block.Block
import org.spongepowered.configurate.objectmapping.ConfigSerializable
import java.util.concurrent.CompletableFuture
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Replaces blocks in the specified instances according to the map configuration.
 * Adjacent blocks are grouped.
 * A random item from the "replace-with" list is chosen for each group.
 * Instances are considered separately.
 *
 * Example:
 * ```yaml
 * replacements:
 *   - bounding-box:
 *       start: 0, 0, 0
 *       end: 100, 100, 100
 *     find:
 *     - minecraft:stone
 *     replace-with:
 *     - minecraft:stone_bricks
 * ```
 */
@DependsOn(InstanceModule::class, ConfigModule::class)
class BlockReplacerModule(private val instances: Iterable<Instance>) : GameModule() {

    @ConfigSerializable
    data class Replacement(
        val boundingBox: BoundingBox,
        val find: List<Block>,
        val replaceWith: List<Block>,
    ) {
        @Suppress("unused") // Configurate requires a zero-argument constructor for serializable objects
        constructor() : this(BoundingBox(), emptyList(), emptyList())
    }

    @ConfigSerializable
    data class BoundingBox(
        val start: Pos,
        val end: Pos
    ) {
        constructor() : this(Pos(0.0, 0.0, 0.0), Pos(0.0, 0.0, 0.0))
    }

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        val cm = parent.getModule<ConfigModule>()
        val replacements = cm.getConfig().node("replacements").getList(Replacement::class.java)
        instances.forEach { instance ->
            replacements?.forEach { (boundingBox, find, replaceWith) ->
                // Preload chunks
                val futures = mutableListOf<CompletableFuture<Chunk>>()
                for (x in range(floor(boundingBox.start.blockX() / 16.0).toInt(), ceil(boundingBox.end.blockX() / 16.0).toInt())) {
                    for (z in range(floor(boundingBox.start.blockZ() / 16.0).toInt(), ceil(boundingBox.end.blockZ() / 16.0).toInt())) {
                        futures += instance.loadChunk(x, z)
                    }
                }

                CompletableFuture.allOf(*futures.toTypedArray()).join()
                // Replace blocks
                for (x in range(boundingBox.start.blockX(), boundingBox.end.blockX())) {
                    for (y in range(boundingBox.start.blockY(), boundingBox.end.blockY())) {
                        for (z in range(boundingBox.start.blockZ(), boundingBox.end.blockZ())) {
                            val b = instance.getBlock(x, y, z)
                            if (find.any { it.compare(b) }) {
                                replaceClump(
                                    instance,
                                    Pos(x.toDouble(), y.toDouble(), z.toDouble()),
                                    replaceWith.random()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun replaceClump(instance: Instance, centerPos: Pos, replaceWith: Block) {
        val checked = mutableListOf<Pos>()
        val queue = mutableListOf(centerPos)
        val blocks = mutableListOf<Pos>()
        val centerBlock = instance.getBlock(centerPos)

        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            if (checked.contains(pos)) continue
            checked.add(pos)
            if (instance.getBlock(pos).compare(centerBlock)) {
                blocks.add(pos)
                queue.addAll(getAdjacent(pos))
            }
        }

        for (block in blocks) {
            instance.setBlock(block, replaceWith)
        }
    }

    private fun getAdjacent(pos: Pos): List<Pos> {
        return listOf(
            Pos(pos.blockX().toDouble() - 1.0, pos.blockY().toDouble(), pos.blockZ().toDouble()),
            Pos(pos.blockX().toDouble() + 1.0, pos.blockY().toDouble(), pos.blockZ().toDouble()),
            Pos(pos.blockX().toDouble(), pos.blockY().toDouble() - 1.0, pos.blockZ().toDouble()),
            Pos(pos.blockX().toDouble(), pos.blockY().toDouble() + 1.0, pos.blockZ().toDouble()),
            Pos(pos.blockX().toDouble(), pos.blockY().toDouble(), pos.blockZ().toDouble() - 1.0),
            Pos(pos.blockX().toDouble(), pos.blockY().toDouble(), pos.blockZ().toDouble() + 1.0),
        )
    }

    private fun range(num1: Int, num2: Int): IntRange {
        return min(num1, num2)..max(num1, num2)
    }
}
