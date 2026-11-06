package voice.features.playbackScreen.view

import androidx.compose.ui.geometry.Rect
import kotlin.test.Test
import kotlin.test.assertEquals

class LyricsTextGeometryTest {

  @Test
  fun `a partially filled Chinese glyph reveals from the left`() {
    val glyphs = listOf(LyricGlyph(Rect(0F, 0F, 20F, 30F), false))
    assertEquals(listOf(Rect(0F, 0F, 10F, 30F)), lyricRevealRects(glyphs, 0.5F))
  }

  @Test
  fun `a partially filled RTL glyph reveals from the right`() {
    val glyphs = listOf(LyricGlyph(Rect(10F, 0F, 30F, 30F), true))
    assertEquals(listOf(Rect(20F, 0F, 30F, 30F)), lyricRevealRects(glyphs, 0.5F))
  }

  @Test
  fun `a wrapped word finishes the first line before the next line`() {
    val glyphs =
      listOf(
        LyricGlyph(Rect(0F, 0F, 20F, 30F), false),
        LyricGlyph(Rect(0F, 40F, 20F, 70F), false),
      )
    assertEquals(
      listOf(Rect(0F, 0F, 20F, 30F), Rect(0F, 40F, 10F, 70F)),
      lyricRevealRects(glyphs, 0.75F),
    )
  }

  @Test
  fun `mixed direction runs follow logical reading order`() {
    val glyphs =
      listOf(
        LyricGlyph(Rect(30F, 0F, 50F, 30F), true),
        LyricGlyph(Rect(0F, 0F, 20F, 30F), false),
      )
    assertEquals(
      listOf(Rect(30F, 0F, 50F, 30F), Rect(0F, 0F, 10F, 30F)),
      lyricRevealRects(glyphs, 0.75F),
    )
  }

  @Test
  fun `unstarted and completed words have stable clip regions`() {
    val glyphs = listOf(LyricGlyph(Rect(0F, 0F, 20F, 30F), false))
    assertEquals(emptyList(), lyricRevealRects(glyphs, 0F))
    assertEquals(listOf(glyphs.single().bounds), lyricRevealRects(glyphs, 1F))
    assertEquals(listOf(glyphs.single().bounds), lyricRevealRects(glyphs, 2F))
    assertEquals(emptyList(), lyricRevealRects(emptyList(), 1F))
  }
}
