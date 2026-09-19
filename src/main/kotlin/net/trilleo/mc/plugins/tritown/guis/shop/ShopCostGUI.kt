package net.trilleo.mc.plugins.tritown.guis.shop

import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
import net.trilleo.mc.plugins.tritown.shops.ShopCost
import net.trilleo.mc.plugins.tritown.shops.ShopDefinition
import net.trilleo.mc.plugins.tritown.shops.ShopEntry
import net.trilleo.mc.plugins.tritown.shops.ShopManager
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * The items on one side of an entry — what a purchase costs in goods, or what a
 * sale pays out in them.
 *
 * Items are added by clicking a stack in your own inventory, exactly as in the
 * shop editor, and the stack's own size becomes the quantity: clicking a stack
 * of 8 iron makes the price 8 iron. Nothing leaves the administrator's
 * inventory at any point.
 */
class ShopCostGUI : PluginGUI(
    id = ID,
    titleKey = "gui.shop-cost.title",
    rows = 6,
    fillMode = FillMode.NONE,
) {

    private data class Target(val shopId: String, val entryId: String, val buying: Boolean)

    private val editing = ConcurrentHashMap<UUID, Target>()

    /** Opens the item side of [entry]'s price when [buying], or of its payout when not. */
    fun open(player: Player, shop: ShopDefinition, entry: ShopEntry, buying: Boolean) {
        editing[player.uniqueId] = Target(shop.id, entry.id, buying)
        GUIManager.open(player, ID)
    }

    override fun setup(player: Player, inventory: Inventory) {
        val (_, entry, buying) = resolve(player) ?: return

        inventory.clear()
        cost(entry, buying).items.forEachIndexed { index, item ->
            if (index < CONTENT_SLOTS) inventory.setItem(index, describe(player, item))
        }

        inventory.setItem(
            SLOT_BACK,
            button(player, Material.ARROW, "gui.shop-cost.back", "gui.shop-cost.back-lore"),
        )
        inventory.setItem(
            SLOT_HINT,
            button(player, Material.PAPER, "gui.shop-cost.add", "gui.shop-cost.add-lore"),
        )
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val (shop, entry, buying) = resolve(player) ?: return

        if (event.clickedInventory === player.inventory) {
            event.currentItem?.let { add(player, entry, buying, it) }
            setup(player, event.view.topInventory)
            return
        }

        when (val slot = event.rawSlot) {
            SLOT_BACK -> ShopRender.navigate { ShopEntryGUI.show(player, shop, entry) }

            in 0 until CONTENT_SLOTS -> {
                val items = cost(entry, buying).items
                if (slot >= items.size) return
                apply(entry, buying, items.filterIndexed { index, _ -> index != slot })
                ShopManager.save()
                setup(player, event.inventory)
            }

            else -> return
        }
    }

    override fun onDrag(event: InventoryDragEvent) {
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val (_, entry, buying) = resolve(player) ?: return
        add(player, entry, buying, event.oldCursor)
        setup(player, event.view.topInventory)
    }

    override fun onClose(event: InventoryCloseEvent) {
        editing.remove((event.player as? Player)?.uniqueId ?: return)
    }

    private fun add(player: Player, entry: ShopEntry, buying: Boolean, stack: ItemStack) {
        if (stack.type.isAir) return

        val items = cost(entry, buying).items
        if (items.size >= CONTENT_SLOTS) return

        apply(entry, buying, items + stack.clone())
        ShopManager.save()
    }

    private fun apply(entry: ShopEntry, buying: Boolean, items: List<ItemStack>) {
        if (buying) {
            entry.buy = (entry.buy ?: ShopCost.FREE).copy(items = items)
        } else {
            entry.sell = (entry.sell ?: ShopCost.FREE).copy(items = items)
        }
    }

    private fun cost(entry: ShopEntry, buying: Boolean): ShopCost =
        (if (buying) entry.buy else entry.sell) ?: ShopCost.FREE

    private fun describe(player: Player, item: ItemStack): ItemStack =
        ShopRender.withLore(item, listOf(player.tr("gui.shop-cost.click-remove")))

    private fun button(player: Player, material: Material, nameKey: String, loreKey: String): ItemStack =
        itemStack(material) {
            name(player.tr(nameKey))
            meta { lore(LoreUtil.wrapLore(player.tr(loreKey))) }
        }

    private fun resolve(player: Player): Triple<ShopDefinition, ShopEntry, Boolean>? {
        val target = editing[player.uniqueId] ?: return null
        val shop = ShopManager.get(target.shopId) ?: return null
        val entry = shop.entry(target.entryId) ?: return null
        return Triple(shop, entry, target.buying)
    }

    companion object {
        const val ID = "shop-cost"

        /** Everything but the last row, which carries the way back out. */
        private const val CONTENT_SLOTS = 45
        private const val SLOT_BACK = 45
        private const val SLOT_HINT = 49

        /** Opens the item side of a price through the registered instance. */
        fun show(player: Player, shop: ShopDefinition, entry: ShopEntry, buying: Boolean): Boolean {
            val gui = GUIManager.getGUI(ID) as? ShopCostGUI ?: return false
            gui.open(player, shop, entry, buying)
            return true
        }
    }
}
