package com.bluedragonmc.games.huehunters.server.module

import com.bluedragonmc.server.Game
import com.bluedragonmc.server.module.GameModule
import net.kyori.adventure.sound.Sound
import net.minestom.server.MinecraftServer
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerMoveEvent
import net.minestom.server.sound.SoundEvent
import net.minestom.server.tag.Tag
import java.time.Duration

/**
 * Plays a goofy noise as you plunge to your death.
 */
class VoidSoundModule(val threshold: Double) : GameModule() {
    private val TAG_FALLING = Tag.Boolean("voidsound_falling")
    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        eventNode.addListener(PlayerMoveEvent::class.java) { event ->
            if (event.player.getTag(TAG_FALLING) == true) return@addListener
            if (event.newPosition.y < threshold) {
                event.player.setTag(TAG_FALLING, true)
                event.player.playSound(Sound.sound(
                    SoundEvent.ITEM_GOAT_HORN_SOUND_5,
                    Sound.Source.PLAYER,
                    1.0f,
                    1.0f
                ), Sound.Emitter.self())
                MinecraftServer.getSchedulerManager().buildTask {
                    event.player.removeTag(TAG_FALLING)
                }.delay(Duration.ofSeconds(7)).schedule()
            }
        }
    }
}