package net.trilleo.mc.plugins.tritown.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender

/**
 * Utility for sending prefix-decorated messages to command senders.
 *
 * Call [init] once during plugin startup (and again after a config reload) to
 * load the configured prefix. The prefix string may be plain text or a
 * MiniMessage-formatted string such as `"<gray>[<gold>TriTown<gray>]"`.
 *
 * ### Usage
 *
 * ```kotlin
 * import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
 *
 * // Plain text or MiniMessage string
 * sender.sendPrefixed("Hello!")
 * player.sendPrefixed("<green>Operation successful!")
 *
 * // Adventure Component
 * player.sendPrefixed(Component.text("Hello!", NamedTextColor.GREEN))
 * ```
 *
 * Every [CommandSender] is an Adventure `Audience` on Paper, so the console and
 * command blocks receive the same prefixed output as players.
 */
object MessageUtil {

    private val mm = MiniMessage.miniMessage()
    private var prefixComponent: Component = Component.empty()

    /**
     * Loads the prefix from [prefixString].
     *
     * [prefixString] may be a plain text string (e.g. `"[TriTown]"`) or
     * a MiniMessage-formatted string (e.g. `"<gray>[<gold>TriTown<gray>]"`).
     * Call this method during plugin startup and again whenever the configuration
     * is reloaded.
     *
     * @param prefixString the prefix text to display before every message
     */
    fun init(prefixString: String) {
        prefixComponent = mm.deserialize(prefixString)
    }

    /**
     * Sends [message] to [sender] with the configured prefix prepended.
     *
     * [message] may be a plain text string or a MiniMessage-formatted string.
     *
     * @param sender  the recipient
     * @param message the message string (plain text or MiniMessage-formatted)
     */
    fun sendPrefixed(sender: CommandSender, message: String) {
        sender.sendMessage(build(mm.deserialize(message)))
    }

    /**
     * Sends [message] to [sender] with the configured prefix prepended.
     *
     * @param sender  the recipient
     * @param message the [Component] to send
     */
    fun sendPrefixed(sender: CommandSender, message: Component) {
        sender.sendMessage(build(message))
    }

    private fun build(message: Component): Component =
        Component.text()
            .append(prefixComponent)
            .append(Component.space())
            .append(message)
            .build()
}

/**
 * Sends [message] to this sender with the plugin prefix prepended.
 *
 * [message] may be a plain text string or a MiniMessage-formatted string.
 *
 * @receiver the recipient, which may be a player, the console or a command block
 * @param message the message string (plain text or MiniMessage-formatted)
 */
fun CommandSender.sendPrefixed(message: String) = MessageUtil.sendPrefixed(this, message)

/**
 * Sends [message] to this sender with the plugin prefix prepended.
 *
 * @receiver the recipient, which may be a player, the console or a command block
 * @param message the [Component] to send
 */
fun CommandSender.sendPrefixed(message: Component) = MessageUtil.sendPrefixed(this, message)
