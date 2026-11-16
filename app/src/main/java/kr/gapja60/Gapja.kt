package kr.gapja60

/** 육십갑자 기본 데이터와 네 가지 암송 방향. 안드로이드 의존성이 없어 단위 테스트로 전수 검사한다. */
object Gapja {
    val STEMS = listOf("갑", "을", "병", "정", "무", "기", "경", "신", "임", "계")
    val STEMS_H = listOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
    val BRANCHES = listOf("자", "축", "인", "묘", "진", "사", "오", "미", "신", "유", "술", "해")
    val BRANCHES_H = listOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")
    val XUN = listOf("갑자순", "갑술순", "갑신순", "갑오순", "갑진순", "갑인순")

    /** 0~59 → 한글 간지 */
    fun hangul(i: Int): String = STEMS[i % 10] + BRANCHES[i % 12]

    fun hanja(i: Int): String = STEMS_H[i % 10] + BRANCHES_H[i % 12]

    /** 6×10 표에서의 가로 줄(순, 0~5) */
    fun xunOf(i: Int): Int = i / 10

    /** 6×10 표에서의 세로 열(천간, 0~9) */
    fun colOf(i: Int): Int = i % 10
}

/**
 * 6×10 표(가로 한 줄 = 한 순, 세로 한 열 = 같은 천간) 기준 네 방향.
 * - 순행: 가로로 갑자 → 계해
 * - 역행: 가로로 계해 → 갑자
 * - 순행 뒤집기: 세로로 위→아래. 갑 열(갑자·갑술·갑신·갑오·갑진·갑인) → 을 열 → … → 계 열
 * - 역행 뒤집기: 세로로 아래→위. 갑 열(갑인·갑진·갑오·갑신·갑술·갑자) → 을 열 → … → 계 열
 */
enum class Direction(val label: String, val sample: String, val reverse: Boolean, val vertical: Boolean) {
    FORWARD("순행", "갑자 → 을축 …", false, false),
    BACKWARD("역행", "계해 → 임술 …", true, false),
    FORWARD_FLIP("순행 뒤집기", "갑자 → 갑술 …", false, true),
    BACKWARD_FLIP("역행 뒤집기", "갑인 → 갑진 …", true, true);

    /** 이 방향의 전체 60개 순서(간지 번호 0~59). */
    fun order(): List<Int> = when {
        !vertical && !reverse -> (0..59).toList()
        !vertical && reverse -> (59 downTo 0).toList()
        !reverse -> (0..9).flatMap { c -> (0..5).map { r -> c + 10 * r } }
        else -> (0..9).flatMap { c -> (5 downTo 0).map { r -> c + 10 * r } }
    }

    /** 구간 묶음: 가로 방향은 6순, 세로 방향은 천간 10열 */
    fun groupOf(i: Int): Int = if (vertical) Gapja.colOf(i) else Gapja.xunOf(i)
    val groupCount: Int get() = if (vertical) 10 else 6

    /** 이 방향으로 읽었을 때의 60개 문자열. */
    fun readings(): List<String> = order().map { Gapja.hangul(it) }
}

enum class Script(val label: String) { HANGUL("한글"), HANJA("한자"), BOTH("병기") }

/**
 * 암송 범위.
 * - groups: 포함할 묶음(가로 방향은 순 0~5, 세로 방향은 천간 열 0~9)
 * - start: 시작 간지(없으면 방향의 처음부터)
 * - a, b: A–B 구간(둘 다 있으면 xuns·start보다 우선, 방향을 따라 a에서 b까지, 필요하면 한 바퀴 넘어감)
 */
data class Range(
    val groups: Set<Int> = (0..9).toSet(),
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
        val list = ord.filter { dir.groupOf(it) in range.groups }
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
