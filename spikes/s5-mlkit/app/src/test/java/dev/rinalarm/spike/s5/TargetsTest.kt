package dev.rinalarm.spike.s5

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetsTest {
    @Test fun sceneIdsAreUnique() {
        val ids = (Targets.targets + Targets.negatives).map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test fun tenTargetTrialsSplitLightEvenlyAndCoverEveryDistance() {
        val conds = (1..Targets.TARGET_TRIALS).map { Targets.condition(Targets.targets[0], it) }
        assertEquals(5, conds.count { it.light == "wake" })
        assertEquals(setOf("close", "mid", "far", "side", "walk"), conds.map { it.how }.toSet())
        assertEquals(10, conds.toSet().size)
    }

    @Test fun negativesAlternateLight() {
        val conds = (1..Targets.NEGATIVE_TRIALS).map { Targets.condition(Targets.negatives[0], it) }
        assertEquals(3, conds.count { it.light == "wake" })
        assertTrue(conds.all { it.how == "hold" })
    }

    @Test fun customIdsAreStableAndSafe() {
        assertEquals("c-rice-cooker", Targets.customId("  Rice Cooker! "))
        assertTrue(Targets.targets.none { it.id.startsWith("c-") })
    }
}
