package net.trilleo.mc.plugins.tritown.economy.storage

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.*
import java.util.UUID
import java.util.logging.Logger
import kotlin.collections.ArrayDeque
import kotlin.collections.ArrayList
import kotlin.collections.Collection
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.emptyList
import kotlin.collections.emptyMap
import kotlin.collections.forEach
import kotlin.collections.getOrPut
import kotlin.collections.map
import kotlin.collections.mapValues
import kotlin.collections.set
import kotlin.collections.toList

/**
 * Stores every account in one JSON file under `<dataFolder>/economy/`.
 *
 * One file rather than one per account: a few thousand accounts come to well
 * under a megabyte, so rewriting the whole file on a background thread costs
 * nothing, and startup is a single parse instead of thousands of file opens.
 *
 * Writes go to a temporary file and are then moved into place, with the
 * previous copy kept as `.bak`. A crash mid-write therefore leaves either the
 * old file or the new one, never a truncated one.
 *
 * @param directory      the plugin data folder
 * @param expectedDigits the currency scale configured right now
 * @param allowRescale   whether a stored scale that differs from [expectedDigits] may be converted
 *                       instead of refused
 * @param rollSizeBytes  how large the transaction log may grow before it is rolled aside; 0 never rolls
 */
class JsonEconomyStorage(
    directory: File,
    private val expectedDigits: Int,
    private val allowRescale: Boolean,
    private val logger: Logger,
    private val rollSizeBytes: Long = 16L * 1024L * 1024L,
) : EconomyStorage {

    private val root = File(directory, DIRECTORY)
    private val accountsFile = File(root, ACCOUNTS_FILE)
    private val backupFile = File(root, "$ACCOUNTS_FILE.bak")
    private val transactionLog = File(root, TRANSACTIONS_FILE)
    private val gson = GsonBuilder().setPrettyPrinting().create()

    /** The transaction log is one record per line, so it must not be pretty-printed. */
    private val logGson = Gson()

    /** The transaction log has its own lock, so appending never waits on an account rewrite. */
    private val logLock = Any()

    /**
     * The last known state of every account.
     *
     * Guarded by [ioLock]: the flush task is the usual writer, but an account
     * deleted in response to a Towny event arrives on the server thread.
     */
    private val mirror = LinkedHashMap<String, StoredAccount>()
    private val ioLock = Any()

    override val schemaVersion: Int = StorageSchema.CURRENT

    override fun initialize() {
        if (!root.exists() && !root.mkdirs()) {
            throw EconomyStorageException("Could not create the economy data directory at ${root.absolutePath}")
        }
    }

    override fun loadAccounts(): LoadedAccounts = synchronized(ioLock) {
        mirror.clear()

        val document = read(accountsFile) ?: read(backupFile, isBackup = true)
        if (document == null) {
            if (accountsFile.exists() || backupFile.exists()) {
                throw EconomyStorageException(
                    "Neither ${accountsFile.name} nor ${backupFile.name} could be read. Refusing to start with an " +
                            "empty economy, because the next save would overwrite whatever is still in those files. " +
                            "Restore a backup, or move them aside to start fresh."
                )
            }
            logger.info("No economy data found; starting with an empty ledger")
            return LoadedAccounts(expectedDigits, emptyList())
        }

        val storedDigits = document.get(KEY_DIGITS)?.asInt ?: expectedDigits
        val accounts = parseAccounts(document)
        val rescaled = reconcileScale(storedDigits, accounts)

        for (account in rescaled) mirror[account.uuid] = account
        logger.info("Loaded ${rescaled.size} economy account(s)")
        return LoadedAccounts(expectedDigits, rescaled)
    }

    override fun saveAccounts(accounts: Collection<StoredAccount>) {
        if (accounts.isEmpty()) return
        synchronized(ioLock) {
            for (account in accounts) mirror[account.uuid] = account
            write()
        }
    }

    override fun deleteAccount(uuid: UUID) {
        synchronized(ioLock) {
            if (mirror.remove(uuid.toString()) != null) write()
        }
    }

    // ── Transactions ────────────────────────────────────────────────────
    //
    // The log is newline-delimited JSON and only ever appended to, so a crash
    // can cost the last line but never corrupt the ones before it. It rolls to a
    // timestamped file once it grows past the configured size, which is also
    // what makes pruning by age a matter of deleting whole files.

    override fun appendTransactions(records: List<StoredTransaction>) {
        if (records.isEmpty()) return
        synchronized(logLock) {
            runCatching {
                rollIfOversized()
                transactionLog.parentFile?.mkdirs()
                BufferedWriter(
                    OutputStreamWriter(
                        FileOutputStream(transactionLog, true),
                        Charsets.UTF_8
                    )
                ).use { writer ->
                    for (record in records) {
                        writer.write(logGson.toJson(record))
                        writer.newLine()
                    }
                }
            }.onFailure {
                logger.warning("Failed to append to ${transactionLog.name}: [${it.javaClass.simpleName}] ${it.message}")
            }
        }
    }

    override fun loadRecentTransactions(maxPerAccount: Int): Map<String, List<StoredTransaction>> {
        if (maxPerAccount <= 0 || !transactionLog.exists()) return emptyMap()

        val perAccount = HashMap<String, ArrayDeque<StoredTransaction>>()
        var skipped = 0

        synchronized(logLock) {
            runCatching {
                transactionLog.forEachLine { line ->
                    val record = parseTransaction(line)
                    if (record == null) {
                        if (line.isNotBlank()) skipped++
                        return@forEachLine
                    }
                    val ring = perAccount.getOrPut(record.account) { ArrayDeque(maxPerAccount) }
                    ring.addLast(record)
                    while (ring.size > maxPerAccount) ring.removeFirst()
                }
            }.onFailure {
                logger.warning("Could not read ${transactionLog.name}: [${it.javaClass.simpleName}] ${it.message}")
            }
        }

        if (skipped > 0) logger.warning("Skipped $skipped unreadable transaction log entries")
        return perAccount.mapValues { (_, ring) -> ring.toList() }
    }

    override fun pruneTransactions(olderThanEpochMs: Long) {
        val rolled = root.listFiles { file -> file.name.startsWith(ROLLED_PREFIX) } ?: return
        for (file in rolled) {
            if (file.lastModified() < olderThanEpochMs && file.delete()) {
                logger.info("Pruned old transaction log ${file.name}")
            }
        }
    }

    override fun close() = Unit

    private fun rollIfOversized() {
        if (rollSizeBytes <= 0L || !transactionLog.exists()) return
        if (transactionLog.length() < rollSizeBytes) return

        val rolled = File(root, "$ROLLED_PREFIX${System.currentTimeMillis()}.log")
        if (transactionLog.renameTo(rolled)) {
            logger.info("Rolled the transaction log to ${rolled.name}")
        } else {
            logger.warning("Could not roll ${transactionLog.name}; it will keep growing")
        }
    }

    private fun parseTransaction(line: String): StoredTransaction? {
        if (line.isBlank()) return null
        return runCatching {
            val record = logGson.fromJson(line, StoredTransaction::class.java)
            if (record.v > StorageSchema.CURRENT) null else record
        }.getOrNull()
    }

    // ── Reading ─────────────────────────────────────────────────────────

    private fun read(file: File, isBackup: Boolean = false): JsonObject? {
        if (!file.exists()) return null
        return try {
            val document = JsonParser.parseString(file.readText()).asJsonObject
            val version = document.get(KEY_VERSION)?.asInt ?: StorageSchema.CURRENT
            StorageSchema.checkReadable(version, file.name)
            if (isBackup) logger.warning("Recovered the economy from ${file.name}")
            document
        } catch (e: EconomyStorageException) {
            throw e
        } catch (e: Exception) {
            logger.warning("Could not read ${file.name}: [${e.javaClass.simpleName}] ${e.message}")
            null
        }
    }

    private fun parseAccounts(document: JsonObject): List<StoredAccount> {
        val array = document.getAsJsonArray(KEY_ACCOUNTS) ?: return emptyList()
        val accounts = ArrayList<StoredAccount>(array.size())

        for (element in array) {
            val entry = runCatching { element.asJsonObject }.getOrNull() ?: continue
            val uuid = entry.get("uuid")?.asString ?: continue

            val balances = HashMap<String, Long>()
            entry.getAsJsonObject("balances")?.entrySet()?.forEach { (currency, value) ->
                runCatching { balances[currency] = value.asLong }
            }

            accounts.add(
                StoredAccount(
                    uuid = uuid,
                    name = entry.get("name")?.asString ?: uuid,
                    type = entry.get("type")?.asString ?: "UNKNOWN",
                    balances = balances,
                    createdAt = entry.get("createdAt")?.asLong ?: 0L,
                    updatedAt = entry.get("updatedAt")?.asLong ?: 0L,
                    startingBalanceGranted = entry.get("startingBalanceGranted")?.asBoolean ?: false,
                )
            )
        }
        return accounts
    }

    /**
     * Reconciles a stored currency scale against the configured one.
     *
     * Reading cents as thousandths would divide every balance by ten, so a
     * mismatch stops the plugin unless the owner has explicitly asked for the
     * conversion.
     */
    private fun reconcileScale(storedDigits: Int, accounts: List<StoredAccount>): List<StoredAccount> {
        if (storedDigits == expectedDigits) return accounts

        if (!allowRescale) {
            throw EconomyStorageException(
                "${accountsFile.name} holds balances at $storedDigits fractional digit(s) but " +
                        "economy.currency.fractional-digits is now $expectedDigits. Change the setting back, or set " +
                        "economy.storage.allow-rescale to true to convert every balance once."
            )
        }

        logger.warning("Rescaling every balance from $storedDigits to $expectedDigits fractional digit(s)")
        val factor = BigDecimal.TEN.pow(Math.abs(expectedDigits - storedDigits))
        return accounts.map { account ->
            account.copy(
                balances = account.balances.mapValues { (_, minor) ->
                    val value = BigDecimal.valueOf(minor)
                    if (expectedDigits > storedDigits) {
                        value.multiply(factor).longValueExact()
                    } else {
                        value.divide(factor, 0, RoundingMode.HALF_UP).longValueExact()
                    }
                }
            )
        }
    }

    // ── Writing ─────────────────────────────────────────────────────────

    private fun write() {
        val document = JsonObject().apply {
            addProperty(KEY_VERSION, StorageSchema.CURRENT)
            addProperty(KEY_DIGITS, expectedDigits)
            add(KEY_ACCOUNTS, gson.toJsonTree(mirror.values.toList()))
        }

        try {
            writeAtomically(accountsFile.toPath(), gson.toJson(document))
        } catch (e: Exception) {
            logger.severe("Failed to write ${accountsFile.name}: [${e.javaClass.simpleName}] ${e.message}")
        }
    }

    private fun writeAtomically(target: Path, content: String) {
        val temporary = target.resolveSibling("${target.fileName}.tmp")
        Files.writeString(
            temporary,
            content,
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE,
        )

        val backup = target.resolveSibling("${target.fileName}.bak")
        if (Files.exists(target)) {
            Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING)
        }

        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val DIRECTORY = "economy"
        const val ACCOUNTS_FILE = "accounts.json"
        const val TRANSACTIONS_FILE = "transactions.log"
        const val ROLLED_PREFIX = "transactions-"
        const val KEY_VERSION = "schemaVersion"
        const val KEY_DIGITS = "fractionalDigits"
        const val KEY_ACCOUNTS = "accounts"
    }
}
