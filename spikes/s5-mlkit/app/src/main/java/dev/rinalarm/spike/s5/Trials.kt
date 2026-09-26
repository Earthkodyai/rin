package dev.rinalarm.spike.s5

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One analysed camera frame. No pixels are kept: only labels, timing and brightness. */
data class Frame(val tMs: Long, val inferMs: Long, val luma: Int, val lux: Float, val labels: List<Pair<String, Float>>)

class TrialRecorder(
    val id: String,
    val session: String,
    val scene: Scene,
    val n: Int,
    val condition: Condition,
    val startWallMs: Long,
    val startElapsedMs: Long,
    val luxStart: Float,
) {
    private val frames = ArrayList<Frame>()
    var width = 0
    var height = 0
    var rotation = 0

    @Synchronized fun add(frameElapsedMs: Long, inferMs: Long, luma: Int, lux: Float, labels: List<Pair<String, Float>>) {
        // A frame grabbed before Start was pressed belongs to no trial.
        if (frameElapsedMs < startElapsedMs) return
        frames += Frame(frameElapsedMs - startElapsedMs, inferMs, luma, lux, labels)
    }

    @Synchronized fun frameCount() = frames.size

    @Synchronized fun toJson(luxEnd: Float): JSONObject = JSONObject()
        .put("type", "trial").put("id", id).put("session", session)
        .put("kind", if (scene.negative) "negative" else "target")
        .put("scene", scene.id).put("name", scene.name).put("n", n)
        .put("light", condition.light).put("how", condition.how)
        .put("start", startWallMs).put("window_ms", Targets.TRIAL_MS)
        .put("lux_start", luxStart.toDouble()).put("lux_end", luxEnd.toDouble())
        .put("w", width).put("h", height).put("rot", rotation)
        .put("frames", JSONArray().apply {
            frames.forEach { f ->
                put(JSONObject().put("t", f.tMs).put("inf", f.inferMs).put("luma", f.luma).put("lux", f.lux.toDouble())
                    .put("labels", JSONArray().apply {
                        f.labels.forEach { (text, conf) -> put(JSONArray().put(text).put(Math.round(conf * 1000) / 1000.0)) }
                    }))
            }
        })
}

/** Append-only JSONL log. Discards are separate records, so nothing is ever rewritten. */
class TrialStore(dir: File) {
    val file = File(dir.apply { mkdirs() }, "trials.jsonl")
    private val counts = HashMap<String, Int>()
    private val ids = ArrayList<Pair<String, String>>() // (trial id, count key), for "discard last"

    init {
        if (file.exists()) file.forEachLine { line ->
            val o = runCatching { JSONObject(line) }.getOrNull() ?: return@forEachLine
            when (o.optString("type")) {
                "trial" -> { val k = key(o.getString("session"), o.getString("scene")); counts.merge(k, 1, Int::plus); ids += o.getString("id") to k }
                "discard" -> ids.firstOrNull { it.first == o.getString("id") }?.let { counts.merge(it.second, -1, Int::plus); ids.remove(it) }
            }
        }
    }

    private fun key(session: String, scene: String) = "$session/$scene"
    fun count(session: String, scene: String) = counts[key(session, scene)] ?: 0
    fun lastId(): String? = ids.lastOrNull()?.first

    @Synchronized fun append(o: JSONObject) {
        file.appendText(o.toString() + "\n")
        if (o.optString("type") == "trial") {
            val k = key(o.getString("session"), o.getString("scene"))
            counts.merge(k, 1, Int::plus); ids += o.getString("id") to k
        }
    }

    @Synchronized fun discard(id: String, reason: String) {
        val entry = ids.firstOrNull { it.first == id } ?: return
        file.appendText(JSONObject().put("type", "discard").put("id", id).put("reason", reason).put("at", System.currentTimeMillis()).toString() + "\n")
        counts.merge(entry.second, -1, Int::plus); ids.remove(entry)
    }
}
