package net.trilleo.mc.plugins.tritown.guis.admin

import net.trilleo.mc.plugins.tritown.config.EconomySettings
import net.trilleo.mc.plugins.tritown.economy.CurrencyRegistry
import net.trilleo.mc.plugins.tritown.economy.EconomyFormat
import net.trilleo.mc.plugins.tritown.economy.Money
import net.trilleo.mc.plugins.tritown.enums.AccountType
import net.trilleo.mc.plugins.tritown.enums.FlowCategory
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The pieces the admin panel's menus draw with.
 *
 * Every figure the panel shows is a number that has to read the same wherever
 * it appears, so the money, the percentages and the cards themselves are built
 * in one place rather than formatted again in each menu.
 *
 * Amounts arrive as minor units, which is how the economy holds and totals
 * them; they are converted to text at this boundary and nowhere earlier.
 */
object PanelRender {

    private val percentFormat = DecimalFormat("0.0", DecimalFormatSymbols(Locale.ROOT))
    private val rateFormat = DecimalFormat("0.00", DecimalFormatSymbols(Locale.ROOT))

    /** [minor] units of the primary currency, ready to embed in MiniMessage. */
    fun money(minor: Long): String =
        if (!CurrencyRegistry.isLoaded) minor.toString()
        else ComponentUtil.escape(EconomyFormat.plain(CurrencyRegistry.primary, Money(minor)))

    /**
     * [minor] as a change: coloured and signed by the translation, so a figure
     * that went up never reads the same as one that went down.
     */
    fun delta(viewer: Player, minor: Long): String = when {
        minor > 0L -> viewer.tr("gui.admin-economy.delta-up", "amount" to money(minor))
        minor < 0L -> viewer.tr("gui.admin-economy.delta-down", "amount" to money(-minor))
        else -> viewer.tr("gui.admin-economy.delta-flat")
    }

    /** [fraction] as a percentage, e.g. `12.4%`. Anything that is not a number reads as zero. */
    fun percent(fraction: Double): String =
        if (!fraction.isFinite()) "0.0%" else "${percentFormat.format(fraction * 100.0)}%"

    /** [fraction] as a percentage at two digits, for rates small enough that one would round them away. */
    fun rate(fraction: Double): String =
        if (!fraction.isFinite()) "0.00%" else "${rateFormat.format(fraction * 100.0)}%"

    /** [part] as a share of [whole], or zero when there is no whole to share. */
    fun share(part: Long, whole: Long): Double = if (whole <= 0L) 0.0 else part.toDouble() / whole.toDouble()

    /** A menu card: a name and a block of wrapped lore. Blank lines are kept, so a card can be grouped. */
    fun card(material: Material, name: String, lines: List<String>): ItemStack = itemStack(material) {
        name(name)
        meta { lore(LoreUtil.wrapLore(lines.joinToString("<newline>"))) }
    }

    /** [millis] in the format `economy.history.time-format` sets, which is already the panel's clock elsewhere. */
    fun time(millis: Long): String = formatter().format(Instant.ofEpochMilli(millis))

    /** A category's name in [viewer]'s language. */
    fun categoryName(viewer: Player, category: FlowCategory): String = viewer.tr(category.key)

    /** An account type's name in [viewer]'s language, as the plural a total is read in. */
    fun holderName(viewer: Player, type: AccountType): String = when (type) {
        AccountType.PLAYER -> viewer.tr("gui.admin-economy.holder-players")
        AccountType.TOWN -> viewer.tr("gui.admin-economy.holder-towns")
        AccountType.NATION -> viewer.tr("gui.admin-economy.holder-nations")
        AccountType.NPC -> viewer.tr("gui.admin-economy.holder-npcs")
        AccountType.SERVER -> viewer.tr("gui.admin-economy.holder-server")
        AccountType.UNKNOWN -> viewer.tr("gui.admin-economy.holder-unknown")
    }

    private fun formatter(): DateTimeFormatter {
        val pattern = if (EconomySettings.isLoaded) EconomySettings.snapshot.history.timeFormat else FALLBACK_TIME
        return runCatching { DateTimeFormatter.ofPattern(pattern, Locale.ROOT) }
            .getOrElse { DateTimeFormatter.ofPattern(FALLBACK_TIME, Locale.ROOT) }
            .withZone(ZoneId.systemDefault())
    }

    private const val FALLBACK_TIME = "yyyy-MM-dd HH:mm"
}
