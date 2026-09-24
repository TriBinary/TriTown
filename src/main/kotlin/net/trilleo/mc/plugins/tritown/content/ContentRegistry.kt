package net.trilleo.mc.plugins.tritown.content

import org.bukkit.plugin.java.JavaPlugin
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * The content files: what the combat layer is made of and tuned with.
 *
 * They live in `plugins/TriTown/content/`, copied from the jar on first start
 * the way the language files are, and `/tritown reload` reads them again. What
 * they describe is swapped in as one snapshot, so nothing ever sees half of an
 * edit.
 *
 * A file that cannot be read keeps what was loaded before it — the bundled
 * copy, the first time — and says so in the console, so a typo made while the
 * server is running never takes combat down with it.
 */
object ContentRegistry {

    private const val FOLDER = "content"
    private const val BALANCE = "balance.yml"

    @Volatile
    private var currentBalance: Balance? = null

    /** The combat tuning in force; the bundled defaults until [load] has run. */
    val balance: Balance
        get() = currentBalance ?: Balance.DEFAULT

    fun load(plugin: JavaPlugin) {
        val folder = File(plugin.dataFolder, FOLDER)
        if (!File(folder, BALANCE).exists()) plugin.saveResource("$FOLDER/$BALANCE", false)

        val root = read(plugin, File(folder, BALANCE)) ?: return
        val result = BalanceParser.parse(root)
        result.warnings.forEach { plugin.logger.warning("$FOLDER/$BALANCE: $it") }
        currentBalance = result.balance
    }

    private fun read(plugin: JavaPlugin, file: File): Map<*, *>? = try {
        file.reader(Charsets.UTF_8).use { Yaml().load<Any?>(it) } as? Map<*, *> ?: emptyMap<Any, Any>()
    } catch (e: Exception) {
        val kept = if (currentBalance == null) "the bundled defaults" else "what was loaded before"
        plugin.logger.severe("$FOLDER/${file.name} could not be read, so $kept stays in force: ${e.message}")
        null
    }
}
