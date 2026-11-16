package kr.gapja60

import android.app.Activity
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.TextView
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** 디버그 전용: '미소년' 목소리 후보 비교용 합성 */
class ExperimentActivity : Activity() {
    private lateinit var tts: TextToSpeech
    private val out by lazy { File(filesDir, "exp").apply { deleteRecursively(); mkdirs() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply { text = "실험 중…" }
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
        val grid = listOf(
            "koc" to listOf(1.0f, 1.05f, 1.1f, 1.15f, 1.2f),
            "kod" to listOf(1.05f, 1.1f, 1.15f, 1.2f),
            "ism" to listOf(0.8f, 0.85f),
            "kob" to listOf(0.8f, 0.85f),
        )
        var n = 0
        for ((key, pitches) in grid) for (net in listOf(false, true)) {
            val v = voices.firstOrNull { it.name.contains("-x-$key-") && it.isNetworkConnectionRequired == net } ?: continue
            tts.voice = v
            for (p in pitches) {
                tts.setPitch(p); tts.setSpeechRate(0.9f)
                for (w in listOf("갑자", "무진", "경신", "신유", "계해")) {
                    val name = "${key}_${if (net) "net" else "loc"}_p${p}_r0.9_$w"
                    val l = CountDownLatch(1); synchronized(latches) { latches[name] = l }
                    tts.synthesizeToFile("$w.", Bundle(), File(out, "$name.wav"), name)
                    l.await(20, TimeUnit.SECONDS); n++
                }
            }
        }
        File(out, "done.txt").writeText("files=$n")
        runOnUiThread { tv.text = "끝 $n" }
    }

    override fun onDestroy() { tts.shutdown(); super.onDestroy() }
}
