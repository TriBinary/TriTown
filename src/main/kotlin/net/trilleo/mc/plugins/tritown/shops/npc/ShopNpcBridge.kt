package net.trilleo.mc.plugins.tritown.shops.npc

import org.bukkit.Bukkit

/**
 * Looks NPCs up for the shop command, without needing FancyNpcs to be installed.
 *
 * Nothing here exposes a FancyNpcs type, so a command or menu that calls it
 * loads normally on a server that does not have the plugin — every method just
 * reports that there are no NPCs.
 */
object ShopNpcBridge {

    /** The plugin shops bind their NPCs from. */
    const val PLUGIN_NAME = "FancyNpcs"

    /** Whether FancyNpcs is installed and running. */
    val isAvailable: Boolean
        get() = Bukkit.getPluginManager().isPluginEnabled(PLUGIN_NAME)

    /** Every NPC name on the server, or an empty list when FancyNpcs is absent. */
    fun names(): List<String> = guard { FancyNpcsAdapter.names() } ?: emptyList()

    /** The stable id of the NPC called [name], which is what a binding stores. */
    fun idOf(name: String): String? = guard { FancyNpcsAdapter.idOf(name) }

    /** What the NPC with [id] is called now, or `null` when it has been deleted. */
    fun nameOf(id: String): String? = guard { FancyNpcsAdapter.nameOf(id) }

    /**
     * Runs [block] only when FancyNpcs is there.
     *
     * The throwable is caught as well as the plugin being checked, because a
     * FancyNpcs whose API has moved on should leave shops working rather than
     * spilling an error into an administrator's command.
     */
    private fun <T> guard(block: () -> T): T? {
        if (!isAvailable) return null
        return runCatching(block).getOrNull()
    }
}
