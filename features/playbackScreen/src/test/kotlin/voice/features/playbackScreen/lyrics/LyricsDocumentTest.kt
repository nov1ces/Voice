package voice.features.playbackScreen.lyrics

import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException

class LyricsDocumentTest {

  @Test
  fun `multiple timestamps repeat a line and fractions use milliseconds`() {
    val lyrics = assertNotNull(parseLyrics("[00:01.1]甲\n[00:02.12]乙\n[00:03.123][00:04.00]丙"))

    assertEquals(listOf(1100L, 2120L, 3123L, 4000L), lyrics.lines.map { it.timeMs })
    assertEquals(listOf("甲", "乙", "丙", "丙"), lyrics.lines.map { it.text })
  }

  @Test
  fun `offset applies to every timestamp even when declared last`() {
    val lyrics = assertNotNull(parseLyrics("[00:01.00]甲\n[00:02.00]乙\n[offset:+250]"))

    assertEquals(listOf(1250L, 2250L), lyrics.lines.map { it.timeMs })
  }

  @Test
  fun `negative offset starts early lines at zero`() {
    val lyrics = assertNotNull(parseLyrics("[offset:-1500]\n[00:01.00]甲\n[00:03.00]乙"))

    assertEquals(listOf(0L, 1500L), lyrics.lines.map { it.timeMs })
  }

  @Test
  fun `metadata and malformed timestamps do not create lyrics`() {
    listOf(
        "",
        "\uFEFF[ti:广播剧]\n[ar:配音演员]\n[offset:100]",
        "[00:72.00]不合法\n[invalid]文本\n普通文本",
        "[999999999999999999999999:00.00]溢出",
      )
      .forEach { assertNull(parseLyrics(it)) }
  }

  @Test
  fun `byte order mark does not hide the first timestamp`() {
    val lyrics = assertNotNull(parseLyrics("\uFEFF[00:00.00]开场"))

    assertEquals("开场", lyrics.lines.single().text)
    assertEquals(0L, lyrics.lines.single().timeMs)
  }

  @Test
  fun `common Chinese lyric encodings preserve dialogue`() {
    val source = "[00:00.00]角色：你好，世界。"

    listOf(Charsets.UTF_8, Charsets.UTF_16, Charset.forName("GB18030")).forEach { charset ->
      val lyrics = assertNotNull(parseLyrics(decodeLyrics(source.toByteArray(charset))))
      assertEquals("角色：你好，世界。", lyrics.lines.single().text)
    }
  }

  @Test
  fun `bilingual lines share one cue and empty cues preserve pauses`() {
    val lyrics =
      assertNotNull(
        parseLyrics("[00:01.00]你好\n[00:01.00]Hello\n[00:02.00]\n[00:04.00]下一句"),
      )

    assertEquals(3, lyrics.lines.size)
    assertEquals("你好\nHello", lyrics.lines.first().text)
    assertEquals("", lyrics.lines[1].text)
    assertEquals(1, lyrics.currentLineIndex(3000L))
    assertNull(parseLyrics("[00:00.00]\n[00:02.00] "))
  }

  @Test
  fun `oversized cues cannot trigger unbounded text layout`() {
    assertNotNull(parseLyrics("[00:00.00]${"字".repeat(4096)}"))
    assertNull(parseLyrics("[00:00.00]${"字".repeat(4097)}"))
    assertNull(parseLyrics("[00:00.00]${"甲".repeat(2050)}\n[00:00.00]${"乙".repeat(2050)}"))
  }

  @Test
  fun `parsing a cancelled chapter stops without returning a document`() {
    assertFailsWith<CancellationException> {
      parseLyrics("[00:00.00]旧章节") { throw CancellationException() }
    }
  }

  @Test
  fun `lookup follows forward and backward seeks across a long episode`() {
    val lyrics = assertNotNull(parseLyrics("[60:00.00]片尾\n[00:05.00]开场\n[30:00.00]中场"))

    assertEquals(-1, lyrics.currentLineIndex(0L))
    assertEquals(0, lyrics.currentLineIndex(5000L))
    assertEquals(1, lyrics.currentLineIndex(1800000L))
    assertEquals(2, lyrics.currentLineIndex(3600000L))
    assertEquals(2, lyrics.currentLineIndex(Long.MAX_VALUE))
    assertEquals(0, lyrics.currentLineIndex(6000L))
    assertEquals(-1, lyrics.currentLineIndex(-1L))
  }

  @Test
  fun `large hour long lyrics retain every cue and seek correctly`() {
    val count = 24000
    val source = buildString {
      repeat(count) { index ->
        val timeMs = index * 150L
        val minutes = timeMs / 60000
        val seconds = timeMs / 1000 % 60
        val milliseconds = timeMs % 1000
        append("[$minutes:${seconds.toString().padStart(2, '0')}.")
        append(milliseconds.toString().padStart(3, '0'))
        append("]角色：这是第 $index 句广播剧台词。\n")
      }
    }
    val lyrics = assertNotNull(parseLyrics(source))

    assertEquals(count, lyrics.lines.size)
    listOf(0, count - 1, count / 2, 1, count - 2).forEach { index ->
      assertEquals(index, lyrics.currentLineIndex(index * 150L))
      assertEquals(index, lyrics.currentLineIndex(index * 150L + 149L))
    }
    assertEquals("角色：这是第 ${count - 1} 句广播剧台词。", lyrics.lines.last().text)
  }
}
