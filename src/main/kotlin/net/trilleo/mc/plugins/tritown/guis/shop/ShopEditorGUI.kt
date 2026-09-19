package net.trilleo.mc.plugins.tritown.guis.shop

import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.PagedLayout
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PagedPluginGUI
import net.trilleo.mc.plugins.tritown.shops.ShopCost
import net.trilleo.mc.plugins.tritown.shops.ShopDefinition
import net.trilleo.mc.plugins.tritown.shops.ShopEntry
import net.trilleo.mc.plugins.tritown.shops.ShopManager
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * What one shop sells, and where entries are added and removed.
 *
 * An entry is added by clicking a stack in your own inventory, or dragging one
 * over the menu. Nothing actually moves: the stack is copied, custom data and
 * all, and stays where it was. An editor that took the item would lose it to a
 * crash or a mistimed close, and an administrator setting up a shop is usually
 * holding the only copy of whatever they are adding.
 *
 * The actions live in the navigation row rather than after the last entry, so
 * they stay under the same finger however many entries the shop grows.
 */
class ShopEditorGUI : PagedPluginGUI(
    id = ID,
    titleKey = "gui.shop-editor.title",
    rows = 6,
    fillMode = FillMode.NONE,
    layout = PagedLayout.FRAMED,
) {

    private val editing = ConcurrentHashMap<UUID, String>()

    /** Opens the editor for [shop]. */
    fun open(player: Player, shop: ShopDefinition) {
        editing[player.uniqueId] = shop.id
        GUIManager.open(player, ID)
    }

    override fun getItems(player: Player): List<ItemStack> {
        val shop = shopOf(player) ?: return emptyList()
        return shop.entries.map { entry -> icon(player, entry) }
    }

    override fun navButtons(player: Player): Map<Int, ItemStack> {
        val shop = shopOf(player) ?: return emptyMap()
        return mapOf(
            SLOT_ADD to button(player, Material.PAPER, "gui.shop-editor.add", "gui.shop-editor.add-lore"),
            SLOT_SETTINGS to settingsButton(player, shop),
            SLOT_STATS to button(player, Material.WRITABLE_BOOK, "gui.shop-editor.stats", "gui.shop-editor.stats-lore"),
            SLOT_LIST to button(player, Material.ARROW, "gui.shop-editor.back", "gui.shop-editor.back-lore"),
        )
    }

    override fun onNavClick(event: InventoryClickEvent, offset: Int) {
        val player = event.whoClicked as? Player ?: return
        val shop = shopOf(player) ?: return

        when (offset) {
            SLOT_SETTINGS -> ShopRender.navigate { ShopSettingsGUI.show(player, shop) }
            SLOT_STATS -> ShopRender.navigate { ShopStatsGUI.show(player, shop) }
            SLOT_LIST -> ShopRender.navigate { ShopListGUI.show(player) }
        }
    }

    /**
     * The paged base class drops clicks outside its own inventory, but one in
     * the administrator's own inventory is how an entry is added, so it is taken
     * before the rest is handed on.
     */
    override fun onClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player
        if (player != null && event.clickedInventory === player.inventory) {
            event.isCancelled = true
            val shop = shopOf(player) ?: return
            event.currentItem?.let { add(player, shop, it) }
            return
        }

        super.onClick(event)
    }

    override fun onContentClick(event: InventoryClickEvent, page: Int) {
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val shop = shopOf(player) ?: return

        val index = contentIndex(page, event.rawSlot) ?: return
        val entry = shop.entries.getOrNull(index) ?: return
        click(event.click, player, shop, entry)
    }

    override fun onDrag(event: InventoryDragEvent) {
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val shop = shopOf(player) ?: return
        add(player, shop, event.oldCursor)
    }

    override fun onClose(event: InventoryCloseEvent) {
        super.onClose(event)
        editing.remove((event.player as? Player)?.uniqueId ?: return)
    }

    private fun click(click: ClickType, player: Player, shop: ShopDefinition, entry: ShopEntry) {
        if (click == ClickType.SHIFT_LEFT) {
            shop.entries.remove(entry)
            ShopManager.save()
            player.sendPrefixed(player.tr("shop.editor.entry-removed"))
            ShopRender.navigate { show(player, shop) }
            return
        }

        ShopRender.navigate { ShopEntryGUI.show(player, shop, entry) }
    }

    /**
     * Adds [stack] as a new entry, priced at nothing until the administrator sets a price.
     *
     * The stack size clicked becomes the bundle, so putting a stack of 16 bread
     * on the shelf sells sixteen loaves at a time without any further setting up.
     */
    private fun add(player: Player, shop: ShopDefinition, stack: ItemStack) {
        if (stack.type.isAir) return

        val entry = ShopEntry(
            item = stack.clone().apply { amount = 1 },
            bundle = stack.amount.coerceAtLeast(1),
            buy = ShopCost.FREE,
        )
        shop.entries += entry
        ShopManager.save()

        player.sendPrefixed(player.tr("shop.editor.entry-added", "item" to ShopRender.itemName(entry.item)))
        ShopRender.navigate { ShopEntryGUI.show(player, shop, entry) }
    }

    private fun icon(player: Player, entry: ShopEntry): ItemStack {
        val lore = buildList {
            add(player.tr("gui.shop-editor.bundle", "amount" to entry.bundleSize))
            if (entry.isBuyable) addAll(buyLines(player, entry)) else add(player.tr("gui.shop-editor.not-buyable"))
            if (entry.isSellable) addAll(sellLines(player, entry)) else add(player.tr("gui.shop-editor.not-sellable"))
            add(player.tr("gui.shop-editor.click-entry"))
            add(player.tr("gui.shop-editor.shift-click-entry"))
        }

        return ShopRender.withLore(entry.displayStack(), lore)
    }

    private fun buyLines(player: Player, entry: ShopEntry): List<String> =
        listOf(player.tr("gui.shop-editor.buy")) + ShopRender.costLines(player, entry.buy)

    private fun sellLines(player: Player, entry: ShopEntry): List<String> =
        listOf(player.tr("gui.shop-editor.sell")) + ShopRender.costLines(player, entry.sell)

    private fun settingsButton(player: Player, shop: ShopDefinition): ItemStack = itemStack(Material.COMPARATOR) {
        name(player.tr("gui.shop-editor.settings"))
        meta { lore(LoreUtil.wrapLore(player.tr("gui.shop-editor.settings-lore", "id" to shop.id))) }
    }

    private fun button(player: Player, material: Material, nameKey: String, loreKey: String): ItemStack =
        itemStack(material) {
            name(player.tr(nameKey))
            meta { lore(LoreUtil.wrapLore(player.tr(loreKey))) }
        }

    private fun shopOf(player: Player): ShopDefinition? = editing[player.uniqueId]?.let(ShopManager::get)

    companion object {
        const val ID = "shop-editor"

        // Offsets in the navigation row; 0, 4 and 8 belong to the page controls.
        private const val SLOT_ADD = 1
        private const val SLOT_SETTINGS = 2
        private const val SLOT_STATS = 3
        private const val SLOT_LIST = 5

        /** Opens the editor for [shop] through the registered instance. */
        fun show(player: Player, shop: ShopDefinition): Boolean {
            val gui = GUIManager.getGUI(ID) as? ShopEditorGUI ?: return false
            gui.open(player, shop)
            return true
        }
    }
}
