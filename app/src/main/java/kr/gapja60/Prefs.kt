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
    /** 0 시스템(낮/밤 자동), 1 낮, 2 밤, 3 수면 */
    var theme: Int get() = int("theme", 2); set(v) = setInt("theme", v)
    var voicePack: String get() = p.getString("pack", "f_calm") ?: "f_calm"; set(v) = p.edit().putString("pack", v).apply()
    fun voiceInfo(pack: String): String = p.getString("voiceInfo_$pack", "") ?: ""
    fun setVoiceInfo(pack: String, v: String) = p.edit().putString("voiceInfo_$pack", v).apply()

    /** 음색: 0 자연, 1 부드럽게(기본), 2 더 부드럽게 */
    var softness: Int get() = int("soft", 1); set(v) = setInt("soft", v)
    /** 목소리 설정: 0 새 설정(권장, 그르렁거림 개선), 1 이전 설정(1.0.19 음높이) */
    var voiceProfile: Int get() = int("vprof", 0); set(v) = setInt("vprof", v)
    fun speechFactor(pack: String, variant: String): Float? =
        p.getFloat("sf_${pack}_$variant", -1f).takeIf { it > 0f }
    fun setSpeechFactor(pack: String, variant: String, v: Float) = setFloat("sf_${pack}_$variant", v)
    var variantVersion: Int get() = int("varVer", 0); set(v) = setInt("varVer", v)
    var voiceVersion: Int get() = int("voiceVer", 0); set(v) = setInt("voiceVer", v)
    /** 배경 소리 크기: 0 없음, 1 작게, 2 보통 */
    var bgLevel: Int get() = int("bg", 0); set(v) = setInt("bg", v)

    // 수면 모드
    var sleepSpeed: Float get() = float("sleepSpeed", 0.8f); set(v) = setFloat("sleepSpeed", v)
    var sleepGapMs: Int get() = int("sleepGap", 1500); set(v) = setInt("sleepGap", v)
    var sleepTimer: Int get() = int("sleepTimer", 30); set(v) = setInt("sleepTimer", v)
    var sleepFadeMin: Int get() = int("sleepFade", 2); set(v) = setInt("sleepFade", v)
    /** 1배속 기준 항목 사이 쉬는 시간(ms). 배속을 올리면 같은 비율로 줄어든다. */
    var gapMs: Int get() = int("gap", 400); set(v) = setInt("gap", v)
    /** 빈칸에서 속으로 채울 시간(ms, 1배속 기준) */
    var thinkMs: Int get() = int("think", 1500); set(v) = setInt("think", v)
    var blankRatio: Float get() = float("blank", 0.3f); set(v) = setFloat("blank", v)
    var blankStreak: Int get() = int("blankStreak", 0); set(v) = setInt("blankStreak", v)
    var contextCount: Int get() = int("ctx", 2); set(v) = setInt("ctx", v)
    /** 0 = 무한 반복 */
    var repeat: Int get() = int("repeat", 1); set(v) = setInt("repeat", v)
    /** 구간 묶음: 가로 방향은 순(0~5), 세로 방향은 천간 열(0~9) */
    fun groups(vertical: Boolean): Set<Int> {
        val k = if (vertical) "cols" else "xuns"
        val d = if (vertical) "0123456789" else "012345"
        return (p.getString(k, d) ?: d).mapNotNull { it.digitToIntOrNull() }.toSet()
    }
    fun setGroups(vertical: Boolean, v: Set<Int>) =
        p.edit().putString(if (vertical) "cols" else "xuns", v.sorted().joinToString("")).apply()

    /** 2차 버전: 뒤집기 정의가 바뀌었으므로 뒤집기 기록을 비우고, 기본 화면을 '밤'으로 */
    fun migrateV2() {
        if (p.getBoolean("v2", false)) return
        val e = p.edit()
        p.all.keys.filter { it.startsWith("ok_2_") || it.startsWith("ng_2_") || it.startsWith("ok_3_") || it.startsWith("ng_3_") || it == "done2" || it == "done3" }
            .forEach { e.remove(it) }
        e.putInt("theme", 2).putBoolean("v2", true).apply()
    }

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

    var voiceEngine: String get() = p.getString("voiceEngine", "") ?: ""; set(v) = p.edit().putString("voiceEngine", v).apply()
    var voiceName: String get() = p.getString("voiceName", "") ?: ""; set(v) = p.edit().putString("voiceName", v).apply()
    var voiceStyle: Int get() = int("voiceStyle", 0); set(v) = setInt("voiceStyle", v)

}
