package kr.gapja60

import android.app.Activity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.TextView
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * 디버그 전용 실험: 같은 간지를 조건별로 합성해 files/exp/ 에 저장한다.
 * 조건 = 목소리(kob 여성, kod 남성) × 원천(local/network) × 음높이 × 말 속도.
 * 파일 이름: {voice}_{src}_p{pitch}_r{rate}_{word}.wav  (합성 원본, 음색 처리 없음)
 */
class ExperimentActivity : Activity() {
    private lateinit var tts: TextToSpeech
    private val out by lazy { File(filesDir, "exp").apply { deleteRecursively(); mkdirs() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply { text = "실험 중…"; textSize = 20f }
        setContentView(tv)
        tts = TextToSpeech(this, { st -> if (st == TextToSpeech.SUCCESS) thread { run(tv) } }, "com.google.android.tts")
    }

    private fun run(tv: TextView) {
        tts.language = Locale.KOREAN
        val latches = HashMap<String, CountDownLatch>()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { synchronized(latches) { latches[id] }?.countDown() }
            @Deprecated("Deprecated in Java") override fun onError(id: String?) { synchronized(latches) { latches[id] }?.countDown() }
        })
        val voices = tts.voices?.filter { it.locale.language == "ko" } ?: emptyList()
        val words = listOf("갑자", "무진", "경신", "신유", "계해")
        val pitches = listOf(1.0f, 0.95f, 0.9f, 0.84f)
        val rates = listOf(0.88f, 2.0f)
        var n = 0
        for (key in listOf("kob", "kod")) for (net in listOf(false, true)) {
            val v = voices.firstOrNull { it.name.contains("-x-$key-") && it.isNetworkConnectionRequired == net } ?: continue
            tts.voice = v
            for (p in pitches) for (r in rates) {
                if (r == 2.0f && p != 0.95f && p != 1.0f) continue
                tts.setPitch(p); tts.setSpeechRate(r)
                for (w in words) {
                    val name = "${key}_${if (net) "net" else "loc"}_p${p}_r${r}_$w"
                    val l = CountDownLatch(1); synchronized(latches) { latches[name] = l }
                    tts.synthesizeToFile("$w.", Bundle(), File(out, "$name.wav"), name)
                    l.await(20, TimeUnit.SECONDS)
                    n++
                    runOnUiThread { tv.text = "실험 중… $n" }
                }
            }
        }
        // 최종 앱 처리 결과 비교: 새 설정 vs 이전 설정, 보통·빠른 음성
        val vb = VoiceBank(this)
        val prefs = Prefs(this)
        for ((prof, tag) in listOf(1 to "old", 0 to "new")) {
            prefs.voiceProfile = prof
            for (id in listOf("f_calm", "m_low")) {
                val p = VoicePacks.byId(id)
                vb.clear(p)
                for (v in if (prof == 0) listOf(VoiceBank.Variant.NORMAL, VoiceBank.Variant.FAST) else listOf(VoiceBank.Variant.NORMAL)) {
                    val r = kotlinx.coroutines.runBlocking { vb.build(p, v) { _, _ -> } }
                    for (w in listOf(0, 4, 56, 57, 59)) {   // 갑자 무진 경신 신유 계해
                        vb.voice(p, w, v).takeIf { it.exists() }?.copyTo(File(out, "final_${tag}_${id}_${v}_${Gapja.hangul(w)}.wav"), true)
                    }
                    Log.i("Gapja60", "final $tag $id $v $r")
                    runOnUiThread { tv.text = "최종 $tag $id $v" }
                }
            }
        }
        prefs.voiceProfile = 0
        File(out, "done.txt").writeText("files=$n voices=${voices.map { it.name }}")
        Log.i("Gapja60", "experiment done $n")
        runOnUiThread { tv.text = "끝 $n" }
    }

    override fun onDestroy() { tts.shutdown(); super.onDestroy() }
}
