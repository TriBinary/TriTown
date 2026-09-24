package net.trilleo.mc.plugins.tritown.mobs

/**
 * How mob levels climb across one world.
 *
 * Around the world's spawn is a hub where mobs are [base]. Beyond it the world
 * is cut into rings [ringWidth] blocks wide, each [perRing] levels harder than
 * the one inside it, up to [cap]. Plain Kotlin, so the rings can be tested.
 */
data class LevelZone(
    val base: Int,
    val spawnRadius: Double,
    val ringWidth: Double,
    val perRing: Int,
    val cap: Int,
) {

    /** 0 inside the hub, then 1 for the first ring out and so on. */
    fun ring(distance: Double): Int =
        if (distance <= spawnRadius) 0 else 1 + ((distance - spawnRadius) / ringWidth).toInt()

    /**
     * The level of a mob spawning [distance] blocks from spawn. [bonus] — night,
     * depth — only applies outside the hub, which stays as tame as its base.
     */
    fun level(distance: Double, bonus: Int): Int {
        val ring = ring(distance)
        val level = base + ring * perRing + if (ring > 0) bonus else 0
        return level.coerceIn(1, cap.coerceAtLeast(base).coerceAtLeast(1))
    }
}
