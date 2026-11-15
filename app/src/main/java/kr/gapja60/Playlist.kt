package kr.gapja60

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.File
import kotlin.math.roundToInt

/** 재생 목록 만들기. 모든 항목은 기기 안의 wav 파일이다. */
class Playlist(private val vb: VoiceBank, private val prefs: Prefs) {
    companion object {
        const val K_KIND = "kind"    // g=간지 낭독, s=간격, b=빈칸(생각 시간), p=다음은?, t=효과음
        const val K_IDX = "idx"
        const val K_DIR = "dir"
        const val K_FLIP = "flip"
        const val K_POS = "pos"
        const val K_TOTAL = "total"
        const val K_LAST = "last"
        private const val K_CLIP = "clip"

        fun resolve(item: MediaItem): MediaItem {
            val uri = item.requestMetadata.mediaUri ?: return item
            val clip = item.requestMetadata.extras?.getLong(K_CLIP, 0L) ?: 0L
            val b = item.buildUpon().setUri(uri)
            if (clip > 0) b.setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(clip).build())
            return b.build()
        }
    }

    private var serial = 0

    private fun item(file: File, title: String, dir: Direction, extras: Bundle, clipMs: Long = 0): MediaItem {
        val uri = Uri.fromFile(file)
        extras.putInt(K_DIR, dir.ordinal)
        val req = MediaItem.RequestMetadata.Builder()
            .setMediaUri(uri)
            .setExtras(Bundle().apply { putLong(K_CLIP, clipMs) })
            .build()
        val meta = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist("육십갑자 · ${dir.label}")
            .setExtras(extras)
            .build()
        val b = MediaItem.Builder()
            .setMediaId("i${serial++}")
            .setUri(uri)
            .setRequestMetadata(req)
            .setMediaMetadata(meta)
        if (clipMs > 0) b.setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(clipMs).build())
        return b.build()
    }

    private fun ex(kind: String, idx: Int, flip: Boolean, pos: Int = 0, total: Int = 0, last: Boolean = false) = Bundle().apply {
        putString(K_KIND, kind); putInt(K_IDX, idx); putBoolean(K_FLIP, flip)
        putInt(K_POS, pos); putInt(K_TOTAL, total); putBoolean(K_LAST, last)
    }

    private fun voice(dir: Direction, idx: Int, pos: Int, total: Int) =
        item(vb.voice(idx, dir.flip), Gapja.hangul(idx, dir.flip), dir, ex("g", idx, dir.flip, pos, total))

    private fun gap(dir: Direction, idx: Int, pos: Int, total: Int, ms: Int, last: Boolean = false, kind: String = "s") =
        item(vb.silence, Gapja.hangul(idx, dir.flip), dir, ex(kind, idx, dir.flip, pos, total, last), ms.coerceIn(30, 5900).toLong())

    /** A. 연속 암송 */
    fun recite(dir: Direction, seq: List<Int>, passes: Int): List<MediaItem> {
        val full = seq.size == 60
        val out = ArrayList<MediaItem>()
        repeat(passes.coerceAtLeast(1)) {
            seq.forEachIndexed { k, idx ->
                out += voice(dir, idx, k + 1, seq.size)
                out += gap(dir, idx, k + 1, seq.size, prefs.gapMs, last = full && k == seq.lastIndex)
            }
        }
        return out
    }

    /** B. 빈칸 암송: 비운 자리는 '틱' → 생각 시간 → 정답 낭독 */
    fun blanks(dir: Direction, seq: List<Int>, ratio: Float): List<MediaItem> {
        val n = (seq.size * ratio).roundToInt().coerceIn(1, seq.size)
        val blank = seq.indices.shuffled().take(n).toSet()
        val out = ArrayList<MediaItem>()
        seq.forEachIndexed { k, idx ->
            if (k in blank) {
                out += item(vb.tick, "?", dir, ex("b", idx, dir.flip, k + 1, seq.size))
                out += gap(dir, idx, k + 1, seq.size, prefs.thinkMs, kind = "b")
            }
            out += voice(dir, idx, k + 1, seq.size)
            out += gap(dir, idx, k + 1, seq.size, prefs.gapMs, last = seq.size == 60 && k == seq.lastIndex)
        }
        return out
    }

    /** C. 이어 말하기: 앞의 간지 몇 개 → '다음은?' */
    fun question(dir: Direction, answer: Int, ctx: Int): List<MediaItem> {
        val out = ArrayList<MediaItem>()
        Sequencer.before(dir, answer, ctx).forEach { idx ->
            out += voice(dir, idx, 0, 0)
            out += gap(dir, idx, 0, 0, prefs.gapMs)
        }
        out += item(vb.next, "다음은?", dir, ex("p", answer, dir.flip))
        return out
    }

    fun reveal(dir: Direction, answer: Int, ok: Boolean): List<MediaItem> = listOf(
        item(if (ok) vb.ok else vb.ng, if (ok) "정답" else "오답", dir, ex("t", answer, dir.flip)),
        voice(dir, answer, 0, 0),
    )
}
