package net.trilleo.mc.plugins.tritown.scoreboard

import net.trilleo.mc.plugins.tritown.config.ScoreboardSettings
import net.trilleo.mc.plugins.tritown.utils.Lang

/** The finished text of one sidebar, before it is parsed into components. */
data class RenderedBoard(val title: String, val lines: List<String>)

/**
 * Turns a board into the text a player sees.
 *
 * Kept apart from [ScoreboardService] so that what a sidebar *says* can be
 * reasoned about without the lifecycle, diffing and Towny plumbing around it.
 * Nothing here touches the server: it takes a context and returns strings.
 */
object BoardRenderer {

    /** Renders [board] for [context], with the title on animation frame [frame]. */
    fun render(
        context: PlayerContext,
        board: BoardDefinition,
        settings: ScoreboardSettings,
        frame: Int,
    ): RenderedBoard {
        val frames = settings.titleFrames
        return RenderedBoard(
            title = resolve(frames[frame.mod(frames.size)], context),
            lines = board.lines.map { key -> if (key.isEmpty()) "" else resolve(key, context) },
        )
    }

    /**
     * Translates [key] for the viewer, then fills in the markers the translation
     * carries.
     *
     * Translating first is what lets a translator move a value to wherever it
     * reads best in their language, rather than being held to the order the
     * English line happens to use.
     */
    private fun resolve(key: String, context: PlayerContext): String =
        PlaceholderEngine.apply(Lang.tr(context.player, key), context)
}
