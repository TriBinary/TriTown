package net.trilleo.mc.plugins.tritown.content

import org.yaml.snakeyaml.Yaml
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BalanceParserTest {

    @Test
    fun `the bundled file is exactly the defaults`() {
        val stream = checkNotNull(javaClass.classLoader.getResourceAsStream("content/balance.yml")) {
            "content/balance.yml is not bundled"
        }
        val root = stream.reader(Charsets.UTF_8).use { Yaml().load<Map<String, Any?>>(it) }

        val result = BalanceParser.parse(root)

        assertEquals(emptyList(), result.warnings)
        assertEquals(Balance.DEFAULT, result.balance)
    }

    @Test
    fun `a missing value takes the default quietly`() {
        val result = BalanceParser.parse(mapOf("player" to mapOf("health" to 250)))

        assertEquals(250.0, result.balance.player.health)
        assertEquals(Balance.DEFAULT.lens, result.balance.lens)
        assertEquals(emptyList(), result.warnings)
    }

    @Test
    fun `an unusable value takes the default and says so`() {
        val result = BalanceParser.parse(
            mapOf(
                "lens" to "five",
                "mobs" to mapOf("health-growth" to 0.5),
            )
        )

        assertEquals(Balance.DEFAULT.lens, result.balance.lens)
        assertEquals(Balance.DEFAULT.mobs.healthGrowth, result.balance.mobs.healthGrowth)
        assertEquals(2, result.warnings.size)
        assertTrue(result.warnings.any { "mobs.health-growth" in it })
    }
}
