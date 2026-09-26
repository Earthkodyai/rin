package dev.rinalarm.spike.s3

import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Matcher ablation on saved transcripts (no phone needed). Skipped unless -Ps3.results=<file> is given.
 * Writes <file>.ablation.md next to the input.
 */
class RescoreTest {
    private val configs = linkedMapOf(
        "v1 (frozen)" to IntentMatcher.Options(),
        "+coverage" to IntentMatcher.Options(coverage = true),
        "+phonetic" to IntentMatcher.Options(phonetic = true),
        "+coverage +phonetic" to IntentMatcher.Options(coverage = true, phonetic = true),
    )

    @Test fun rescore() {
        val path = System.getProperty("s3.results").orEmpty()
        assumeTrue("no -Ps3.results given", path.isNotBlank())
        // Several result files can be combined (comma-separated), e.g. a baseline run plus an ablation run.
        val utt = path.split(',').flatMap { File(it).readLines() }.filter { it.isNotBlank() }.map { JSONObject(it) }
            .filter { it.getString("phase") == "utt" }.toMutableList()
        // Cascade: Android's text unless it is empty, then Vosk grammar's (Android drops one-word answers).
        val fallback = "vosk-grammar"
        for (primary in utt.map { it.getString("engine") }.distinct().filter { it.startsWith("android") }) {
            val byId = utt.filter { it.getString("engine") == fallback }.associateBy { it.getString("id") }
            if (byId.isEmpty()) break
            utt += utt.filter { it.getString("engine") == primary }.map { a ->
                val pick = if (a.getString("text").isBlank()) byId[a.getString("id")] ?: a else a
                JSONObject(pick.toString()).put("engine", "$primary > $fallback")
            }
        }
        val engines = utt.map { it.getString("engine") }.distinct()
        val out = StringBuilder("| Engine | " + configs.keys.joinToString(" | ") + " |\n|---|" + "---|".repeat(configs.size) + "\n")
        for (e in engines) {
            val rows = utt.filter { it.getString("engine") == e }
            out.append("| $e | ")
            out.append(configs.values.joinToString(" | ") { o ->
                val ok = rows.count { IntentMatcher.classify(it.getString("text"), o).name == it.getString("intent") }
                "$ok/${rows.size} (${"%.1f".format(100.0 * ok / rows.size)}%)"
            })
            out.append(" |\n")
        }
        out.append("\nChanged by +coverage +phonetic (v1 -> new):\n")
        val both = configs.values.last()
        for (r in utt) {
            val a = IntentMatcher.classify(r.getString("text")); val b = IntentMatcher.classify(r.getString("text"), both)
            if (a != b) out.append("- ${r.getString("engine")} ${r.getString("id")} [${r.getString("intent")}] \"${r.getString("text")}\": $a -> $b\n")
        }
        File("${path.split(',').first()}.ablation.md").writeText(out.toString())
        println(out)
    }
}
