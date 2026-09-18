package net.trilleo.mc.plugins.tritown.listeners.economy

import com.palmergames.bukkit.towny.event.DeleteNationEvent
import com.palmergames.bukkit.towny.event.DeleteTownEvent
import com.palmergames.bukkit.towny.event.RenameNationEvent
import com.palmergames.bukkit.towny.event.RenameTownEvent
import net.trilleo.mc.plugins.tritown.config.EconomySettings
import net.trilleo.mc.plugins.tritown.economy.*
import net.trilleo.mc.plugins.tritown.enums.TransactionType
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import java.util.*

/**
 * Keeps town and nation bank accounts in step with Towny.
 *
 * Accounts are keyed by UUID, so a rename does not move any money — but the
 * name index still has to follow, and that is not optional. Towny only tells an
 * economy about a rename when the server runs VaultUnlocked, and with plain
 * Vault it never does. A stale `town-Riverbend` entry left pointing at the old
 * town would be handed to a *newly created* town that reuses the name, quietly
 * merging two towns' banks.
 */
class TownyObjectListener : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRenameTown(event: RenameTownEvent) {
        rename(event.town.uuid, TownyAccountNaming.townPrefix + event.town.name)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRenameNation(event: RenameNationEvent) {
        rename(event.nation.uuid, TownyAccountNaming.nationPrefix + event.nation.name)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeleteTown(event: DeleteTownEvent) {
        close(event.townUUID, TownyAccountNaming.townPrefix + event.townName)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onDeleteNation(event: DeleteNationEvent) {
        close(event.nationUUID, TownyAccountNaming.nationPrefix + event.nationName)
    }

    private fun rename(uuid: UUID?, newName: String) {
        if (uuid == null) return
        EconomyService.renameAccount(uuid, newName)
    }

    /**
     * Closes the account behind a deleted town or nation.
     *
     * By the time this runs Towny has already emptied the bank through Vault —
     * moving it to its server account when its closed economy is on — so there
     * is no money left to account for here. What is left is TriTown's own
     * bookkeeping.
     */
    private fun close(uuid: UUID?, name: String) {
        if (uuid == null || !EconomyService.isReady) return
        val account = EconomyService.account(uuid) ?: return

        val currency = CurrencyRegistry.primary
        EconomyContext.with(
            EconomyContext.SOURCE_TOWNY,
            TransactionReason.of(TransactionReason.TOWN_DELETED, "name" to name),
        ) {
            EconomyService.record(
                account = uuid,
                counterparty = null,
                currency = currency,
                type = TransactionType.CLOSED,
                amount = account.balance(currency.id),
                balanceAfter = Money.ZERO,
            )
        }

        if (EconomySettings.snapshot.deleteAccountsOnDelete) {
            EconomyService.deleteAccount(uuid)
        } else {
            // Kept as an empty account so an administrator can still audit it.
            EconomyService.setBalance(account, currency, Money.ZERO)
        }
    }
}
