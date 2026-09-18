package net.trilleo.mc.plugins.tritown.economy

import net.trilleo.mc.plugins.tritown.enums.AccountType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EconomyLedgerTest {

    private val dollar = Currency("dollar", "Dollar", "Dollars", "$", 2, "%symbol%%amount%", "%symbol%%amount%")

    private fun ledger(limits: LedgerLimits = LedgerLimits.DEFAULT) = EconomyLedger(limits)

    private fun EconomyLedger.player(name: String, uuid: UUID = UUID.randomUUID()) =
        getOrCreate(uuid, name, AccountType.PLAYER)

    @Test
    fun `creates an account once and returns the same instance afterwards`() {
        val ledger = ledger()
        val uuid = UUID.randomUUID()

        val first = ledger.getOrCreate(uuid, "Alex", AccountType.PLAYER)
        val second = ledger.getOrCreate(uuid, "Alex", AccountType.PLAYER)

        assertSame(first, second)
        assertEquals(1, ledger.size)
        assertEquals(Money.ZERO, first.balance(dollar.id))
    }

    @Test
    fun `promotes an unknown account to a player once the owner is identified`() {
        val ledger = ledger()
        val uuid = UUID.randomUUID()

        ledger.getOrCreate(uuid, "Alex", AccountType.UNKNOWN)
        val promoted = ledger.getOrCreate(uuid, "Alex", AccountType.PLAYER)

        assertEquals(AccountType.PLAYER, promoted.type)
    }

    @Test
    fun `finds accounts by name regardless of case`() {
        val ledger = ledger()
        val account = ledger.player("Alex")

        assertSame(account, ledger.byName("alex"))
        assertSame(account, ledger.byName("ALEX"))
        assertNull(ledger.byName("someone-else"))
    }

    @Test
    fun `deposits and withdraws`() {
        val ledger = ledger()
        val account = ledger.player("Alex")

        assertEquals(Money(10000L), (ledger.deposit(account, dollar, dollar.of(100.0)) as EconomyResult.Success).balance)
        assertEquals(Money(7550L), (ledger.withdraw(account, dollar, dollar.of(24.5)) as EconomyResult.Success).balance)
        assertEquals(Money(7550L), account.balance(dollar.id))
    }

    @Test
    fun `refuses to overdraw by default`() {
        val ledger = ledger()
        val account = ledger.player("Alex")
        ledger.deposit(account, dollar, dollar.of(10.0))

        val result = ledger.withdraw(account, dollar, dollar.of(10.01))

        assertEquals("money.error.insufficient-funds", (result as EconomyResult.Failure).key)
        assertEquals(Money(1000L), account.balance(dollar.id))
    }

    @Test
    fun `allows overdrawing when configured to`() {
        val ledger = ledger(LedgerLimits(allowNegativeBalances = true))
        val account = ledger.player("Alex")

        val result = ledger.withdraw(account, dollar, dollar.of(5.0))

        assertTrue(result.isSuccess)
        assertEquals(Money(-500L), account.balance(dollar.id))
    }

    @Test
    fun `rejects negative amounts`() {
        val ledger = ledger()
        val account = ledger.player("Alex")

        assertEquals("money.error.negative-amount", ledger.deposit(account, dollar, Money(-1L)).failureKey)
        assertEquals("money.error.negative-amount", ledger.withdraw(account, dollar, Money(-1L)).failureKey)
    }

    @Test
    fun `fails a deposit that would break the balance cap rather than clamping it`() {
        val ledger = ledger(LedgerLimits(capByCurrency = mapOf("dollar" to 10000L)))
        val account = ledger.player("Alex")
        ledger.deposit(account, dollar, dollar.of(90.0))

        val result = ledger.deposit(account, dollar, dollar.of(20.0))

        assertEquals("money.error.balance-cap", result.failureKey)
        assertEquals(Money(9000L), account.balance(dollar.id))
    }

    @Test
    fun `sets a balance outright and reports the difference moved`() {
        val ledger = ledger()
        val account = ledger.player("Alex")
        ledger.deposit(account, dollar, dollar.of(100.0))

        val result = ledger.setBalance(account, dollar, dollar.of(25.0)) as EconomyResult.Success

        assertEquals(Money(2500L), result.balance)
        assertEquals(Money(7500L), result.moved)
    }

    @Test
    fun `transfers atomically and reports both balances`() {
        val ledger = ledger()
        val from = ledger.player("Alex")
        val to = ledger.player("Sam")
        ledger.deposit(from, dollar, dollar.of(100.0))

        val result = ledger.transfer(from, to, dollar, dollar.of(30.0)) as EconomyResult.Success

        assertEquals(Money(7000L), result.balance)
        assertEquals(Money(3000L), result.counterpartyBalance)
        assertEquals(Money(7000L), from.balance(dollar.id))
        assertEquals(Money(3000L), to.balance(dollar.id))
    }

    @Test
    fun `leaves both sides untouched when a transfer cannot be afforded`() {
        val ledger = ledger()
        val from = ledger.player("Alex")
        val to = ledger.player("Sam")
        ledger.deposit(from, dollar, dollar.of(10.0))

        val result = ledger.transfer(from, to, dollar, dollar.of(30.0))

        assertEquals("money.error.insufficient-funds", result.failureKey)
        assertEquals(Money(1000L), from.balance(dollar.id))
        assertEquals(Money.ZERO, to.balance(dollar.id))
    }

    @Test
    fun `refuses a transfer to the same account`() {
        val ledger = ledger()
        val account = ledger.player("Alex")

        assertEquals("money.error.same-account", ledger.transfer(account, account, dollar, dollar.of(1.0)).failureKey)
    }

    @Test
    fun `tracks and drains changed accounts`() {
        val ledger = ledger()
        val account = ledger.player("Alex")

        assertTrue(ledger.hasPendingWrites())
        assertEquals(setOf(account.uuid), ledger.drainDirty())
        assertTrue(!ledger.hasPendingWrites())

        ledger.deposit(account, dollar, dollar.of(1.0))
        assertEquals(setOf(account.uuid), ledger.drainDirty())
    }

    @Test
    fun `seeds loaded accounts without marking them for writing`() {
        val ledger = ledger()
        val uuid = UUID.randomUUID()

        ledger.load(listOf(MoneyAccount(uuid, "Alex", AccountType.PLAYER)))

        assertEquals(1, ledger.size)
        assertSame(ledger.get(uuid), ledger.byName("alex"))
        assertTrue(!ledger.hasPendingWrites())
    }
}
