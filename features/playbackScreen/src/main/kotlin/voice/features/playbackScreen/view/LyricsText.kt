package voice.features.playbackScreen.view

import android.icu.text.BreakIterator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.isActive
import voice.core.ui.animationsEnabled
import voice.core.ui.bookText
import voice.features.playbackScreen.lyrics.LyricLine
import voice.features.playbackScreen.lyrics.LyricSegment
import voice.features.playbackScreen.lyrics.LyricsDocument
import voice.features.playbackScreen.lyrics.interpolatedLyricsPosition
import voice.features.playbackScreen.lyrics.lyricSegmentProgress

@Composable
internal fun rememberLyricsPosition(
  lyrics: LyricsDocument,
  samplePositionMs: Long,
  playing: Boolean,
  speed: Float,
  continuousLyrics: Boolean = true,
): State<Long> {
  val position = remember(lyrics) { mutableLongStateOf(samplePositionMs) }
  val sample =
    remember(lyrics, samplePositionMs, playing, speed, continuousLyrics) {
      PositionSample(samplePositionMs, System.nanoTime(), playing, speed)
    }
  val animate = remember(lyrics) { lyrics.lines.any { it.segments.isNotEmpty() } }
  val enabled = animationsEnabled() && continuousLyrics
  LaunchedEffect(sample, animate, enabled) {
    fun currentPosition(): Long =
      if (enabled)
        interpolatedLyricsPosition(
          sample.positionMs,
          sample.timeNanos,
          System.nanoTime(),
          sample.playing,
          sample.speed,
        )
      else sample.positionMs
    position.longValue = currentPosition()
    if (playing && animate && enabled) {
      while (isActive) {
        withFrameNanos { position.longValue = currentPosition() }
      }
    }
  }
  return position
}

@Composable
internal fun LyricText(
  line: LyricLine,
  active: Boolean,
  position: State<Long>,
  onFollow: () -> Unit,
  onLineClick: (Long) -> Unit,
  modifier: Modifier = Modifier,
  continuousLyrics: Boolean = true,
) {
  val colors = MaterialTheme.colorScheme
  val drawing = remember(line) { mutableStateOf<LyricDrawing?>(null) }
  val sentenceHighlight = active && (!continuousLyrics || line.segments.isEmpty())
  Text(
    text = line.text.ifBlank { "\u00a0" },
    style = MaterialTheme.typography.headlineMedium.bookText(),
    fontWeight = if (sentenceHighlight) FontWeight.Bold else FontWeight.Medium,
    color = if (sentenceHighlight) colors.onSurface else colors.onSurfaceVariant,
    onTextLayout = { layout ->
      drawing.value = LyricDrawing(layout, line)
    },
    modifier =
      modifier
        .fillMaxWidth()
        .semantics { selected = active }
        .clickable {
          onFollow()
          onLineClick(line.timeMs)
        }
        .padding(vertical = 8.dp)
        .drawWithContent {
          drawContent()
          if (continuousLyrics && active && line.segments.isNotEmpty()) {
            val value = drawing.value
            if (value != null) {
              val now = position.value
              if (now >= line.timeMs && (line.endTimeMs == null || now < line.endTimeMs)) {
                val reveal = Path()
                for (segment in value.segments) {
                  val progress = lyricSegmentProgress(segment.timing, now)
                  if (progress >= 1F) {
                    reveal.addPath(segment.fullPath)
                  } else if (progress > 0F) {
                    for (bounds in lyricRevealRects(segment.glyphs, progress)) {
                      reveal.addRect(bounds)
                    }
                  }
                }
                clipPath(reveal) { drawText(value.layout, color = colors.onSurface) }
              }
            }
          }
        },
  )
}

private fun segmentDrawings(line: LyricLine, layout: TextLayoutResult): List<SegmentDrawing> {
  if (line.segments.isEmpty()) return emptyList()
  val characters = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(line.text) }
  return line.segments.map { segment ->
    val glyphs = mutableListOf<LyricGlyph>()
    var offset = segment.startIndex
    while (offset < segment.endIndex) {
      val next =
        characters
          .following(offset)
          .takeIf { it != BreakIterator.DONE }
          ?.coerceAtMost(segment.endIndex) ?: segment.endIndex
      var bounds = layout.getBoundingBox(offset)
      for (index in offset + 1 until next) {
        val box = layout.getBoundingBox(index)
        bounds =
          Rect(
            minOf(bounds.left, box.left),
            minOf(bounds.top, box.top),
            maxOf(bounds.right, box.right),
            maxOf(bounds.bottom, box.bottom),
          )
      }
      if (bounds.width > 0F) {
        glyphs +=
          LyricGlyph(bounds, layout.getBidiRunDirection(offset) == ResolvedTextDirection.Rtl)
      }
      offset = next
    }
    SegmentDrawing(
      segment,
      layout.getPathForRange(segment.startIndex, segment.endIndex),
      glyphs,
    )
  }
}

private data class PositionSample(
  val positionMs: Long,
  val timeNanos: Long,
  val playing: Boolean,
  val speed: Float,
)

private class LyricDrawing(val layout: TextLayoutResult, line: LyricLine) {
  val segments: List<SegmentDrawing> by
    lazy(LazyThreadSafetyMode.NONE) {
      segmentDrawings(line, layout)
    }
}

private data class SegmentDrawing(
  val timing: LyricSegment,
  val fullPath: Path,
  val glyphs: List<LyricGlyph>,
)
