package net.trilleo.mc.plugins.tritown.utils

import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * Translations for every player-facing string.
 *
 * Language files live in `plugins/TriTown/lang/<id>.yml` (e.g. `en_US.yml`,
 * `zh_CN.yml`). The bundled files are copied there on first start, and server
 * owners may edit them or drop in new languages. Keys missing from a file fall
 * back to the bundled copy of that language, then to English.
 *
 * Values are MiniMessage strings with `{placeholder}` arguments. Arguments are
 * inserted verbatim, so escape player-written text with
 * `MiniMessage.miniMessage().escapeTags(...)` before passing it.
 *
 * ```kotlin
 * sender.sendPrefixed(sender.tr("command.reload.done"))
 * ```
 *
 * Items are the exception to reading in your own language: the server cannot
 * show an item's text to each player differently, so everything drawn on an
 * item is in the one item language ([item]).
 *
 * Loaded translations are read from Towny's threads through the Vault economy,
 * so [load] publishes an immutable snapshot rather than mutating one in place.
 */
object Lang {

    const val DEFAULT = "en_US"
    const val AUTO = "auto"
    val BUNDLED = listOf("en_US", "zh_CN")

    private class Language(val id: String, val values: Map<String, String>)

    @Volatile
    private var languages: Map<String, Language> = emptyMap()

    @Volatile
    private var fallback = Language(DEFAULT, emptyMap())

    @Volatile
    private var configured = AUTO

    @Volatile
    private var itemLanguage = fallback

    /**
     * A stamp of the item language's text, the same from one start to the next,
     * which changes whenever anything drawn on an item could read differently.
     */
    @Volatile
    var itemRevision: Int = 0
        private set

    /**
     * Loads every language file. [language] is `auto` (client locale) or a
     * language id that everyone sees; [items] is the language items are written
     * in, or `auto` for [language]'s, and English when that is `auto` too.
     */
    fun load(plugin: JavaPlugin, language: String, items: String) {
        val folder = File(plugin.dataFolder, "lang")
        BUNDLED.forEach { if (!File(folder, "$it.yml").exists()) plugin.saveResource("lang/$it.yml", false) }

        val english = bundled(plugin, DEFAULT)
        languages = folder.listFiles { file -> file.extension == "yml" }.orEmpty()
            .sortedBy { it.name }
            .associate { file ->
                val id = file.nameWithoutExtension
                val values = english + bundled(plugin, id) + flatten(YamlConfiguration.loadConfiguration(file))
                id.lowercase() to Language(id, values)
            }
        fallback = languages[DEFAULT.lowercase()] ?: Language(DEFAULT, english)
        configured = language
        if (!language.equals(AUTO, ignoreCase = true) && match(language) == null) {
            plugin.logger.warning("Unknown language '$language' in config.yml; using $DEFAULT.")
        }

        if (!items.equals(AUTO, ignoreCase = true) && match(items) == null) {
            plugin.logger.warning("Unknown item-language '$items' in config.yml; items follow language instead.")
        }
        itemLanguage = listOf(items, language).firstNotNullOfOrNull { id ->
            id.takeUnless { it.equals(AUTO, ignoreCase = true) }?.let(::match)
        } ?: fallback
        itemRevision = 31 * itemLanguage.id.hashCode() + itemLanguage.values.hashCode()
    }

    /** Ids of every loaded language file, such as `en_US`, sorted by file name. */
    val ids: List<String>
        get() = languages.values.map { it.id }

    /** The translation of [key] for [sender], with each `{name}` replaced by its argument. */
    fun tr(sender: CommandSender?, key: String, vararg args: Pair<String, Any?>): String {
        val template = language(sender).values[key] ?: key
        return args.fold(template) { text, (name, value) -> text.replace("{$name}", value.toString()) }
    }

    /** The translation of [key] for [sender], or `null` when no language defines it. */
    fun find(sender: CommandSender?, key: String): String? = language(sender).values[key]

    /**
     * The translation of [key] in the item language, for text drawn on an item.
     * An item is one object seen by everyone, so it is written once, in one
     * language, and every copy stays identical.
     */
    fun item(key: String): String = itemLanguage.values[key] ?: key

    /**
     * The id of the language [sender] reads, such as `zh_CN` — the same one [tr]
     * picks, so text kept outside the language files can follow it.
     */
    fun idFor(sender: CommandSender?): String = language(sender).id

    private fun language(sender: CommandSender?): Language = language((sender as? Player)?.locale()?.toString())

    /** The language for a client asking for [clientLocale], whatever `language` in `config.yml` makes of it. */
    private fun language(clientLocale: String?): Language {
        val id = if (configured.equals(AUTO, ignoreCase = true)) clientLocale else configured
        return id?.let(::match) ?: fallback
    }

    /** Matches `zh_tw` to `zh_TW` if present, otherwise to any language sharing its prefix (`zh_CN`). */
    private fun match(id: String): Language? =
        languages[id.lowercase()]
            ?: languages.values.firstOrNull {
                it.id.substringBefore('_').equals(id.substringBefore('_'), ignoreCase = true)
            }

    private fun bundled(plugin: JavaPlugin, id: String): Map<String, String> =
        plugin.getResource("lang/$id.yml")?.reader(Charsets.UTF_8)
            ?.use { flatten(YamlConfiguration.loadConfiguration(it)) }
            .orEmpty()

    private fun flatten(yaml: YamlConfiguration): Map<String, String> =
        yaml.getKeys(true)
            .filterNot(yaml::isConfigurationSection)
            .associateWith { yaml.getString(it).orEmpty() }
}

/** Translates [key] for this sender. See [Lang.tr]. */
fun CommandSender.tr(key: String, vararg args: Pair<String, Any?>): String = Lang.tr(this, key, *args)
