package dev.rinalarm.spike.s5

/**
 * Part B (design change after part A): lights on first, and targets are the tester's own objects,
 * taught once at setup and matched by image embeddings. The app stays blind: which frames count
 * as "found" is decided on the PC by tools/score_b.py. Part A's generic-label version of this file
 * is in commit 1b4b563.
 */
data class Scene(val id: String, val name: String, val hint: String, val negative: Boolean = false)

data class Condition(val light: String, val how: String) {
    fun describe(): String {
        val l = when (light) {
            "dark" -> "Light: OFF, as when you wake"
            else -> "Light: room lights ON"
        }
        val h = when (how) {
            "close" -> "Distance: close, about 40 cm"
            "mid" -> "Distance: about 1 m"
            "far" -> "Distance: about 2 m"
            "side" -> "Angle: from the side, about 1 m"
            "walk" -> "Start 2–3 m away, press Start, walk up and aim"
            "hold" -> "From bed, without getting up"
            else -> how
        }
        return "$l\n$h"
    }
}

object Targets {
    const val TARGET_TRIALS = 10
    const val NEGATIVE_TRIALS = 6
    const val TRIAL_MS = 8_000L
    const val TEACH_MS = 6_000L
    const val BED_SCAN_MS = 15_000L

    /** Things reachable from bed. With the lights on nothing may match; in the dark the light gate must block. */
    val negatives = listOf(
        Scene("n-bed", "Bed", "Point at the bed / mattress", negative = true),
        Scene("n-pillow", "Pillow or blanket", "", negative = true),
        Scene("n-ceiling", "Ceiling", "Lying down, point up", negative = true),
        Scene("n-wall", "Wall or floor by the bed", "", negative = true),
        Scene("n-bedside", "Bedside table / things by the bed", "", negative = true),
        Scene("n-dark", "Anything, lights OFF", "Checks the light gate", negative = true),
    )

    private val targetConditions = listOf("close", "mid", "far", "side", "walk").map { Condition("on", it) }

    /** Trial [n] (1-based) gets a fixed condition, so each session covers the same mix. */
    fun condition(scene: Scene, n: Int): Condition =
        if (scene.negative) Condition(if (scene.id == "n-dark") "dark" else "on", "hold")
        else targetConditions[(n - 1).mod(targetConditions.size)]

    fun planned(scene: Scene) = if (scene.negative) NEGATIVE_TRIALS else TARGET_TRIALS

    /** Objects typed on the phone get a stable id from their name. */
    fun objectId(name: String) = "o-" + name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}
