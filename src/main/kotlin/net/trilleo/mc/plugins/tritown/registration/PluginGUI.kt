package net.trilleo.mc.plugins.tritown.registration

import net.trilleo.mc.plugins.tritown.enums.FillMode
import net.trilleo.mc.plugins.tritown.utils.tr
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory

/**
 * Base class for all plugin GUIs (chest-based inventory menus).
 *
 * Extend this class and place the subclass anywhere inside the
 * `net.trilleo.mc.plugins.tritown.guis` package (or any subpackage) to
 * have it automatically discovered and registered at startup.
 *
 * The class must have either:
 * - A no-arg constructor, **or**
 * - A constructor that accepts a single `JavaPlugin` parameter (the plugin
 *   instance will be injected automatically).
 *
 * Example:
 * ```kotlin
 * package net.trilleo.mc.plugins.tritown.guis
 *
 * import org.bukkit.Material
 * import org.bukkit.inventory.ItemStack
 *
 * class SettingsGUI : PluginGUI(
 *     id = "settings",
 *     titleKey = "gui.settings.title",
 *     rows = 3,
 *     fillMode = FillMode.DARK
 * ) {
 *     override fun setup(player: Player, inventory: Inventory) {
 *         inventory.setItem(13, ItemStack(Material.COMPASS))
 *     }
 *
 *     override fun onClick(event: InventoryClickEvent) {
 *         event.isCancelled = true
 *         event.whoClicked.sendMessage("You clicked slot ${event.slot}!")
 *     }
 * }
 * ```
 */
abstract class PluginGUI(
    val id: String,
    val titleKey: String,
    val rows: Int = 3,
    val fillMode: FillMode = FillMode.NONE
) {

    /**
     * The inventory title, in [player]'s language.
     *
     * Override this when the title carries live data, such as a name or a
     * count; translate the key yourself when you do.
     *
     * @param player the player the GUI is being opened for
     */
    open fun title(player: Player): Component =
        MiniMessage.miniMessage().deserialize(player.tr(titleKey))

    /**
     * Called when the GUI inventory is being created and opened for a player.
     * Use this to populate the inventory with items.
     *
     * @param player    the player the GUI is being opened for
     * @param inventory the inventory to populate
     */
    abstract fun setup(player: Player, inventory: Inventory)

    /**
     * Called when a player clicks inside this GUI.
     *
     * By default, all clicks are cancelled to prevent item theft.
     * Override to add custom click handling.
     *
     * @param event the inventory click event
     */
    open fun onClick(event: InventoryClickEvent) {
        event.isCancelled = true
    }

    /**
     * Called when a player closes this GUI.
     *
     * Override to add custom close handling (e.g. cleanup or saving).
     *
     * @param event the inventory close event
     */
    open fun onClose(event: InventoryCloseEvent) {}
}
