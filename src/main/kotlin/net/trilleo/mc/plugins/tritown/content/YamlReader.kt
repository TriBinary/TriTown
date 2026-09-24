package net.trilleo.mc.plugins.tritown.content

/**
 * Typed reads out of the map SnakeYAML parses a content file into, collecting
 * a warning for every value that is present but unusable.
 *
 * A value that is simply missing takes its default quietly: an owner who
 * leaves a line out wants the default. One that is there but wrong takes the
 * default too, and is named in [warnings], so a typo is heard about instead of
 * silently doing nothing.
 */
class YamlReader(private val root: Map<*, *>) {

    val warnings = mutableListOf<String>()

    fun number(path: List<String>, default: Double, min: Double): Double {
        val raw = find(path) ?: return default
        val value = (raw as? Number)?.toDouble()
        if (value == null || value.isNaN() || value.isInfinite() || value < min) {
            warn(path, "a number of at least $min", raw, default)
            return default
        }
        return value
    }

    fun integer(path: List<String>, default: Int, min: Int): Int {
        val raw = find(path) ?: return default
        val value = wholeNumber(raw)
        if (value == null || value < min) {
            warn(path, "a whole number of at least $min", raw, default)
            return default
        }
        return value
    }

    /**
     * A `[low, high]` pair of whole numbers of at least [min], or a single one
     * standing for both.
     */
    fun range(path: List<String>, default: IntRange, min: Int): IntRange {
        val raw = find(path) ?: return default
        val bounds = when (raw) {
            is List<*> -> raw.map(::wholeNumber)
            else -> listOf(wholeNumber(raw), wholeNumber(raw))
        }
        val low = bounds.getOrNull(0)
        val high = bounds.getOrNull(1)
        if (bounds.size != 2 || low == null || high == null || low < min || high < low) {
            warn(path, "[low, high] whole numbers of at least $min", raw, "[${default.first}, ${default.last}]")
            return default
        }
        return low..high
    }

    fun boolean(path: List<String>, default: Boolean): Boolean {
        val raw = find(path) ?: return default
        if (raw !is Boolean) {
            warn(path, "true or false", raw, default)
            return default
        }
        return raw
    }

    fun text(path: List<String>): String? = find(path)?.toString()?.takeIf { it.isNotBlank() }

    fun texts(path: List<String>): List<String> = when (val raw = find(path)) {
        null -> emptyList()
        is List<*> -> raw.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
        else -> listOf(raw.toString())
    }

    /** The keys of the section at [path], in the order the file has them. */
    fun keys(path: List<String>): List<String> =
        (find(path) as? Map<*, *>)?.keys?.map { it.toString() }.orEmpty()

    /** The entries of the list at [path] that are sections. */
    fun sections(path: List<String>): List<Map<*, *>> =
        (find(path) as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()

    /** Records a problem this reader could not see for itself, such as an id that names nothing. */
    fun warn(path: List<String>, message: String) {
        warnings += "${path.joinToString(".")}: $message"
    }

    private fun warn(path: List<String>, expected: String, raw: Any, default: Any) {
        warnings += "${path.joinToString(".")} must be $expected, not '$raw'; using $default"
    }

    private fun wholeNumber(raw: Any?): Int? =
        (raw as? Number)?.takeIf { it.toDouble() == it.toLong().toDouble() }?.toInt()

    private fun find(path: List<String>): Any? {
        var node: Any? = root
        for (segment in path) node = (node as? Map<*, *>)?.get(segment) ?: return null
        return node
    }
}
