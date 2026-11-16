package kr.gapja60

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 네 방향 × 60개 = 240항목 전수 검사. 기대값은 코드와 독립적으로 손으로 적은 표다. */
class GapjaTest {
    private fun words(s: String) = s.trimIndent().trim().split(Regex("\\s+"))

    /** 가로(순행): 한 줄 = 한 순 */
    private val forward = words("""
        갑자 을축 병인 정묘 무진 기사 경오 신미 임신 계유
        갑술 을해 병자 정축 무인 기묘 경진 신사 임오 계미
        갑신 을유 병술 정해 무자 기축 경인 신묘 임진 계사
        갑오 을미 병신 정유 무술 기해 경자 신축 임인 계묘
        갑진 을사 병오 정미 무신 기유 경술 신해 임자 계축
        갑인 을묘 병진 정사 무오 기미 경신 신유 임술 계해
    """)

    /** 세로 위→아래(순행 뒤집기): 한 줄 = 한 천간 열 */
    private val forwardFlip = words("""
        갑자 갑술 갑신 갑오 갑진 갑인
        을축 을해 을유 을미 을사 을묘
        병인 병자 병술 병신 병오 병진
        정묘 정축 정해 정유 정미 정사
        무진 무인 무자 무술 무신 무오
        기사 기묘 기축 기해 기유 기미
        경오 경진 경인 경자 경술 경신
        신미 신사 신묘 신축 신해 신유
        임신 임오 임진 임인 임자 임술
        계유 계미 계사 계묘 계축 계해
    """)

    /** 세로 아래→위(역행 뒤집기) */
    private val backwardFlip = words("""
        갑인 갑진 갑오 갑신 갑술 갑자
        을묘 을사 을미 을유 을해 을축
        병진 병오 병신 병술 병자 병인
        정사 정미 정유 정해 정축 정묘
        무오 무신 무술 무자 무인 무진
        기미 기유 기해 기축 기묘 기사
        경신 경술 경자 경인 경진 경오
        신유 신해 신축 신묘 신사 신미
        임술 임자 임인 임진 임오 임신
        계해 계축 계묘 계사 계미 계유
    """)

    @Test fun forward() = assertEquals(forward, Direction.FORWARD.readings())
    @Test fun backward() = assertEquals(forward.reversed(), Direction.BACKWARD.readings())
    @Test fun forwardFlip() = assertEquals(forwardFlip, Direction.FORWARD_FLIP.readings())
    @Test fun backwardFlip() = assertEquals(backwardFlip, Direction.BACKWARD_FLIP.readings())

    @Test fun allUnique() {
        for (d in Direction.entries) {
            assertEquals(60, d.readings().size)
            assertEquals(60, d.readings().toSet().size)
            assertEquals(forward.toSet(), d.readings().toSet())
        }
    }

    @Test fun samples() {
        assertEquals("丙午", Gapja.hanja(42))
        assertEquals(listOf("갑자", "갑술", "갑신"), Direction.FORWARD_FLIP.readings().take(3))
        assertEquals(listOf("갑인", "갑진", "갑오"), Direction.BACKWARD_FLIP.readings().take(3))
        // 열이 끝나면 다음 열로
        assertEquals(listOf("갑인", "을축"), Direction.FORWARD_FLIP.readings().subList(5, 7))
        assertEquals(listOf("갑자", "을묘"), Direction.BACKWARD_FLIP.readings().subList(5, 7))
    }

    @Test fun ranges() {
        val f = Direction.FORWARD
        assertEquals(listOf(20, 21, 22), Sequencer.sequence(f, Range(groups = setOf(2))).take(3))
        assertEquals(10, Sequencer.sequence(f, Range(groups = setOf(2))).size)
        assertEquals(58, Sequencer.sequence(f, Range(start = 58)).first())
        assertEquals(60, Sequencer.sequence(f, Range(start = 58)).size)
        assertEquals(listOf(58, 59, 0, 1), Sequencer.sequence(f, Range(a = 58, b = 1)))
        assertEquals(listOf(1, 0, 59, 58), Sequencer.sequence(Direction.BACKWARD, Range(a = 1, b = 58)))
        assertEquals(listOf(57, 58), Sequencer.before(f, 59, 2))
        assertEquals(0, Sequencer.next(f, 59))
        assertTrue(Sequencer.sequence(f, Range(groups = emptySet())).isEmpty())

        // 세로: 을 열(1)만 → 을축 을해 을유 을미 을사 을묘
        val v = Direction.FORWARD_FLIP
        assertEquals(listOf(1, 11, 21, 31, 41, 51), Sequencer.sequence(v, Range(groups = setOf(1))))
        assertEquals(listOf(51, 41, 31, 21, 11, 1), Sequencer.sequence(Direction.BACKWARD_FLIP, Range(groups = setOf(1))))
        // 갑인(50) 다음은 을축(1), 계해(59) 다음은 갑자(0)
        assertEquals(1, Sequencer.next(v, 50))
        assertEquals(0, Sequencer.next(v, 59))
        // 역행 뒤집기: 갑자(0) 다음은 을묘(51)
        assertEquals(51, Sequencer.next(Direction.BACKWARD_FLIP, 0))
        assertEquals(listOf(40, 50), Sequencer.before(v, 1, 2))
    }
}
