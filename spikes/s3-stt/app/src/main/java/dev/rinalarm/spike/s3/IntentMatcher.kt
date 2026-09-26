package dev.rinalarm.spike.s3

enum class Intent { AFFIRM, DENY, SNOOZE, TIRED, SICK, GREET, THANKS, UNKNOWN }

/**
 * Keyword + fuzzy intent matcher, frozen before any test speech was recorded so the spike
 * measures STT, not a matcher tuned to the test set. First rule (in list order) that matches wins.
 */
object IntentMatcher {

    private class Rule(val intent: Intent, phrases: List<String>, val negated: Intent? = null) {
        val phrases = phrases.map { it.split(' ') }
    }

    private val NEGATORS = setOf("not", "no", "never")

    private val RULES = listOf(
        // "I don't know" must not read as a refusal.
        Rule(Intent.UNKNOWN, listOf("do not know", "not sure", "no idea")),
        Rule(Intent.SICK, listOf(
            "sick", "ill", "unwell", "fever", "headache", "head hurts", "migraine", "stomachache",
            "stomach", "flu", "a cold", "cough", "nauseous", "nausea", "throw up", "dizzy", "hurts", "pain",
            "feel bad", "feel terrible", "feel awful", "feeling bad", "feeling terrible", "feeling awful",
            "not feeling well", "not feel well", "not feeling good", "not feel good",
        ), negated = Intent.UNKNOWN),
        Rule(Intent.SNOOZE, listOf(
            "snooze", "minutes", "minute", "five more", "ten more", "few more", "one more", "more time",
            "more sleep", "sleep more", "sleep a bit", "let me sleep", "later", "not yet", "in a bit",
            "wait", "hold on",
        )),
        Rule(Intent.TIRED, listOf(
            "tired", "sleepy", "exhausted", "drained", "worn out", "groggy", "no energy", "low energy",
            "did not sleep", "barely slept", "bad sleep", "slept badly", "could not sleep", "stayed up",
            "up late", "zombie", "can not wake", "sleep deprived",
        ), negated = Intent.UNKNOWN),
        Rule(Intent.DENY, listOf("no", "nope", "nah", "not really", "never", "do not want", "i do not", "i will not")),
        Rule(Intent.THANKS, listOf("thanks", "thank", "appreciate", "thx", "cheers")),
        Rule(Intent.GREET, listOf("good morning", "morning", "hello", "hi", "hey", "hiya", "yo", "what is up", "howdy")),
        Rule(Intent.AFFIRM, listOf(
            "yes", "yeah", "yep", "yup", "ya", "yea", "sure", "okay", "ok", "alright", "all right",
            "ready", "awake", "i am up", "getting up", "fine", "good", "great", "well", "of course",
            "definitely", "absolutely", "let us go", "sounds good", "got it", "uh huh", "mhm",
            "i will", "i can", "i did", "why not", "totally",
        ), negated = Intent.DENY),
    )

    private val CONTRACTIONS = mapOf(
        "can't" to "can not", "cant" to "can not", "won't" to "will not", "wont" to "will not",
        "don't" to "do not", "dont" to "do not", "didn't" to "did not", "didnt" to "did not",
        "i'm" to "i am", "im" to "i am", "i'll" to "i will", "let's" to "let us", "lets" to "let us",
        "what's" to "what is", "whats" to "what is", "couldn't" to "could not", "couldnt" to "could not",
        "okey" to "okay", "o.k." to "ok",
    )

    fun normalize(text: String): List<String> {
        val words = text.lowercase().replace('’', '\'').split(Regex("[^a-z0-9'.]+")).filter { it.isNotBlank() }
        return words.flatMap { w ->
            val t = w.trim('.', '\'')
            (CONTRACTIONS[t] ?: t.replace(Regex("n't$"), " not").replace(Regex("'(m|re|ll|s|ve|d)$"), ""))
                .replace(".", "").split(' ')
        }.filter { it.isNotBlank() }
    }

    /**
     * Ablation switches. All off = v1, the matcher frozen before any speech was recorded.
     * [coverage] adds phrases found missing in the dev-set error analysis; [phonetic] also accepts a
     * token whose Thai-accent-aware sound key equals the cue's (seek ~ sick, slip ~ sleep).
     */
    data class Options(val coverage: Boolean = false, val phonetic: Boolean = false)

    private val COVERAGE_RULES = listOf(
        Rule(Intent.SNOOZE, listOf("want to sleep", "wanna sleep", "want sleep", "back to sleep", "sleep again")),
    )

    fun classify(text: String?, opts: Options = Options()): Intent {
        val tokens = normalize(text ?: return Intent.UNKNOWN)
        if (tokens.isEmpty()) return Intent.UNKNOWN
        // Coverage rules go first: they are more specific than the single words they overlap ("sleep").
        val rules = if (opts.coverage) COVERAGE_RULES + RULES else RULES
        for (rule in rules) {
            for (p in rule.phrases) {
                for (start in 0..tokens.size - p.size) {
                    if (!p.indices.all { wordMatches(p[it], tokens[start + it], opts.phonetic) }) continue
                    val negated = rule.negated != null &&
                        (maxOf(0, start - 2) until start).any { tokens[it] in NEGATORS }
                    if (!negated) return rule.intent
                    if (rule.negated != Intent.UNKNOWN) return rule.negated!!
                    // "not tired": this cue doesn't count; keep looking.
                }
            }
        }
        return Intent.UNKNOWN
    }

    /** Exact for short words; one edit allowed for words of 5+ letters (STT spelling slips). */
    private fun wordMatches(cue: String, token: String, phonetic: Boolean = false): Boolean =
        cue == token || (cue.length >= 5 && token.length >= 5 && editDistance(cue, token) <= 1) ||
            (phonetic && cue.length >= 4 && token.length >= 3 && token !in SOUND_STOP && soundKey(cue) == soundKey(token))

    // Common words whose sound key collides with a cue (when~well, yet~yes, high~hi, what~wait).
    private val SOUND_STOP = setOf("when", "then", "what", "yet", "high", "the", "there", "they", "will", "fill", "this")

    /**
     * Rough sound key that merges the contrasts Thai speakers of English commonly don't make,
     * from general L1-transfer patterns (not from any recording): vowel length (sick/seek,
     * slip/sleep, full/fool), v->w, th->t/d, z->s, sh->ch, r->l, and Thai final consonants
     * (final s/ch -> t, l -> n, f/v -> p). Doubled letters and silent final e are dropped.
     */
    fun soundKey(word: String): String {
        var w = word.lowercase().filter { it in 'a'..'z' }
        if (w.length > 3 && w.endsWith("e")) w = w.dropLast(1)
        val digraphs = listOf("ph" to "f", "th" to "t", "sh" to "ch", "ck" to "k", "wh" to "w", "kn" to "n",
            "gh" to "", "ee" to "i", "ea" to "i", "ie" to "i", "ey" to "i", "oo" to "u", "ou" to "u",
            "oa" to "o", "ai" to "e", "ay" to "e")
        for ((a, b) in digraphs) w = w.replace(a, b)
        w = w.replace(Regex("c(?=[eiy])"), "s").replace('c', 'k').replace('q', 'k').replace("x", "ks")
            .replace('z', 's').replace('v', 'w').replace('r', 'l').replace('y', 'i').replace('d', 't')
        w = w.replace(Regex("(.)\\1+"), "$1")
        // Thai final consonants: only p t k m n ng w y can end a syllable.
        w = w.replace(Regex("(s|ch|j)$"), "t").replace(Regex("l$"), "n").replace(Regex("f$"), "p")
        return w
    }

    private fun editDistance(a: String, b: String): Int {
        if (kotlin.math.abs(a.length - b.length) > 1) return 2
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            cur.copyInto(prev)
        }
        return prev[b.length]
    }

    /** Cue phrases (normalized form) for Android's EXTRA_BIASING_STRINGS. */
    fun cuePhrases(): List<String> = RULES.flatMap { r -> r.phrases.map { it.joinToString(" ") } }.distinct()

    /** Words for Vosk's grammar mode: every cue word in raw spoken form, common fillers, and [unk]. */
    fun voskVocabulary(): List<String> {
        val cueWords = RULES.flatMap { r -> r.phrases.flatten() }
        val raw = listOf("don't", "didn't", "can't", "won't", "i'm", "i'll", "let's", "what's", "couldn't")
        val fillers = listOf(
            "i", "am", "so", "a", "the", "you", "me", "rin", "just", "please", "really", "very", "feel",
            "feeling", "today", "bit", "it", "is", "my", "too", "and", "but", "oh", "um", "uh", "up", "get",
            "go", "now", "still", "little", "bed", "sleep", "slept", "night", "want", "to", "do", "have",
        )
        return (cueWords + raw + fillers).distinct() + "[unk]"
    }
}
