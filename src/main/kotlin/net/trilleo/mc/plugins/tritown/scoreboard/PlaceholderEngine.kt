package net.trilleo.mc.plugins.tritown.scoreboard

/**
 * Fills the `%name%` markers a translated sidebar line carries.
 *
 * A line is translated first and substituted second, so a translator can move
 * a value to wherever it reads best in their language. Every value a resolver
 * returns is already escaped for MiniMessage, because the line it lands in is
 * parsed as MiniMessage afterwards.
 *
 * An unknown marker is left exactly as written rather than blanked, so a typo
 * in `config.yml` shows up on screen instead of silently losing a value.
 */
object PlaceholderEngine {

    private val pattern = Regex("%([a-z_]+)%")
    private val resolvers = mutableMapOf<String, (PlayerContext) -> String>()

    /** Registers [resolver] under [name], to be written as `%name%` in a translation. */
    fun register(name: String, resolver: (PlayerContext) -> String) {
        resolvers[name] = resolver
    }

    /**
     * Replaces every known marker in [template] with its value for [context].
     *
     * A resolver that fails falls back to the "none" text rather than taking
     * the whole sidebar down with it: Towny objects can lose their mayor or
     * capital mid-render when a town is being deleted underneath us.
     */
    fun apply(template: String, context: PlayerContext): String {
        if (!template.contains('%')) return template
        return pattern.replace(template) { match ->
            val resolver = resolvers[match.groupValues[1]] ?: return@replace match.value
            runCatching { resolver(context) }.getOrElse { context.none() }
        }
    }

    /** Forgets every resolver, so a reload re-registers them from scratch. */
    fun clear() {
        resolvers.clear()
    }
}
