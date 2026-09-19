package net.trilleo.mc.plugins.tritown.guis.admin

import com.palmergames.bukkit.towny.TownyAPI
import net.trilleo.mc.plugins.tritown.Main
import net.trilleo.mc.plugins.tritown.config.ShopSettings
import net.trilleo.mc.plugins.tritown.economy.EconomyPulse
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.StatsWindow
import net.trilleo.mc.plugins.tritown.guis.shop.ShopRender
import net.trilleo.mc.plugins.tritown.registration.GUIFrame
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
import net.trilleo.mc.plugins.tritown.shops.ShopManager
import net.trilleo.mc.plugins.tritown.utils.EconomyUtil
import net.trilleo.mc.plugins.tritown.utils.TownyUtil
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/**
 * The way in to everything an administrator runs the server from.
 *
 * Deliberately thin: it names the sections and shows just enough of each to say
 * whether it is worth opening. Everything a section knows lives in that
 * section's own menu, so adding one here is adding a card, not rewriting this.
 */
class AdminPanelGUI : PluginGUI(
    id = ID,
    titleKey = "gui.admin.title",
    rows = 3,
    fillMode = FillMode.NONE,
) {

    override fun setup(player: Player, inventory: Inventory) {
        GUIFrame.draw(inventory, listOf(ECONOMY_SLOT, SHOPS_SLOT, SERVER_SLOT))

        if (player.hasPermission(ECONOMY_PERMISSION)) inventory.setItem(ECONOMY_SLOT, economy(player))
        if (player.hasPermission(SHOPS_PERMISSION)) inventory.setItem(SHOPS_SLOT, shops(player))
        inventory.setItem(SERVER_SLOT, server(player))
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        val player = event.whoClicked as? Player ?: return

        when (event.rawSlot) {
            ECONOMY_SLOT -> if (player.hasPermission(ECONOMY_PERMISSION)) {
                GUIManager.openLater(player, EconomyPanelGUI.ID)
            }

            SHOPS_SLOT -> if (player.hasPermission(SHOPS_PERMISSION)) {
                GUIManager.openLater(player, AdminShopsGUI.ID)
            }
        }
    }

    // ── Cards ───────────────────────────────────────────────────────────

    private fun economy(player: Player): ItemStack {
        val lines = mutableListOf(player.tr("gui.admin.economy-lore"))

        if (!EconomyPulse.isEnabled) {
            lines += player.tr("gui.admin.economy-stats-off")
        } else {
            val supply = EconomyPulse.latest()
            val flow = EconomyPulse.window(StatsWindow.DAY.hours)
            lines += ""
            lines += player.tr(
                "gui.admin.economy-supply",
                "amount" to PanelRender.money(supply?.total ?: 0L),
            )
            lines += player.tr(
                "gui.admin.economy-net",
                "amount" to PanelRender.delta(player, flow.net),
                "window" to player.tr(StatsWindow.DAY.key),
            )
            lines += player.tr("gui.admin.economy-accounts", "amount" to (supply?.accountCount ?: 0))
        }

        lines += ""
        lines += player.tr("gui.admin.click-open")
        return PanelRender.card(Material.GOLD_INGOT, player.tr("gui.admin.economy"), lines)
    }

    private fun shops(player: Player): ItemStack {
        val lines = mutableListOf(player.tr("gui.admin.shops-lore"))

        if (!ShopSettings.isLoaded || !ShopSettings.snapshot.enabled || !ShopManager.isReady) {
            lines += player.tr("gui.admin.shops-off")
        } else {
            val shops = ShopManager.all()
            val moneyIn = shops.sumOf { shop -> shop.entries.sumOf { it.stats.moneyIn } }
            val moneyOut = shops.sumOf { shop -> shop.entries.sumOf { it.stats.moneyOut } }
            lines += ""
            lines += player.tr("gui.admin.shops-count", "amount" to shops.size)
            lines += player.tr("gui.admin.shops-taken", "amount" to ShopRender.money(moneyIn))
            lines += player.tr("gui.admin.shops-paid", "amount" to ShopRender.money(moneyOut))
        }

        lines += ""
        lines += player.tr("gui.admin.click-open")
        return PanelRender.card(Material.EMERALD, player.tr("gui.admin.shops"), lines)
    }

    /**
     * What the server is running, in the two or three numbers that say whether
     * anything is wrong before the sections are opened.
     */
    private fun server(player: Player): ItemStack {
        val towny = TownyAPI.getInstance()
        val provider = runCatching { EconomyUtil.economy.name }.getOrNull()

        val lines = listOf(
            player.tr("gui.admin.server-version", "version" to Main.instance.pluginMeta.version),
            player.tr(
                "gui.admin.server-provider",
                "provider" to (provider ?: player.tr("gui.admin.server-no-provider")),
            ),
            "",
            player.tr("gui.admin.server-towns", "amount" to towny.towns.size),
            player.tr("gui.admin.server-nations", "amount" to towny.nations.size),
            player.tr("gui.admin.server-online", "amount" to Bukkit.getOnlinePlayers().size),
            player.tr(
                "gui.admin.server-newday",
                "time" to TownyUtil.duration(player, TownyUtil.secondsUntilNewDay()),
            ),
        )

        return PanelRender.card(Material.BEACON, player.tr("gui.admin.server"), lines)
    }

    companion object {
        const val ID = "admin-panel"

        const val ECONOMY_PERMISSION = "tritown.admin.economy"
        const val SHOPS_PERMISSION = "tritown.admin.shops"

        private const val ECONOMY_SLOT = 11
        private const val SHOPS_SLOT = 13
        private const val SERVER_SLOT = 15
    }
}
