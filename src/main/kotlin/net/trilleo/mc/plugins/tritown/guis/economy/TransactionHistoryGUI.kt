package net.trilleo.mc.plugins.tritown.guis.economy

import net.kyori.adventure.text.minimessage.MiniMessage
import net.trilleo.mc.plugins.tritown.config.EconomySettings
import net.trilleo.mc.plugins.tritown.economy.CurrencyRegistry
import net.trilleo.mc.plugins.tritown.economy.EconomyFormat
import net.trilleo.mc.plugins.tritown.economy.EconomyService
import net.trilleo.mc.plugins.tritown.economy.MoneyAccount
import net.trilleo.mc.plugins.tritown.economy.TownyAccountNaming
import net.trilleo.mc.plugins.tritown.economy.TransactionReason
import net.trilleo.mc.plugins.tritown.economy.TransactionRecord
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.TransactionType
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.utils.Lang
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Shows an account's recent transactions.
 *
 * GUIs are singletons — one instance serves every viewer — so the account being
 * looked at and the rendered items are held per viewer. Both are built once in
 * [open], because `getItems` is called on every render and again for every page
 * count, and re-rendering there would do the same work several times per click.
 *
 * The view is a snapshot taken when it was opened and does not update itself,
 * which is the right behaviour for a record of what already happened.
 */
class TransactionHistoryGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.history.title",
    rows = 6,
    fillMode = FillMode.NONE,
) {

    private val snapshots = ConcurrentHashMap<UUID, List<ItemStack>>()

    /** Opens the history of [subject] for [viewer]. */
    fun open(viewer: Player, subject: MoneyAccount) {
        snapshots[viewer.uniqueId] = render(viewer, subject)
        GUIManager.open(viewer, ID)
    }

    override fun getItems(player: Player): List<ItemStack> = snapshots[player.uniqueId] ?: emptyList()

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        event.isCancelled = true
    }

    override fun onClose(event: InventoryCloseEvent) {
        // The base class clears the page it is holding for this viewer here.
        super.onClose(event)
        snapshots.remove((event.player as? Player)?.uniqueId ?: return)
    }

    private fun render(viewer: Player, subject: MoneyAccount): List<ItemStack> {
        val currency = CurrencyRegistry.primary
        val records = EconomyService.history(subject.uuid)
        val formatter = timestampFormatter()

        val header = itemStack(Material.PLAYER_HEAD) {
            name(viewer.tr("gui.history.header", "name" to escape(displayName(subject))))
            meta {
                lore(
                    LoreUtil.wrapLore(
                        viewer.tr(
                            "gui.history.header-lore",
                            "balance" to EconomyFormat.plain(currency, subject.balance(currency.id)),
                            "count" to records.size,
                        )
                    )
                )
            }
        }

        if (records.isEmpty()) {
            val empty = itemStack(Material.BARRIER) {
                name(viewer.tr("gui.history.empty"))
                meta { lore(LoreUtil.wrapLore(viewer.tr("gui.history.empty-lore"))) }
            }
            return listOf(header, empty)
        }

        return listOf(header) + records.map { entry(viewer, it, formatter) }
    }

    private fun entry(viewer: Player, record: TransactionRecord, formatter: DateTimeFormatter): ItemStack {
        val currency = CurrencyRegistry.get(record.currency) ?: CurrencyRegistry.primary
        val amount = EconomyFormat.plain(currency, record.money)
        val signed = viewer.tr(if (record.type.isCredit) "gui.history.credit" else "gui.history.debit", "amount" to amount)

        val lore = buildString {
            append(viewer.tr("gui.history.type", "type" to label(viewer, record.type)))
            append("<newline>")
            append(
                viewer.tr(
                    "gui.history.balance-after",
                    "balance" to EconomyFormat.plain(currency, record.balance),
                )
            )
            counterpartyName(record)?.let {
                append("<newline>")
                append(viewer.tr("gui.history.with", "name" to escape(it)))
            }
            if (record.reason.isNotBlank()) {
                append("<newline>")
                append(viewer.tr("gui.history.reason", "reason" to TransactionReason.translate(viewer, record.reason)))
            }
            append("<newline>")
            append(viewer.tr("gui.history.source", "source" to source(viewer, record.source)))
            append("<newline>")
            append(viewer.tr("gui.history.time", "time" to formatter.format(Instant.ofEpochMilli(record.timestamp))))
        }

        return itemStack(material(record.type)) {
            name(signed)
            meta { lore(LoreUtil.wrapLore(lore)) }
        }
    }

    private fun counterpartyName(record: TransactionRecord): String? {
        val other = record.counterparty ?: return null
        val account = EconomyService.account(other) ?: return null
        return displayName(account)
    }

    private fun displayName(account: MoneyAccount): String =
        if (account.type.isGovernment) TownyAccountNaming.stripPrefix(account.name) else account.name

    /** Where the money moved from, as TriTown recorded it; another plugin's source is shown as it was written. */
    private fun source(viewer: Player, source: String): String =
        Lang.find(viewer, "money.source.$source") ?: escape(source)

    private fun label(viewer: Player, type: TransactionType): String = when (type) {
        TransactionType.DEPOSIT -> viewer.tr("money.transaction.deposit")
        TransactionType.WITHDRAW -> viewer.tr("money.transaction.withdraw")
        TransactionType.TRANSFER_IN -> viewer.tr("money.transaction.transfer-in")
        TransactionType.TRANSFER_OUT -> viewer.tr("money.transaction.transfer-out")
        TransactionType.SET -> viewer.tr("money.transaction.set")
        TransactionType.CLOSED -> viewer.tr("money.transaction.closed")
    }

    private fun material(type: TransactionType): Material = when (type) {
        TransactionType.DEPOSIT, TransactionType.TRANSFER_IN -> Material.LIME_DYE
        TransactionType.WITHDRAW, TransactionType.TRANSFER_OUT -> Material.RED_DYE
        TransactionType.SET -> Material.PAPER
        TransactionType.CLOSED -> Material.BARRIER
    }

    private fun timestampFormatter(): DateTimeFormatter {
        val pattern = EconomySettings.snapshot.history.timeFormat
        return runCatching { DateTimeFormatter.ofPattern(pattern, Locale.ROOT) }
            .getOrElse { DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT) }
            .withZone(ZoneId.systemDefault())
    }

    /** Account names, and the reasons attached to them, can come from a player-named town. */
    private fun escape(text: String): String = MiniMessage.miniMessage().escapeTags(text)

    companion object {
        const val ID = "eco-history"
    }
}
