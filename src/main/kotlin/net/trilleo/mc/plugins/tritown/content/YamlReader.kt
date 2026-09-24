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
        val value = (raw as? Number)?.takeIf { it.toDouble() == it.toLong().toDouble() }?.toInt()
        if (value == null || value < min) {
            warn(path, "a whole number of at least $min", raw, default)
            return default
        }
        return value
    }

    private fun warn(path: List<String>, expected: String, raw: Any, default: Any) {
        warnings += "${path.joinToString(".")} must be $expected, not '$raw'; using $default"
    }

    private fun find(path: List<String>): Any? {
        var node: Any? = root
        for (segment in path) node = (node as? Map<*, *>)?.get(segment) ?: return null
        return node
    }
}
