package kr.gapja60

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * 화면이 꺼져도 계속 재생하는 서비스.
 * 잠금화면·알림·블루투스 버튼 조작과 오디오 포커스(전화 수신 시 멈춤 후 재개)는 Media3가 처리한다.
 */
class PlaybackService : MediaSessionService() {
    companion object {
        const val CMD_SLEEP = "kr.gapja60.SLEEP"
    }

    private var session: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val handler = Handler(Looper.getMainLooper())
    // 잠들기 타이머: 끝나기 fade분 전부터 음량을 서서히 줄인 뒤 멈춤
    private var fadeEnd = 0L
    private var fadeMs = 0L
    private val fadeStep = object : Runnable {
        override fun run() {
            val left = fadeEnd - SystemClock.elapsedRealtime()
            if (left <= 0) { player.pause(); player.volume = 1f; return }
            player.volume = (left.toFloat() / fadeMs).coerceIn(0f, 1f).let { it * it }   // 귀에 자연스럽게 줄어들도록 제곱 곡선
            handler.postDelayed(this, 500)
        }
    }
    private val sleepRunnable = Runnable { handler.post(fadeStep) }
    private var playStart = 0L

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)   // 이어폰을 빼면 멈춤
            .setWakeMode(C.WAKE_MODE_LOCAL)       // 화면이 꺼져도 CPU 유지
            .build()

        val prefs = Prefs(this)
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val now = SystemClock.elapsedRealtime()
                if (isPlaying) playStart = now
                else if (playStart > 0) { prefs.addStudy(now - playStart); playStart = 0 }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val ex = mediaItem?.mediaMetadata?.extras ?: return
                if (ex.getBoolean(Playlist.K_LAST)) prefs.addCompletion(ex.getInt(Playlist.K_DIR))
            }
        })

        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(open)
            .setCallback(Callback())
            .build()
    }

    private inner class Callback : MediaSession.Callback {
        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val cmds = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(CMD_SLEEP, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(cmds)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == CMD_SLEEP) {
                handler.removeCallbacks(sleepRunnable); handler.removeCallbacks(fadeStep)
                player.volume = 1f
                val min = args.getInt("min", 0)
                if (min > 0) {
                    val total = min * 60_000L
                    fadeMs = (args.getInt("fade", 2) * 60_000L).coerceIn(5_000L, total / 2)
                    fadeEnd = SystemClock.elapsedRealtime() + total
                    handler.postDelayed(sleepRunnable, total - fadeMs)
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        // 컨트롤러에서 넘어온 항목은 파일 경로가 빠질 수 있으므로 requestMetadata로 복원한다.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.map { Playlist.resolve(it) }.toMutableList())
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(sleepRunnable); handler.removeCallbacks(fadeStep)
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
