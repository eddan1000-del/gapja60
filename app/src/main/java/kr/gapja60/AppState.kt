package kr.gapja60

import android.content.Context
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import kotlin.math.roundToInt
import kotlin.random.Random

enum class Mode(val label: String) { RECITE("암송"), BLANK("빈칸"), NEXT("잇기"), STATS("기록") }

/** 화면 상태와 재생 제어. */
class AppState(ctx: Context) {
    val prefs = Prefs(ctx).apply { migrateV2() }
    val voices = VoiceBank(ctx).apply { cleanupLegacy(); upgradeIfNeeded(prefs) }
    var pack by mutableStateOf(VoicePacks.byId(prefs.voicePack))
        private set
    private val playlist = Playlist(voices, prefs) { pack }

    var controller: MediaController? by mutableStateOf(null)
        private set

    var voiceReady by mutableStateOf(voices.isReady(pack))
    var direction by mutableStateOf(Direction.entries[prefs.direction.coerceIn(0, 3)])
        private set
    var mode by mutableStateOf(Mode.RECITE)
    var script by mutableStateOf(Script.entries[prefs.script.coerceIn(0, 2)])
    var theme by mutableIntStateOf(prefs.theme)
    var speed by mutableFloatStateOf(prefs.speed)
        private set
    var showSettings by mutableStateOf(false)

    // 재생 상태
    var playing by mutableStateOf(false)
    var curIdx by mutableIntStateOf(-1)
    var curKind by mutableStateOf("")
    var curPos by mutableIntStateOf(0)
    var curTotal by mutableIntStateOf(0)
    var ended by mutableStateOf(false)

    // 수면 모드
    var sleeping by mutableStateOf(false)
    private var speedBeforeSleep = 1f

    // 범위
    var groups by mutableStateOf(prefs.groups(direction.vertical))
    var start by mutableStateOf<Int?>(null)
    var abA by mutableStateOf<Int?>(null)
    var abB by mutableStateOf<Int?>(null)
    var abPicking by mutableStateOf(false)
    var repeat by mutableIntStateOf(prefs.repeat)
    var sleepMin by mutableIntStateOf(0)

    // 빈칸
    var blankRatio by mutableFloatStateOf(prefs.blankRatio)
    var askBlankResult by mutableStateOf(false)

    // 이어 말하기
    var qAnswer by mutableIntStateOf(-1)
    var qChoices by mutableStateOf<List<Int>>(emptyList())
    var qPicked by mutableIntStateOf(-1)
    var qScore by mutableIntStateOf(0)
    var qCount by mutableIntStateOf(0)
    var ctxCount by mutableIntStateOf(prefs.contextCount)

    var statsVersion by mutableIntStateOf(0)   // 기록 화면 새로고침용

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) {
                ended = true
                if (mode == Mode.BLANK) askBlankResult = true
            }
        }
    }

    fun attach(c: MediaController) {
        controller = c
        c.addListener(listener)
        c.setPlaybackSpeed(speed)
        sendBg()
        sync(c)
    }

    fun detach() {
        controller?.removeListener(listener)
        controller = null
    }

    private fun sync(p: Player) {
        playing = p.isPlaying || (p.playWhenReady && p.playbackState == Player.STATE_BUFFERING)
        val ex = p.currentMediaItem?.mediaMetadata?.extras
        if (ex != null) {
            curIdx = ex.getInt(Playlist.K_IDX, -1)
            curKind = ex.getString(Playlist.K_KIND) ?: ""
            curPos = ex.getInt(Playlist.K_POS, 0)
            curTotal = ex.getInt(Playlist.K_TOTAL, 0)
        }
    }

    // ---- 방향·범위 ----
    fun selectDirection(d: Direction) {
        if (d == direction) return
        val orientationChanged = d.vertical != direction.vertical
        direction = d
        prefs.direction = d.ordinal
        if (orientationChanged) { groups = prefs.groups(d.vertical); start = null; abA = null; abB = null }
        stop()
    }

    fun sequence(): List<Int> = Sequencer.sequence(direction, Range(groups, start, abA, abB))

    fun toggleGroup(x: Int) {
        groups = if (x in groups) groups - x else groups + x
        if (groups.isEmpty()) groups = setOf(x)
        prefs.setGroups(direction.vertical, groups)
        abA = null; abB = null
    }

    /** 표의 칸을 눌렀을 때: A–B 지정 중이면 구간 끝점, 아니면 그 간지부터 재생 */
    fun tapCell(idx: Int) {
        if (abPicking) {
            if (abA == null || abB != null) { abA = idx; abB = null } else { abB = idx; abPicking = false }
            return
        }
        when (mode) {
            Mode.RECITE, Mode.BLANK -> { start = idx; abA = null; abB = null; play(restart = true) }
            else -> {}
        }
    }

    fun clearRange() {
        start = null; abA = null; abB = null; abPicking = false
        groups = (0 until direction.groupCount).toSet(); prefs.setGroups(direction.vertical, groups)
    }

    // ---- 재생 ----
    fun changeSpeed(v: Float) {
        val s = ((v * 4).roundToInt() / 4f).coerceIn(0.5f, 5f)
        speed = s
        prefs.speed = s
        controller?.setPlaybackSpeed(s)
    }

    private fun load(items: List<MediaItem>, loop: Boolean) {
        val c = controller ?: return
        ended = false
        c.setMediaItems(items)
        c.repeatMode = if (loop) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        c.setPlaybackSpeed(speed)
        c.prepare()
        c.play()
    }

    fun play(restart: Boolean = false) {
        val c = controller ?: return
        if (!restart && c.mediaItemCount > 0 && !ended && (mode == Mode.RECITE || mode == Mode.BLANK)) { c.play(); return }
        val seq = sequence()
        if (seq.isEmpty()) return
        when (mode) {
            Mode.RECITE -> load(playlist.recite(direction, seq, if (repeat == 0) 1 else repeat), loop = repeat == 0)
            Mode.BLANK -> load(playlist.blanks(direction, seq, blankRatio), loop = false)
            else -> {}
        }
    }

    fun toggle() {
        val c = controller ?: return
        if (playing) c.pause() else if (mode == Mode.NEXT) replayQuestion() else play()
    }

    fun skip(d: Int) {
        val c = controller ?: return
        // 간지 낭독 항목 단위로 이동
        var i = c.currentMediaItemIndex
        val n = c.mediaItemCount
        if (n == 0) return
        repeat(n) {
            i += d
            if (i < 0 || i >= n) return
            val k = c.getMediaItemAt(i).mediaMetadata.extras?.getString(Playlist.K_KIND)
            if (k == "g" || k == "b" && d > 0) { c.seekTo(i, 0); c.play(); return }
        }
    }

    fun stop() {
        controller?.let { it.stop(); it.clearMediaItems() }
        curIdx = -1; curKind = ""; curPos = 0; curTotal = 0
        qAnswer = -1; qChoices = emptyList(); qPicked = -1
    }

    fun changeRepeat(v: Int) { repeat = v; prefs.repeat = v }

    fun setSleep(min: Int) {
        sleepMin = min
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_SLEEP, Bundle.EMPTY),
            Bundle().apply { putInt("min", min); putInt("fade", prefs.sleepFadeMin) },
        )
    }

    // ---- 수면 모드: 느리게, 간격 길게, 효과음 없이, 무한 반복, 타이머 끝에 서서히 작아짐 ----
    fun startSleep() {
        val seq = sequence().ifEmpty { direction.order() }
        if (mode != Mode.RECITE) mode = Mode.RECITE
        speedBeforeSleep = speed
        speed = prefs.sleepSpeed
        sleeping = true
        load(playlist.recite(direction, seq, 1, gapMs = prefs.sleepGapMs), loop = true)
        setSleep(prefs.sleepTimer)
    }

    fun exitSleep() {
        sleeping = false
        speed = speedBeforeSleep
        controller?.setPlaybackSpeed(speed)
    }

    // ---- 음색·배경 소리 ----
    var softness by mutableIntStateOf(prefs.softness)
    var bgLevel by mutableIntStateOf(prefs.bgLevel)

    /** 음색을 바꾸면 지금 목소리를 새 음색으로 다시 만든다 */
    fun changeSoftness(v: Int) {
        if (v == softness) return
        softness = v; prefs.softness = v
        stop(); voices.clear(pack); voiceReady = false
    }

    var hqVoice by mutableStateOf(prefs.hqVoice)

    /** 고품질 목소리 켜기/끄기: 바꾸면 지금 목소리를 다시 만든다 */
    fun changeHq(v: Boolean) {
        if (v == hqVoice) return
        hqVoice = v; prefs.hqVoice = v
        stop(); voices.clear(pack); voiceReady = false
    }

    fun changeBg(v: Int) { bgLevel = v; prefs.bgLevel = v; sendBg() }

    private fun sendBg() {
        controller?.sendCustomCommand(
            SessionCommand(PlaybackService.CMD_BG, Bundle.EMPTY),
            Bundle().apply { putFloat("level", listOf(0f, 0.12f, 0.25f)[bgLevel.coerceIn(0, 2)]) },
        )
    }

    /** 미리 듣기: 이미 만들어 둔 목소리는 실제 파일(음색 처리 포함)로 들려준다 */
    fun previewFiles(p: VoicePack): Boolean {
        if (!voices.isReady(p)) return false
        load(playlist.preview(p), loop = false)
        return true
    }

    // ---- 음성 버전 ----
    fun selectPack(p: VoicePack) {
        if (p.id == pack.id) return
        stop()
        pack = p
        prefs.voicePack = p.id
        voiceReady = voices.isReady(p)
    }

    // ---- 빈칸 ----
    fun setBlank(v: Float) { blankRatio = ((v * 10).roundToInt() / 10f).coerceIn(0.1f, 0.9f); prefs.blankRatio = blankRatio }

    fun reportBlank(success: Boolean) {
        askBlankResult = false
        if (success) {
            val s = prefs.blankStreak + 1
            if (s >= 2 && blankRatio < 0.9f) { setBlank(blankRatio + 0.1f); prefs.blankStreak = 0 } else prefs.blankStreak = s
        } else prefs.blankStreak = 0
    }

    // ---- 이어 말하기 ----
    fun newQuestion() {
        val d = direction
        // 자주 틀린 간지일수록 더 자주
        val w = (0..59).map { 1.0 + 3.0 * prefs.ngCount(d.ordinal, it) / (1.0 + prefs.okCount(d.ordinal, it)) }
        var r = Random.nextDouble() * w.sum()
        var ans = 59
        for (i in 0..59) { r -= w[i]; if (r <= 0) { ans = i; break } }
        val ord = d.order()
        val p = ord.indexOf(ans)
        val pool = listOf(1, -1, 2, -2, 10, -10, 12, -12, 3).map { ord[((p + it) % 60 + 60) % 60] }.distinct().shuffled()
        qAnswer = ans
        qChoices = (listOf(ans) + pool.filter { it != ans }.take(3)).shuffled()
        qPicked = -1
        replayQuestion()
    }

    fun replayQuestion() {
        if (qAnswer < 0) { newQuestion(); return }
        load(playlist.question(direction, qAnswer, ctxCount), loop = false)
    }

    fun pick(idx: Int) {
        if (qPicked >= 0 || qAnswer < 0) return
        qPicked = idx
        val ok = idx == qAnswer
        qCount++
        if (ok) qScore++
        prefs.record(direction.ordinal, qAnswer, ok)
        statsVersion++
        load(playlist.reveal(direction, qAnswer, ok), loop = false)
    }

    fun setCtx(n: Int) { ctxCount = n; prefs.contextCount = n }

    // ---- 표기 ----
    fun label(idx: Int): String = if (script == Script.HANGUL) Gapja.hangul(idx) else Gapja.hanja(idx)

    fun sub(idx: Int): String? = if (script == Script.BOTH) Gapja.hangul(idx) else null
}
