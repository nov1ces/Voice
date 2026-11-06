package voice.features.playbackScreen.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsProgressTest {

  @Test
  fun `ordinary sentence timestamps provide an estimated sweep`() {
    val lyrics = kotlin.test.assertNotNull(parseLyrics("[00:01.00]Fictional dialogue\n[00:03.00]"))
    val line = lyrics.lines.single()
    assertEquals(emptyList(), line.segments)
    val estimated = line.sweepSegments().single()
    assertEquals(LyricSegment(1000L, 3000L, 0, line.text.length), estimated)
    assertEquals(0.5F, lyricSegmentProgress(estimated, 2000L))
  }

  @Test
  fun `precise word times take priority over sentence estimates`() {
    val precise = listOf(LyricSegment(1200L, 1500L, 0, 1), LyricSegment(1500L, 2000L, 1, 2))
    val line = LyricLine(1000L, "甲乙", 2000L, precise)
    assertEquals(precise, line.sweepSegments(10000L))
  }

  @Test
  fun `the final sentence can use the physical audio duration`() {
    val line = LyricLine(5000L, "Last fictional sentence")
    assertEquals(emptyList(), line.sweepSegments())
    assertEquals(emptyList(), line.sweepSegments(5000L))
    assertEquals(emptyList(), line.sweepSegments(4000L))
    assertEquals(listOf(LyricSegment(5000L, 9000L, 0, line.text.length)), line.sweepSegments(9000L))
    assertEquals(7000L, line.copy(endTimeMs = 7000L).sweepSegments(9000L).single().endTimeMs)
  }

  private val segment = LyricSegment(1000L, 2000L, 0, 2)

  @Test
  fun `a word fills continuously and stays filled after its end`() {
    listOf(
        500L to 0F,
        1000L to 0F,
        1250L to 0.25F,
        1500L to 0.5F,
        1750L to 0.75F,
        2000L to 1F,
        5000L to 1F,
      )
      .forEach { (position, progress) ->
        assertEquals(progress, lyricSegmentProgress(segment, position))
      }
  }

  @Test
  fun `a delayed first word stays unfilled until its own start`() {
    val delayed = LyricSegment(2000L, 3000L, 0, 1)
    assertEquals(0F, lyricSegmentProgress(delayed, 1500L))
    assertEquals(0F, lyricSegmentProgress(delayed, 2000L))
    assertEquals(0.5F, lyricSegmentProgress(delayed, 2500L))
  }

  @Test
  fun `a final word without an end becomes filled at its start`() {
    val open = segment.copy(endTimeMs = null)
    assertEquals(0F, lyricSegmentProgress(open, 999L))
    assertEquals(1F, lyricSegmentProgress(open, 1000L))
    assertEquals(1F, lyricSegmentProgress(open, 5000L))
  }

  @Test
  fun `playback interpolation follows the chosen speed`() {
    val sampleTime = 1_000_000_000L
    val now = sampleTime + 250_000_000L
    assertEquals(1250L, interpolatedLyricsPosition(1000L, sampleTime, now, true, 1F))
    assertEquals(1125L, interpolatedLyricsPosition(1000L, sampleTime, now, true, 0.5F))
    assertEquals(1500L, interpolatedLyricsPosition(1000L, sampleTime, now, true, 2F))
  }

  @Test
  fun `pausing freezes at the player sample`() {
    assertEquals(1000L, interpolatedLyricsPosition(1000L, 0L, 500_000_000L, false, 2F))
  }

  @Test
  fun `new samples correct both forward and backward seeks`() {
    val time = 2_000_000_000L
    assertEquals(60100L, interpolatedLyricsPosition(60000L, time, time + 100_000_000L, true, 1F))
    assertEquals(
      1000L,
      interpolatedLyricsPosition(1000L, time + 100_000_000L, time + 100_000_000L, true, 1F),
    )
  }

  @Test
  fun `stale samples cannot advance the local clock without a bound`() {
    assertEquals(2000L, interpolatedLyricsPosition(1000L, 0L, 10_000_000_000L, true, 1F))
    assertEquals(3000L, interpolatedLyricsPosition(1000L, 0L, 10_000_000_000L, true, 2F))
    assertEquals(1000L, interpolatedLyricsPosition(1000L, 500_000_000L, 0L, true, 1F))
  }

  @Test
  fun `invalid speeds use normal playback speed`() {
    listOf(0F, -1F, Float.NaN, Float.POSITIVE_INFINITY).forEach { speed ->
      assertEquals(1250L, interpolatedLyricsPosition(1000L, 0L, 250_000_000L, true, speed))
    }
  }

  @Test
  fun `clock arithmetic never wraps a valid position to negative`() {
    assertEquals(
      Long.MAX_VALUE,
      interpolatedLyricsPosition(Long.MAX_VALUE - 2L, 0L, 500_000_000L, true, 2F),
    )
  }
}
