package net.trilleo.mc.plugins.tritown.content

import org.bukkit.Material
import org.bukkit.entity.EntityType
import org.bukkit.plugin.java.JavaPlugin
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * The content files: what the combat layer is made of and tuned with.
 *
 * | File          | Holds                                                   |
 * |:--------------|:--------------------------------------------------------|
 * | `balance.yml` | [Balance]: the numbers every fight is worked out with   |
 * | `items.yml`   | [ContentItemDef]s: materials, essence                    |
 * | `mobs.yml`    | [LootTable]: which mob drops what                        |
 *
 * They live in `plugins/TriTown/content/`, copied from the jar on first start
 * the way the language files are, and `/tritown reload` reads them again. What
 * each describes is swapped in whole, so nothing ever sees half of an edit.
 *
 * A file that cannot be read keeps what was loaded before it — nothing, or the
 * bundled balance, the first time — and says so in the console, so a typo made
 * while the server is running never takes combat down with it.
 */
object ContentRegistry {

    private const val FOLDER = "content"
    private const val BALANCE = "balance.yml"
    private const val ITEMS = "items.yml"
    private const val MOBS = "mobs.yml"

    @Volatile
    private var currentBalance: Balance? = null

    /** The combat tuning in force; the bundled defaults until [load] has run. */
    val balance: Balance
        get() = currentBalance ?: Balance.DEFAULT

    @Volatile
    var items: Map<String, ContentItemDef> = emptyMap()
        private set

    @Volatile
    var loot: LootTable = LootTable.EMPTY
        private set

    fun load(plugin: JavaPlugin) {
        val folder = File(plugin.dataFolder, FOLDER)
        listOf(BALANCE, ITEMS, MOBS).forEach {
            if (!File(folder, it).exists()) plugin.saveResource("$FOLDER/$it", false)
        }

        read(plugin, File(folder, BALANCE))?.let { root ->
            val result = BalanceParser.parse(root)
            report(plugin, BALANCE, result.warnings)
            currentBalance = result.balance
        }

        read(plugin, File(folder, ITEMS))?.let { root ->
            val result = ContentParser.items(root)
            val unknownModels = result.value.values
                .filter { Material.matchMaterial(it.model) == null }
                .map { "items.${it.id}.model: '${it.model}' is not a vanilla item, so it will look broken" }
            report(plugin, ITEMS, result.warnings + unknownModels)
            items = result.value
        }

        read(plugin, File(folder, MOBS))?.let { root ->
            val result = ContentParser.loot(root, items.keys)
            val kinds = EntityType.entries.map { it.name }.toSet()
            val unknownMobs = result.value.families.keys
                .filter { it !in kinds }
                .map { "families: '$it' is not a kind of mob" }
            report(plugin, MOBS, result.warnings + unknownMobs)
            loot = result.value
        }
    }

    private fun report(plugin: JavaPlugin, file: String, warnings: List<String>) {
        warnings.forEach { plugin.logger.warning("$FOLDER/$file: $it") }
    }

    private fun read(plugin: JavaPlugin, file: File): Map<*, *>? = try {
        file.reader(Charsets.UTF_8).use { Yaml().load<Any?>(it) } as? Map<*, *> ?: emptyMap<Any, Any>()
    } catch (e: Exception) {
        plugin.logger.severe("$FOLDER/${file.name} could not be read, so what was loaded before stays in force: ${e.message}")
        null
    }
}
