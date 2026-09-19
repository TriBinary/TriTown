package net.trilleo.mc.plugins.tritown.shops.npc

import de.oliver.fancynpcs.api.FancyNpcsPlugin

/**
 * The only class in TriTown that names a FancyNpcs type outside the listener.
 *
 * Kept separate from [ShopNpcBridge] on purpose: the JVM resolves a class the
 * first time a method actually reaches it, so as long as nothing here appears in
 * the bridge's own signatures, this class is never loaded on a server without
 * FancyNpcs and the missing plugin is simply invisible.
 */
internal object FancyNpcsAdapter {

    /** Every NPC's name, for tab completion. */
    fun names(): List<String> =
        FancyNpcsPlugin.get().npcManager.allNpcs.mapNotNull { it.data?.name }.sorted()

    /** The stable id of the NPC called [name], or `null` when there is none. */
    fun idOf(name: String): String? = FancyNpcsPlugin.get().npcManager.getNpc(name)?.data?.id

    /** What the NPC with [id] is currently called, or `null` when it no longer exists. */
    fun nameOf(id: String): String? = FancyNpcsPlugin.get().npcManager.getNpcById(id)?.data?.name
}
