package kr.gapja60

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class SoftVoiceTest {
    private val rate = 24000

    private fun tone(f: Double, ms: Int) = ShortArray(rate * ms / 1000) { (sin(2 * PI * f * it / rate) * 12000).toInt().toShort() }
    private fun level(x: ShortArray): Double = x.sumOf { abs(it.toDouble()) } / x.size

    @Test fun lengthAndRange() {
        val inp = ShortArray(rate / 2) { (Random(1).nextInt(-20000, 20000)).toShort() }
        for (st in SoftVoice.STYLES) {
            val out = SoftVoice.process(inp, rate, st)
            assertEquals(inp.size + rate * SoftVoice.TAIL_MS / 1000, out.size)
            assertTrue(out.all { it in -32767..32767 })
            assertTrue(out.any { it.toInt() != 0 })
        }
    }

    @Test fun keepsSpeechBandSoftensHighs() {
        val st = SoftVoice.STYLES[1]
        // 같은 크기의 1kHz(말소리 핵심 대역)와 7kHz(쇳소리) → 처리 후 7kHz가 훨씬 작아야 한다
        val mid = SoftVoice.process(tone(1000.0, 400), rate, st)
        val high = SoftVoice.process(tone(7000.0, 400), rate, st)
        // 두 결과는 각자 음량 맞춤되므로, 처리 전후 비율 대신 필터 응답을 직접 비교하기 위해 섞어서 검사
        val mix = ShortArray(rate * 400 / 1000) { ((sin(2 * PI * 1000.0 * it / rate) + sin(2 * PI * 7000.0 * it / rate)) * 6000).toInt().toShort() }
        val out = SoftVoice.process(mix, rate, st)
        assertTrue(level(mid) > 0 && level(high) > 0)
        // 7kHz 성분 추정: 1차 차분(고역 강조)의 에너지 비율이 입력보다 줄었는지
        fun hfRatio(x: ShortArray): Double {
            var d = 0.0; var e = 0.0
            for (i in 1 until x.size) { d += abs(x[i] - x[i - 1].toDouble()); e += abs(x[i].toDouble()) }
            return d / e
        }
        assertTrue("고음이 줄어야 함", hfRatio(out.copyOf(mix.size)) < hfRatio(mix) * 0.8)
    }
}
