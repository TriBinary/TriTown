package net.trilleo.mc.plugins.tritown.guis.admin

import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.config.EconomySettings
import net.trilleo.mc.plugins.tritown.config.ShopSettings
import net.trilleo.mc.plugins.tritown.economy.*
import net.trilleo.mc.plugins.tritown.enums.AccountType
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.FlowCategory
import net.trilleo.mc.plugins.tritown.guis.shop.ShopRender
import net.trilleo.mc.plugins.tritown.registration.GUIFrame
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
import net.trilleo.mc.plugins.tritown.shops.ShopManager
import net.trilleo.mc.plugins.tritown.utils.ComponentUtil
import net.trilleo.mc.plugins.tritown.utils.EconomyUtil
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * What the server's economy is doing, on one screen.
 *
 * The top row is the economy as it stands — how much currency exists, who holds
 * it and how unevenly. The middle row is what has moved over the window the
 * viewer has chosen, which is where money is created and where it drains away.
 * The bottom row is the same window drawn as a column per slice, so a payday, a
 * duplication bug or a shop nobody can afford shows up as a shape rather than a
 * number.
 *
 * Nothing here is cached: the figures are read from [EconomyPulse] when the menu
 * is drawn, which costs a walk over the hours still kept and no disk at all. The
 * ledger itself is only ever measured on the flush task, so a menu that opens
 * shows the last measurement rather than taking a new one.
 */
class EconomyPanelGUI : PluginGUI(
    id = ID,
    titleKey = "gui.admin-economy.title",
    rows = ROWS,
    fillMode = FillMode.NONE,
) {

    override fun setup(player: Player, inventory: Inventory) {
        GUIFrame.draw(inventory, GUIFrame.contentSlots(ROWS))
        render(player, inventory)
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return

        when (event.rawSlot) {
            SHOPS -> if (player.hasPermission(AdminPanelGUI.SHOPS_PERMISSION)) {
                GUIManager.openLater(player, AdminShopsGUI.ID)
            }

            BREAKDOWN -> GUIManager.openLater(player, EconomyFlowGUI.ID)

            BACK -> GUIManager.openLater(player, AdminPanelGUI.ID)

            WINDOW -> {
                PanelState.cycle(player, event.click != ClickType.RIGHT)
                click(player)
                render(player, event.inventory)
            }

            REFRESH -> {
                click(player)
                render(player, event.inventory)
            }

            HEALTH -> {
                val plugin = Main.instance
                plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable { EconomyService.flush() })
                player.sendPrefixed(player.tr("command.eco.flushing"))
            }
        }
    }

    // ── Rendering ───────────────────────────────────────────────────────

    private fun render(player: Player, inventory: Inventory) {
        inventory.setItem(
            BACK,
            button(Material.ARROW, player.tr("gui.admin-economy.back"), player.tr("gui.admin-economy.back-lore")),
        )

        if (!EconomyPulse.isEnabled) {
            renderDisabled(player, inventory)
            return
        }

        val window = PanelState.window(player)
        val flow = EconomyPulse.window(window.hours, CHART.size)
        val supply = EconomyPulse.latest()
        val previous = EconomyPulse.sampleAt(flow.from)
        val days = ((flow.to - flow.from).toDouble() / MILLIS_PER_DAY).coerceAtLeast(MINIMUM_DAYS)

        inventory.setItem(SUPPLY, supplyCard(player, supply, previous, window.key))
        inventory.setItem(ACCOUNTS, accountsCard(player, supply))
        inventory.setItem(DISTRIBUTION, distributionCard(player, supply))
        inventory.setItem(LEADERS, leadersCard(player))
        inventory.setItem(CIRCULATION, circulationCard(player, flow, supply, days))
        inventory.setItem(SHOPS, shopsCard(player, flow))
        inventory.setItem(HEALTH, healthCard(player))

        inventory.setItem(GENERATION, generationCard(player, flow, days))
        inventory.setItem(SINKS, sinksCard(player, flow, days))
        inventory.setItem(NET, netCard(player, flow, supply, days))
        inventory.setItem(TOWNY, townyCard(player, flow))
        inventory.setItem(ADMIN, adminCard(player, flow))
        inventory.setItem(NEWCOMERS, newcomersCard(player, flow))
        inventory.setItem(BREAKDOWN, breakdownCard(player))

        renderChart(player, inventory, flow)

        inventory.setItem(
            WINDOW,
            PanelRender.card(
                Material.CLOCK,
                player.tr("gui.admin-economy.window", "window" to player.tr(window.key)),
                listOf(
                    player.tr(
                        "gui.admin-economy.window-range",
                        "from" to PanelRender.time(flow.from),
                        "to" to PanelRender.time(flow.to),
                    ),
                    "",
                    player.tr("gui.admin-economy.window-lore"),
                ),
            ),
        )
        inventory.setItem(
            REFRESH,
            button(
                Material.SPYGLASS,
                player.tr("gui.admin-economy.refresh"),
                player.tr("gui.admin-economy.refresh-lore"),
            ),
        )
    }

    /** Everything but the way out, for a server that has switched the figures off. */
    private fun renderDisabled(player: Player, inventory: Inventory) {
        val slots = GUIFrame.contentSlots(ROWS)
        val pane = GUIFrame.pane()
        for (slot in slots) inventory.setItem(slot, pane.clone())

        inventory.setItem(
            slots[slots.size / 2],
            PanelRender.card(
                Material.BARRIER,
                player.tr("gui.admin-economy.disabled"),
                listOf(player.tr("gui.admin-economy.disabled-lore")),
            ),
        )
    }

    private fun supplyCard(
        player: Player,
        supply: EconomyPulse.Supply?,
        previous: EconomyPulse.Supply?,
        windowKey: String,
    ): ItemStack {
        if (supply == null) return measuring(player, Material.GOLD_BLOCK, "gui.admin-economy.supply")

        val elsewhere = supply.total - supply.held - supply.banked
        val lines = listOf(
            player.tr("gui.admin-economy.supply-total", "amount" to PanelRender.money(supply.total)),
            "",
            player.tr(
                "gui.admin-economy.supply-wallets",
                "amount" to PanelRender.money(supply.held),
                "percent" to PanelRender.percent(PanelRender.share(supply.held, supply.total)),
            ),
            player.tr(
                "gui.admin-economy.supply-banks",
                "amount" to PanelRender.money(supply.banked),
                "percent" to PanelRender.percent(PanelRender.share(supply.banked, supply.total)),
            ),
            player.tr(
                "gui.admin-economy.supply-elsewhere",
                "amount" to PanelRender.money(elsewhere),
                "percent" to PanelRender.percent(PanelRender.share(elsewhere, supply.total)),
            ),
            "",
            player.tr(
                "gui.admin-economy.supply-change",
                "window" to player.tr(windowKey),
                "amount" to PanelRender.delta(player, supply.total - (previous?.total ?: supply.total)),
            ),
            player.tr(
                "gui.admin-economy.supply-average",
                "amount" to PanelRender.money(if (supply.wallets == 0) 0L else supply.held / supply.wallets),
            ),
            player.tr("gui.admin-economy.measured", "time" to PanelRender.time(supply.at)),
        )

        return PanelRender.card(Material.GOLD_BLOCK, player.tr("gui.admin-economy.supply"), lines)
    }

    private fun accountsCard(player: Player, supply: EconomyPulse.Supply?): ItemStack {
        if (supply == null) return measuring(player, Material.PLAYER_HEAD, "gui.admin-economy.accounts")

        val other = (supply.accounts[AccountType.NPC] ?: 0) + (supply.accounts[AccountType.SERVER] ?: 0)
        val lines = listOf(
            player.tr("gui.admin-economy.accounts-total", "amount" to supply.accountCount),
            "",
            player.tr("gui.admin-economy.accounts-wallets", "amount" to supply.wallets),
            player.tr("gui.admin-economy.accounts-towns", "amount" to (supply.accounts[AccountType.TOWN] ?: 0)),
            player.tr("gui.admin-economy.accounts-nations", "amount" to (supply.accounts[AccountType.NATION] ?: 0)),
            player.tr("gui.admin-economy.accounts-other", "amount" to other),
            "",
            player.tr(
                "gui.admin-economy.accounts-active",
                "amount" to supply.activeWallets,
                "percent" to PanelRender.percent(
                    if (supply.wallets == 0) 0.0 else supply.activeWallets.toDouble() / supply.wallets
                ),
            ),
        )

        return PanelRender.card(Material.PLAYER_HEAD, player.tr("gui.admin-economy.accounts"), lines)
    }

    private fun distributionCard(player: Player, supply: EconomyPulse.Supply?): ItemStack {
        if (supply == null) return measuring(player, Material.COMPARATOR, "gui.admin-economy.distribution")

        val lines = listOf(
            player.tr("gui.admin-economy.distribution-median", "amount" to PanelRender.money(supply.median)),
            player.tr("gui.admin-economy.distribution-mean", "amount" to PanelRender.money(supply.mean)),
            player.tr("gui.admin-economy.distribution-richest", "amount" to PanelRender.money(supply.richest)),
            "",
            player.tr("gui.admin-economy.distribution-top", "percent" to PanelRender.percent(supply.topShare)),
            player.tr(
                "gui.admin-economy.distribution-gini",
                "value" to PanelRender.percent(supply.gini),
                "label" to player.tr(giniLabel(supply.gini)),
            ),
            "",
            player.tr("gui.admin-economy.distribution-lore"),
        )

        return PanelRender.card(Material.COMPARATOR, player.tr("gui.admin-economy.distribution"), lines)
    }

    private fun leadersCard(player: Player): ItemStack {
        val snapshot = BaltopCache.current
        val lines = mutableListOf<String>()

        if (snapshot.entries.isEmpty()) {
            lines += player.tr("gui.admin-economy.leaders-empty")
        } else {
            snapshot.entries.take(LEADER_LINES).forEachIndexed { index, entry ->
                lines += player.tr(
                    "gui.admin-economy.leaders-line",
                    "rank" to index + 1,
                    "name" to name(entry),
                    "amount" to PanelRender.money(entry.balance.minor),
                )
            }
            lines += ""
            lines += player.tr("gui.admin-economy.leaders-updated", "time" to PanelRender.time(snapshot.refreshedAt))
        }

        return PanelRender.card(Material.DIAMOND, player.tr("gui.admin-economy.leaders"), lines)
    }

    private fun circulationCard(
        player: Player,
        flow: EconomyPulse.Flow,
        supply: EconomyPulse.Supply?,
        days: Double,
    ): ItemStack {
        val average = if (flow.transfers == 0L) 0L else flow.circulated / flow.transfers
        val velocity = if (supply == null || supply.total <= 0L) {
            0.0
        } else {
            (flow.circulated / days) / supply.total
        }

        val lines = listOf(
            player.tr("gui.admin-economy.circulation-volume", "amount" to PanelRender.money(flow.circulated)),
            player.tr("gui.admin-economy.circulation-count", "amount" to flow.transfers),
            player.tr("gui.admin-economy.circulation-average", "amount" to PanelRender.money(average)),
            "",
            player.tr("gui.admin-economy.circulation-velocity", "percent" to PanelRender.rate(velocity)),
            "",
            player.tr("gui.admin-economy.circulation-lore"),
        )

        return PanelRender.card(Material.ENDER_PEARL, player.tr("gui.admin-economy.circulation"), lines)
    }

    private fun shopsCard(player: Player, flow: EconomyPulse.Flow): ItemStack {
        val lines = mutableListOf<String>()

        if (!ShopSettings.isLoaded || !ShopSettings.snapshot.enabled || !ShopManager.isReady) {
            lines += player.tr("gui.admin-economy.shops-off")
        } else {
            val shops = ShopManager.all()
            lines += player.tr("gui.admin-economy.shops-count", "amount" to shops.size)
            lines += ""
            lines += player.tr(
                "gui.admin-economy.shops-created",
                "amount" to PanelRender.money(flow.createdBy[FlowCategory.SHOP] ?: 0L),
            )
            lines += player.tr(
                "gui.admin-economy.shops-destroyed",
                "amount" to PanelRender.money(flow.destroyedBy[FlowCategory.SHOP] ?: 0L),
            )
            lines += player.tr(
                "gui.admin-economy.shops-net",
                "amount" to PanelRender.delta(player, flow.netOf(FlowCategory.SHOP)),
            )
            lines += ""
            lines += player.tr(
                "gui.admin-economy.shops-lifetime",
                "taken" to ShopRender.money(shops.sumOf { shop -> shop.entries.sumOf { it.stats.moneyIn } }),
                "paid" to ShopRender.money(shops.sumOf { shop -> shop.entries.sumOf { it.stats.moneyOut } }),
            )
            lines += ""
            lines += player.tr("gui.admin-economy.click-shops")
        }

        return PanelRender.card(Material.EMERALD, player.tr("gui.admin-economy.shops"), lines)
    }

    private fun healthCard(player: Player): ItemStack {
        val settings = EconomySettings.snapshot
        val currency = CurrencyRegistry.primary
        val provider = runCatching { EconomyUtil.economy.name }.getOrNull()

        val lines = listOf(
            player.tr(
                "gui.admin-economy.health-provider",
                "provider" to (provider ?: player.tr("gui.admin.server-no-provider")),
            ),
            player.tr(
                "gui.admin-economy.health-currency",
                "currency" to ComponentUtil.escape(currency.plural),
                "symbol" to ComponentUtil.escape(currency.symbol),
            ),
            player.tr(
                "gui.admin-economy.health-storage",
                "storage" to settings.storageType,
                "seconds" to settings.flushIntervalSeconds,
            ),
            player.tr(
                "gui.admin-economy.health-history",
                "state" to player.tr(if (settings.history.enabled) "common.on" else "common.off"),
                "days" to settings.history.retentionDays,
            ),
            player.tr(
                "gui.admin-economy.health-stats",
                "time" to (EconomyPulse.earliest()?.let(PanelRender::time) ?: player.tr("common.unknown")),
            ),
            "",
            player.tr("gui.admin-economy.click-flush"),
        )

        return PanelRender.card(Material.REDSTONE_TORCH, player.tr("gui.admin-economy.health"), lines)
    }

    private fun generationCard(player: Player, flow: EconomyPulse.Flow, days: Double): ItemStack {
        val lines = mutableListOf(
            player.tr("gui.admin-economy.generation-total", "amount" to PanelRender.money(flow.created)),
            player.tr("gui.admin-economy.flow-rate", "amount" to PanelRender.money(perDay(flow.created, days))),
            "",
        )
        lines += breakdown(player, flow.createdBy, flow.created)
        return PanelRender.card(Material.WATER_BUCKET, player.tr("gui.admin-economy.generation"), lines)
    }

    private fun sinksCard(player: Player, flow: EconomyPulse.Flow, days: Double): ItemStack {
        val lines = mutableListOf(
            player.tr("gui.admin-economy.sinks-total", "amount" to PanelRender.money(flow.destroyed)),
            player.tr("gui.admin-economy.flow-rate", "amount" to PanelRender.money(perDay(flow.destroyed, days))),
            "",
        )
        lines += breakdown(player, flow.destroyedBy, flow.destroyed)
        return PanelRender.card(Material.HOPPER, player.tr("gui.admin-economy.sinks"), lines)
    }

    private fun netCard(
        player: Player,
        flow: EconomyPulse.Flow,
        supply: EconomyPulse.Supply?,
        days: Double,
    ): ItemStack {
        val perDay = perDay(flow.net, days)
        val total = supply?.total ?: 0L
        val drift = if (total <= 0L) 0.0 else perDay.toDouble() / total

        val lines = mutableListOf(
            player.tr("gui.admin-economy.net-total", "amount" to PanelRender.delta(player, flow.net)),
            player.tr("gui.admin-economy.net-rate", "amount" to PanelRender.delta(player, perDay)),
            player.tr("gui.admin-economy.net-drift", "percent" to PanelRender.rate(drift)),
            "",
        )

        lines += when {
            total <= 0L || perDay == 0L -> player.tr("gui.admin-economy.net-stable")
            perDay > 0L -> player.tr("gui.admin-economy.net-doubling", "days" to total / perDay)
            else -> player.tr("gui.admin-economy.net-emptying", "days" to total / -perDay)
        }

        lines += ""
        lines += player.tr("gui.admin-economy.net-lore")

        val material = when {
            flow.net > 0L -> Material.LIME_DYE
            flow.net < 0L -> Material.RED_DYE
            else -> Material.GRAY_DYE
        }
        return PanelRender.card(material, player.tr("gui.admin-economy.net"), lines)
    }

    private fun townyCard(player: Player, flow: EconomyPulse.Flow): ItemStack {
        val created = flow.createdBy[FlowCategory.TOWNY] ?: 0L
        val destroyed = flow.destroyedBy[FlowCategory.TOWNY] ?: 0L
        val intoBanks = (flow.createdTo[AccountType.TOWN] ?: 0L) + (flow.createdTo[AccountType.NATION] ?: 0L)
        val outOfBanks =
            (flow.destroyedFrom[AccountType.TOWN] ?: 0L) + (flow.destroyedFrom[AccountType.NATION] ?: 0L)

        val lines = listOf(
            player.tr("gui.admin-economy.towny-created", "amount" to PanelRender.money(created)),
            player.tr("gui.admin-economy.towny-destroyed", "amount" to PanelRender.money(destroyed)),
            player.tr("gui.admin-economy.towny-net", "amount" to PanelRender.delta(player, created - destroyed)),
            "",
            player.tr("gui.admin-economy.towny-in", "amount" to PanelRender.money(intoBanks)),
            player.tr("gui.admin-economy.towny-out", "amount" to PanelRender.money(outOfBanks)),
            "",
            player.tr("gui.admin-economy.towny-lore"),
        )

        return PanelRender.card(Material.BELL, player.tr("gui.admin-economy.towny"), lines)
    }

    private fun adminCard(player: Player, flow: EconomyPulse.Flow): ItemStack {
        val given = flow.createdBy[FlowCategory.ADMIN] ?: 0L
        val taken = flow.destroyedBy[FlowCategory.ADMIN] ?: 0L

        val lines = listOf(
            player.tr("gui.admin-economy.admin-given", "amount" to PanelRender.money(given)),
            player.tr("gui.admin-economy.admin-taken", "amount" to PanelRender.money(taken)),
            player.tr("gui.admin-economy.admin-net", "amount" to PanelRender.delta(player, given - taken)),
            "",
            player.tr("gui.admin-economy.admin-set", "amount" to PanelRender.money(flow.adjusted)),
            "",
            player.tr("gui.admin-economy.admin-lore"),
        )

        return PanelRender.card(Material.COMMAND_BLOCK, player.tr("gui.admin-economy.admin"), lines)
    }

    private fun newcomersCard(player: Player, flow: EconomyPulse.Flow): ItemStack {
        val paid = flow.createdBy[FlowCategory.STARTING_BALANCE] ?: 0L
        val each = CurrencyRegistry.primary.of(EconomySettings.snapshot.startingBalance).minor

        val lines = listOf(
            player.tr("gui.admin-economy.newcomers-total", "amount" to PanelRender.money(paid)),
            player.tr("gui.admin-economy.newcomers-count", "amount" to if (each <= 0L) 0L else paid / each),
            "",
            player.tr("gui.admin-economy.newcomers-each", "amount" to PanelRender.money(each)),
        )

        return PanelRender.card(Material.EGG, player.tr("gui.admin-economy.newcomers"), lines)
    }

    private fun breakdownCard(player: Player): ItemStack = PanelRender.card(
        Material.BOOK,
        player.tr("gui.admin-economy.breakdown"),
        listOf(player.tr("gui.admin-economy.breakdown-lore"), "", player.tr("gui.admin-economy.click-open")),
    )

    /**
     * One column per slice of the window, as tall as the slice's net change is
     * large next to the biggest one.
     *
     * The stack size is the bar: a column that moved nothing shows one pane and a
     * column that moved the most shows sixty-four, which reads as a chart at a
     * glance without a single pixel of custom texture.
     */
    private fun renderChart(player: Player, inventory: Inventory, flow: EconomyPulse.Flow) {
        val tallest = flow.slices.maxOfOrNull { abs(it.net) } ?: 0L

        flow.slices.forEachIndexed { index, slice ->
            val slot = CHART.getOrNull(index) ?: return@forEachIndexed
            val height = if (tallest <= 0L) 1 else ((abs(slice.net) * BAR_MAX) / tallest).toInt().coerceIn(1, BAR_MAX)

            val material = when {
                slice.created == 0L && slice.destroyed == 0L -> Material.GRAY_STAINED_GLASS_PANE
                slice.net > 0L -> Material.LIME_STAINED_GLASS_PANE
                slice.net < 0L -> Material.RED_STAINED_GLASS_PANE
                else -> Material.YELLOW_STAINED_GLASS_PANE
            }

            val lines = listOf(
                player.tr("gui.admin-economy.chart-created", "amount" to PanelRender.money(slice.created)),
                player.tr("gui.admin-economy.chart-destroyed", "amount" to PanelRender.money(slice.destroyed)),
                player.tr("gui.admin-economy.chart-net", "amount" to PanelRender.delta(player, slice.net)),
                "",
                player.tr("gui.admin-economy.chart-lore"),
            )

            val bar = PanelRender.card(
                material,
                player.tr(
                    "gui.admin-economy.chart",
                    "from" to PanelRender.time(slice.from),
                    "to" to PanelRender.time(slice.to),
                ),
                lines,
            )
            bar.amount = height
            inventory.setItem(slot, bar)
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    /** The three categories that moved the most, with their share of the total. */
    private fun breakdown(player: Player, amounts: Map<FlowCategory, Long>, total: Long): List<String> {
        if (total <= 0L) return listOf(player.tr("gui.admin-economy.flow-empty"))

        return amounts.entries
            .sortedByDescending { it.value }
            .take(BREAKDOWN_LINES)
            .map { (category, amount) ->
                player.tr(
                    "gui.admin-economy.flow-line",
                    "name" to PanelRender.categoryName(player, category),
                    "amount" to PanelRender.money(amount),
                    "percent" to PanelRender.percent(PanelRender.share(amount, total)),
                )
            }
    }

    private fun measuring(player: Player, material: Material, nameKey: String): ItemStack =
        PanelRender.card(material, player.tr(nameKey), listOf(player.tr("gui.admin-economy.measuring")))

    private fun button(material: Material, name: String, lore: String): ItemStack =
        PanelRender.card(material, name, listOf(lore))

    private fun perDay(amount: Long, days: Double): Long = (amount / days).roundToLong()

    private fun name(entry: BaltopCache.Entry): String {
        val stripped = if (entry.type.isGovernment) TownyAccountNaming.stripPrefix(entry.name) else entry.name
        return ComponentUtil.escape(stripped)
    }

    private fun giniLabel(gini: Double): String = when {
        gini < 0.3 -> "gui.admin-economy.gini-even"
        gini < 0.5 -> "gui.admin-economy.gini-fair"
        gini < 0.7 -> "gui.admin-economy.gini-uneven"
        else -> "gui.admin-economy.gini-extreme"
    }

    private fun click(player: Player) {
        player.playSound(Sound.sound(Key.key("minecraft:ui.button.click"), Sound.Source.UI, 1f, 1f))
    }

    companion object {
        const val ID = "admin-economy"

        private const val ROWS = 5

        private const val SUPPLY = 10
        private const val ACCOUNTS = 11
        private const val DISTRIBUTION = 12
        private const val LEADERS = 13
        private const val CIRCULATION = 14
        private const val SHOPS = 15
        private const val HEALTH = 16

        private const val GENERATION = 19
        private const val SINKS = 20
        private const val NET = 21
        private const val TOWNY = 22
        private const val ADMIN = 23
        private const val NEWCOMERS = 24
        private const val BREAKDOWN = 25

        private val CHART = (28..34).toList()

        private const val BACK = 38
        private const val WINDOW = 40
        private const val REFRESH = 42

        /** How many lines a card spends naming the categories behind a total. */
        private const val BREAKDOWN_LINES = 3

        /** How many accounts the leaderboard card lists. */
        private const val LEADER_LINES = 5

        /** The tallest a chart column can be, which is a full stack. */
        private const val BAR_MAX = 64

        private const val MILLIS_PER_DAY = 24.0 * 60.0 * 60.0 * 1000.0

        /** Keeps a daily rate finite when the window is shorter than a day's worth of data. */
        private const val MINIMUM_DAYS = 1.0 / 24.0
    }
}
