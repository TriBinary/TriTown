package net.trilleo.mc.plugins.tritown.economy

import com.palmergames.bukkit.config.ConfigNodes
import com.palmergames.bukkit.towny.TownySettings

/**
 * The prefixes Towny puts on non-player economy account names.
 *
 * Towny addresses a town's bank as `town-Riverbend` and a nation's as
 * `nation-Everward`, with the prefixes configurable in Towny's own config. They
 * are read once and cached here because account classification happens on the
 * Vault hot path, which Towny calls from its own threads — reaching into
 * Towny's configuration from there would be both slow and unnecessary.
 *
 * Call [load] after Towny has enabled, and again on reload.
 */
object TownyAccountNaming {

    @Volatile
    var townPrefix: String = "town-"
        private set

    @Volatile
    var nationPrefix: String = "nation-"
        private set

    @Volatile
    var npcPrefix: String = "NPC"
        private set

    /** The account Towny funnels money into when its closed economy is enabled. */
    @Volatile
    var serverAccountName: String = "towny-server"
        private set

    /** Re-reads the prefixes from Towny, keeping the current values if Towny cannot be asked. */
    fun load() {
        runCatching { TownySettings.getTownAccountPrefix() }.getOrNull()?.let { townPrefix = it }
        runCatching { TownySettings.getNationAccountPrefix() }.getOrNull()?.let { nationPrefix = it }
        runCatching { TownySettings.getNPCPrefix() }.getOrNull()?.let { npcPrefix = it }
        runCatching { TownySettings.getString(ConfigNodes.ECO_CLOSED_ECONOMY_SERVER_ACCOUNT) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let { serverAccountName = it }
    }

    /** Strips the town or nation prefix from [name], for showing a bank account to a player. */
    fun stripPrefix(name: String): String = when {
        name.startsWith(townPrefix, ignoreCase = true) -> name.substring(townPrefix.length)
        name.startsWith(nationPrefix, ignoreCase = true) -> name.substring(nationPrefix.length)
        else -> name
    }
}
