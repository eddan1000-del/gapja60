package kr.gapja60

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 합성 음성을 듣기 편하게 다듬는 음색 처리(안드로이드 의존성 없음 → 단위 테스트 가능).
 *
 * 1) 귀를 찌르는 고음(치찰음·금속성) 줄이기: 고음 셸프 감쇠 + 저역 통과
 * 2) 약간의 따뜻함: 저음 셸프 소폭 보강
 * 3) 말머리가 '툭' 튀지 않게: 부드러운 압축(큰 소리만 살짝 눌러 고르게)
 * 4) 공간감: 아주 짧은 잔향을 조금 섞어 기계음의 건조함 완화
 * 5) 시작·끝 페이드
 * 발음 명료도에 중요한 1~4kHz 대역은 거의 건드리지 않는다.
 */
object SoftVoice {
    data class Style(
        val lowpassHz: Double,
        val highShelfHz: Double, val highShelfDb: Double,
        val lowShelfHz: Double, val lowShelfDb: Double,
        val compRatio: Double,
        val reverbMix: Double,
        val fadeInMs: Int, val fadeOutMs: Int,
        val targetRms: Double,
    )

    /** 0 자연 · 1 부드럽게(기본) · 2 더 부드럽게 */
    val STYLES = listOf(
        Style(9000.0, 3500.0, -1.5, 220.0, 0.0, 1.5, 0.04, 15, 40, 3600.0),
        Style(6500.0, 3000.0, -4.0, 220.0, 1.5, 2.5, 0.10, 25, 70, 3000.0),
        Style(5000.0, 2500.0, -6.0, 220.0, 2.5, 3.0, 0.16, 35, 100, 2600.0),
    )
    val NAMES = listOf("자연", "부드럽게", "더 부드럽게")

    const val TAIL_MS = 320

    fun process(pcm: ShortArray, rate: Int, style: Style): ShortArray {
        if (pcm.isEmpty() || rate <= 0) return pcm
        val tail = rate * TAIL_MS / 1000
        val x = DoubleArray(pcm.size + tail) { if (it < pcm.size) pcm[it] / 32768.0 else 0.0 }

        // 1)·2) 음색(EQ)
        val nyq = rate / 2.0
        biquad(x, lowShelf(style.lowShelfHz, style.lowShelfDb, rate))
        biquad(x, highShelf(min(style.highShelfHz, nyq * 0.8), style.highShelfDb, rate))
        biquad(x, lowpass(min(style.lowpassHz, nyq * 0.9), 0.707, rate))

        // 3) 부드러운 압축
        val peak0 = x.maxOf { abs(it) }.coerceAtLeast(1e-9)
        for (i in x.indices) x[i] /= peak0
        compress(x, rate, threshold = 0.3, ratio = style.compRatio)

        // 5) 페이드(소리 부분 기준)
        val fi = rate * style.fadeInMs / 1000
        val fo = rate * style.fadeOutMs / 1000
        for (i in 0 until min(fi, pcm.size)) x[i] *= 0.5 - 0.5 * cos(PI * i / fi)
        for (k in 0 until min(fo, pcm.size)) { val i = pcm.size - 1 - k; x[i] *= 0.5 - 0.5 * cos(PI * k / fo) }

        // 4) 잔향
        if (style.reverbMix > 0) reverb(x, rate, style.reverbMix)

        // 음량 맞춤(말소리 부분 RMS) + 최대값 제한
        var sum = 0.0; var n = 0
        val pk = x.maxOf { abs(it) }.coerceAtLeast(1e-9)
        for (v in x) if (abs(v) > pk * 0.05) { sum += v * v; n++ }
        val rms = if (n > 0) sqrt(sum / n) else 1e-9
        val gain = min(style.targetRms / 32768.0 / rms, 0.8 / pk)
        return ShortArray(x.size) { (x[it] * gain * 32767).toInt().coerceIn(-32767, 32767).toShort() }
    }

    // ───── 필터 (RBJ Audio EQ Cookbook) ─────
    private class Coef(val b0: Double, val b1: Double, val b2: Double, val a1: Double, val a2: Double)

    private fun norm(b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) =
        Coef(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)

    private fun lowpass(f: Double, q: Double, fs: Int): Coef {
        val w = 2 * PI * f / fs; val c = cos(w); val al = sin(w) / (2 * q)
        return norm((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + al, -2 * c, 1 - al)
    }

    private fun highShelf(f: Double, db: Double, fs: Int): Coef {
        val a = 10.0.pow(db / 40); val w = 2 * PI * f / fs; val c = cos(w)
        val al = sin(w) / 2 * sqrt(2.0); val sa = 2 * sqrt(a) * al
        return norm(
            a * ((a + 1) + (a - 1) * c + sa), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - sa),
            (a + 1) - (a - 1) * c + sa, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - sa,
        )
    }

    private fun lowShelf(f: Double, db: Double, fs: Int): Coef {
        val a = 10.0.pow(db / 40); val w = 2 * PI * f / fs; val c = cos(w)
        val al = sin(w) / 2 * sqrt(2.0); val sa = 2 * sqrt(a) * al
        return norm(
            a * ((a + 1) - (a - 1) * c + sa), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - sa),
            (a + 1) + (a - 1) * c + sa, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - sa,
        )
    }

    private fun biquad(x: DoubleArray, k: Coef) {
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        for (i in x.indices) {
            val v = x[i]
            val y = k.b0 * v + k.b1 * x1 + k.b2 * x2 - k.a1 * y1 - k.a2 * y2
            x2 = x1; x1 = v; y2 = y1; y1 = y
            x[i] = y
        }
    }

    private fun compress(x: DoubleArray, fs: Int, threshold: Double, ratio: Double) {
        if (ratio <= 1.0) return
        val att = exp(-1.0 / (fs * 0.008)); val rel = exp(-1.0 / (fs * 0.12))
        var env = 0.0
        for (i in x.indices) {
            val a = abs(x[i])
            env = if (a > env) att * env + (1 - att) * a else rel * env + (1 - rel) * a
            if (env > threshold) x[i] *= (threshold * (env / threshold).pow(1 / ratio)) / env
        }
    }

    /** 짧은 방 잔향(Schroeder: 병렬 콤 4개 + 직렬 올패스 2개) */
    private fun reverb(x: DoubleArray, fs: Int, mix: Double) {
        val dry = x.copyOf()
        val wet = DoubleArray(x.size)
        for ((ms, g) in listOf(29.7 to 0.70, 37.1 to 0.68, 41.1 to 0.66, 43.7 to 0.64)) {
            val d = max(1, (fs * ms / 1000).toInt())
            val buf = DoubleArray(x.size)
            for (i in x.indices) {
                val fb = if (i >= d) buf[i - d] else 0.0
                buf[i] = dry[i] + g * fb
                wet[i] += fb * 0.25
            }
        }
        for ((ms, g) in listOf(5.0 to 0.7, 1.7 to 0.7)) {
            val d = max(1, (fs * ms / 1000).toInt())
            val inp = wet.copyOf()
            for (i in wet.indices) {
                val xd = if (i >= d) inp[i - d] else 0.0
                val yd = if (i >= d) wet[i - d] else 0.0
                wet[i] = -g * inp[i] + xd + g * yd
            }
        }
        for (i in x.indices) x[i] = dry[i] * (1 - mix) + wet[i] * mix
    }
}
