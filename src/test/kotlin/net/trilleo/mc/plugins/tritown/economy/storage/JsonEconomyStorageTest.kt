package net.trilleo.mc.plugins.tritown.economy.storage

import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class JsonEconomyStorageTest {

    @TempDir
    lateinit var directory: File

    private val logger = Logger.getLogger("JsonEconomyStorageTest").apply { level = Level.OFF }

    private fun storage(digits: Int = 2, allowRescale: Boolean = false) =
        JsonEconomyStorage(directory, digits, allowRescale, logger).also { it.initialize() }

    private fun account(name: String, minor: Long, uuid: UUID = UUID.randomUUID()) = StoredAccount(
        uuid = uuid.toString(),
        name = name,
        type = "PLAYER",
        balances = mapOf("dollar" to minor),
        createdAt = 1_000L,
        updatedAt = 2_000L,
    )

    private fun accountsFile() = File(File(directory, "economy"), "accounts.json")
    private fun backupFile() = File(File(directory, "economy"), "accounts.json.bak")

    @Test
    fun `starts empty when nothing has been stored`() {
        val loaded = storage().loadAccounts()

        assertTrue(loaded.accounts.isEmpty())
        assertEquals(2, loaded.fractionalDigits)
    }

    @Test
    fun `round trips accounts`() {
        val alex = account("Alex", 10_000L)
        val town = account("town-Riverbend", 5_500L).copy(type = "TOWN")
        storage().saveAccounts(listOf(alex, town))

        val loaded = storage().loadAccounts().accounts.associateBy { it.name }

        assertEquals(2, loaded.size)
        assertEquals(alex, loaded["Alex"])
        assertEquals(town, loaded["town-Riverbend"])
    }

    @Test
    fun `replaces a stored account rather than duplicating it`() {
        val uuid = UUID.randomUUID()
        val store = storage()
        store.saveAccounts(listOf(account("Alex", 100L, uuid)))
        store.saveAccounts(listOf(account("Alex", 900L, uuid)))

        val loaded = storage().loadAccounts().accounts

        assertEquals(1, loaded.size)
        assertEquals(900L, loaded.single().balances["dollar"])
    }

    @Test
    fun `deletes an account`() {
        val uuid = UUID.randomUUID()
        val store = storage()
        store.saveAccounts(listOf(account("Alex", 100L, uuid), account("Sam", 200L)))

        store.deleteAccount(uuid)

        val loaded = storage().loadAccounts().accounts
        assertEquals(listOf("Sam"), loaded.map { it.name })
    }

    @Test
    fun `keeps the previous file as a backup and leaves a valid file behind every write`() {
        val store = storage()
        store.saveAccounts(listOf(account("Alex", 100L)))
        store.saveAccounts(listOf(account("Sam", 200L)))

        assertTrue(accountsFile().exists())
        assertTrue(backupFile().exists())
        assertTrue(!File(File(directory, "economy"), "accounts.json.tmp").exists(), "temp file was left behind")
        assertEquals(2, storage().loadAccounts().accounts.size)
    }

    @Test
    fun `falls back to the backup when the main file is corrupt`() {
        val store = storage()
        store.saveAccounts(listOf(account("Alex", 100L)))
        store.saveAccounts(listOf(account("Sam", 200L)))
        accountsFile().writeText("{ this is not json")

        val loaded = storage().loadAccounts().accounts

        assertEquals(listOf("Alex"), loaded.map { it.name })
    }

    @Test
    fun `refuses to start rather than losing data when both copies are unreadable`() {
        val store = storage()
        store.saveAccounts(listOf(account("Alex", 100L)))
        store.saveAccounts(listOf(account("Sam", 200L)))
        accountsFile().writeText("{ broken")
        backupFile().writeText("also broken")

        val error = assertFailsWith<EconomyStorageException> { storage().loadAccounts() }

        assertTrue(error.message!!.contains("Refusing to start"), error.message!!)
    }

    @Test
    fun `refuses data written by a newer build`() {
        File(directory, "economy").mkdirs()
        accountsFile().writeText("""{"schemaVersion": 99, "fractionalDigits": 2, "accounts": []}""")

        val error = assertFailsWith<EconomyStorageException> { storage().loadAccounts() }

        assertTrue(error.message!!.contains("newer version"), error.message!!)
    }

    @Test
    fun `refuses a changed currency scale by default`() {
        storage(digits = 2).saveAccounts(listOf(account("Alex", 10_000L)))

        val error = assertFailsWith<EconomyStorageException> { storage(digits = 3).loadAccounts() }

        assertTrue(error.message!!.contains("allow-rescale"), error.message!!)
    }

    @Test
    fun `rescales balances when the owner opts in`() {
        storage(digits = 2).saveAccounts(listOf(account("Alex", 10_000L)))

        // 100.00 at two digits becomes 100.000 at three.
        val widened = storage(digits = 3, allowRescale = true).loadAccounts().accounts.single()
        assertEquals(100_000L, widened.balances["dollar"])

        // 123.45 at two digits rounds to 123.5 at one.
        storage(digits = 2).saveAccounts(listOf(account("Alex", 12_345L)))
        val narrowed = storage(digits = 1, allowRescale = true).loadAccounts().accounts.single()
        assertEquals(1_235L, narrowed.balances["dollar"])
    }

    @Test
    fun `skips malformed entries instead of failing the whole load`() {
        File(directory, "economy").mkdirs()
        accountsFile().writeText(
            """
            {
              "schemaVersion": 1,
              "fractionalDigits": 2,
              "accounts": [
                "not an object",
                { "name": "missing a uuid" },
                { "uuid": "${UUID.randomUUID()}", "name": "Alex", "type": "PLAYER", "balances": { "dollar": 42 } }
              ]
            }
            """.trimIndent()
        )

        val loaded = storage().loadAccounts().accounts

        assertEquals(listOf("Alex"), loaded.map { it.name })
        assertEquals(42L, loaded.single().balances["dollar"])
    }
}
