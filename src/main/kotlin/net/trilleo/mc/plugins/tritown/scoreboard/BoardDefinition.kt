package net.trilleo.mc.plugins.tritown.scoreboard

/**
 * One sidebar layout, as declared under `scoreboard.boards` in `config.yml`.
 *
 * @param id        the name the board is written under, used by `/tritown scoreboard board <id>`
 * @param priority  higher wins when several boards' conditions match at once
 * @param condition when this board applies
 * @param lines     translation keys, top line first, already wrapped in the shared
 *                  header and footer; an empty entry is a blank spacer
 */
data class BoardDefinition(
    val id: String,
    val priority: Int,
    val condition: BoardCondition,
    val lines: List<String>,
)
