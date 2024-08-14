package com.bluedragonmc.games.huehunters.server.module

import com.bluedragonmc.server.BRAND_COLOR_PRIMARY_2
import com.bluedragonmc.server.Game
import com.bluedragonmc.server.event.GameStartEvent
import com.bluedragonmc.server.event.GameStateChangedEvent
import com.bluedragonmc.server.event.PlayerLeaveGameEvent
import com.bluedragonmc.server.module.GameModule
import com.bluedragonmc.server.utils.GameState
import com.bluedragonmc.server.utils.manage
import com.bluedragonmc.server.utils.plus
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.title.Title
import net.kyori.adventure.title.TitlePart
import net.minestom.server.MinecraftServer
import net.minestom.server.entity.Player
import net.minestom.server.event.Event
import net.minestom.server.event.EventNode
import net.minestom.server.event.player.PlayerSpawnEvent
import net.minestom.server.event.player.PlayerUseItemEvent
import net.minestom.server.item.ItemComponent
import net.minestom.server.item.ItemStack
import net.minestom.server.item.Material
import java.time.Duration

/**
 * Gives each player a full hotbar of items they can click to vote to start the game.
 * When the majority of players vote to start, the countdown starts.
 */
class VoteStartModule : GameModule() {

    private val voteStartItem = ItemStack.of(Material.GREEN_CONCRETE).with(
        ItemComponent.ITEM_NAME,
        Component.text("Vote to start ", NamedTextColor.GREEN) + Component.text("(Right click)", NamedTextColor.GRAY)
    )
    private val cancelVoteItem = ItemStack.of(Material.RED_CONCRETE).with(
        ItemComponent.ITEM_NAME,
        Component.text("Cancel vote ", NamedTextColor.RED) + Component.text("(Right click)", NamedTextColor.GRAY)
    )

    private var votes = mutableListOf<Player>()

    private lateinit var parent: Game

    override fun initialize(parent: Game, eventNode: EventNode<Event>) {
        this.parent = parent
        eventNode.addListener(PlayerSpawnEvent::class.java) { event ->
            fill(event.player, voteStartItem)
        }
        eventNode.addListener(PlayerUseItemEvent::class.java) { event ->
            if (event.hand != Player.Hand.MAIN) return@addListener

            when (event.itemStack) {
                voteStartItem -> {
                    votes += event.player
                    fill(event.player, cancelVoteItem)
                }

                cancelVoteItem -> {
                    votes -= event.player
                    fill(event.player, voteStartItem)
                }

                else -> {}
            }
            event.isCancelled = true
            update()
        }
        eventNode.addListener(PlayerLeaveGameEvent::class.java) { event ->
            votes.remove(event.player)
            update()
        }
        MinecraftServer.getSchedulerManager().buildTask {
            if (countdown != null) {
                if (countdown!! > 0) {
                    parent.showTitle(
                        Title.title(
                            Component.text(countdown!!, BRAND_COLOR_PRIMARY_2),
                            Component.empty(),
                            Title.Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ZERO)
                        )
                    )
                } else {
                    parent.sendTitlePart(
                        TitlePart.TITLE,
                        Component.text("GO!", NamedTextColor.GREEN)
                            .decorate(TextDecoration.BOLD)
                    )
                    cancelCountdown()
                    clearPlayerInventories()
                    parent.callEvent(GameStartEvent(parent))
                    parent.state = GameState.INGAME
                    countdown = null
                    votes.clear()
                }

                countdown = countdown?.minus(1)
            }
        }.repeat(Duration.ofSeconds(1)).schedule().manage(parent)
        eventNode.addListener(GameStateChangedEvent::class.java) { event ->
            if (event.newState != GameState.WAITING && event.newState != GameState.STARTING) {
                clearPlayerInventories()
            }
        }
    }

    private fun fill(player: Player, item: ItemStack) {
        for (i in 0..8) {
            player.inventory.setItemStack(i, item)
        }
    }

    private fun clearPlayerInventories() {
        for (player in parent.players) {
            for ((index, stack) in player.inventory.itemStacks.withIndex()) {
                if (stack == voteStartItem || stack == cancelVoteItem) {
                    player.inventory.setItemStack(index, ItemStack.AIR)
                }
            }
        }
    }

    private var countdown: Int? = null

    private fun update() {
        if (parent.players.size >= 2 && votes.size >= parent.players.size / 2f) {
            startCountdown()
        } else if (countdown != null) {
            cancelCountdown()
        }
    }

    private fun startCountdown() {
        if (countdown == null) {
            countdown = 5
        }
        parent.state = GameState.STARTING
    }

    private fun cancelCountdown() {
        if (countdown != null) {
            parent.showTitle(
                Title.title(
                    Component.text("Cancelled!", NamedTextColor.RED),
                    Component.text("Vote to start using the items in your hotbar", NamedTextColor.RED),
                    Title.Times.times(Duration.ZERO, Duration.ofSeconds(5), Duration.ofSeconds(1))
                )
            )
        }
        countdown = null
        if (parent.state == GameState.STARTING) {
            parent.state = GameState.WAITING
        }
    }

    fun hasVoted(player: Player) = votes.contains(player)
}