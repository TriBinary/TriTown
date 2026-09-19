package net.trilleo.mc.plugins.tritown.listeners.shop

import de.oliver.fancynpcs.api.actions.ActionTrigger
import de.oliver.fancynpcs.api.events.NpcInteractEvent
import net.trilleo.mc.plugins.tritown.config.ShopSettings
import net.trilleo.mc.plugins.tritown.guis.shop.ShopGUI
import net.trilleo.mc.plugins.tritown.shops.ShopAccess
import net.trilleo.mc.plugins.tritown.shops.ShopManager
import net.trilleo.mc.plugins.tritown.utils.sendPrefixed
import net.trilleo.mc.plugins.tritown.utils.tr
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * Opens a shop when a player clicks the NPC standing behind its counter.
 *
 * This is the only class in the plugin that names a FancyNpcs type in a
 * signature, and that is deliberate: the package scanner skips a class it cannot
 * load, so on a server without FancyNpcs this listener quietly never registers
 * and the rest of the feature is unaffected.
 */
class ShopNpcListener : Listener {

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    fun onNpcInteract(event: NpcInteractEvent) {
        if (!ShopManager.isReady) return
        if (!ShopSettings.isLoaded || !ShopSettings.snapshot.enabled) return

        // Only a click opens a shop; anything else an NPC reacts to is left alone.
        if (event.interactionType != ActionTrigger.ANY_CLICK &&
            event.interactionType != ActionTrigger.LEFT_CLICK &&
            event.interactionType != ActionTrigger.RIGHT_CLICK
        ) {
            return
        }

        val shop = ShopManager.byNpc(event.npc.data.id) ?: return
        val player = event.player

        // Cancelled either way, so the NPC's own actions do not fire alongside
        // the shop, or on top of the refusal.
        event.isCancelled = true

        if (!ShopAccess.canOpen(player, shop)) {
            player.sendPrefixed(player.tr("common.error", "message" to player.tr("shop.error.locked")))
            return
        }

        ShopGUI.show(player, shop)
    }
}
