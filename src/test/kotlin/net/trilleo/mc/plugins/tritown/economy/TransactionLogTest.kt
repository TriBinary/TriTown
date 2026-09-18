package net.trilleo.mc.plugins.tritown.economy

import net.trilleo.mc.plugins.tritown.enums.TransactionType
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactionLogTest {

    private val dollar = Currency("dollar", "Dollar", "Dollars", "$", 2, "%symbol%%amount%", "%symbol%%amount%")
    private val account = UUID.randomUUID()

    private fun TransactionLog.deposit(minor: Long) =
        record(account, null, dollar, TransactionType.DEPOSIT, Money(minor), Money(minor))

    @Test
    fun `keeps records most recent first`() {
        val log = TransactionLog(maxPerAccount = 10)
        log.deposit(100L)
        log.deposit(200L)
        log.deposit(300L)

        assertEquals(listOf(300L, 200L, 100L), log.recent(account).map { it.amount })
    }

    @Test
    fun `numbers records in order`() {
        val log = TransactionLog(maxPerAccount = 10)
        log.deposit(1L)
        log.deposit(1L)

        val ids = log.recent(account).map { it.id }.sorted()
        assertEquals(listOf(1L, 2L), ids)
    }

    @Test
    fun `drops the oldest record once the ring is full`() {
        val log = TransactionLog(maxPerAccount = 3)
        repeat(5) { log.deposit((it + 1) * 100L) }

        val kept = log.recent(account).map { it.amount }
        assertEquals(listOf(500L, 400L, 300L), kept)
    }

    @Test
    fun `keeps no history for an account that has never been touched`() {
        val log = TransactionLog(maxPerAccount = 10)
        log.deposit(100L)

        assertTrue(log.recent(UUID.randomUUID()).isEmpty())
    }

    @Test
    fun `queues records for writing and clears the queue when drained`() {
        val log = TransactionLog(maxPerAccount = 10)
        log.deposit(100L)
        log.deposit(200L)

        assertTrue(log.hasPendingWrites())
        assertEquals(listOf(100L, 200L), log.drainPending().map { it.amount })
        assertTrue(!log.hasPendingWrites())
        assertTrue(log.drainPending().isEmpty())
    }

    @Test
    fun `draining does not disturb the in-memory history`() {
        val log = TransactionLog(maxPerAccount = 10)
        log.deposit(100L)
        log.drainPending()

        assertEquals(listOf(100L), log.recent(account).map { it.amount })
    }

    @Test
    fun `continues numbering after a reload`() {
        val log = TransactionLog(maxPerAccount = 10)
        val seeded = TransactionRecord(
            id = 41L,
            timestamp = 1L,
            account = account,
            counterparty = null,
            currency = dollar.id,
            type = TransactionType.DEPOSIT,
            amount = 50L,
            balanceAfter = 50L,
            source = "vault",
            reason = "",
        )
        log.seed(lastId = 41L, history = mapOf(account to listOf(seeded)))

        assertEquals(42L, log.deposit(10L).id)
        assertEquals(listOf(10L, 50L), log.recent(account).map { it.amount })
    }

    @Test
    fun `records the attribution in force`() {
        val log = TransactionLog(maxPerAccount = 10)

        val external = log.deposit(1L)
        val attributed = EconomyContext.command(TransactionReason.STARTING_BALANCE) { log.deposit(2L) }
        val afterwards = log.deposit(3L)

        assertEquals(EconomyContext.DEFAULT.source, external.source)
        assertEquals(EconomyContext.SOURCE_COMMAND, attributed.source)
        assertEquals(TransactionReason.STARTING_BALANCE, attributed.reason)
        assertEquals(EconomyContext.DEFAULT.source, afterwards.source, "the attribution leaked past its block")
    }

    @Test
    fun `forgets one account without disturbing another`() {
        val log = TransactionLog(maxPerAccount = 10)
        val other = UUID.randomUUID()
        log.deposit(100L)
        log.record(other, null, dollar, TransactionType.DEPOSIT, Money(5L), Money(5L))

        log.forget(account)

        assertTrue(log.recent(account).isEmpty())
        assertEquals(1, log.recent(other).size)
    }
}
