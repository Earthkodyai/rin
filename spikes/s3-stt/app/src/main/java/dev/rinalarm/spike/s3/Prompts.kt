package dev.rinalarm.spike.s3

/** One thing Rin says and the kind of answer the tester should give, in their own words. */
data class Prompt(val id: String, val intent: Intent, val cond: String, val question: String)

object Prompts {
    val TASK = mapOf(
        Intent.AFFIRM to "Agree / say yes",
        Intent.DENY to "Refuse / say no (not \"later\")",
        Intent.SNOOZE to "Ask for more sleep time",
        Intent.TIRED to "Say you're tired or sleepy",
        Intent.SICK to "Say you feel sick or unwell",
        Intent.GREET to "Greet her back",
        Intent.THANKS to "Thank her",
        Intent.UNKNOWN to "Say something off-topic (not an answer)",
    )

    val COND = mapOf(
        "normal" to "Phone in hand, normal voice",
        "sleepy" to "Lying down, quiet sleepy mumble",
        "far" to "Phone on the bed/nightstand ~1 m away",
    )

    private val QUESTIONS = mapOf(
        Intent.AFFIRM to listOf("Good morning! Are you awake?", "Ready to get up?", "Did you sleep okay?",
            "Shall we start the morning mission?", "Can you sit up for me?", "Are you up yet?"),
        Intent.DENY to listOf("Want to hear a fun fact?", "Did you sleep okay?", "Should I sing you a song?",
            "Do you want breakfast ideas?", "Are you out of bed yet?", "Did you remember your dream?"),
        Intent.SNOOZE to listOf("Time to get up!", "Rise and shine!", "It's seven o'clock!",
            "Come on, open your eyes!", "Up we go!", "The sun is up. Are you?"),
        Intent.TIRED to listOf("How are you feeling?", "You look sleepy. How was your night?", "How's your energy today?",
            "What's wrong?", "Why so slow today?", "How did you sleep?"),
        Intent.SICK to listOf("How are you feeling?", "You sound different. Are you okay?", "What's wrong?",
            "Is everything okay?", "How's your body today?", "You're quiet. Everything alright?"),
        Intent.GREET to listOf("Good morning!", "Hey you!", "Morning, sleepyhead!", "Hi there!",
            "Rise and shine!", "Hello again!"),
        Intent.THANKS to listOf("Mission complete! Great job!", "I saved your streak for you.", "You did it!",
            "I picked a nice song for you.", "Here's your weather: sunny.", "I'll wake you again tomorrow."),
        Intent.UNKNOWN to listOf("How are you feeling?", "Ready to get up?", "What's on your mind?",
            "Did you sleep okay?", "Time to get up!", "Good morning!"),
    )

    private val CONDS = listOf("normal", "normal", "normal", "sleepy", "sleepy", "far")

    // Record all normal answers first, then lie down for the sleepy ones, then put the phone down.
    private val COND_ORDER = listOf("normal", "sleepy", "far")

    /** 8 intents x 6 = 48, interleaved in a fixed order so the same intent rarely comes twice in a row. */
    val ALL: List<Prompt> = run {
        val list = QUESTIONS.flatMap { (intent, qs) ->
            qs.mapIndexed { i, q -> Prompt("%s-%d".format(intent.name.lowercase(), i + 1), intent, CONDS[i], q) }
        }
        list.shuffled(java.util.Random(3)).sortedBy { COND_ORDER.indexOf(it.cond) }
    }
}
