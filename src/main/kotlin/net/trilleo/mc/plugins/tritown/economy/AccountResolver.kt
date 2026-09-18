package net.trilleo.mc.plugins.tritown.economy

import com.palmergames.bukkit.towny.TownyEconomyHandler
import net.trilleo.mc.plugins.tritown.enums.AccountType
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.util.*

/**
 * Works out which account a Vault call is about.
 *
 * Towny reaches the economy two different ways depending on its
 * `economy.advanced.modern` setting: normally it passes a synthetic
 * `OfflinePlayer` carrying the town's or nation's UUID, but on servers where
 * that setting is off it calls the deprecated name-based methods instead. Both
 * have to land on the same account, which is what [resolveName] is for.
 */
object AccountResolver {

    /**
     * Decides what [name] represents.
     *
     * Classification is by name prefix rather than by asking Towny, because
     * this runs on the Vault hot path from Towny's own threads. An account that
     * is neither a government nor a currently-online player is left
     * [AccountType.UNKNOWN] and promoted when its owner joins — Towny hands out
     * a synthetic offline player for offline residents too, so at this point a
     * resident and a stranger look identical.
     */
    fun classify(uuid: UUID, name: String): AccountType = when {
        name.equals(TownyAccountNaming.serverAccountName, ignoreCase = true) -> AccountType.SERVER
        name.startsWith(TownyAccountNaming.townPrefix, ignoreCase = true) -> AccountType.TOWN
        name.startsWith(TownyAccountNaming.nationPrefix, ignoreCase = true) -> AccountType.NATION
        name.startsWith(TownyAccountNaming.npcPrefix, ignoreCase = true) -> AccountType.NPC
        Bukkit.getPlayer(uuid) != null -> AccountType.PLAYER
        else -> AccountType.UNKNOWN
    }

    /** The name to file [player] under. Falls back to the UUID when Bukkit has never seen a name. */
    fun nameOf(player: OfflinePlayer): String = player.name ?: player.uniqueId.toString()

    /**
     * Resolves [name] to the UUID and type it belongs to, or `null` when nothing
     * on the server answers to it.
     *
     * `Bukkit.getOfflinePlayer(String)` is deliberately never called: it blocks
     * on a web request to Mojang, and for a typo it happily invents an account
     * that will never be used again.
     */
    fun resolveName(name: String): Identity? {
        val townyObject = runCatching { TownyEconomyHandler.getTownyObjectUUID(name) }.getOrNull()
        if (townyObject != null) return Identity(townyObject, name, classify(townyObject, name))

        val online = Bukkit.getPlayerExact(name)
        if (online != null) return Identity(online.uniqueId, online.name, AccountType.PLAYER)

        return null
    }

    /** A resolved account owner. */
    data class Identity(val uuid: UUID, val name: String, val type: AccountType)
}
