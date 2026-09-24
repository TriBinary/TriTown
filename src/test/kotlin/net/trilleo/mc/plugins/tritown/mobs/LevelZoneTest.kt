package net.trilleo.mc.plugins.tritown.mobs

import kotlin.test.Test
import kotlin.test.assertEquals

class LevelZoneTest {

    private val overworld = LevelZone(base = 1, spawnRadius = 300.0, ringWidth = 500.0, perRing = 2, cap = 30)

    @Test
    fun `the spawn area stays at its base, bonuses and all`() {
        assertEquals(0, overworld.ring(0.0))
        assertEquals(0, overworld.ring(300.0))
        assertEquals(1, overworld.level(250.0, bonus = 4))
    }

    @Test
    fun `each ring out is harder than the one inside it`() {
        assertEquals(1, overworld.ring(300.1))
        assertEquals(3, overworld.level(300.1, bonus = 0))
        assertEquals(1, overworld.ring(799.9))
        assertEquals(2, overworld.ring(800.0))
        assertEquals(5, overworld.level(800.0, bonus = 0))
    }

    @Test
    fun `bonuses count outside the spawn area`() {
        assertEquals(7, overworld.level(1_000.0, bonus = 2))
    }

    @Test
    fun `levels stop at the cap`() {
        assertEquals(30, overworld.level(1_000_000.0, bonus = 10))
    }

    @Test
    fun `a cap below the base is lifted to it`() {
        val zone = LevelZone(base = 10, spawnRadius = 0.0, ringWidth = 100.0, perRing = 5, cap = 5)
        assertEquals(10, zone.level(1_000.0, bonus = 0))
    }
}
