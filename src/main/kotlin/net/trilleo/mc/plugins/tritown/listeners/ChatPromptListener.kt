package net.trilleo.mc.plugins.tritown.listeners

import io.papermc.paper.event.player.AsyncChatEvent
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.trilleo.mc.plugins.tritown.utils.ChatPrompt
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Feeds chat into [ChatPrompt] when a player is answering a question.
 *
 * Runs at the lowest priority so an answer is taken before any chat plugin
 * formats or broadcasts it, and the event is cancelled so the answer — which
 * may be a permission node or a price — never reaches the channel.
 */
class ChatPromptListener : Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        if (!ChatPrompt.isWaiting(event.player)) return

        val message = PlainTextComponentSerializer.plainText().serialize(event.message())
        if (ChatPrompt.consume(event.player, message)) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        ChatPrompt.cancel(event.player)
    }
}
