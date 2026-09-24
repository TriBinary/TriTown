package net.trilleo.mc.plugins.tritown.mobs

import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class DamageLedgerTest {

    private val mob = UUID.randomUUID()
    private val alex = UUID.randomUUID()
    private val sam = UUID.randomUUID()

    @Test
    fun `every player's damage counts towards the share`() {
        val ledger = DamageLedger()
        ledger.credit(mob, alex, 30.0)
        ledger.credit(mob, sam, 20.0)
        ledger.credit(mob, alex, 10.0)

        assertEquals(0.6, ledger.share(mob, max = 100.0), 1e-9)
    }

    @Test
    fun `nothing is credited for nothing`() {
        val ledger = DamageLedger()
        ledger.credit(mob, alex, 0.0)
        ledger.credit(mob, alex, -5.0)

        assertEquals(0.0, ledger.share(mob, max = 100.0))
    }

    @Test
    fun `a forgotten mob starts over`() {
        val ledger = DamageLedger()
        ledger.credit(mob, alex, 80.0)
        ledger.forget(mob)

        assertEquals(0.0, ledger.share(mob, max = 100.0))
    }
}
