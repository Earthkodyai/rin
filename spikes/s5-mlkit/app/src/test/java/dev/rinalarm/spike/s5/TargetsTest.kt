package dev.rinalarm.spike.s5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetsTest {
    private val obj = Scene(Targets.objectId("Fridge"), "Fridge", "")

    @Test fun negativeIdsAreUnique() {
        val ids = Targets.negatives.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun tenTargetTrialsCoverEveryDistanceTwiceWithLightsOn() {
        val conds = (1..Targets.TARGET_TRIALS).map { Targets.condition(obj, it) }
        assertTrue(conds.all { it.light == "on" })
        assertEquals(listOf("close", "mid", "far", "side", "walk").associateWith { 2 }, conds.groupingBy { it.how }.eachCount())
    }

    @Test fun onlyTheDarkNegativeIsDark() {
        Targets.negatives.forEach { n ->
            assertEquals(n.id, if (n.id == "n-dark") "dark" else "on", Targets.condition(n, 1).light)
        }
    }

    @Test fun objectIdsAreStableAndSafe() {
        assertEquals("o-rice-cooker", Targets.objectId("  Rice Cooker! "))
        assertTrue(Targets.negatives.none { it.id.startsWith("o-") })
    }
}
