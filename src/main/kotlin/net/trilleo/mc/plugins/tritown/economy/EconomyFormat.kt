package net.trilleo.mc.plugins.tritown.economy

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

/**
 * Renders money for display.
 *
 * Two surfaces, deliberately different: [plain] produces the string Vault hands
 * to other plugins, which print it verbatim and must not receive MiniMessage
 * tags, while [rich] produces the component TriTown puts in its own messages.
 *
 * Both are safe to call from any thread. `DecimalFormat` is not thread-safe and
 * Towny calls Vault's `format` from its own executor, so the formatters are held
 * per thread and rebuilt whenever [invalidate] bumps the generation after a
 * config reload.
 */
object EconomyFormat {

    private val mm = MiniMessage.miniMessage()

    @Volatile
    private var generation: Long = 0L

    private val locals = ThreadLocal.withInitial { LocalFormatters() }

    /** Discards every cached formatter, so the next call picks up reloaded currency settings. */
    fun invalidate() {
        generation++
    }

    /** Formats [amount] of the primary currency for Vault. */
    fun plain(amount: Double): String {
        val currency = CurrencyRegistry.primary
        return plain(currency, currency.of(amount))
    }

    /** Formats [money] of [currency] as plain text, with no MiniMessage tags. */
    fun plain(currency: Currency, money: Money): String =
        apply(currency.plainFormat, currency, money)

    /** Formats [money] of [currency] as a component, using the currency's MiniMessage pattern. */
    fun rich(currency: Currency, money: Money): Component =
        mm.deserialize(apply(currency.richFormat, currency, money))

    /** Formats [amount] of the primary currency as a component. */
    fun rich(amount: Double): Component {
        val currency = CurrencyRegistry.primary
        return rich(currency, currency.of(amount))
    }

    /** The bare number, grouped and at the currency's scale, without symbol or name. */
    fun amount(currency: Currency, money: Money): String =
        formatter(currency).format(currency.toDouble(money))

    private fun apply(pattern: String, currency: Currency, money: Money): String =
        pattern
            .replace("%symbol%", currency.symbol)
            .replace("%amount%", amount(currency, money))
            .replace("%currency%", currency.nameFor(money))

    private fun formatter(currency: Currency): DecimalFormat {
        val cache = locals.get()
        if (cache.generation != generation) {
            cache.generation = generation
            cache.byCurrency.clear()
        }
        return cache.byCurrency.getOrPut(currency.id) { build(currency) }
    }

    // Locale.ROOT keeps the separators stable no matter what locale the server
    // starts under, because other plugins parse what Vault's format returns.
    private fun build(currency: Currency): DecimalFormat =
        DecimalFormat("#,##0", DecimalFormatSymbols(Locale.ROOT)).apply {
            minimumFractionDigits = currency.fractionalDigits
            maximumFractionDigits = currency.fractionalDigits
            isGroupingUsed = true
        }

    private class LocalFormatters {
        var generation: Long = -1L
        val byCurrency = HashMap<String, DecimalFormat>()
    }
}
