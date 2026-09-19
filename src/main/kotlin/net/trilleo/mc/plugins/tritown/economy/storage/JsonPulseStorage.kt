package net.trilleo.mc.plugins.tritown.economy.storage

import com.google.gson.GsonBuilder
import java.io.File
import java.nio.file.*
import java.util.logging.Logger

/**
 * Keeps the economy statistics in one JSON file under `<dataFolder>/economy/`.
 *
 * Written the same way balances are — to a temporary file that is then moved
 * into place — so a crash mid-write leaves the previous file rather than a
 * truncated one. Unlike balances there is no backup copy: statistics are worth
 * keeping but nobody's money depends on them, and a file that cannot be read
 * simply starts the history again.
 */
class JsonPulseStorage(directory: File, private val logger: Logger) {

    private val file = File(File(directory, DIRECTORY), FILE)
    private val gson = GsonBuilder().setPrettyPrinting().create()

    /** Reads what was stored, or `null` when there is nothing readable yet. */
    fun load(): StoredPulse? {
        if (!file.exists()) return null
        return try {
            val stored = gson.fromJson(file.readText(), StoredPulse::class.java) ?: return null
            StorageSchema.checkReadable(stored.v, file.name)
            stored
        } catch (e: EconomyStorageException) {
            // Statistics are not worth refusing to start over, unlike balances.
            logger.warning(e.message)
            null
        } catch (e: Exception) {
            logger.warning("Could not read ${file.name}: [${e.javaClass.simpleName}] ${e.message}")
            null
        }
    }

    /** Writes [snapshot], replacing whatever was there. */
    fun save(snapshot: StoredPulse) {
        try {
            file.parentFile?.mkdirs()
            writeAtomically(file.toPath(), gson.toJson(snapshot))
        } catch (e: Exception) {
            logger.warning("Failed to write ${file.name}: [${e.javaClass.simpleName}] ${e.message}")
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

        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val DIRECTORY = "economy"
        const val FILE = "statistics.json"
    }
}
