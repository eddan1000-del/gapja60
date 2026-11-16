package kr.gapja60

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * 음성 버전. voiceKeys는 구글 한국어 음성 이름(ko-kr-x-<key>-local/network)의 후보이며 앞의 것이 우선이다.
 * 구글 음성의 한국어 목소리: ism·kob = 여성, koc·kod = 남성.
 * pitch는 1.0보다 낮춰 높은 톤을 피한다.
 */
data class VoicePack(
    val id: String,
    val label: String,
    val desc: String,
    val voiceKeys: List<String>,
    val pitch: Float,
    val rate: Float,
)

object VoicePacks {
    val ALL = listOf(
        // 음높이를 지나치게 낮추면 오히려 기계음처럼 들리므로 0.9~0.97 범위에서 살짝만 낮춘다
        VoicePack("f_calm", "여성 · 차분", "부드럽고 천천히", listOf("kob", "ism"), 0.95f, 0.82f),
        VoicePack("f_clear", "여성 · 또렷", "또박또박, 차분하게", listOf("ism", "kob"), 0.97f, 0.9f),
        VoicePack("m_low", "남성 · 저음", "낮고 천천히", listOf("kod", "koc"), 0.9f, 0.82f),
        VoicePack("m_clear", "남성 · 또렷", "또박또박, 차분하게", listOf("koc", "kod"), 0.95f, 0.9f),
        VoicePack("default", "기기 기본", "이 폰의 기본 한국어 음성", emptyList(), 0.96f, 0.86f),
    )
    fun byId(id: String) = ALL.firstOrNull { it.id == id } ?: ALL.last()
}

/**
 * 음성 파일(간지 60개 + '다음은?')을 버전별로 기기 안에 만들어 둔다.
 * - assets/voice/<버전id>/ 에 f_00.wav … f_59.wav, next.wav 가 있으면(성우 녹음 등) 그것을 우선 사용
 * - 없으면 기기의 한국어 음성 합성으로 한 번만 만들고, 음량을 고르게 맞추고 앞뒤 무음 길이를 통일한다
 */
class VoiceBank(private val ctx: Context) {
    companion object {
        const val TAG = "Gapja60"
        const val VOICE_VERSION = 3
    }

    val root = File(ctx.filesDir, "voice").apply { mkdirs() }
    private fun packDir(p: VoicePack) = File(root, p.id).apply { mkdirs() }

    fun voice(p: VoicePack, idx: Int) = File(packDir(p), "f_%02d.wav".format(idx))
    fun next(p: VoicePack) = File(packDir(p), "next.wav")
    val ok get() = File(root, "ok.wav")
    val ng get() = File(root, "ng.wav")
    val tick get() = File(root, "tick.wav")
    val silence get() = File(root, "silence.wav")
    val noise get() = File(root, "bg_brown.wav")


    /** 음성 처리 방식이 바뀌면(버전 올림) 만들어 둔 음성을 모두 지워 다시 만들게 한다 */
    fun upgradeIfNeeded(prefs: Prefs) {
        if (prefs.voiceVersion >= VOICE_VERSION) return
        VoicePacks.ALL.forEach { clear(it) }
        prefs.voiceVersion = VOICE_VERSION
    }

    fun isReady(p: VoicePack): Boolean =
        (0..59).all { voice(p, it).length() > 500 } && next(p).length() > 500 &&
            silence.exists() && ok.exists() && ng.exists() && tick.exists() && noise.exists()

    /** 1차 버전의 옛 파일(뒤집기 음성 등) 정리 */
    fun cleanupLegacy() {
        root.listFiles()?.filter { it.isFile && (it.name.startsWith("f_") || it.name.startsWith("r_") || it.name == "next.wav") }?.forEach { it.delete() }
    }

    fun clear(p: VoicePack) { packDir(p).listFiles()?.forEach { it.delete() } }

    sealed class Result {
        data object Ok : Result()
        data object NoKorean : Result()
        data object NoEngine : Result()
        data object VoiceNotInstalled : Result()
        data class Failed(val what: String, val code: Int) : Result()
    }

    var status: (String) -> Unit = {}

    // ───────── 사용 가능한 음성 버전 ─────────
    /** 이 기기에서 만들 수 있는 음성 버전 목록(기기 기본은 항상 포함) */
    suspend fun availablePacks(): List<VoicePack> {
        val tts = initTts("com.google.android.tts") ?: initTts(null) ?: return listOf(VoicePacks.byId("default"))
        return try {
            val names = runCatching { tts.voices?.filter { it.locale.language == "ko" }?.map { it.name } }.getOrNull() ?: emptyList()
            VoicePacks.ALL.filter { p -> p.voiceKeys.isEmpty() || p.voiceKeys.any { k -> names.any { it.contains("-x-$k-") } } }
        } finally { tts.shutdown() }
    }

    private fun chooseVoice(tts: TextToSpeech, p: VoicePack): Voice? {
        val ko = runCatching { tts.voices?.filter { it.locale.language == "ko" } }.getOrNull() ?: return null
        for (k in p.voiceKeys) {
            val c = ko.filter { it.name.contains("-x-$k-") }
            (c.firstOrNull { !it.isNetworkConnectionRequired } ?: c.firstOrNull())?.let { return it }
        }
        return null
    }

    // ───────── 미리 듣기 ─────────
    private var previewTts: TextToSpeech? = null

    suspend fun preview(p: VoicePack) {
        val tts = previewTts ?: (initTts("com.google.android.tts") ?: initTts(null))?.also { previewTts = it } ?: return
        tts.setLanguage(Locale.KOREAN)
        chooseVoice(tts, p)?.let { runCatching { tts.voice = it } }
        tts.setPitch(p.pitch); tts.setSpeechRate(p.rate)
        // 미리 듣기는 실제 파일(음색 처리 포함)로 들려주는 것이 정확하므로, 파일이 있으면 그걸 우선 사용
        tts.speak("갑자. 을축. 병인. 정묘.", TextToSpeech.QUEUE_FLUSH, null, "preview")
    }

    fun stopPreview() { previewTts?.stop() }
    fun releasePreview() { previewTts?.shutdown(); previewTts = null }

    // ───────── 만들기 ─────────
    suspend fun build(p: VoicePack, onProgress: (Int, Int) -> Unit): Result {
        withContext(Dispatchers.IO) { writeTones() }
        if (copyFromAssets(p, onProgress)) return Result.Ok

        val tts = initTts("com.google.android.tts") ?: initTts(null) ?: return Result.NoEngine
        try {
            val lang = tts.setLanguage(Locale.KOREAN)
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) return Result.NoKorean
            val v = chooseVoice(tts, p)
            if (v != null) runCatching { tts.voice = v }
            tts.setSpeechRate(p.rate)
            tts.setPitch(p.pitch)
            val vname = runCatching { tts.voice?.name }.getOrNull() ?: "한국어"
            Log.i(TAG, "pack=${p.id} engine=${tts.defaultEngine} voice=$vname")
            Prefs(ctx).setVoiceInfo(p.id, "${p.label} · $vname")

            val pending = ConcurrentHashMap<String, CompletableDeferred<Int>>()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { id?.let { pending.remove(it)?.complete(0) } }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { id?.let { pending.remove(it)?.complete(-1) } }
                override fun onError(id: String?, code: Int) { id?.let { pending.remove(it)?.complete(code) } }
            })

            val jobs = ArrayList<Pair<String, File>>()
            // 마침표를 붙여 끝이 내려가는 차분한 평서 억양으로 읽게 한다
            for (i in 0..59) jobs += (Gapja.hangul(i) + ".") to voice(p, i)
            jobs += "다음은?" to next(p)

            suspend fun synth(text: String, file: File): Int {
                val tmp = File(file.parentFile, file.name + ".tmp")
                val d = CompletableDeferred<Int>()
                pending[file.name] = d
                val r = tts.synthesizeToFile(text, Bundle(), tmp, file.name)
                val code = if (r != TextToSpeech.SUCCESS) -100 + r else (withTimeoutOrNull(20_000) { d.await() } ?: -200)
                if (code != 0 || tmp.length() < 500) { tmp.delete(); return if (code == 0) -300 else code }
                val style = SoftVoice.STYLES[Prefs(ctx).softness.coerceIn(0, 2)]
                withContext(Dispatchers.Default) {
                    if (!polish(tmp, file, style)) tmp.renameTo(file) else tmp.delete()
                }
                return 0
            }

            jobs.forEachIndexed { n, (text, file) ->
                onProgress(n, jobs.size)
                if (file.length() > 500) return@forEachIndexed
                var code = synth(text, file)
                var tries = 0
                while (code != 0 && n == 0 && tries < 12) {
                    Log.w(TAG, "synth fail '$text' code=$code, retry $tries")
                    status("한국어 음성을 준비하는 중… (${tries + 1}/12)")
                    delay(5_000); tries++
                    if (tries == 6 && v != null) {
                        // 고른 목소리가 이 폰에 아직 없으면 기본 한국어 목소리로 대체
                        tts.setLanguage(Locale.KOREAN); tts.setPitch(p.pitch); tts.setSpeechRate(p.rate)
                        Prefs(ctx).setVoiceInfo(p.id, "${p.label} · 기본 한국어 목소리로 대체")
                    }
                    code = synth(text, file)
                }
                if (code != 0 && n > 0) code = synth(text, file)
                if (code != 0) {
                    Log.e(TAG, "synth fail '$text' code=$code")
                    return if (code == TextToSpeech.ERROR_NOT_INSTALLED_YET || code == TextToSpeech.ERROR_NETWORK ||
                        code == TextToSpeech.ERROR_NETWORK_TIMEOUT || n == 0) Result.VoiceNotInstalled
                    else Result.Failed(text, code)
                }
                status("")
            }
            onProgress(jobs.size, jobs.size)
            return Result.Ok
        } finally {
            tts.shutdown()
        }
    }

    private suspend fun initTts(engine: String?): TextToSpeech? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { c ->
                val holder = arrayOfNulls<TextToSpeech>(1)
                holder[0] = TextToSpeech(ctx, { st ->
                    val t = holder[0]
                    if (c.isActive) {
                        if (st == TextToSpeech.SUCCESS && t != null) c.resume(t) else { t?.shutdown(); c.resume(null) }
                    }
                }, engine)
            }
        }
    }

    private suspend fun copyFromAssets(p: VoicePack, onProgress: (Int, Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val names = runCatching { ctx.assets.list("voice/${p.id}")?.toSet() }.getOrNull() ?: return@withContext false
        val need = (0..59).map { voice(p, it).name } + next(p).name
        if (!names.containsAll(need)) return@withContext false
        need.forEachIndexed { n, name ->
            onProgress(n, need.size)
            ctx.assets.open("voice/${p.id}/$name").use { inp -> File(packDir(p), name).outputStream().use { inp.copyTo(it) } }
        }
        true
    }

    // ───────── 다듬기: 무음 정리 + 음색 처리 ─────────
    /**
     * 합성된 wav(16비트 PCM)를 읽어 앞뒤 무음을 잘라내고(앞 60ms로 통일),
     * SoftVoice로 고음 완화·압축·잔향·페이드·음량 맞춤을 한다. 실패하면 false(원본 그대로 사용).
     */
    private fun polish(src: File, dst: File, style: SoftVoice.Style): Boolean = runCatching {
        val bytes = src.readBytes()
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 44 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE") return false
        var pos = 12
        var rate = 0; var channels = 1; var bits = 16; var dataOff = -1; var dataLen = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4); val len = bb.getInt(pos + 4)
            if (id == "fmt ") { channels = bb.getShort(pos + 10).toInt(); rate = bb.getInt(pos + 12); bits = bb.getShort(pos + 22).toInt() }
            if (id == "data") { dataOff = pos + 8; dataLen = min(len, bytes.size - dataOff); break }
            pos += 8 + len + (len and 1)
        }
        if (dataOff < 0 || bits != 16 || rate <= 0 || channels < 1) return false
        val frames = dataLen / (2 * channels)
        val pcm = ShortArray(frames) { bb.getShort(dataOff + it * 2 * channels) }
        val peak = pcm.maxOfOrNull { abs(it.toInt()) } ?: 0
        if (peak < 200) return false
        val th = peak * 0.03
        var a = pcm.indexOfFirst { abs(it.toInt()) > th }
        var b = pcm.indexOfLast { abs(it.toInt()) > th }
        if (a < 0 || b <= a) return false
        a = (a - rate * 30 / 1000).coerceAtLeast(0); b = (b + rate * 40 / 1000).coerceAtMost(frames - 1)
        val soft = SoftVoice.process(pcm.copyOfRange(a, b + 1), rate, style)
        val lead = rate * 60 / 1000
        val out = ShortArray(lead + soft.size)
        System.arraycopy(soft, 0, out, lead, soft.size)
        writeWav(dst, out, rate)
        true
    }.getOrElse { Log.w(TAG, "polish failed", it); false }

    // ───────── 안내음 ─────────
    private fun writeTones() {
        val rate = 22050
        if (!silence.exists()) writeWav(silence, ShortArray(rate * 6), rate)
        if (!ok.exists()) writeWav(ok, tones(rate, listOf(523.3 to 110, 659.3 to 160)), rate)
        if (!ng.exists()) writeWav(ng, tones(rate, listOf(293.7 to 140, 246.9 to 200)), rate)
        if (!tick.exists()) writeWav(tick, tones(rate, listOf(880.0 to 40)), rate)
        if (!noise.exists()) writeWav(noise, brownNoise(rate, 20), rate)
    }

    /** 낮게 웅웅거리는 브라운 노이즈(빗소리·바람 소리에 가까움). 끝과 처음을 겹쳐 이음매 없이 반복되게 한다. */
    private fun brownNoise(rate: Int, seconds: Int): ShortArray {
        val n = rate * seconds; val xf = rate * 2
        val rnd = java.util.Random(60)
        val raw = DoubleArray(n + xf)
        var v = 0.0; var lp = 0.0
        for (i in raw.indices) {
            v = (v + (rnd.nextDouble() * 2 - 1) * 0.02) * 0.998
            lp += (v - lp) * 0.25
            raw[i] = lp
        }
        // 직류 성분 제거
        val mean = raw.average(); for (i in raw.indices) raw[i] -= mean
        // 끝 2초를 처음 2초에 겹쳐 이음매 제거
        for (i in 0 until xf) { val t = i.toDouble() / xf; raw[i] = raw[i] * t + raw[n + i] * (1 - t) }
        val pk = (0 until n).maxOf { abs(raw[it]) }.coerceAtLeast(1e-9)
        return ShortArray(n) { (raw[it] / pk * 9000).toInt().toShort() }
    }

    private fun tones(rate: Int, notes: List<Pair<Double, Int>>): ShortArray {
        val out = ArrayList<Short>()
        for ((f, ms) in notes) {
            val n = rate * ms / 1000
            for (i in 0 until n) {
                val env = min(1.0, min(i, n - i) / (rate * 0.012))
                out += (sin(2 * PI * f * i / rate) * env * 6000).toInt().toShort()
            }
        }
        repeat(rate / 20) { out += 0 }
        return out.toShortArray()
    }

    private fun writeWav(file: File, pcm: ShortArray, rate: Int) {
        RandomAccessFile(file, "rw").use { f ->
            f.setLength(0)
            val dataLen = pcm.size * 2
            fun i32(v: Int) { f.write(v and 0xff); f.write(v shr 8 and 0xff); f.write(v shr 16 and 0xff); f.write(v shr 24 and 0xff) }
            fun i16(v: Int) { f.write(v and 0xff); f.write(v shr 8 and 0xff) }
            f.writeBytes("RIFF"); i32(36 + dataLen); f.writeBytes("WAVE")
            f.writeBytes("fmt "); i32(16); i16(1); i16(1); i32(rate); i32(rate * 2); i16(2); i16(16)
            f.writeBytes("data"); i32(dataLen)
            val buf = ByteArray(dataLen)
            pcm.forEachIndexed { k, s -> buf[2 * k] = (s.toInt() and 0xff).toByte(); buf[2 * k + 1] = (s.toInt() shr 8 and 0xff).toByte() }
            f.write(buf)
        }
    }
}
