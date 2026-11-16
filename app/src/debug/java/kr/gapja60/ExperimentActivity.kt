package kr.gapja60

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import java.io.File
import kotlin.concurrent.thread

/** 디버그 전용: 미소년 목소리의 '힘' 비교. 앱과 같은 방식으로 실제 음성 파일을 만들어 files/exp 에 모은다 */
class ExperimentActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = TextView(this).apply { text = "실험 중…" }
        setContentView(tv)
        thread {
            val out = File(filesDir, "exp").apply { deleteRecursively(); mkdirs() }
            val vb = VoiceBank(this)
            val packs = listOf(
                VoicePack("x_old", "1.0.33 미소년", "", listOf("kod", "koc"), 1.14f, 0.86f, relaxed = true),
                VoicePacks.byId("m_boy"),
                VoicePacks.byId("b_boy"),
                VoicePacks.byId("f_calm"),
            )
            for (p in packs) {
                vb.clear(p)
                val r = kotlinx.coroutines.runBlocking { vb.build(p) { _, _ -> } }
                for (w in listOf(0, 4, 56, 57, 59, 13, 26)) vb.voice(p, w).takeIf { it.exists() }
                    ?.copyTo(File(out, "${p.id}_${Gapja.hangul(w)}.wav"), true)
                File(out, "log.txt").appendText("${p.id} $r ${Prefs(this).voiceInfo(p.id)}\n")
                runOnUiThread { tv.text = "${p.id} 끝" }
            }
            File(out, "done.txt").writeText("ok")
        }
    }
}
