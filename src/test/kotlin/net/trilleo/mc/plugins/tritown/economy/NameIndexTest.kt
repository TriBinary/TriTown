package net.trilleo.mc.plugins.tritown.economy

import net.trilleo.mc.plugins.tritown.enums.AccountType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The name index only exists so that Vault's deprecated name-based methods and
 * TriTown's own commands can find an account. Towny never tells a plain Vault
 * economy about a town rename, so these cases are the ones that decide whether
 * two towns can end up sharing a bank.
 */
class NameIndexTest {

    private fun ledger() = EconomyLedger()

    @Test
    fun `a rename moves the name to the same account`() {
        val ledger = ledger()
        val uuid = UUID.randomUUID()
        val account = ledger.getOrCreate(uuid, "town-Riverbend", AccountType.TOWN)

        ledger.rename(account, "town-Stonehill")

        assertNull(ledger.byName("town-Riverbend"))
        assertSame(account, ledger.byName("town-Stonehill"))
        assertEquals("town-Stonehill", account.name)
        assertEquals(uuid, ledger.byName("town-stonehill")?.uuid)
    }

    @Test
    fun `a town reusing a freed name does not inherit the old bank`() {
        val ledger = ledger()
        val old = ledger.getOrCreate(UUID.randomUUID(), "town-Riverbend", AccountType.TOWN)
        ledger.rename(old, "town-Stonehill")

        val fresh = ledger.getOrCreate(UUID.randomUUID(), "town-Riverbend", AccountType.TOWN)

        assertSame(fresh, ledger.byName("town-Riverbend"))
        assertTrue(fresh.uuid != old.uuid)
    }

    @Test
    fun `renaming an account does not steal a name another account already holds`() {
        val ledger = ledger()
        val first = ledger.getOrCreate(UUID.randomUUID(), "town-Riverbend", AccountType.TOWN)
        ledger.rename(first, "town-Stonehill")
        val second = ledger.getOrCreate(UUID.randomUUID(), "town-Riverbend", AccountType.TOWN)

        // The first account changes name again; its stale key now belongs to the second account.
        ledger.rename(first, "town-Clearwater")

        assertSame(second, ledger.byName("town-Riverbend"))
        assertSame(first, ledger.byName("town-Clearwater"))
    }

    @Test
    fun `looking up an account by a new name refreshes the index`() {
        val ledger = ledger()
        val uuid = UUID.randomUUID()
        ledger.getOrCreate(uuid, "Alex", AccountType.PLAYER)

        val renamed = ledger.getOrCreate(uuid, "Alexandra", AccountType.PLAYER)

        assertEquals(uuid, renamed.uuid)
        assertNull(ledger.byName("Alex"))
        assertSame(renamed, ledger.byName("Alexandra"))
    }

    @Test
    fun `removing an account frees its name`() {
        val ledger = ledger()
        val uuid = UUID.randomUUID()
        ledger.getOrCreate(uuid, "town-Riverbend", AccountType.TOWN)

        ledger.remove(uuid)

        assertNull(ledger.byName("town-Riverbend"))
        assertEquals(0, ledger.size)
    }

    @Test
    fun `suggests names by prefix, case-insensitively and within the limit`() {
        val ledger = ledger()
        listOf("Alex", "Alexandra", "Sam").forEach {
            ledger.getOrCreate(UUID.randomUUID(), it, AccountType.PLAYER)
        }

        assertEquals(setOf("Alex", "Alexandra"), ledger.suggestNames("ale", 10).toSet())
        assertEquals(1, ledger.suggestNames("", 1).size)
        assertTrue(ledger.suggestNames("zz", 10).isEmpty())
    }
}
