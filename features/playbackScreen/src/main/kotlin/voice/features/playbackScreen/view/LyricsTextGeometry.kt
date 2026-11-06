package voice.features.playbackScreen.view

import androidx.compose.ui.geometry.Rect

internal fun lyricCenterScrollOffset(
  itemHeight: Int,
  viewportStartOffset: Int,
  viewportEndOffset: Int,
): Int {
  val viewportHeight = viewportEndOffset - viewportStartOffset
  val visibleHeight = itemHeight.coerceIn(0, viewportHeight.coerceAtLeast(0))
  return -viewportStartOffset - (viewportHeight - visibleHeight) / 2
}

internal data class LyricGlyph(val bounds: Rect, val rtl: Boolean)

internal fun lyricRevealRects(glyphs: List<LyricGlyph>, progress: Float): List<Rect> {
  if (!progress.isFinite()) return emptyList()
  var remaining = glyphs.sumOf { it.bounds.width.toDouble() }.toFloat() * progress.coerceIn(0F, 1F)
  if (remaining <= 0F) return emptyList()
  return buildList {
    for (glyph in glyphs) {
      if (remaining <= 0F) break
      val bounds = glyph.bounds
      val width = remaining.coerceAtMost(bounds.width)
      add(
        if (glyph.rtl) bounds.copy(left = bounds.right - width)
        else bounds.copy(right = bounds.left + width),
      )
      remaining -= bounds.width
    }
  }
}
