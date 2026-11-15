package kr.gapja60

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * 음성 파일 120개(정방향 60 + 뒤집기 60)와 안내음을 기기 안에 만들어 둔다.
 * - assets/voice/ 에 f_00.wav … r_59.wav, next.wav 가 들어 있으면(성우 녹음 등) 그것을 우선 사용한다.
 * - 없으면 기기의 한국어 음성 합성(구글 음성 우선)으로 한 번만 만든다.
 * 이후 재생은 모두 이 파일로 하므로 배속·백그라운드 재생이 안정적이다.
 */
class VoiceBank(private val ctx: Context) {
    val dir = File(ctx.filesDir, "voice").apply { mkdirs() }

    fun voice(idx: Int, flip: Boolean) = File(dir, (if (flip) "r_" else "f_") + "%02d".format(idx) + ".wav")
    val next get() = File(dir, "next.wav")
    val ok get() = File(dir, "ok.wav")
    val ng get() = File(dir, "ng.wav")
    val tick get() = File(dir, "tick.wav")
    val silence get() = File(dir, "silence.wav")

    fun isReady(): Boolean =
        (0..59).all { voice(it, false).length() > 500 && voice(it, true).length() > 500 } &&
            next.length() > 500 && silence.exists() && ok.exists() && ng.exists() && tick.exists()

    sealed class Result {
        data object Ok : Result()
        data object NoKorean : Result()
        data object NoEngine : Result()
        data class Failed(val what: String) : Result()
    }

    suspend fun build(onProgress: (Int, Int) -> Unit): Result {
        withContext(Dispatchers.IO) { writeTones() }
        if (copyFromAssets(onProgress)) return Result.Ok

        val tts = initTts("com.google.android.tts") ?: initTts(null) ?: return Result.NoEngine
        try {
            val lang = tts.setLanguage(Locale.KOREAN)
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) return Result.NoKorean
            runCatching {
                tts.voices
                    ?.filter { it.locale.language == "ko" && !it.isNetworkConnectionRequired && !it.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) }
                    ?.maxByOrNull { it.quality }
                    ?.let { tts.voice = it }
            }
            tts.setSpeechRate(1.0f)
            tts.setPitch(1.0f)
            Prefs(ctx).voiceInfo = "${tts.defaultEngine ?: "기본 엔진"} · ${runCatching { tts.voice?.name }.getOrNull() ?: "한국어"}"

            val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { id?.let { pending.remove(it)?.complete(true) } }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { id?.let { pending.remove(it)?.complete(false) } }
                override fun onError(id: String?, code: Int) { id?.let { pending.remove(it)?.complete(false) } }
            })

            val jobs = ArrayList<Pair<String, File>>()
            for (i in 0..59) jobs += Gapja.hangul(i, false) to voice(i, false)
            for (i in 0..59) jobs += Gapja.hangul(i, true) to voice(i, true)
            jobs += "다음은?" to next

            jobs.forEachIndexed { n, (text, file) ->
                onProgress(n, jobs.size)
                if (file.length() > 500) return@forEachIndexed   // 이미 만들어진 파일은 건너뜀(중단 후 이어서 만들기)
                val tmp = File(dir, file.name + ".tmp")
                val d = CompletableDeferred<Boolean>()
                pending[file.name] = d
                val r = tts.synthesizeToFile(text, Bundle(), tmp, file.name)
                val done = r == TextToSpeech.SUCCESS && (withTimeoutOrNull(20_000) { d.await() } ?: false)
                if (!done || tmp.length() < 500) { tmp.delete(); return Result.Failed(text) }
                tmp.renameTo(file)
            }
            onProgress(jobs.size, jobs.size)
            return Result.Ok
        } finally {
            tts.shutdown()
        }
    }

    fun clear() { dir.listFiles()?.forEach { it.delete() } }

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

    private suspend fun copyFromAssets(onProgress: (Int, Int) -> Unit): Boolean = withContext(Dispatchers.IO) {
        val names = runCatching { ctx.assets.list("voice")?.toSet() }.getOrNull() ?: return@withContext false
        val need = (0..59).flatMap { listOf(voice(it, false).name, voice(it, true).name) } + next.name
        if (!names.containsAll(need)) return@withContext false
        need.forEachIndexed { n, name ->
            onProgress(n, need.size)
            ctx.assets.open("voice/$name").use { inp -> File(dir, name).outputStream().use { inp.copyTo(it) } }
        }
        true
    }

    // ---- 안내음(직접 합성한 짧은 소리) ----
    private fun writeTones() {
        val rate = 22050
        if (!silence.exists()) writeWav(silence, ShortArray(rate * 6), rate)
        if (!ok.exists()) writeWav(ok, tones(rate, listOf(784.0 to 90, 1046.5 to 140)), rate)
        if (!ng.exists()) writeWav(ng, tones(rate, listOf(330.0 to 120, 262.0 to 180)), rate)
        if (!tick.exists()) writeWav(tick, tones(rate, listOf(1318.5 to 45)), rate)
    }

    private fun tones(rate: Int, notes: List<Pair<Double, Int>>): ShortArray {
        val out = ArrayList<Short>()
        for ((f, ms) in notes) {
            val n = rate * ms / 1000
            for (i in 0 until n) {
                val env = min(1.0, min(i, n - i) / (rate * 0.008))   // 짧은 페이드로 '틱' 잡음 방지
                out += (sin(2 * PI * f * i / rate) * env * 9000).toInt().toShort()
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
