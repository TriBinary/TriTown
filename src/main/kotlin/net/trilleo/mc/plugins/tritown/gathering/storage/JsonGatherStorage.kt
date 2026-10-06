package net.trilleo.mc.plugins.tritown.gathering.storage

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import net.trilleo.mc.plugins.tritown.utils.AtomicFile
import java.io.File
import java.util.logging.Logger

/**
 * Keeps resource regions, and the blocks harvested in them, under
 * `<dataFolder>/gathering/`.
 *
 * Two files, because they change at very different rates: a region is edited
 * now and then and written at once, while harvested blocks change on every
 * swing of a pickaxe and are flushed on an interval. Both are written through
 * [AtomicFile], and a file that will not parse falls back to its backup.
 */
class JsonGatherStorage(directory: File, private val logger: Logger) {

    private val root = File(directory, DIRECTORY)
    private val regionsFile = File(root, REGIONS_FILE)
    private val depletedFile = File(root, DEPLETED_FILE)
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val ioLock = Any()
    private val depletedType = object : TypeToken<List<StoredDepleted>>() {}.type

    /**
     * Every stored region.
     *
     * @throws GatherStorageException when neither the file nor its backup can be
     *   read, which keeps the feature off rather than letting an empty list
     *   overwrite the regions on the next save
     */
    fun loadRegions(): List<StoredRegion> = synchronized(ioLock) {
        val document = readWithBackup(regionsFile) ?: return emptyList()
        val array = document.getAsJsonArray(KEY_REGIONS) ?: return emptyList()
        array.mapNotNull { element ->
            runCatching { gson.fromJson(element, StoredRegion::class.java) }.getOrNull()
                ?.takeIf { it.id.isNotBlank() }
                ?: run {
                    logger.warning("Skipped an unreadable region in ${regionsFile.name}")
                    null
                }
        }
    }

    fun saveRegions(regions: List<StoredRegion>) = write(regionsFile, KEY_REGIONS, regions)

    /** Every harvested block. Losing this file only means those blocks stay as they are. */
    fun loadDepleted(): List<StoredDepleted> = synchronized(ioLock) {
        val document = runCatching { readWithBackup(depletedFile) }.getOrNull() ?: return emptyList()
        val array = document.getAsJsonArray(KEY_DEPLETED) ?: return emptyList()
        runCatching { gson.fromJson<List<StoredDepleted>>(array, depletedType) }.getOrNull().orEmpty()
    }

    fun saveDepleted(nodes: List<StoredDepleted>) = write(depletedFile, KEY_DEPLETED, nodes)

    private fun write(file: File, key: String, values: Any) = synchronized(ioLock) {
        root.mkdirs()
        val document = JsonObject().apply {
            addProperty(KEY_VERSION, SCHEMA)
            add(key, gson.toJsonTree(values))
        }
        try {
            AtomicFile.write(file.toPath(), File(root, "${file.name}.bak").toPath(), gson.toJson(document))
        } catch (e: Exception) {
            logger.severe("Failed to write ${file.name}: [${e.javaClass.simpleName}] ${e.message}")
        }
    }

    private fun readWithBackup(file: File): JsonObject? {
        root.mkdirs()
        read(file)?.let { return it }
        if (file.exists()) logger.warning("${file.name} could not be read; falling back to the backup")
        val backup = File(root, "${file.name}.bak")
        read(backup)?.let { return it }
        if (backup.exists()) throw GatherStorageException("Neither ${file.name} nor its backup could be read")
        return null
    }

    private fun read(file: File): JsonObject? {
        if (!file.exists()) return null
        return runCatching { JsonParser.parseString(file.readText()).asJsonObject }.getOrNull()
    }

    private companion object {
        const val DIRECTORY = "gathering"
        const val REGIONS_FILE = "regions.json"
        const val DEPLETED_FILE = "depleted.json"
        const val KEY_VERSION = "schemaVersion"
        const val KEY_REGIONS = "regions"
        const val KEY_DEPLETED = "depleted"
        const val SCHEMA = 1
    }
}

/** Raised when stored regions cannot be read, and must not be silently replaced. */
class GatherStorageException(message: String) : Exception(message)
