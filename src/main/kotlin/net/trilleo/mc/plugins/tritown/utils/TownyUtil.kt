package net.trilleo.mc.plugins.tritown.utils

import com.palmergames.bukkit.towny.TownySettings
import com.palmergames.bukkit.towny.`object`.Government
import com.palmergames.bukkit.towny.`object`.Nation
import com.palmergames.bukkit.towny.`object`.Town
import com.palmergames.util.TimeMgmt
import org.bukkit.entity.Player

/**
 * Reads and formats Towny data for TriTown's own displays.
 *
 * Anything that comes from Towny is player-written — town names, boards, mayor
 * names — so it must pass through [text] before being embedded in a MiniMessage
 * string. Nothing here is cached: Towny stays the source of truth, and every
 * value is re-read when it is needed.
 */
object TownyUtil {

    private val legacyCodes = Regex("[&§][0-9a-fk-orx]", RegexOption.IGNORE_CASE)

    /** Strips legacy colour codes and escapes MiniMessage tags in player-written [value]. */
    fun text(value: String): String = ComponentUtil.escape(value.replace(legacyCodes, ""))

    /** A Towny object name (underscores shown as spaces), safe for MiniMessage. */
    fun name(name: String): String = text(name.replace('_', ' '))

    /** Formats [amount] with the server's currency, or `-` when no economy is available. */
    fun money(amount: Double): String =
        if (EconomyUtil.isAvailable) text(EconomyUtil.format(amount)) else "-"

    /**
     * Formats the balance of [government]'s bank account.
     *
     * Towny's cached balance is used rather than a live lookup, because this is
     * read once per player per sidebar refresh and the real balance may sit
     * behind another plugin's blocking economy call.
     */
    fun balance(government: Government): String = money(government.account.cachedBalance)

    /** What [town] is charged at the next new day, including overclaim and neutrality penalties. */
    fun townUpkeep(town: Town): Double {
        if (!TownySettings.isTaxingDaily()) return 0.0
        val upkeep = if (town.hasUpkeep()) TownySettings.getTownUpkeepCost(town) else 0.0
        val penalty = if (town.isOverClaimed) TownySettings.getTownPenaltyUpkeepCost(town) else 0.0
        val neutrality = if (town.isNeutral) TownySettings.getTownNeutralityCost(town) else 0.0
        return upkeep + penalty + neutrality
    }

    /** What [nation] is charged at the next new day, including its neutrality cost. */
    fun nationUpkeep(nation: Nation): Double {
        if (!TownySettings.isTaxingDaily()) return 0.0
        val neutrality = if (nation.isNeutral) TownySettings.getNationNeutralityCost(nation) else 0.0
        return TownySettings.getNationUpkeepCost(nation) + neutrality
    }

    /** Whether [town] cannot cover [townUpkeep] out of its bank at the next new day. */
    fun cannotAffordUpkeep(town: Town): Boolean {
        if (town.isRuined || town.isBankrupt) return false
        val due = townUpkeep(town)
        return due > 0.0 && due > town.account.cachedBalance
    }

    /** Seconds until Towny's next new day, when taxes and upkeep are collected. */
    fun secondsUntilNewDay(): Long = TimeMgmt.townyTime(true)

    /** A duration of [seconds] as hours and minutes in [player]'s language. */
    fun duration(player: Player, seconds: Long): String =
        player.tr("common.duration", "hours" to seconds / 3600, "minutes" to seconds % 3600 / 60)

    /** An On/Off label in [player]'s language. */
    fun onOff(player: Player, value: Boolean): String = player.tr(if (value) "common.on" else "common.off")
}
