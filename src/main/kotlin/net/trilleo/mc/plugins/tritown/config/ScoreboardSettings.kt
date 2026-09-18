package net.trilleo.mc.plugins.tritown.config

import net.trilleo.mc.plugins.tritown.scoreboard.BoardCondition
import net.trilleo.mc.plugins.tritown.scoreboard.BoardDefinition
import net.trilleo.mc.plugins.tritown.scoreboard.PlayerContext
import java.util.logging.Logger

/**
 * An immutable snapshot of the `scoreboard` block of `config.yml`.
 *
 * Built the same way as [EconomySettings]: a reload parses a whole new snapshot
 * and swaps it in, so a render already in flight never sees half of one board
 * definition and half of another.
 *
 * @param refreshIntervalTicks how often an unchanged sidebar is re-rendered
 * @param defaultOn            whether a player who has never used the command sees the sidebar
 * @param boards               every board, highest priority first
 */
data class ScoreboardSettings(
    val enabled: Boolean,
    val refreshIntervalTicks: Long,
    val defaultOn: Boolean,
    val titleFrameIntervalTicks: Long,
    val titleFrames: List<String>,
    val boards: List<BoardDefinition>,
) {

    /** The board [context] should see, or `null` when no board's condition matches. */
    fun boardFor(context: PlayerContext): BoardDefinition? = boards.firstOrNull { it.condition.matches(context) }

    /** The board written under [id], or `null` when there is none. */
    fun board(id: String): BoardDefinition? = boards.firstOrNull { it.id.equals(id, ignoreCase = true) }

    companion object {

        /** Minecraft cannot show more than this many lines on the sidebar. */
        const val MAX_LINES: Int = 15

        @Volatile
        private var current: ScoreboardSettings? = null

        /** The settings in force. */
        val snapshot: ScoreboardSettings
            get() = current ?: error("Scoreboard settings have not been loaded yet")

        /** Whether [load] has run. */
        val isLoaded: Boolean
            get() = current != null

        /** Reads the `scoreboard` block from [config] and makes it the current snapshot. */
        fun load(config: PluginConfig, logger: Logger): ScoreboardSettings =
            read(config, logger).also { current = it }

        private fun read(config: PluginConfig, logger: Logger): ScoreboardSettings {
            val header = config.getStringList("scoreboard.header")
            val footer = config.getStringList("scoreboard.footer")

            return ScoreboardSettings(
                enabled = config.getBoolean("scoreboard.enabled", true),
                refreshIntervalTicks = config.getLong("scoreboard.refresh-interval", 2L).coerceIn(1L, 3600L) * 20L,
                defaultOn = config.getBoolean("scoreboard.default-on", true),
                titleFrameIntervalTicks = config.getLong("scoreboard.title-frame-interval", 10L).coerceIn(1L, 1200L),
                titleFrames = config.getStringList("scoreboard.title").ifEmpty { listOf("scoreboard.title.frame-1") },
                boards = readBoards(config, logger, header, footer),
            )
        }

        /**
         * Reads every board, wrapping each one's own lines in the shared frame.
         *
         * The frame is folded in here rather than at render time so that a board
         * is a finished list of lines by the time anything draws it, and so the
         * fifteen-line limit is checked against what a player will actually see.
         */
        private fun readBoards(
            config: PluginConfig,
            logger: Logger,
            header: List<String>,
            footer: List<String>,
        ): List<BoardDefinition> =
            config.getKeys("scoreboard.boards").mapNotNull { id ->
                val path = "scoreboard.boards.$id"
                val conditionName = config.getString("$path.condition", BoardCondition.ALWAYS.id)
                val condition = BoardCondition.parse(conditionName)

                if (condition == null) {
                    logger.warning(
                        "Scoreboard board '$id' has unknown condition '$conditionName'; it will never be shown. " +
                            "Known conditions: ${BoardCondition.ids().joinToString(", ")}"
                    )
                    return@mapNotNull null
                }

                val own = config.getStringList("$path.lines")
                val frame = header.size + footer.size
                val room = (MAX_LINES - frame).coerceAtLeast(0)

                if (own.size > room) {
                    logger.warning(
                        "Scoreboard board '$id' declares ${own.size} lines, but the header and footer take $frame of " +
                            "the $MAX_LINES available, so only its first $room are shown."
                    )
                }

                BoardDefinition(
                    id = id,
                    priority = config.getInt("$path.priority", 0),
                    condition = condition,
                    lines = header + own.take(room) + footer,
                )
            }.sortedByDescending { it.priority }
    }
}
