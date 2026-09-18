package net.trilleo.mc.plugins.tritown.economy

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.*

class EconomyFormatTest {

    private val dollar = Currency(
        id = "dollar",
        singular = "Dollar",
        plural = "Dollars",
        symbol = "$",
        fractionalDigits = 2,
        plainFormat = "%symbol%%amount%",
        richFormat = "<gold>%symbol%%amount%</gold>",
    )

    @BeforeTest
    fun setUp() {
        CurrencyRegistry.load(listOf(dollar), dollar.id)
        EconomyFormat.invalidate()
    }

    @AfterTest
    fun tearDown() {
        CurrencyRegistry.clear()
    }

    @Test
    fun `formats plain amounts with grouping and a fixed scale`() {
        assertEquals("$0.00", EconomyFormat.plain(0.0))
        assertEquals("$100.00", EconomyFormat.plain(100.0))
        assertEquals("$1,234.56", EconomyFormat.plain(1234.56))
        assertEquals("$1,000,000.00", EconomyFormat.plain(1_000_000.0))
    }

    @Test
    fun `plain output carries no MiniMessage tags because other plugins print it verbatim`() {
        val formatted = EconomyFormat.plain(1234.56)

        assertTrue(!formatted.contains('<'), "plain output must not contain tags: $formatted")
        assertTrue(!formatted.contains('>'), "plain output must not contain tags: $formatted")
    }

    @Test
    fun `rich output renders the configured MiniMessage pattern`() {
        val rendered = PlainTextComponentSerializer.plainText().serialize(EconomyFormat.rich(1234.56))

        assertEquals("$1,234.56", rendered)
    }

    @Test
    fun `picks the singular name only for exactly one unit`() {
        val pattern = dollar.copy(plainFormat = "%amount% %currency%")

        assertEquals("1.00 Dollar", EconomyFormat.plain(pattern, pattern.of(1.0)))
        assertEquals("2.00 Dollars", EconomyFormat.plain(pattern, pattern.of(2.0)))
        assertEquals("0.00 Dollars", EconomyFormat.plain(pattern, pattern.of(0.0)))
        assertEquals("-1.00 Dollar", EconomyFormat.plain(pattern, pattern.of(-1.0)))
    }

    @Test
    fun `honours a currency with no fractional digits`() {
        val credit = Currency("credit", "Credit", "Credits", "", 0, "%amount% %currency%", "%amount%")

        assertEquals("1,500 Credits", EconomyFormat.plain(credit, credit.of(1500.0)))
    }

    @Test
    fun `formats identically from many threads at once`() {
        val expected = EconomyFormat.plain(1234.56)
        val pool = Executors.newFixedThreadPool(16)

        val results = try {
            pool.invokeAll((1..16).map { Callable { List(500) { EconomyFormat.plain(1234.56) }.toSet() } })
                .map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        // A shared DecimalFormat garbles output under contention; every result must be the one string.
        assertEquals(setOf(expected), results.flatten().toSet())
    }
}
