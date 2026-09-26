package dev.rinalarm.spike.s5

/**
 * What the tester points the camera at. The app only needs ids, names and trial conditions:
 * which ML Kit labels count as "found" lives in tools/labels.json and is scored on the PC,
 * so the app stays blind (it never shows whether a trial passed).
 */
data class Scene(val id: String, val name: String, val hint: String, val negative: Boolean = false)

data class Condition(val light: String, val how: String) {
    fun describe(): String {
        val l = if (light == "wake") "Light: as when you wake (don't switch on extra lights)" else "Light: room lights ON"
        val h = when (how) {
            "close" -> "Distance: close, about 40 cm"
            "mid" -> "Distance: about 1 m"
            "far" -> "Distance: about 2 m"
            "side" -> "Angle: from the side, about 1 m"
            "walk" -> "Start 2–3 m away, press Start, walk up and aim"
            "hold" -> "Point where you would lie and look at it"
            else -> how
        }
        return "$l\n$h"
    }
}

object Targets {
    const val TARGET_TRIALS = 10
    const val NEGATIVE_TRIALS = 6
    const val TRIAL_MS = 8_000L

    /** Mirror-free things that are away from the bed. The tester skips what they don't own. */
    val targets = listOf(
        Scene("cup", "Cup or mug", "Any cup you drink from"),
        Scene("shoes", "Shoes", "Shoes by the door"),
        Scene("sink", "Kitchen sink", "NOT the bathroom sink (mirror)"),
        Scene("tv", "TV", "Screen off is fine"),
        Scene("clock", "Clock", "Wall or desk clock, not the phone"),
        Scene("chair", "Chair", ""),
        Scene("sofa", "Sofa", ""),
        Scene("desk", "Desk or table", ""),
        Scene("computer", "Laptop or computer", ""),
        Scene("shelf", "Shelf or bookshelf", ""),
        Scene("stairs", "Stairs", ""),
        Scene("plant", "Plant or flower pot", ""),
        Scene("curtain", "Curtain", "Only if it's not right next to the bed"),
        Scene("cookware", "Pot or pan", ""),
        Scene("kitchen", "Kitchen counter or cabinets", ""),
        Scene("umbrella", "Umbrella", ""),
        Scene("helmet", "Motorbike helmet", ""),
        Scene("motorcycle", "Motorbike", ""),
        Scene("bag", "Bag or backpack", ""),
    )

    /** Things you can reach without leaving bed. No target may be "found" here. */
    val negatives = listOf(
        Scene("n-bed", "Bed", "Point at the bed / mattress", negative = true),
        Scene("n-pillow", "Pillow or blanket", "", negative = true),
        Scene("n-ceiling", "Ceiling", "Lying down, point up", negative = true),
        Scene("n-wall", "Wall or floor by the bed", "", negative = true),
        Scene("n-bedside", "Bedside table / things by the bed", "", negative = true),
        Scene("n-dark", "Dark: cover the lens with a finger", "", negative = true),
    )

    private val targetConditions = listOf(
        Condition("wake", "close"), Condition("on", "close"),
        Condition("wake", "mid"), Condition("on", "mid"),
        Condition("wake", "far"), Condition("on", "far"),
        Condition("wake", "side"), Condition("on", "side"),
        Condition("wake", "walk"), Condition("on", "walk"),
    )

    /** Trial [n] (1-based) gets a fixed condition, so each session covers the same mix. */
    fun condition(scene: Scene, n: Int): Condition =
        if (scene.negative) Condition(if (n % 2 == 1) "wake" else "on", "hold")
        else targetConditions[(n - 1).mod(targetConditions.size)]

    fun planned(scene: Scene) = if (scene.negative) NEGATIVE_TRIALS else TARGET_TRIALS

    /** Custom targets typed on the phone get a stable id from their name. */
    fun customId(name: String) = "c-" + name.trim().lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
}
