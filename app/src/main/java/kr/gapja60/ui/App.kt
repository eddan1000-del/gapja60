@file:OptIn(ExperimentalMaterial3Api::class)

package kr.gapja60.ui

import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kr.gapja60.AppState
import kr.gapja60.Direction
import kr.gapja60.Gapja
import kr.gapja60.Mode
import kr.gapja60.Script
import kr.gapja60.VoiceBank
import java.util.Locale

@Composable
fun GapjaApp(s: AppState) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            !s.voiceReady -> VoiceSetup(s)
            s.showSettings -> Settings(s)
            else -> Home(s)
        }
    }
}

// ───────────────────────── 음성 준비 ─────────────────────────
@Composable
private fun VoiceSetup(s: AppState) {
    val ctx = LocalContext.current
    var progress by remember { mutableStateOf(0f) }
    var error by remember { mutableStateOf<VoiceBank.Result?>(null) }
    var attempt by remember { mutableIntStateOf(0) }

    var status by remember { mutableStateOf("") }
    LaunchedEffect(attempt) {
        error = null
        s.voices.status = { status = it }
        val r = s.voices.build { n, total -> progress = n.toFloat() / total }
        if (r is VoiceBank.Result.Ok) s.voiceReady = true else error = r
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("甲子", fontFamily = SerifGapja, fontSize = 72.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("육십갑자", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))
        val e = error
        if (e == null) {
            Text("음성을 준비하고 있습니다", style = MaterialTheme.typography.titleMedium)
            Text(status.ifBlank { "처음 한 번만 필요합니다 · 약 1~2분" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)))
            Text("${(progress * 121).toInt()} / 121", Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val msg = when (e) {
                VoiceBank.Result.NoKorean -> "한국어 음성 데이터가 없습니다.\n'음성 데이터 설치'에서 한국어를 내려받은 뒤 다시 시도해 주세요."
                VoiceBank.Result.NoEngine -> "음성 합성 엔진을 찾지 못했습니다.\nPlay 스토어에서 'Google 음성 서비스'를 설치해 주세요."
                VoiceBank.Result.VoiceNotInstalled -> "한국어 음성이 아직 이 폰에 설치되지 않았습니다.\n'음성 데이터 설치'에서 한국어(대한민국)를 내려받은 뒤\n'다시 시도'를 눌러 주세요."
                is VoiceBank.Result.Failed -> "'${e.what}' 음성을 만들지 못했습니다(오류 ${e.code}).\n잠시 후 다시 시도해 주세요."
                else -> ""
            }
            Text(msg, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(20.dp))
            Button(onClick = { attempt++ }, Modifier.fillMaxWidth().height(56.dp)) { Text("다시 시도") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                runCatching { ctx.startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { runCatching { ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            }, Modifier.fillMaxWidth().height(56.dp)) { Text("음성 데이터 설치") }
        }
    }
}

// ───────────────────────── 메인 ─────────────────────────
@Composable
private fun Home(s: AppState) {
    val haptic = LocalHapticFeedback.current
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        // 상단
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("육십갑자", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = { s.showSettings = true }) { Text("설정") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            DirectionCards(s) { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
            Spacer(Modifier.height(12.dp))
            TabRow(selectedTabIndex = s.mode.ordinal, containerColor = Color.Transparent) {
                Mode.entries.forEach { m ->
                    Tab(selected = s.mode == m, onClick = {
                        if (s.mode != m) { s.stop(); s.mode = m }
                    }, text = { Text(m.label, maxLines = 1, softWrap = false) })
                }
            }
            Spacer(Modifier.height(12.dp))
            if (s.mode != Mode.NEXT) {
                GapjaGrid(s) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                Spacer(Modifier.height(14.dp))
            }
            when (s.mode) {
                Mode.RECITE -> RecitePanel(s)
                Mode.BLANK -> BlankPanel(s)
                Mode.NEXT -> NextPanel(s)
                Mode.STATS -> StatsPanel(s)
            }
            Spacer(Modifier.height(16.dp))
        }
        if (s.mode != Mode.STATS) PlayerBar(s)
    }

    if (s.askBlankResult) {
        AlertDialog(
            onDismissRequest = { s.askBlankResult = false },
            title = { Text("이번 회차") },
            text = { Text("빈칸을 모두 막힘없이 채웠나요?\n두 번 연속 성공하면 빈칸을 10% 늘립니다. (현재 ${(s.blankRatio * 100).toInt()}%)") },
            confirmButton = { Button(onClick = { s.reportBlank(true) }) { Text("예") } },
            dismissButton = { TextButton(onClick = { s.reportBlank(false) }) { Text("아니요") } },
        )
    }
}

@Composable
private fun DirectionCards(s: AppState, onPick: () -> Unit) {
    val dirs = Direction.entries
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (row in 0..1) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (col in 0..1) {
                val d = dirs[row * 2 + col]
                val sel = s.direction == d
                val bg by animateColorAsState(if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, label = "dirBg")
                val fg = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Column(
                    Modifier.weight(1f).heightIn(min = 84.dp).clip(RoundedCornerShape(18.dp)).background(bg)
                        .border(BorderStroke(1.dp, if (sel) Color.Transparent else MaterialTheme.colorScheme.outline), RoundedCornerShape(18.dp))
                        .clickable { onPick(); s.selectDirection(d) }
                        .semantics { contentDescription = "${d.label}, ${d.sample}" + if (sel) ", 선택됨" else "" }
                        .padding(14.dp),
                ) {
                    Text(d.label, color = fg, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, softWrap = false)
                    Spacer(Modifier.height(4.dp))
                    Text((if (d.reverse) "← " else "→ ") + d.sample, color = fg.copy(alpha = 0.85f), fontFamily = SerifGapja, fontSize = 15.sp, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun GapjaGrid(s: AppState, onTap: () -> Unit) {
    val rot = remember { Animatable(0f) }
    var shown by remember { mutableStateOf(s.direction) }
    LaunchedEffect(s.direction) {
        if (shown != s.direction) {
            rot.animateTo(90f, tween(170)); shown = s.direction
            rot.snapTo(-90f); rot.animateTo(0f, tween(230))
        }
    }
    val inSeq = remember(s.xuns, s.start, s.abA, s.abB, s.direction) { s.sequence().toSet() }
    val showCurrent = !(s.mode == Mode.NEXT && s.qPicked < 0 && (s.curKind == "p"))
    val stats = s.mode == Mode.STATS
    @Suppress("UNUSED_VARIABLE") val v = s.statsVersion

    Column(
        Modifier.graphicsLayer { rotationY = rot.value; cameraDistance = 14f * density },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (x in 0..5) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (k in 0..9) {
                    val i = x * 10 + k
                    val cur = showCurrent && s.curIdx == i && s.curKind in setOf("g", "s", "t")
                    val ng = if (stats) s.prefs.ngCount(shown.ordinal, i) else 0
                    val ok = if (stats) s.prefs.okCount(shown.ordinal, i) else 0
                    val heat = if (ng + ok == 0) 0f else ng.toFloat() / (ng + ok)
                    val base = when {
                        cur -> MaterialTheme.colorScheme.primary
                        stats && heat > 0f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f + 0.6f * heat)
                        else -> MaterialTheme.colorScheme.surface
                    }
                    val bg by animateColorAsState(base, tween(120), label = "cell")
                    val fg = if (cur) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    val mark = i == s.abA || i == s.abB || (i == s.start && s.abA == null)
                    val dim = (s.mode == Mode.RECITE || s.mode == Mode.BLANK) && i !in inSeq
                    Column(
                        Modifier.weight(1f).aspectRatio(0.74f).clip(RoundedCornerShape(9.dp)).background(bg)
                            .border(if (mark) 2.dp else 0.5.dp, if (mark) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(9.dp))
                            .alpha(if (dim) 0.35f else 1f)
                            .clickable { onTap(); s.tapCell(i) }
                            .semantics { contentDescription = "${Gapja.hangul(i, shown.flip)}, ${i + 1}번째" },
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val main = when (s.script) { Script.HANGUL -> Gapja.hangul(i, shown.flip); else -> Gapja.hanja(i, shown.flip) }
                        Text(main, fontFamily = SerifGapja, color = fg, fontSize = 13.sp, lineHeight = 14.sp, maxLines = 1, softWrap = false)
                        if (s.script == Script.BOTH) Text(Gapja.hangul(i, shown.flip), color = fg.copy(alpha = 0.7f), fontSize = 9.sp, lineHeight = 10.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("가로 한 줄 = 한 순(갑자순…갑인순)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (shown.reverse) "← 역행" else "→ 순행", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
    }
}

// ───────────────────────── 모드별 패널 ─────────────────────────
@Composable
private fun Label(t: String) = Text(t, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))

@Composable
private fun <T> Chips(options: List<Pair<T, String>>, selected: (T) -> Boolean, onClick: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        options.forEach { (v, t) ->
            FilterChip(selected = selected(v), onClick = { onClick(v) }, label = { Text(t, maxLines = 1) }, modifier = Modifier.heightIn(min = 48.dp))
        }
    }
}

@Composable
private fun XunChips(s: AppState) {
    Label("구간 (순)")
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        for (r in 0..1) Chips((r * 3 until r * 3 + 3).map { it to Gapja.XUN[it] }, { it in s.xuns && s.abA == null }) { s.toggleXun(it) }
    }
}

@Composable
private fun RangeRow(s: AppState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(onClick = { s.abPicking = !s.abPicking; if (s.abPicking) { s.abA = null; s.abB = null } }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (s.abPicking) (if (s.abA == null) "A 칸을 누르세요" else "B 칸을 누르세요") else "A–B 구간")
        }
        if (s.abA != null && s.abB != null) Text("${Gapja.hangul(s.abA!!, s.direction.flip)} → ${Gapja.hangul(s.abB!!, s.direction.flip)}", fontFamily = SerifGapja)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { s.clearRange() }) { Text("초기화") }
    }
}

@Composable
private fun RecitePanel(s: AppState) {
    Text("표의 간지를 누르면 그 자리부터 재생합니다.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    XunChips(s)
    RangeRow(s)
    Label("반복")
    Chips(listOf(1 to "1회", 3 to "3회", 5 to "5회", 0 to "무한"), { s.repeat == it }) { s.changeRepeat(it) }
    Label("잠들기 타이머")
    Chips(listOf(0 to "끔", 15 to "15분", 30 to "30분", 60 to "60분"), { s.sleepMin == it }) { s.setSleep(it) }
}

@Composable
private fun BlankPanel(s: AppState) {
    Text("'틱' 소리가 나면 속으로 채우세요. 잠시 뒤 정답을 들려줍니다.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Label("빈칸 비율 ${(s.blankRatio * 100).toInt()}%")
    Slider(value = s.blankRatio, onValueChange = { s.setBlank(it) }, valueRange = 0.1f..0.9f, steps = 7,
        modifier = Modifier.semantics { contentDescription = "빈칸 비율 ${(s.blankRatio * 100).toInt()}퍼센트" })
    var think by remember { mutableStateOf(s.prefs.thinkMs / 1000f) }
    Label("생각할 시간 ${"%.1f".format(Locale.US, think)}초 (1배속 기준)")
    Slider(value = think, onValueChange = { think = (it * 2).toInt() / 2f; s.prefs.thinkMs = (think * 1000).toInt() }, valueRange = 0.5f..4f, steps = 6)
    XunChips(s)
    RangeRow(s)
}

@Composable
private fun NextPanel(s: AppState) {
    val haptic = LocalHapticFeedback.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("앞의 간지를 듣고 다음 간지를 고르세요.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (s.qCount > 0) Text("${s.qScore} / ${s.qCount}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
    Label("들려줄 앞 간지 수")
    Chips(listOf(1 to "1개", 2 to "2개", 3 to "3개"), { s.ctxCount == it }) { s.setCtx(it) }
    Spacer(Modifier.height(12.dp))
    if (s.qChoices.isEmpty()) {
        Button(onClick = { s.newQuestion() }, Modifier.fillMaxWidth().height(60.dp)) { Text("시작", fontSize = 18.sp) }
        return
    }
    val ctxText = kr.gapja60.Sequencer.before(s.direction, s.qAnswer, s.ctxCount).joinToString(" · ") { Gapja.hangul(it, s.direction.flip) }
    Text("$ctxText · ?", fontFamily = SerifGapja, fontSize = 22.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    Spacer(Modifier.height(10.dp))
    for (r in 0..1) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
            for (c in 0..1) {
                val i = s.qChoices[r * 2 + c]
                val done = s.qPicked >= 0
                val color = when {
                    done && i == s.qAnswer -> Color(0xFF2E7D4F)
                    done && i == s.qPicked -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surface
                }
                val fg = if (done && (i == s.qAnswer || i == s.qPicked)) Color.White else MaterialTheme.colorScheme.onSurface
                Column(
                    Modifier.weight(1f).height(84.dp).clip(RoundedCornerShape(18.dp)).background(color)
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(18.dp))
                        .clickable(enabled = !done) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress); s.pick(i)
                        }
                        .semantics { contentDescription = Gapja.hangul(i, s.direction.flip) },
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(s.label(i), fontFamily = SerifGapja, fontSize = 26.sp, color = fg)
                    s.sub(i)?.let { Text(it, fontSize = 13.sp, color = fg.copy(alpha = 0.75f)) }
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = { s.replayQuestion() }, Modifier.weight(1f).height(56.dp)) { Text("다시 듣기") }
        Button(onClick = { s.newQuestion() }, Modifier.weight(1f).height(56.dp)) { Text("다음 문제") }
    }
}

@Composable
private fun StatsPanel(s: AppState) {
    @Suppress("UNUSED_VARIABLE") val v = s.statsVersion
    var confirm by remember { mutableStateOf(false) }
    val p = s.prefs
    Text("표의 색이 진할수록 '이어 말하기'에서 자주 막힌 간지입니다. (${s.direction.label})", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        StatBox("연속 학습", "${p.streak()}일", Modifier.weight(1f))
        StatBox("총 학습", "${p.studyMs / 60000}분", Modifier.weight(1f))
    }
    Label("방향별 60갑자 완주")
    Direction.entries.forEach { d ->
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Text(d.label, Modifier.weight(1f))
            Text("${p.completions(d.ordinal)}회", fontWeight = FontWeight.Bold)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    }
    Spacer(Modifier.height(16.dp))
    OutlinedButton(onClick = { confirm = true }, Modifier.fillMaxWidth().height(52.dp)) { Text("기록 지우기") }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("기록을 모두 지울까요?") },
        text = { Text("학습 시간, 연속 학습일, 완주 횟수, 이어 말하기 기록이 지워집니다. 설정은 그대로 둡니다.") },
        confirmButton = { Button(onClick = { p.resetRecords(); s.statsVersion++; confirm = false }) { Text("지우기") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("취소") } },
    )
}

@Composable
private fun StatBox(title: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).padding(16.dp)) {
        Text(title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold)
    }
}

// ───────────────────────── 재생 막대 ─────────────────────────
@Composable
private fun PlayerBar(s: AppState) {
    val haptic = LocalHapticFeedback.current
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 12.dp, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val hidden = s.curIdx < 0 || s.curKind == "b" || s.curKind == "p"
                AnimatedContent(targetState = if (hidden) -1 else s.curIdx, transitionSpec = { fadeIn(tween(90)) togetherWith fadeOut(tween(90)) }, label = "now", modifier = Modifier.weight(1f)) { idx ->
                    Column {
                        Text(if (idx < 0) (if (s.curKind == "b" || s.curKind == "p") "?" else "—") else s.label(idx),
                            fontFamily = SerifGapja, fontSize = 40.sp, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                        val sub = if (idx >= 0) s.sub(idx) else null
                        Text(listOfNotNull(sub, if (s.curTotal > 0) "${s.curPos} / ${s.curTotal}" else null).ifEmpty { listOf(s.direction.label) }.joinToString("  ·  "),
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                }
                RoundBtn("◀◀", "이전", 52) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); s.skip(-1) }
                Spacer(Modifier.width(8.dp))
                RoundBtn(if (s.playing) "❚❚" else "▶", if (s.playing) "멈춤" else "재생", 68, primary = true) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress); s.toggle()
                }
                Spacer(Modifier.width(8.dp))
                RoundBtn("▶▶", "다음", 52) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); s.skip(1) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                RoundBtn("−", "느리게", 44) { s.changeSpeed(s.speed - 0.25f) }
                Slider(
                    value = s.speed, onValueChange = { s.changeSpeed(it) }, valueRange = 0.5f..5f, steps = 17,
                    colors = SliderDefaults.colors(activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { contentDescription = "속도 ${s.speed}배" },
                )
                RoundBtn("+", "빠르게", 44) { s.changeSpeed(s.speed + 0.25f) }
                Text("${"%.2f".format(Locale.US, s.speed).trimEnd('0').trimEnd('.')}×", Modifier.width(56.dp), textAlign = TextAlign.End,
                    fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun RoundBtn(text: String, desc: String, size: Int, primary: Boolean = false, onClick: () -> Unit) {
    val bg = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(50)).background(bg).clickable(onClick = onClick).semantics { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) { Text(text, color = fg, fontSize = (size / 2.8).sp, fontWeight = FontWeight.Bold) }
}

// ───────────────────────── 설정 ─────────────────────────
@Composable
private fun Settings(s: AppState) {
    BackHandler { s.showSettings = false }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var gap by remember { mutableStateOf(s.prefs.gapMs.toFloat()) }
    var confirmVoice by remember { mutableStateOf(false) }
    val version = remember { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "" }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { s.showSettings = false }) { Text("← 돌아가기") }
        }
        Text("설정", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Label("표기")
        Chips(Script.entries.map { it to it.label }, { s.script == it }) { s.script = it; s.prefs.script = it.ordinal }
        Label("화면")
        Chips(listOf(0 to "시스템", 1 to "밝게", 2 to "어둡게"), { s.theme == it }) { s.theme = it; s.prefs.theme = it }
        Label("간지 사이 쉬는 시간 ${gap.toInt()}ms (1배속 기준, 배속에 비례해 줄어듦)")
        Slider(value = gap, onValueChange = { gap = (it / 50).toInt() * 50f; s.prefs.gapMs = gap.toInt() }, valueRange = 50f..1500f)
        Text("새 설정은 다음 재생부터 적용됩니다.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Label("음성")
        Text(s.prefs.voiceInfo.ifBlank { "사용자 제공 음성 파일" }, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { confirmVoice = true }, Modifier.fillMaxWidth().height(52.dp)) { Text("음성 다시 만들기") }
        Text("기기의 음성 설정(엔진·목소리)을 바꾼 뒤 다시 만들면 새 목소리로 바뀝니다.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text("육십갑자 $version", fontWeight = FontWeight.Bold)
        Text("광고·로그인·인터넷 연결 없이 작동합니다. 모든 기록은 이 기기에만 저장됩니다.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text("글꼴: Noto Serif CJK (SIL Open Font License 1.1)\n재생: AndroidX Media3 (Apache License 2.0)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (confirmVoice) AlertDialog(
        onDismissRequest = { confirmVoice = false },
        title = { Text("음성을 다시 만들까요?") },
        text = { Text("현재 음성 파일을 지우고 기기의 음성으로 다시 만듭니다. 1~2분 걸립니다.") },
        confirmButton = {
            Button(onClick = {
                confirmVoice = false
                scope.launch { s.stop(); s.voices.clear(); s.showSettings = false; s.voiceReady = false }
            }) { Text("다시 만들기") }
        },
        dismissButton = { TextButton(onClick = { confirmVoice = false }) { Text("취소") } },
    )
}
