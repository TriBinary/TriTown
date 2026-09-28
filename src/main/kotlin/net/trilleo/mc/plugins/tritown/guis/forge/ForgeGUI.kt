package net.trilleo.mc.plugins.tritown.guis.forge

import net.trilleo.mc.plugins.tritown.content.ContentRegistry
import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.gear.Forge
import net.trilleo.mc.plugins.tritown.gear.ForgeCosts
import net.trilleo.mc.plugins.tritown.gear.Gear
import net.trilleo.mc.plugins.tritown.gear.GearText
import net.trilleo.mc.plugins.tritown.guis.ConfirmGUI
import net.trilleo.mc.plugins.tritown.guis.admin.PanelRender
import net.trilleo.mc.plugins.tritown.guis.menu.MainMenuGUI
import net.trilleo.mc.plugins.tritown.guis.menu.MenuRender
import net.trilleo.mc.plugins.tritown.registration.GUIFrame
import net.trilleo.mc.plugins.tritown.registration.GUIManager
import net.trilleo.mc.plugins.tritown.registration.PluginGUI
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
 * The Forge: crafting new gear, and upgrading, refining, reforging or
 * salvaging the piece in the viewer's main hand.
 *
 * The held piece sits at the top and the actions below it. An action the
 * piece cannot take — a sixth star, a rarity above the cap — is left out
 * rather than greyed, and with nothing in hand only crafting is offered. Every
 * action that costs something asks first ([ConfirmGUI]) and is carried out by
 * [Forge], which checks everything again at that moment.
 */
class ForgeGUI : PluginGUI(
    id = ID,
    titleKey = "gui.forge.title",
    rows = 6,
    fillMode = FillMode.NONE,
) {

    private enum class Action { CRAFT, UPGRADE, REFINE, REFORGE, SALVAGE }

    /** Which action each slot holds, per viewer, since the actions offered depend on what they hold. */
    private val layouts = ConcurrentHashMap<UUID, Map<Int, Action>>()

    override fun setup(player: Player, inventory: Inventory) {
        val piece = Gear.read(player.inventory.itemInMainHand)
        val actions = listOfNotNull(
            Action.CRAFT,
            Action.UPGRADE.takeIf { piece != null && upgradeCost(piece) != null },
            Action.REFINE.takeIf { piece != null && refineCost(piece) != null },
            Action.REFORGE.takeIf { piece != null && ContentRegistry.gear.reforgesFor(piece.def.slot).isNotEmpty() },
            Action.SALVAGE.takeIf { piece != null },
        )
        val layout = GUIFrame.packedColumns(actions.size).zip(actions)
            .associate { (column, action) -> ACTION_ROW * ROW_SIZE + column to action }
        layouts[player.uniqueId] = layout

        val slots = layout.mapValues { (_, action) -> render(player, action, piece) } +
                mapOf(HELD_SLOT to held(player, piece), BACK_SLOT to MenuRender.back(player))
        GUIFrame.draw(inventory, slots.keys)
        slots.forEach { (slot, item) -> inventory.setItem(slot, item) }
    }

    override fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
        if (event.clickedInventory !== event.view.topInventory) return
        val player = event.whoClicked as? Player ?: return

        if (event.rawSlot == BACK_SLOT) {
            MenuRender.later(player) { MainMenuGUI.show(player) }
            return
        }
        when (layouts[player.uniqueId]?.get(event.rawSlot) ?: return) {
            Action.CRAFT -> MenuRender.later(player) { ForgeRecipesGUI.show(player) }
            Action.UPGRADE -> confirm(
                player,
                "gui.forge.upgrade-confirm",
                "forge.upgraded",
                ::upgradeCost,
                Forge::upgrade
            )

            Action.REFINE -> confirm(player, "gui.forge.refine-confirm", "forge.refined", ::refineCost, Forge::refine)
            Action.REFORGE -> confirm(
                player,
                "gui.forge.reforge-confirm",
                "forge.reforged",
                ::reforgeCost,
                Forge::reforge
            )

            Action.SALVAGE -> confirm(player, "gui.forge.salvage-confirm", "forge.salvaged", { null }, Forge::salvage)
        }
    }

    override fun onClose(event: InventoryCloseEvent) {
        layouts.remove(event.player.uniqueId)
    }

    /**
     * Asks before [act] runs on the held piece, showing it and what it costs,
     * then says how it went and comes back here.
     */
    private fun confirm(
        player: Player,
        titleKey: String,
        doneKey: String,
        cost: (Gear.Piece) -> ForgeCosts.Cost?,
        act: (Player) -> Forge.Result,
    ) {
        val held = player.inventory.itemInMainHand
        val piece = Gear.read(held) ?: return
        val lines = cost(piece)?.let { ForgeRender.costLines(player, it) }.orEmpty()
        MenuRender.later(player) {
            ConfirmGUI.show(
                player,
                title = player.tr(titleKey),
                subject = held.clone(),
                choices = listOf(ConfirmGUI.Choice(ForgeRender.confirm(player, lines)) { viewer ->
                    ForgeRender.announce(viewer, act(viewer), doneKey)
                    show(viewer)
                }),
                onCancel = ::show,
            )
        }
    }

    private fun render(viewer: Player, action: Action, piece: Gear.Piece?): ItemStack = when (action) {
        Action.CRAFT -> PanelRender.card(
            Material.CRAFTING_TABLE,
            viewer.tr("gui.forge.craft"),
            listOf(viewer.tr("gui.forge.craft-lore"), "", viewer.tr("gui.admin.click-open")),
        )

        Action.UPGRADE -> card(
            viewer, Material.NETHER_STAR, "gui.forge.upgrade",
            viewer.tr(
                "gui.forge.upgrade-lore",
                "bonus" to ContentRegistry.balance.gear.starBonus.toInt(),
                "stars" to piece!!.data.stars,
                "max" to ContentRegistry.balance.gear.maxStars,
            ),
            upgradeCost(piece),
        )

        Action.REFINE -> card(
            viewer, Material.DIAMOND, "gui.forge.refine",
            viewer.tr(
                "gui.forge.refine-lore",
                "from" to GearText.rarity(viewer, piece!!.data.rarity),
                "to" to GearText.rarity(viewer, ForgeCosts.nextRarity(piece.data.rarity)),
            ),
            refineCost(piece),
        )

        Action.REFORGE -> card(
            viewer, Material.ANVIL, "gui.forge.reforge",
            viewer.tr(
                "gui.forge.reforge-lore",
                "reforge" to (piece!!.data.reforge?.let { ContentRegistry.gear.reforges[it] }?.let { viewer.tr(it.key) }
                    ?: viewer.tr("common.none")),
            ),
            reforgeCost(piece),
        )

        Action.SALVAGE -> {
            val returned =
                ForgeCosts.salvage(piece!!.def, piece.data, ContentRegistry.balance.forge, ContentRegistry.loot)
            val lines = listOf(viewer.tr("gui.forge.salvage-lore")) + returned.map { (id, amount) ->
                viewer.tr("gui.forge.salvage-item", "amount" to amount, "name" to GearText.item(viewer, id))
            }
            PanelRender.card(
                Material.GRINDSTONE,
                viewer.tr("gui.forge.salvage"),
                lines + "" + viewer.tr("gui.forge.click-act"),
            )
        }
    }

    /** An action on the held piece: what it does, what it costs, and the click to do it. */
    private fun card(
        viewer: Player,
        material: Material,
        nameKey: String,
        about: String,
        cost: ForgeCosts.Cost?
    ): ItemStack {
        val lines = listOf(about, "") + cost?.let { ForgeRender.costLines(viewer, it) }.orEmpty() +
                "" + viewer.tr("gui.forge.click-act")
        return PanelRender.card(material, viewer.tr(nameKey), lines)
    }

    /** The piece in the viewer's hand, or how to put one there. */
    private fun held(viewer: Player, piece: Gear.Piece?): ItemStack =
        if (piece != null) viewer.inventory.itemInMainHand.clone()
        else PanelRender.card(
            Material.ITEM_FRAME,
            viewer.tr("gui.forge.empty"),
            listOf(viewer.tr("gui.forge.empty-lore")),
        )

    private fun upgradeCost(piece: Gear.Piece): ForgeCosts.Cost? {
        val balance = ContentRegistry.balance
        return ForgeCosts.upgrade(piece.def, piece.data, balance.gear, balance.forge, ContentRegistry.loot)
    }

    private fun refineCost(piece: Gear.Piece): ForgeCosts.Cost? =
        ForgeCosts.refine(piece.def, piece.data, ContentRegistry.balance.forge, ContentRegistry.loot)

    private fun reforgeCost(piece: Gear.Piece): ForgeCosts.Cost =
        ForgeCosts.reforge(piece.def, ContentRegistry.balance.forge, ContentRegistry.loot)

    companion object {
        const val ID = "forge"

        private const val ROW_SIZE = 9
        private const val HELD_SLOT = 13
        private const val ACTION_ROW = 3
        private const val BACK_SLOT = 49

        fun show(player: Player): Boolean = GUIManager.open(player, ID)
    }
}
