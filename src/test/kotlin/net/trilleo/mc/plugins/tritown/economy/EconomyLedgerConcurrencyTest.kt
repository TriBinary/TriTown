package net.trilleo.mc.plugins.tritown.economy

import net.trilleo.mc.plugins.tritown.enums.AccountType
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Towny calls the economy from its own threads by default, so the ledger has to
 * hold up under genuine contention.
 *
 * These tests fail by timing out if the two-account lock ordering is ever
 * broken, and by a changed total if a read-modify-write escapes its lock.
 */
class EconomyLedgerConcurrencyTest {

    private val dollar = Currency("dollar", "Dollar", "Dollars", "$", 2, "%symbol%%amount%", "%symbol%%amount%")

    private companion object {
        const val THREADS = 8
        const val TRANSFERS_PER_THREAD = 10_000
        const val ACCOUNTS = 20
        const val STARTING_MINOR = 100_000L
        const val TIMEOUT_SECONDS = 60L
    }

    @Test
    fun `concurrent transfers never create or destroy money`() {
        val ledger = EconomyLedger()
        val accounts = (0 until ACCOUNTS).map { index ->
            ledger.getOrCreate(UUID.randomUUID(), "player$index", AccountType.PLAYER).also {
                ledger.deposit(it, dollar, Money(STARTING_MINOR))
            }
        }
        val expectedTotal = STARTING_MINOR * ACCOUNTS

        runConcurrently { threadIndex ->
            val random = Random(threadIndex)
            repeat(TRANSFERS_PER_THREAD) {
                val from = accounts[random.nextInt(ACCOUNTS)]
                val to = accounts[random.nextInt(ACCOUNTS)]
                if (from.uuid != to.uuid) {
                    ledger.transfer(from, to, dollar, Money(random.nextLong(1L, 500L)))
                }
            }
        }

        val total = accounts.sumOf { it.balance(dollar.id).minor }
        assertEquals(expectedTotal, total, "total money supply changed")
        assertTrue(accounts.none { it.balance(dollar.id).isNegative }, "an account was overdrawn")
    }

    @Test
    fun `transfers in opposite directions between the same pair do not deadlock`() {
        val ledger = EconomyLedger()
        val left = ledger.getOrCreate(UUID.randomUUID(), "left", AccountType.PLAYER)
        val right = ledger.getOrCreate(UUID.randomUUID(), "right", AccountType.PLAYER)
        ledger.deposit(left, dollar, Money(STARTING_MINOR))
        ledger.deposit(right, dollar, Money(STARTING_MINOR))

        runConcurrently { threadIndex ->
            val (from, to) = if (threadIndex % 2 == 0) left to right else right to left
            repeat(TRANSFERS_PER_THREAD) { ledger.transfer(from, to, dollar, Money(1L)) }
        }

        assertEquals(STARTING_MINOR * 2, left.balance(dollar.id).minor + right.balance(dollar.id).minor)
    }

    @Test
    fun `concurrent deposits and withdrawals on one account settle exactly`() {
        val ledger = EconomyLedger()
        val account = ledger.getOrCreate(UUID.randomUUID(), "alex", AccountType.PLAYER)
        ledger.deposit(account, dollar, Money(STARTING_MINOR))

        runConcurrently { threadIndex ->
            repeat(TRANSFERS_PER_THREAD) {
                if (threadIndex % 2 == 0) {
                    ledger.deposit(account, dollar, Money(3L))
                } else {
                    ledger.withdraw(account, dollar, Money(3L))
                }
            }
        }

        assertEquals(STARTING_MINOR, account.balance(dollar.id).minor)
    }

    @Test
    fun `concurrent creation of one uuid yields a single account`() {
        val ledger = EconomyLedger()
        val uuid = UUID.randomUUID()
        val seen = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<MoneyAccount, Boolean>())

        runConcurrently {
            repeat(1_000) { seen.add(ledger.getOrCreate(uuid, "alex", AccountType.PLAYER)) }
        }

        assertEquals(1, seen.size)
        assertEquals(1, ledger.size)
    }

    /** Runs [body] on [THREADS] threads at once and rethrows the first failure. */
    private fun runConcurrently(body: (threadIndex: Int) -> Unit) {
        val pool = Executors.newFixedThreadPool(THREADS)
        val start = CountDownLatch(1)
        val done = CountDownLatch(THREADS)
        val failure = AtomicReference<Throwable?>()

        repeat(THREADS) { index ->
            pool.execute {
                try {
                    start.await()
                    body(index)
                } catch (t: Throwable) {
                    failure.compareAndSet(null, t)
                } finally {
                    done.countDown()
                }
            }
        }

        start.countDown()
        val finished = done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        pool.shutdownNow()

        assertTrue(finished, "threads did not finish within $TIMEOUT_SECONDS s, which points at a deadlock")
        assertNull(failure.get(), "a worker thread failed: ${failure.get()}")
    }
}
