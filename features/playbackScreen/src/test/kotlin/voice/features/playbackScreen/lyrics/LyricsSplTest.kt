package voice.features.playbackScreen.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LyricsSplTest {

  @Test
  fun `SPL timestamp widths preserve millisecond precision`() {
    listOf(
        "[103:3.405678]甲" to 6_183_405L,
        "[3:2.5]甲" to 182_500L,
        "[1:2.02]甲" to 62_020L,
        "[1:2.000001]甲" to 62_000L,
        "[999:59.999999]甲" to 59_999_999L,
      )
      .forEach { (source, expected) ->
        assertEquals(expected, assertNotNull(parseLyrics(source)).lines.single().timeMs)
      }
  }

  @Test
  fun `inline and standalone sentence endings have the same timing`() {
    val inline = assertNotNull(parseLyrics("[00:01.000]甲[00:02.000]"))
    val separate = assertNotNull(parseLyrics("[00:01.000]甲\n[00:02.000]"))

    assertEquals(inline, separate)
    assertEquals("甲", inline.lines.single().text)
    assertEquals(2000L, inline.lines.single().endTimeMs)
    assertEquals(0, inline.currentLineIndex(1999L))
    assertEquals(-1, inline.currentLineIndex(2000L))
    assertEquals(0, inline.followLineIndex(2500L))
    assertNull(parseLyrics("[00:01.000]\n[00:02.000]"))
  }

  @Test
  fun `an ending at the next start creates no empty or translated line`() {
    val lyrics = assertNotNull(parseLyrics("[00:01.000]甲\n[00:02.000]\n[00:02.000]乙"))

    assertEquals(listOf("甲", "乙"), lyrics.lines.map { it.text })
    assertEquals(2000L, lyrics.lines.first().endTimeMs)
    assertEquals(1, lyrics.currentLineIndex(2000L))
  }

  @Test
  fun `only an implicit sentence end uses the next start`() {
    val implicit = assertNotNull(parseLyrics("[00:01.000]甲\n[00:03.000]乙"))
    val explicit = assertNotNull(parseLyrics("[00:01.000]甲[00:05.000]\n[00:03.000]乙[00:04.000]"))

    assertEquals(3000L, implicit.lines.first().endTimeMs)
    assertEquals(5000L, explicit.lines.first().endTimeMs)
    assertEquals(1, explicit.currentLineIndex(3500L))
    assertEquals(-1, explicit.currentLineIndex(4500L))
    assertEquals(1, explicit.followLineIndex(4500L))
  }

  @Test
  fun `untimed translation lines follow the current sentence`() {
    val lyrics =
      assertNotNull(parseLyrics("[00:01.000]甲[00:03.000]\nTranslation\n別の訳\n[00:05.000]乙\nNext"))

    assertEquals(listOf("甲\nTranslation\n別の訳", "乙\nNext"), lyrics.lines.map { it.text })
    assertEquals(3000L, lyrics.lines.first().endTimeMs)
    assertEquals(-1, lyrics.currentLineIndex(4000L))
  }

  @Test
  fun `translations with matching times need not be adjacent`() {
    val lyrics = assertNotNull(parseLyrics("[00:01.000]甲\n[00:05.000]乙\n[00:01.000]Translation"))

    assertEquals(listOf("甲\nTranslation", "乙"), lyrics.lines.map { it.text })
  }

  @Test
  fun `a timed translation inherits the primary sentence ending`() {
    val lyrics =
      assertNotNull(parseLyrics("[00:01.000]甲[00:03.000]\n[00:01.000]Translation\n[00:05.000]乙"))

    assertEquals("甲\nTranslation", lyrics.lines.first().text)
    assertEquals(3000L, lyrics.lines.first().endTimeMs)
    assertEquals(-1, lyrics.currentLineIndex(4000L))
  }

  @Test
  fun `square and angle segment markers have the same meaning`() {
    val square = assertNotNull(parseLyrics("[00:01.000]甲[00:02.000]乙[00:04.000]"))
    val angle = assertNotNull(parseLyrics("[00:01.000]甲<00:02.000>乙[00:04.000]"))

    assertEquals(square, angle)
    val line = square.lines.single()
    assertEquals("甲乙", line.text)
    assertEquals(listOf("甲"), line.activeText(1500L))
    assertEquals(listOf("乙"), line.activeText(2500L))
    assertEquals(emptyList(), line.activeText(4000L))
  }

  @Test
  fun `a delayed first segment does not start with the sentence`() {
    val lyrics = assertNotNull(parseLyrics("[00:01.000]<00:02.000>甲<00:03.000>乙[00:04.000]"))
    val line = lyrics.lines.single()

    assertEquals(0, lyrics.currentLineIndex(1500L))
    assertEquals(emptyList(), line.activeText(1500L))
    assertEquals(listOf("甲"), line.activeText(2500L))
    assertEquals(listOf("乙"), line.activeText(3500L))
    assertEquals(-1, lyrics.currentLineIndex(4000L))
  }

  @Test
  fun `invalid inner times are removed while their text stays visible`() {
    val lyrics =
      assertNotNull(
        parseLyrics("[00:01.000]甲<00:00.500>乙<00:02.000>丙<00:01.500>丁<00:09.000>戊[00:04.000]"),
      )
    val line = lyrics.lines.single()

    assertEquals("甲乙丙丁戊", line.text)
    assertEquals(listOf("甲乙"), line.activeText(1500L))
    assertEquals(listOf("丙丁戊"), line.activeText(2500L))
  }

  @Test
  fun `repeated sentences validate inner times against each start`() {
    val lyrics = assertNotNull(parseLyrics("[00:01.000][00:10.000]甲[00:03.000]乙[00:04.000]"))

    assertEquals(listOf(1000L, 10000L), lyrics.lines.map { it.timeMs })
    assertEquals(listOf("甲乙", "甲乙"), lyrics.lines.map { it.text })
    assertEquals(listOf("甲"), lyrics.lines.first().activeText(2000L))
    assertEquals(listOf("甲乙"), lyrics.lines.last().activeText(11000L))
  }

  @Test
  fun `offset applies to sentence ends and delayed word boundaries`() {
    val lyrics =
      assertNotNull(parseLyrics("[offset:500]\n[00:01.000]<00:02.000>甲<00:03.000>乙[00:04.000]"))
    val line = lyrics.lines.single()

    assertEquals(1500L, line.timeMs)
    assertEquals(4500L, line.endTimeMs)
    assertEquals(emptyList(), line.activeText(2000L))
    assertEquals(listOf("甲"), line.activeText(2750L))
    assertEquals(listOf("乙"), line.activeText(3750L))
  }

  @Test
  fun `timed translations keep segment ranges in the combined text`() {
    val lyrics =
      assertNotNull(
        parseLyrics("[00:01.000]甲<00:02.000>乙[00:03.000]\n[00:01.000]AB<00:02.000>CD[00:03.000]"),
      )
    val line = lyrics.lines.single()

    assertEquals("甲乙\nABCD", line.text)
    assertEquals(listOf("甲", "AB"), line.activeText(1500L))
    assertEquals(listOf("乙", "CD"), line.activeText(2500L))
  }

  @Test
  fun `segment ranges keep emoji intact`() {
    val line = assertNotNull(parseLyrics("[00:01.000]🙂<00:02.000>甲[00:03.000]")).lines.single()

    assertEquals(listOf("🙂"), line.activeText(1500L))
    assertEquals(listOf("甲"), line.activeText(2500L))
  }

  @Test
  fun `recognized awlrc containers do not replace visible text or break translation adjacency`() {
    val lyrics =
      assertNotNull(parseLyrics("[00:01.000]甲\n [AWLRC:LrC:QQ,other:QQ==] \nTranslation"))

    assertEquals("甲\nTranslation", lyrics.lines.single().text)
    assertNull(parseLyrics("[awlrc:lrc:QQ==,awlrc:Qg==]"))
    assertEquals(
      "[awlrc:lrc:QQ==]",
      assertNotNull(parseLyrics("[00:01.000][awlrc:lrc:QQ==]")).lines.single().text,
    )
  }

  @Test
  fun `unrecognized awlrc text follows ordinary translation rules`() {
    listOf("[awlrc:lrc:]", "[awlrc:other:QQ==]", "[awlrc:lrc:Q$]", "[awlrc:lrc:QQ===]").forEach {
      field ->
      val lyrics = assertNotNull(parseLyrics("[00:01.000]甲\n$field"))
      assertEquals("甲\n$field", lyrics.lines.single().text)
    }
  }

  @Test
  fun `long SPL episodes keep sentence gaps and follow positions accurate`() {
    val count = 24000
    fun time(value: Long): String =
      "[${value / 60000}:${(value / 1000 % 60).toString().padStart(2, '0')}.${(value % 1000).toString().padStart(3, '0')}]"
    val source = buildString {
      repeat(count) { index ->
        val start = index * 150L
        append(time(start))
        append("台词 $index")
        append(time(start + 100L))
        append('\n')
      }
    }
    val lyrics = assertNotNull(parseLyrics(source))

    assertEquals(count, lyrics.lines.size)
    listOf(0, count - 1, count / 2, 1, count - 2).forEach { index ->
      assertEquals(index, lyrics.currentLineIndex(index * 150L + 99L))
      assertEquals(-1, lyrics.currentLineIndex(index * 150L + 125L))
      assertEquals(index, lyrics.followLineIndex(index * 150L + 125L))
    }
    assertTrue(lyrics.lines.none { '[' in it.text || ']' in it.text })
  }

  private fun LyricLine.activeText(positionMs: Long): List<String> =
    activeSegments(positionMs).map { text.substring(it.startIndex, it.endIndex) }
}
