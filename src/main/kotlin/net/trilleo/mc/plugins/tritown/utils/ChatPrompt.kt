package net.trilleo.mc.plugins.tritown.utils

import net.trilleo.mc.plugins.tritown.Main
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Asks a player to type something in chat and hands the answer back.
 *
 * A chest menu has nowhere to type, so anything free-form an editor needs — a
 * name, an exact price, a permission node — is asked for in chat instead. The
 * menu closes, the question is sent, and the answer reopens whatever comes next.
 *
 * ### Usage
 *
 * ```kotlin
 * player.closeInventory()
 * ChatPrompt.ask(player, player.tr("gui.shop-editor.prompt-price")) { input ->
 *     val price = input.toDoubleOrNull() ?: return@ask
 *     entry.buy = ShopCost(price)
 * }
 * ```
 *
 * The answer arrives on the server thread, so a callback may touch Bukkit
 * freely. Typing the cancel word, quitting, or being asked something else
 * instead drops the pending question without running the callback.
 */
object ChatPrompt {

    private val pending = ConcurrentHashMap<UUID, (String) -> Unit>()

    /**
     * Asks [player] the question [message], then runs [onInput] with what they type.
     *
     * Any question already waiting for them is dropped, so two menus cannot
     * both be listening at once.
     */
    fun ask(player: Player, message: String, onInput: (String) -> Unit) {
        pending[player.uniqueId] = onInput
        player.sendPrefixed(message)
        player.sendPrefixed(player.tr("common.prompt-cancel"))
    }

    /** Whether [player] is being asked something. */
    fun isWaiting(player: Player): Boolean = pending.containsKey(player.uniqueId)

    /** Drops any question waiting for [player] without running its callback. */
    fun cancel(player: Player) {
        pending.remove(player.uniqueId)
    }

    /**
     * Feeds [message] to the question [player] was asked.
     *
     * Called from the chat listener, which runs off the main thread, so the
     * callback is handed to the scheduler rather than run where it arrives.
     *
     * @return `true` when the message was an answer and should not reach chat
     */
    fun consume(player: Player, message: String): Boolean {
        val callback = pending.remove(player.uniqueId) ?: return false

        val answer = message.trim()
        if (answer.equals(CANCEL_WORD, ignoreCase = true)) {
            player.sendPrefixed(player.tr("common.prompt-cancelled"))
            return true
        }

        Bukkit.getScheduler().runTask(Main.instance, Runnable { callback(answer) })
        return true
    }

    /** The word a player types to back out of a question. */
    const val CANCEL_WORD = "cancel"
}
