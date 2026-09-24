package net.trilleo.mc.plugins.tritown.utils

import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TranslatableComponent
import net.kyori.adventure.text.minimessage.translation.MiniMessageTranslator
import net.kyori.adventure.translation.GlobalTranslator
import java.util.*

/**
 * Lets text that many players see — an item's name and lore — be written once
 * and read by each player in their own language.
 *
 * An item is stored once, but seen by whoever holds it, trades for it or looks
 * at it in a shop. So its text is a translatable component keyed
 * `tritown:<lang key>` instead of a rendered sentence, and Paper renders it
 * through [GlobalTranslator] for each client as it is sent. This translator
 * answers those keys out of [Lang]. Because the item itself only carries keys,
 * two copies are always identical and stack whoever made them.
 *
 * Only `tritown:` keys are answered; every other translation, the game's own
 * included, is left to whoever owns it.
 */
object LangTranslator : MiniMessageTranslator() {

    private const val NAMESPACE = "tritown:"
    private val NAME = Key.key("tritown", "lang")

    /** A component that shows [key] from the language files in each viewer's language. */
    fun component(key: String): TranslatableComponent = Component.translatable(NAMESPACE + key)

    override fun name(): Key = NAME

    override fun getMiniMessageString(key: String, locale: Locale): String? {
        if (!key.startsWith(NAMESPACE)) return null
        return Lang.find(locale, key.removePrefix(NAMESPACE))
    }

    fun register() {
        GlobalTranslator.translator().addSource(this)
    }

    fun unregister() {
        GlobalTranslator.translator().removeSource(this)
    }
}
