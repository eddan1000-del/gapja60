package kr.gapja60

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 네 방향 × 60개 = 240항목 전수 검사. 기대값은 코드와 독립적으로 손으로 적은 표다. */
class GapjaTest {
    private val expected = """
        갑자 을축 병인 정묘 무진 기사 경오 신미 임신 계유
        갑술 을해 병자 정축 무인 기묘 경진 신사 임오 계미
        갑신 을유 병술 정해 무자 기축 경인 신묘 임진 계사
        갑오 을미 병신 정유 무술 기해 경자 신축 임인 계묘
        갑진 을사 병오 정미 무신 기유 경술 신해 임자 계축
        갑인 을묘 병진 정사 무오 기미 경신 신유 임술 계해
    """.trimIndent().split(Regex("\\s+"))

    private fun flip(s: String) = "${s[1]}${s[0]}"

    @Test fun forward() = assertEquals(expected, Direction.FORWARD.readings())
    @Test fun backward() = assertEquals(expected.reversed(), Direction.BACKWARD.readings())
    @Test fun forwardFlip() = assertEquals(expected.map(::flip), Direction.FORWARD_FLIP.readings())
    @Test fun backwardFlip() = assertEquals(expected.reversed().map(::flip), Direction.BACKWARD_FLIP.readings())

    @Test fun samples() {
        assertEquals("자갑", Direction.FORWARD_FLIP.readings().first())
        assertEquals("해계", Direction.FORWARD_FLIP.readings().last())
        assertEquals("해계", Direction.BACKWARD_FLIP.readings().first())
        assertEquals("유신", Direction.BACKWARD_FLIP.readings()[2])
        assertEquals("丙午", Gapja.hanja(42))
        assertEquals("午丙", Gapja.hanja(42, true))
    }

    @Test fun allUnique() {
        for (d in Direction.entries) {
            assertEquals(60, d.readings().toSet().size)
            assertEquals(60, d.order().toSet().size)
        }
    }

    @Test fun ranges() {
        val f = Direction.FORWARD
        assertEquals(10, Sequencer.sequence(f, Range(xuns = setOf(2))).size)
        assertEquals(listOf(20, 21, 22), Sequencer.sequence(f, Range(xuns = setOf(2))).take(3))
        assertEquals(58, Sequencer.sequence(f, Range(start = 58)).first())
        assertEquals(60, Sequencer.sequence(f, Range(start = 58)).size)
        assertEquals(listOf(58, 59, 0, 1), Sequencer.sequence(f, Range(a = 58, b = 1)))
        assertEquals(listOf(1, 0, 59, 58), Sequencer.sequence(Direction.BACKWARD, Range(a = 1, b = 58)))
        assertEquals(listOf(57, 58), Sequencer.before(f, 59, 2))
        assertEquals(listOf(1, 0), Sequencer.before(Direction.BACKWARD, 59, 2))
        assertEquals(0, Sequencer.next(f, 59))
        assertEquals(59, Sequencer.next(Direction.BACKWARD, 0))
        assertTrue(Sequencer.sequence(f, Range(xuns = emptySet())).isEmpty())
    }
}
