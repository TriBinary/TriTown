package net.trilleo.mc.plugins.tritown.economy

import net.trilleo.mc.plugins.tritown.economy.storage.JsonPulseStorage
import net.trilleo.mc.plugins.tritown.enums.AccountType
import net.trilleo.mc.plugins.tritown.enums.FlowCategory
import net.trilleo.mc.plugins.tritown.enums.TransactionType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EconomyPulseTest {

    @TempDir
    lateinit var directory: File

    private val logger = Logger.getLogger("EconomyPulseTest").apply { level = Level.OFF }
    private val currency = Currency("dollar", "Dollar", "Dollars", "$", 2, "%symbol%%amount%", "%symbol%%amount%")

    @BeforeEach
    fun setUp() {
        EconomyPulse.start(JsonPulseStorage(directory, logger), currency, retentionDays = 30)
    }

    @AfterEach
    fun tearDown() {
        EconomyPulse.shutdown()
    }

    private fun deposit(minor: Long, reason: String, holder: AccountType = AccountType.PLAYER) =
        EconomyPulse.record(
            TransactionType.DEPOSIT, currency, Money(minor), holder, EconomyContext.SOURCE_COMMAND, reason
        )

    private fun withdraw(minor: Long, reason: String, holder: AccountType = AccountType.PLAYER) =
        EconomyPulse.record(
            TransactionType.WITHDRAW, currency, Money(minor), holder, EconomyContext.SOURCE_COMMAND, reason
        )

    @Test
    fun `totals deposits as created and withdrawals as destroyed`() {
        deposit(10_000L, TransactionReason.STARTING_BALANCE)
        withdraw(2_500L, TransactionReason.SHOP_BUY)

        val flow = EconomyPulse.window(hours = 24)

        assertEquals(10_000L, flow.created)
        assertEquals(2_500L, flow.destroyed)
        assertEquals(7_500L, flow.net)
        assertEquals(2, flow.movements.toInt())
    }

    @Test
    fun `files movements under the category their reason belongs to`() {
        deposit(10_000L, TransactionReason.STARTING_BALANCE)
        deposit(500L, TransactionReason.SHOP_SELL)
        withdraw(300L, TransactionReason.SHOP_BUY)
        withdraw(1_000L, TransactionReason.TOWNY, AccountType.TOWN)

        val flow = EconomyPulse.window(hours = 24)

        assertEquals(10_000L, flow.createdBy[FlowCategory.STARTING_BALANCE])
        assertEquals(500L, flow.createdBy[FlowCategory.SHOP])
        assertEquals(300L, flow.destroyedBy[FlowCategory.SHOP])
        assertEquals(200L, flow.netOf(FlowCategory.SHOP))
        assertEquals(-1_000L, flow.netOf(FlowCategory.TOWNY))
    }

    @Test
    fun `counts a transfer once and leaves the supply alone`() {
        EconomyPulse.record(
            TransactionType.TRANSFER_OUT, currency, Money(4_000L), AccountType.PLAYER,
            EconomyContext.SOURCE_COMMAND, TransactionReason.PAYMENT,
        )
        EconomyPulse.record(
            TransactionType.TRANSFER_IN, currency, Money(4_000L), AccountType.PLAYER,
            EconomyContext.SOURCE_COMMAND, TransactionReason.PAYMENT,
        )

        val flow = EconomyPulse.window(hours = 24)

        assertEquals(4_000L, flow.circulated)
        assertEquals(1L, flow.transfers)
        assertEquals(0L, flow.created)
        assertEquals(0L, flow.destroyed)
    }

    @Test
    fun `keeps balances that were set apart from money that was created`() {
        EconomyPulse.record(
            TransactionType.SET, currency, Money(9_000L), AccountType.PLAYER,
            EconomyContext.SOURCE_COMMAND, TransactionReason.ADMIN_SET,
        )

        val flow = EconomyPulse.window(hours = 24)

        assertEquals(9_000L, flow.adjusted)
        assertEquals(0L, flow.created)
        assertEquals(0L, flow.destroyed)
    }

    @Test
    fun `ignores a currency it is not keeping figures in`() {
        val other = currency.copy(id = "credit")
        EconomyPulse.record(
            TransactionType.DEPOSIT, other, Money(5_000L), AccountType.PLAYER,
            EconomyContext.SOURCE_COMMAND, TransactionReason.EXTERNAL,
        )

        assertTrue(EconomyPulse.window(hours = 24).isEmpty)
    }

    @Test
    fun `splits the window into columns that add up to the whole`() {
        deposit(1_000L, TransactionReason.EXTERNAL)
        withdraw(400L, TransactionReason.EXTERNAL)

        val flow = EconomyPulse.window(hours = 24, slices = 7)

        assertEquals(7, flow.slices.size)
        assertEquals(1_000L, flow.slices.sumOf { it.created })
        assertEquals(400L, flow.slices.sumOf { it.destroyed })
        // Everything just recorded belongs to the last column, which ends now.
        assertEquals(1_000L, flow.slices.last().created)
    }

    @Test
    fun `measures the supply and how unevenly it is held`() {
        val ledger = EconomyLedger()
        ledger.deposit(ledger.getOrCreate(uuid(1), "Alex", AccountType.PLAYER), currency, Money(1_000L))
        ledger.deposit(ledger.getOrCreate(uuid(2), "Blair", AccountType.PLAYER), currency, Money(3_000L))
        ledger.deposit(ledger.getOrCreate(uuid(3), "Casey", AccountType.PLAYER), currency, Money(6_000L))
        ledger.deposit(ledger.getOrCreate(uuid(4), "town-Riverbend", AccountType.TOWN), currency, Money(5_000L))

        EconomyPulse.sample(ledger, currency)
        val supply = assertNotNull(EconomyPulse.latest())

        assertEquals(15_000L, supply.total)
        assertEquals(10_000L, supply.held)
        assertEquals(5_000L, supply.banked)
        assertEquals(3, supply.wallets)
        assertEquals(3_000L, supply.median)
        assertEquals(6_000L, supply.richest)
        assertTrue(supply.gini > 0.0, "an uneven ledger should not read as perfectly equal")
    }

    @Test
    fun `reads back what it wrote`() {
        deposit(7_000L, TransactionReason.STARTING_BALANCE)
        EconomyPulse.flush()

        EconomyPulse.start(JsonPulseStorage(directory, logger), currency, retentionDays = 30)
        val flow = EconomyPulse.window(hours = 24)

        assertEquals(7_000L, flow.created)
        assertEquals(7_000L, flow.createdBy[FlowCategory.STARTING_BALANCE])
    }

    @Test
    fun `drops figures written in another currency rather than reading them as its own`() {
        deposit(7_000L, TransactionReason.STARTING_BALANCE)
        EconomyPulse.flush()

        EconomyPulse.start(JsonPulseStorage(directory, logger), currency.copy(id = "credit"), retentionDays = 30)

        assertTrue(EconomyPulse.window(hours = 24).isEmpty)
    }

    private fun uuid(seed: Long) = java.util.UUID(0L, seed)
}
