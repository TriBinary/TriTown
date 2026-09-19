package net.trilleo.mc.plugins.tritown.guis.shop

import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.ShopSortMode
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
import net.trilleo.mc.plugins.tritown.shops.ShopDefinition
import net.trilleo.mc.plugins.tritown.shops.ShopManager
import net.trilleo.mc.plugins.tritown.shops.ShopSorting
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Puts a whole shop in order at once.
 *
 * An order is chosen first and applied second, because sorting overwrites an
 * arrangement that may have taken a while to make by hand and there is nothing
 * to undo it with: the shop's order is what is saved, not how it was reached.
 * That is also why the accept button only appears once an order is chosen —
 * there is no single click here that rearranges a shop.
 */
class ShopSortGUI : PluginGUI(
    id = ID,
    titleKey = "gui.shop-sort.title",
    rows = 3,
    fillMode = FillMode.DARK,
) {

    private data class Pending(val shopId: String, val mode: ShopSortMode? = null)

    private val pending = ConcurrentHashMap<UUID, Pending>()

    /** Asks [player] how [shop] should be ordered. */
    fun open(player: Player, shop: ShopDefinition) {
        pending[player.uniqueId] = Pending(shop.id)
        GUIManager.open(player, ID)
    }

    override fun setup(player: Player, inventory: Inventory) {
        val chosen = pending[player.uniqueId]?.mode

        inventory.setItem(SLOT_INFO, button(player, Material.BOOK, "gui.shop-sort.info", "gui.shop-sort.info-lore"))

        inventory.setItem(SLOT_NAME, choice(player, ShopSortMode.NAME, chosen))
        inventory.setItem(SLOT_NAME_REVERSED, choice(player, ShopSortMode.NAME_REVERSED, chosen))
        inventory.setItem(SLOT_PRICE, choice(player, ShopSortMode.PRICE, chosen))
        inventory.setItem(SLOT_PRICE_REVERSED, choice(player, ShopSortMode.PRICE_REVERSED, chosen))

        if (chosen != null) {
            inventory.setItem(
                SLOT_CONFIRM,
                button(player, Material.LIME_CONCRETE, "gui.shop-sort.confirm", "gui.shop-sort.confirm-lore"),
            )
        }
        inventory.setItem(
            SLOT_CANCEL,
            button(player, Material.RED_CONCRETE, "gui.shop-sort.cancel", "gui.shop-sort.cancel-lore"),
        )
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val held = pending[player.uniqueId] ?: return

        val mode = MODE_SLOTS[event.rawSlot]
        if (mode != null) {
            pending[player.uniqueId] = held.copy(mode = mode)
            setup(player, event.inventory)
            return
        }

        when (event.rawSlot) {
            SLOT_CONFIRM -> apply(player)
            SLOT_CANCEL -> back(player)
        }
    }

    override fun onClose(event: InventoryCloseEvent) {
        pending.remove((event.player as? Player)?.uniqueId ?: return)
    }

    private fun apply(player: Player) {
        val held = pending[player.uniqueId] ?: return
        val mode = held.mode ?: return
        val shop = ShopManager.get(held.shopId) ?: return

        ShopSorting.sort(shop, mode)
        ShopManager.save()

        player.sendPrefixed(player.tr("shop.editor.sorted", "amount" to shop.entries.size))
        ShopRender.navigate { ShopEditorGUI.show(player, shop) }
    }

    /** Back to the editor the sort was asked for from, rather than out into the world. */
    private fun back(player: Player) {
        val shop = pending[player.uniqueId]?.let { ShopManager.get(it.shopId) }
        if (shop == null) {
            player.closeInventory()
            return
        }
        ShopRender.navigate { ShopEditorGUI.show(player, shop) }
    }

    private fun choice(player: Player, mode: ShopSortMode, chosen: ShopSortMode?): ItemStack {
        val lines = buildList {
            add(player.tr(loreKey(mode)))
            if (mode == chosen) add(player.tr("gui.shop-sort.chosen")) else add(player.tr("gui.shop-sort.click-choose"))
        }

        val item = itemStack(material(mode)) {
            name(player.tr(nameKey(mode)))
            meta { lore(LoreUtil.wrapLore(lines.joinToString("<newline>"))) }
        }

        return if (mode == chosen) ShopRender.glowing(item) else item
    }

    private fun nameKey(mode: ShopSortMode): String = when (mode) {
        ShopSortMode.NAME -> "gui.shop-sort.name"
        ShopSortMode.NAME_REVERSED -> "gui.shop-sort.name-reversed"
        ShopSortMode.PRICE -> "gui.shop-sort.price"
        ShopSortMode.PRICE_REVERSED -> "gui.shop-sort.price-reversed"
    }

    private fun loreKey(mode: ShopSortMode): String = when (mode) {
        ShopSortMode.NAME, ShopSortMode.NAME_REVERSED -> "gui.shop-sort.name-lore"
        ShopSortMode.PRICE, ShopSortMode.PRICE_REVERSED -> "gui.shop-sort.price-lore"
    }

    private fun material(mode: ShopSortMode): Material = when (mode) {
        ShopSortMode.NAME, ShopSortMode.NAME_REVERSED -> Material.NAME_TAG
        ShopSortMode.PRICE, ShopSortMode.PRICE_REVERSED -> Material.GOLD_INGOT
    }

    private fun button(player: Player, material: Material, nameKey: String, loreKey: String): ItemStack =
        itemStack(material) {
            name(player.tr(nameKey))
            meta { lore(LoreUtil.wrapLore(player.tr(loreKey))) }
        }

    companion object {
        const val ID = "shop-sort"

        private const val SLOT_INFO = 4
        private const val SLOT_NAME = 10
        private const val SLOT_NAME_REVERSED = 12
        private const val SLOT_PRICE = 14
        private const val SLOT_PRICE_REVERSED = 16
        private const val SLOT_CONFIRM = 21
        private const val SLOT_CANCEL = 23

        private val MODE_SLOTS = mapOf(
            SLOT_NAME to ShopSortMode.NAME,
            SLOT_NAME_REVERSED to ShopSortMode.NAME_REVERSED,
            SLOT_PRICE to ShopSortMode.PRICE,
            SLOT_PRICE_REVERSED to ShopSortMode.PRICE_REVERSED,
        )

        /** Opens the sort menu for [shop] through the registered instance. */
        fun show(player: Player, shop: ShopDefinition): Boolean {
            val gui = GUIManager.getGUI(ID) as? ShopSortGUI ?: return false
            gui.open(player, shop)
            return true
        }
    }
}
