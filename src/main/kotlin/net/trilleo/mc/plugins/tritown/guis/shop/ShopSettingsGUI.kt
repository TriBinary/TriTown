package net.trilleo.mc.plugins.tritown.guis.shop

import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.enums.TownyRequirement
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
import net.trilleo.mc.plugins.tritown.shops.ShopDefinition
import net.trilleo.mc.plugins.tritown.shops.ShopManager
import net.trilleo.mc.plugins.tritown.utils.ChatPrompt
import net.trilleo.mc.plugins.tritown.utils.LoreUtil
import net.trilleo.mc.plugins.tritown.utils.itemStack
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * A shop as a whole: what it is called, who may open it, and which NPCs stand
 * behind the counter.
 *
 * The NPC list is shown but not edited here — binding needs an NPC's name, which
 * is a command's job, and the command tab-completes those names.
 */
class ShopSettingsGUI : PluginGUI(
    id = ID,
    titleKey = "gui.shop-settings.title",
    rows = 3,
    fillMode = FillMode.DARK,
) {

    private val editing = ConcurrentHashMap<UUID, String>()

    /** Opens the settings of [shop]. */
    fun open(player: Player, shop: ShopDefinition) {
        editing[player.uniqueId] = shop.id
        GUIManager.open(player, ID)
    }

    override fun setup(player: Player, inventory: Inventory) {
        val shop = shopOf(player) ?: return

        inventory.setItem(SLOT_NAME, name(player, shop))
        inventory.setItem(SLOT_PERMISSION, permission(player, shop))
        inventory.setItem(SLOT_TOWNY, towny(player, shop))
        inventory.setItem(SLOT_NPCS, npcs(player, shop))
        inventory.setItem(
            SLOT_STATS,
            button(player, Material.WRITABLE_BOOK, "gui.shop-settings.stats", "gui.shop-settings.stats-lore"),
        )
        inventory.setItem(
            SLOT_BACK,
            button(player, Material.ARROW, "gui.shop-settings.back", "gui.shop-settings.back-lore"),
        )
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        val shop = shopOf(player) ?: return
        val clear = event.click == ClickType.RIGHT || event.click == ClickType.SHIFT_RIGHT

        when (event.rawSlot) {
            SLOT_NAME -> return prompt(player, shop, player.tr("gui.shop-settings.prompt-name")) { input ->
                if (input.isNotBlank()) {
                    shop.displayName = input
                    ShopManager.save()
                }
            }

            SLOT_PERMISSION -> {
                if (clear) {
                    shop.gate = shop.gate.copy(permission = null)
                    ShopManager.save()
                } else {
                    return prompt(player, shop, player.tr("gui.shop-settings.prompt-permission")) { input ->
                        shop.gate = shop.gate.copy(permission = input.ifBlank { null })
                        ShopManager.save()
                    }
                }
            }

            SLOT_TOWNY -> {
                shop.gate = shop.gate.copy(towny = cycle(shop.gate.towny, clear))
                ShopManager.save()
            }

            SLOT_STATS -> return ShopRender.navigate { ShopStatsGUI.show(player, shop) }
            SLOT_BACK -> return ShopRender.navigate { ShopEditorGUI.show(player, shop) }
            else -> return
        }

        setup(player, event.inventory)
    }

    override fun onClose(event: InventoryCloseEvent) {
        editing.remove((event.player as? Player)?.uniqueId ?: return)
    }

    private fun prompt(player: Player, shop: ShopDefinition, question: String, onInput: (String) -> Unit) {
        player.closeInventory()
        ChatPrompt.ask(player, question) { input ->
            onInput(input)
            show(player, shop)
        }
    }

    private fun cycle(value: TownyRequirement, backwards: Boolean): TownyRequirement {
        val values = TownyRequirement.entries
        val step = if (backwards) -1 else 1
        return values[(value.ordinal + step + values.size) % values.size]
    }

    private fun name(player: Player, shop: ShopDefinition): ItemStack = itemStack(Material.NAME_TAG) {
        name(shop.displayName)
        meta {
            lore(
                LoreUtil.wrapLore(
                    player.tr("gui.shop-settings.id", "id" to shop.id) +
                            "<newline>" + player.tr("gui.shop-settings.click-rename")
                )
            )
        }
    }

    private fun permission(player: Player, shop: ShopDefinition): ItemStack = itemStack(Material.PAPER) {
        name(player.tr("gui.shop-settings.permission"))
        meta {
            lore(
                LoreUtil.wrapLore(
                    player.tr(
                        "gui.shop-settings.value",
                        "value" to (shop.gate.permission ?: player.tr("common.none")),
                    ) +
                            "<newline>" + player.tr("gui.shop-settings.click-set") +
                            "<newline>" + player.tr("gui.shop-settings.right-click-clear")
                )
            )
        }
    }

    private fun towny(player: Player, shop: ShopDefinition): ItemStack = itemStack(Material.OAK_SIGN) {
        name(player.tr("gui.shop-settings.towny"))
        meta {
            lore(
                LoreUtil.wrapLore(
                    player.tr(
                        "gui.shop-settings.value",
                        "value" to ShopRender.requirementName(player, shop.gate.towny),
                    ) + "<newline>" + player.tr("gui.shop-settings.click-cycle")
                )
            )
        }
    }

    private fun npcs(player: Player, shop: ShopDefinition): ItemStack = itemStack(Material.PLAYER_HEAD) {
        name(player.tr("gui.shop-settings.npcs"))
        meta {
            lore(
                LoreUtil.wrapLore(
                    player.tr("gui.shop-settings.npc-count", "amount" to shop.npcIds.size) +
                            "<newline>" + player.tr("gui.shop-settings.npc-lore", "id" to shop.id)
                )
            )
        }
    }

    private fun button(player: Player, material: Material, nameKey: String, loreKey: String): ItemStack =
        itemStack(material) {
            name(player.tr(nameKey))
            meta { lore(LoreUtil.wrapLore(player.tr(loreKey))) }
        }

    private fun shopOf(player: Player): ShopDefinition? = editing[player.uniqueId]?.let(ShopManager::get)

    companion object {
        const val ID = "shop-settings"

        private const val SLOT_NAME = 10
        private const val SLOT_PERMISSION = 11
        private const val SLOT_TOWNY = 12
        private const val SLOT_NPCS = 14
        private const val SLOT_STATS = 16
        private const val SLOT_BACK = 22

        /** Opens a shop's settings through the registered instance. */
        fun show(player: Player, shop: ShopDefinition): Boolean {
            val gui = GUIManager.getGUI(ID) as? ShopSettingsGUI ?: return false
            gui.open(player, shop)
            return true
        }
    }
}
