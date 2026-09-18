package net.trilleo.mc.plugins.tritown.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage

/**
 * Turns the MiniMessage strings translations are written in into the
 * [Component]s the server actually sends.
 *
 * [Lang.tr] returns a string so that callers can substitute into it, which
 * leaves every caller needing the same final step. Going through one instance
 * here also means player-written text is escaped with the same MiniMessage
 * instance that parses it.
 */
object ComponentUtil {

    private val miniMessage = MiniMessage.miniMessage()

    /** Parses [message] as MiniMessage. */
    fun parse(message: String): Component = miniMessage.deserialize(message)

    /** Escapes MiniMessage tags in player-written [value] so it renders as typed. */
    fun escape(value: String): String = miniMessage.escapeTags(value)
}
