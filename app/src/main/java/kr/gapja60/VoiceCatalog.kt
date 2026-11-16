package kr.gapja60

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** 말투(톤). 낭독 파일을 만들 때의 속도·음높이. 재생 배속은 이와 별도로 0.5~5배. */
data class VoiceStyle(val id: Int, val name: String, val desc: String, val rate: Float, val pitch: Float)

val STYLES = listOf(
    VoiceStyle(0, "편안하게", "약간 느리고 부드럽게 · 기본", 0.90f, 0.95f),
    VoiceStyle(1, "또렷하게", "발음을 분명하게", 0.82f, 1.00f),
    VoiceStyle(2, "낮고 차분하게", "낮은 음으로 차분하게", 0.85f, 0.86f),
    VoiceStyle(3, "또박또박", "천천히 한 음절씩", 0.70f, 0.96f),
    VoiceStyle(4, "자연스럽게", "엔진 기본 속도와 높이", 1.00f, 1.00f),
)

fun styleOf(id: Int) = STYLES.firstOrNull { it.id == id } ?: STYLES[0]

/** 기기에서 고를 수 있는 한국어 목소리 하나 */
data class VoiceOption(
    val engine: String,
    val engineLabel: String,
    val voiceName: String?,   // null이면 엔진 기본 목소리
    val label: String,
    val male: Boolean?,       // 알 수 없으면 null
)

object VoiceNames {
    /** 구글 한국어 음성 이름 → 표시 이름. (koc·kod는 남성, ism·kob는 여성) */
    fun label(name: String?): Pair<String, Boolean?> {
        val n = name?.lowercase() ?: return "기본 목소리" to null
        return when {
            "koc" in n -> "남성 1" to true
            "kod" in n -> "남성 2" to true
            "ism" in n -> "여성 1" to false
            "kob" in n -> "여성 2" to false
            Regex("smt.*m\\d|male|_m\\d").containsMatchIn(n) -> "남성" to true
            Regex("smt.*f\\d|female|_f\\d").containsMatchIn(n) -> "여성" to false
            else -> name to null
        }
    }

    fun engineLabel(pkg: String) = when {
        "google" in pkg -> "구글"
        "samsung" in pkg -> "삼성"
        else -> pkg.substringAfterLast('.')
    }

    /** 같은 목소리의 -local/-network 중 기기 안에서 도는 것을 우선, 옛 형식 이름은 제외 */
    fun pick(voices: Collection<Voice>): List<Voice> {
        val ko = voices.filter { it.locale.language == "ko" && !it.name.endsWith("-language") }
        val byBase = ko.groupBy { it.name.removeSuffix("-local").removeSuffix("-network") }
        return byBase.values.map { g -> g.firstOrNull { !it.isNetworkConnectionRequired } ?: g.first() }
    }

    /** 처음 실행 때 쓸 기본 목소리: 남성 우선 */
    fun default(voices: Collection<Voice>): Voice? {
        val list = pick(voices)
        return list.firstOrNull { "koc" in it.name && !it.isNetworkConnectionRequired }
            ?: list.firstOrNull { label(it.name).second == true && !it.isNetworkConnectionRequired }
            ?: list.firstOrNull { label(it.name).second == true }
    }
}

/** 목소리 고르기 화면에서 쓰는 목록과 미리듣기 */
class VoiceCatalog(private val ctx: Context) {
    private val engines = LinkedHashMap<String, TextToSpeech>()
    private val voiceMap = HashMap<String, Voice>()

    suspend fun load(): List<VoiceOption> {
        val first = open(null) ?: return emptyList()
        val pkgs = first.engines.map { it.name }.ifEmpty { listOfNotNull(first.defaultEngine) }
        first.shutdown()
        val out = ArrayList<VoiceOption>()
        for (pkg in pkgs) {
            val tts = open(pkg) ?: continue
            val lang = tts.setLanguage(Locale.KOREAN)
            if (lang == TextToSpeech.LANG_NOT_SUPPORTED || lang == TextToSpeech.LANG_MISSING_DATA) { tts.shutdown(); continue }
            engines[pkg] = tts
            val voices = runCatching { VoiceNames.pick(tts.voices ?: emptySet()) }.getOrDefault(emptyList())
            if (voices.isEmpty()) {
                out += VoiceOption(pkg, VoiceNames.engineLabel(pkg), null, "기본 목소리", null)
            } else {
                voices.forEach { v ->
                    voiceMap["$pkg/${v.name}"] = v
                    val (label, male) = VoiceNames.label(v.name)
                    out += VoiceOption(pkg, VoiceNames.engineLabel(pkg), v.name, label + if (v.isNetworkConnectionRequired) " (인터넷 필요)" else "", male)
                }
            }
        }
        // 남성 먼저, 그다음 여성, 그 밖
        return out.sortedWith(compareBy({ if (it.male == true) 0 else if (it.male == false) 1 else 2 }, { it.engineLabel }, { it.label }))
    }

    fun preview(o: VoiceOption, style: VoiceStyle, text: String) {
        val tts = engines[o.engine] ?: return
        runCatching {
            tts.setLanguage(Locale.KOREAN)
            o.voiceName?.let { n -> voiceMap["${o.engine}/$n"]?.let { tts.voice = it } }
            tts.setSpeechRate(style.rate)
            tts.setPitch(style.pitch)
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "preview")
        }
    }

    fun stop() { engines.values.forEach { runCatching { it.stop() } } }

    fun close() { engines.values.forEach { runCatching { it.shutdown() } }; engines.clear() }

    private suspend fun open(engine: String?): TextToSpeech? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { c ->
                val h = arrayOfNulls<TextToSpeech>(1)
                h[0] = TextToSpeech(ctx, { st ->
                    val t = h[0]
                    if (c.isActive) { if (st == TextToSpeech.SUCCESS && t != null) c.resume(t) else { t?.shutdown(); c.resume(null) } }
                }, engine)
            }
        }
    }
}
