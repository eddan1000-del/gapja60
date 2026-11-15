package kr.gapja60

/** 육십갑자 기본 데이터와 네 가지 암송 방향. 안드로이드 의존성이 없어 단위 테스트로 전수 검사한다. */
object Gapja {
    val STEMS = listOf("갑", "을", "병", "정", "무", "기", "경", "신", "임", "계")
    val STEMS_H = listOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
    val BRANCHES = listOf("자", "축", "인", "묘", "진", "사", "오", "미", "신", "유", "술", "해")
    val BRANCHES_H = listOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")
    val XUN = listOf("갑자순", "갑술순", "갑신순", "갑오순", "갑진순", "갑인순")

    /** 0~59 → 한글. flip이면 지지-천간 순(자갑). */
    fun hangul(i: Int, flip: Boolean = false): String =
        if (flip) BRANCHES[i % 12] + STEMS[i % 10] else STEMS[i % 10] + BRANCHES[i % 12]

    fun hanja(i: Int, flip: Boolean = false): String =
        if (flip) BRANCHES_H[i % 12] + STEMS_H[i % 10] else STEMS_H[i % 10] + BRANCHES_H[i % 12]

    fun xunOf(i: Int): Int = i / 10
}

enum class Direction(val label: String, val sample: String, val reverse: Boolean, val flip: Boolean) {
    FORWARD("순행", "갑자 → 계해", false, false),
    BACKWARD("역행", "계해 → 갑자", true, false),
    FORWARD_FLIP("순행 뒤집기", "자갑 → 해계", false, true),
    BACKWARD_FLIP("역행 뒤집기", "해계 → 자갑", true, true);

    /** 이 방향의 전체 60개 순서(간지 번호 0~59). */
    fun order(): List<Int> = if (reverse) (59 downTo 0).toList() else (0..59).toList()

    /** 이 방향으로 읽었을 때의 60개 문자열. */
    fun readings(): List<String> = order().map { Gapja.hangul(it, flip) }
}

enum class Script(val label: String) { HANGUL("한글"), HANJA("한자"), BOTH("병기") }

/**
 * 암송 범위.
 * - xuns: 포함할 순(0~5)
 * - start: 시작 간지(없으면 방향의 처음부터)
 * - a, b: A–B 구간(둘 다 있으면 xuns·start보다 우선, 방향을 따라 a에서 b까지, 필요하면 한 바퀴 넘어감)
 */
data class Range(
    val xuns: Set<Int> = (0..5).toSet(),
    val start: Int? = null,
    val a: Int? = null,
    val b: Int? = null,
)

object Sequencer {
    fun sequence(dir: Direction, range: Range): List<Int> {
        val ord = dir.order()
        if (range.a != null && range.b != null) {
            val ia = ord.indexOf(range.a)
            val ib = ord.indexOf(range.b)
            val out = ArrayList<Int>()
            var k = ia
            while (true) {
                out += ord[k]
                if (k == ib) break
                k = (k + 1) % 60
            }
            return out
        }
        val list = ord.filter { Gapja.xunOf(it) in range.xuns }
        val s = range.start
        if (s == null || s !in list) return list
        val p = list.indexOf(s)
        return list.drop(p) + list.take(p)
    }

    /** 방향 순서에서 position 바로 앞 k개(한 바퀴 순환). */
    fun before(dir: Direction, idx: Int, k: Int): List<Int> {
        val ord = dir.order()
        val p = ord.indexOf(idx)
        return (k downTo 1).map { ord[((p - it) % 60 + 60) % 60] }
    }

    fun next(dir: Direction, idx: Int): Int {
        val ord = dir.order()
        return ord[(ord.indexOf(idx) + 1) % 60]
    }
}
