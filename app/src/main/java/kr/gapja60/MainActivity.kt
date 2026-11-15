package kr.gapja60

import android.content.ComponentName
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kr.gapja60.ui.GapjaApp
import kr.gapja60.ui.GapjaTheme

class MainActivity : ComponentActivity() {
    private lateinit var state: AppState
    private var future: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        state = AppState(applicationContext)
        setContent {
            GapjaTheme(state.theme) { GapjaApp(state) }
        }
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val f = MediaController.Builder(this, token).buildAsync()
        future = f
        f.addListener({ runCatching { state.attach(f.get()) } }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        state.detach()
        future?.let { MediaController.releaseFuture(it) }
        future = null
        super.onStop()
    }
}
