package net.trilleo.mc.plugins.tritown.shops.storage

import java.io.File
import java.nio.file.Files
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The shop file is the only copy of work that took an administrator an
 * afternoon, so what it must never do is lose it: not to a crash mid-write, not
 * to one malformed entry, and not to an older build opening a newer file.
 */
class JsonShopStorageTest {

    private val directory: File = Files.createTempDirectory("tritown-shops").toFile()
    private val logger = Logger.getAnonymousLogger().apply { level = Level.OFF }
    private val storage = JsonShopStorage(directory, logger)

    private val shopsFile = File(File(directory, "shops"), "shops.json")
    private val backupFile = File(File(directory, "shops"), "shops.json.bak")

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `a server with no shops starts empty`() {
        assertEquals(emptyList(), storage.loadAll())
    }

    @Test
    fun `a shop survives a round trip`() {
        val shop = StoredShop(
            id = "market",
            displayName = "<gold>Market",
            permission = "tritown.shop.market",
            towny = "HAS_TOWN",
            hideWhenLocked = true,
            npcIds = listOf("npc-1", "npc-2"),
            entries = listOf(
                StoredEntry(
                    id = "entry-1",
                    item = "encoded-bread",
                    buy = StoredCost(12.5, listOf("encoded-iron")),
                    sell = StoredCost(6.25),
                    limitAmount = 3,
                    limitPeriod = "DAILY",
                    stockMax = 64,
                    stockRestockSeconds = 3600L,
                    stockRemaining = 12,
                    bought = 7L,
                    moneyIn = 87.5,
                )
            ),
        )

        storage.saveAll(listOf(shop))

        assertEquals(listOf(shop), storage.loadAll())
    }

    @Test
    fun `saving replaces what was there rather than adding to it`() {
        storage.saveAll(listOf(StoredShop(id = "one"), StoredShop(id = "two")))
        storage.saveAll(listOf(StoredShop(id = "one")))

        assertEquals(listOf("one"), storage.loadAll().map { it.id })
    }

    @Test
    fun `the previous copy is kept as a backup`() {
        storage.saveAll(listOf(StoredShop(id = "one")))
        storage.saveAll(listOf(StoredShop(id = "two")))

        assertTrue(backupFile.exists())
        assertTrue(backupFile.readText().contains("one"))
    }

    @Test
    fun `a corrupt file falls back to the backup instead of starting empty`() {
        storage.saveAll(listOf(StoredShop(id = "one")))
        storage.saveAll(listOf(StoredShop(id = "two")))
        shopsFile.writeText("{ this is not json")

        assertEquals(listOf("one"), storage.loadAll().map { it.id })
    }

    @Test
    fun `neither copy being readable is an error, not an empty server`() {
        storage.saveAll(listOf(StoredShop(id = "one")))
        storage.saveAll(listOf(StoredShop(id = "two")))
        shopsFile.writeText("{ broken")
        backupFile.writeText("also broken")

        assertFailsWith<ShopStorageException> { storage.loadAll() }
    }

    @Test
    fun `one unreadable shop is skipped rather than taking the rest with it`() {
        shopsFile.parentFile.mkdirs()
        shopsFile.writeText(
            """
            {
              "schemaVersion": 1,
              "shops": [
                { "id": "good", "displayName": "Good" },
                "not an object",
                { "displayName": "No id at all" }
              ]
            }
            """.trimIndent()
        )

        assertEquals(listOf("good"), storage.loadAll().map { it.id })
    }

    @Test
    fun `a file from a newer build is refused rather than half-read`() {
        shopsFile.parentFile.mkdirs()
        shopsFile.writeText("""{ "schemaVersion": ${ShopSchema.CURRENT + 1}, "shops": [] }""")

        assertFailsWith<ShopStorageException> { storage.loadAll() }
    }
}
