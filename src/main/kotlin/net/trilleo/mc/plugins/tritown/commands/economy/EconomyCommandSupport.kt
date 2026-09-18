package net.trilleo.mc.plugins.tritown.commands.economy

import net.kyori.adventure.text.minimessage.MiniMessage
import net.trilleo.mc.plugins.tritown.config.EconomySettings
import net.trilleo.mc.plugins.tritown.economy.Currency
import net.trilleo.mc.plugins.tritown.economy.CurrencyRegistry
import net.trilleo.mc.plugins.tritown.economy.EconomyFormat
import net.trilleo.mc.plugins.tritown.economy.EconomyService
import net.trilleo.mc.plugins.tritown.economy.Money
import net.trilleo.mc.plugins.tritown.economy.MoneyAccount
import net.trilleo.mc.plugins.tritown.economy.TownyAccountNaming
import net.trilleo.mc.plugins.tritown.enums.AccountType
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.command.CommandSender

/**
 * Shared plumbing for the economy commands.
 *
 * These live beside the commands rather than in the economy package because
 * they are about talking to a player, not about moving money.
 */

/** How many account names a single tab completion offers, to keep the packet small. */
internal const val NAME_SUGGESTION_LIMIT = 40

/**
 * Whether the economy commands register as top-level commands.
 *
 * Read when the command object is built, which works because the settings are
 * loaded in `onLoad`, long before the command registrar runs.
 */
internal fun topLevelAliases(): Boolean =
    EconomySettings.isLoaded && EconomySettings.snapshot.topLevelAliases

/**
 * Checks that the economy is usable, telling [sender] why if it is not.
 *
 * The economy can legitimately be switched off, or another plugin can be
 * supplying one that these commands do not drive.
 */
internal fun requireEconomy(sender: CommandSender): Boolean {
    if (EconomyService.isReady && CurrencyRegistry.isLoaded) return true
    sender.sendPrefixed(sender.tr("common.economy-unavailable"))
    return false
}

/** The currency these commands work in. */
internal fun primaryCurrency(): Currency = CurrencyRegistry.primary

/**
 * Parses a player-typed amount.
 *
 * Grouping separators are accepted because players copy amounts straight out of
 * chat, where they are formatted with them.
 */
internal fun parseAmount(input: String, currency: Currency): Money? {
    val value = input.replace(",", "").toDoubleOrNull() ?: return null
    if (!value.isFinite() || value < 0.0) return null
    return runCatching { currency.of(value) }.getOrNull()
}

/** Formats [money] for a chat message. */
internal fun display(money: Money, currency: Currency = primaryCurrency()): String =
    EconomyFormat.plain(currency, money)

/**
 * The name to show for [account].
 *
 * Town and nation banks carry Towny's configured prefix, which is meaningful to
 * Towny and noise to a player. Player-written names are escaped, since a town
 * can be called anything.
 */
internal fun displayName(account: MoneyAccount): String = displayName(account.name, account.type)

/** The name to show for an account called [name] of [type]. */
internal fun displayName(name: String, type: AccountType): String {
    val stripped = if (type.isGovernment) TownyAccountNaming.stripPrefix(name) else name
    return MiniMessage.miniMessage().escapeTags(stripped)
}

/** Resolves [name] to an account, telling [sender] when nothing answers to it. */
internal fun resolveOrTell(sender: CommandSender, name: String): MoneyAccount? {
    val account = EconomyService.resolveByName(name)
    if (account == null) {
        sender.sendPrefixed(sender.tr("common.no-account", "name" to MiniMessage.miniMessage().escapeTags(name)))
    }
    return account
}

/** What kind of account [type] is, in [sender]'s language. */
internal fun accountTypeName(sender: CommandSender, type: AccountType): String = when (type) {
    AccountType.PLAYER -> sender.tr("money.account-type.player")
    AccountType.TOWN -> sender.tr("money.account-type.town")
    AccountType.NATION -> sender.tr("money.account-type.nation")
    AccountType.NPC -> sender.tr("money.account-type.npc")
    AccountType.SERVER -> sender.tr("money.account-type.server")
    AccountType.UNKNOWN -> sender.tr("money.account-type.unknown")
}
