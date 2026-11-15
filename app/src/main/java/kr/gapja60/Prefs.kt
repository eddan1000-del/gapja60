package kr.gapja60

import android.content.Context
import java.time.LocalDate

/** 설정과 학습 기록. 모두 기기 안에만 저장된다. */
class Prefs(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("gapja60", Context.MODE_PRIVATE)

    private fun int(k: String, d: Int) = p.getInt(k, d)
    private fun setInt(k: String, v: Int) = p.edit().putInt(k, v).apply()
    private fun float(k: String, d: Float) = p.getFloat(k, d)
    private fun setFloat(k: String, v: Float) = p.edit().putFloat(k, v).apply()

    var direction: Int get() = int("dir", 0); set(v) = setInt("dir", v)
    var speed: Float get() = float("speed", 1f); set(v) = setFloat("speed", v)
    var script: Int get() = int("script", Script.BOTH.ordinal); set(v) = setInt("script", v)
    /** 0 시스템, 1 밝게, 2 어둡게 */
    var theme: Int get() = int("theme", 0); set(v) = setInt("theme", v)
    /** 1배속 기준 항목 사이 쉬는 시간(ms). 배속을 올리면 같은 비율로 줄어든다. */
    var gapMs: Int get() = int("gap", 400); set(v) = setInt("gap", v)
    /** 빈칸에서 속으로 채울 시간(ms, 1배속 기준) */
    var thinkMs: Int get() = int("think", 1500); set(v) = setInt("think", v)
    var blankRatio: Float get() = float("blank", 0.3f); set(v) = setFloat("blank", v)
    var blankStreak: Int get() = int("blankStreak", 0); set(v) = setInt("blankStreak", v)
    var contextCount: Int get() = int("ctx", 2); set(v) = setInt("ctx", v)
    /** 0 = 무한 반복 */
    var repeat: Int get() = int("repeat", 1); set(v) = setInt("repeat", v)
    var xuns: Set<Int>
        get() = (p.getString("xuns", "012345") ?: "012345").mapNotNull { it.digitToIntOrNull() }.toSet()
        set(v) = p.edit().putString("xuns", v.sorted().joinToString("")).apply()

    // ---- 기록 ----
    fun addStudy(ms: Long) {
        if (ms <= 0) return
        val today = LocalDate.now().toEpochDay()
        val days = studyDays().toMutableSet().apply { add(today) }
        p.edit()
            .putLong("studyMs", p.getLong("studyMs", 0) + ms)
            .putString("days", days.sorted().takeLast(400).joinToString(","))
            .apply()
    }

    val studyMs: Long get() = p.getLong("studyMs", 0)

    private fun studyDays(): Set<Long> =
        (p.getString("days", "") ?: "").split(",").mapNotNull { it.toLongOrNull() }.toSet()

    /** 오늘(또는 어제)까지 이어진 연속 학습일 */
    fun streak(): Int {
        val days = studyDays()
        var d = LocalDate.now().toEpochDay()
        if (d !in days) d -= 1
        var n = 0
        while (d in days) { n++; d-- }
        return n
    }

    fun addCompletion(dir: Int) = setInt("done$dir", int("done$dir", 0) + 1)
    fun completions(dir: Int) = int("done$dir", 0)

    fun record(dir: Int, idx: Int, ok: Boolean) {
        val k = if (ok) "ok_${dir}_$idx" else "ng_${dir}_$idx"
        setInt(k, int(k, 0) + 1)
    }
    fun okCount(dir: Int, idx: Int) = int("ok_${dir}_$idx", 0)
    fun ngCount(dir: Int, idx: Int) = int("ng_${dir}_$idx", 0)

    fun resetRecords() {
        val e = p.edit()
        p.all.keys.filter { it.startsWith("ok_") || it.startsWith("ng_") || it.startsWith("done") || it == "studyMs" || it == "days" || it == "blankStreak" }
            .forEach { e.remove(it) }
        e.apply()
    }

    var voiceInfo: String get() = p.getString("voiceInfo", "") ?: ""; set(v) = p.edit().putString("voiceInfo", v).apply()
}
