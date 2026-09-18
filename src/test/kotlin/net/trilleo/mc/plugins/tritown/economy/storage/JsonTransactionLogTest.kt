package net.trilleo.mc.plugins.tritown.economy.storage

import java.io.File
import java.util.UUID
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class JsonTransactionLogTest {

    @TempDir
    lateinit var directory: File

    private val logger = Logger.getLogger("JsonTransactionLogTest").apply { level = Level.OFF }
    private val account = UUID.randomUUID().toString()

    private fun storage(rollSizeBytes: Long = 16L * 1024L * 1024L) =
        JsonEconomyStorage(directory, 2, false, logger, rollSizeBytes).also { it.initialize() }

    private fun transaction(id: Long, amount: Long, owner: String = account) = StoredTransaction(
        v = StorageSchema.CURRENT,
        id = id,
        timestamp = System.currentTimeMillis(),
        account = owner,
        counterparty = null,
        currency = "dollar",
        type = "DEPOSIT",
        amount = amount,
        balanceAfter = amount,
        source = "command",
        reason = "test",
        meta = emptyMap(),
    )

    private fun logFile() = File(File(directory, "economy"), "transactions.log")

    @Test
    fun `round trips transactions`() {
        val store = storage()
        store.appendTransactions(listOf(transaction(1L, 100L), transaction(2L, 200L)))

        val loaded = storage().loadRecentTransactions(10)[account].orEmpty()

        assertEquals(listOf(100L, 200L), loaded.map { it.amount })
        assertEquals("test", loaded.first().reason)
    }

    @Test
    fun `writes one line per record`() {
        val store = storage()
        store.appendTransactions(listOf(transaction(1L, 100L), transaction(2L, 200L)))
        store.appendTransactions(listOf(transaction(3L, 300L)))

        assertEquals(3, logFile().readLines().count { it.isNotBlank() })
    }

    @Test
    fun `keeps only the most recent entries per account`() {
        val store = storage()
        val other = UUID.randomUUID().toString()
        store.appendTransactions((1L..10L).map { transaction(it, it * 100L) })
        store.appendTransactions(listOf(transaction(11L, 5L, other)))

        val loaded = storage().loadRecentTransactions(3)

        assertEquals(listOf(800L, 900L, 1000L), loaded[account]!!.map { it.amount })
        assertEquals(listOf(5L), loaded[other]!!.map { it.amount })
    }

    @Test
    fun `skips unreadable lines instead of losing the whole log`() {
        val store = storage()
        store.appendTransactions(listOf(transaction(1L, 100L)))
        logFile().appendText("this is not json\n")
        store.appendTransactions(listOf(transaction(2L, 200L)))

        val loaded = storage().loadRecentTransactions(10)[account].orEmpty()

        assertEquals(listOf(100L, 200L), loaded.map { it.amount })
    }

    @Test
    fun `skips lines written by a newer build`() {
        val store = storage()
        store.appendTransactions(listOf(transaction(1L, 100L), transaction(2L, 200L).copy(v = 99)))

        val loaded = storage().loadRecentTransactions(10)[account].orEmpty()

        assertEquals(listOf(100L), loaded.map { it.amount })
    }

    @Test
    fun `rolls the log once it grows past the limit`() {
        val store = storage(rollSizeBytes = 256L)
        repeat(20) { store.appendTransactions(listOf(transaction(it.toLong(), it * 10L))) }

        val rolled = File(directory, "economy").listFiles { f -> f.name.startsWith("transactions-") }.orEmpty()

        assertTrue(rolled.isNotEmpty(), "the log was never rolled")
        assertTrue(logFile().exists())
    }

    @Test
    fun `prunes rolled logs past their retention but keeps the live one`() {
        val store = storage(rollSizeBytes = 256L)
        repeat(20) { store.appendTransactions(listOf(transaction(it.toLong(), it * 10L))) }

        val economy = File(directory, "economy")
        economy.listFiles { f -> f.name.startsWith("transactions-") }!!.forEach {
            it.setLastModified(System.currentTimeMillis() - 10L * 24L * 60L * 60L * 1000L)
        }

        store.pruneTransactions(System.currentTimeMillis() - 24L * 60L * 60L * 1000L)

        assertTrue(economy.listFiles { f -> f.name.startsWith("transactions-") }!!.isEmpty())
        assertTrue(logFile().exists(), "the live log must not be pruned")
    }

    @Test
    fun `returns nothing when no log has been written`() {
        assertTrue(storage().loadRecentTransactions(10).isEmpty())
    }
}
