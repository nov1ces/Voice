package voice.features.playbackScreen.view

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import voice.core.ui.VoiceTheme
import voice.features.playbackScreen.lyrics.parseLyrics

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LyricsTextTest {

  @get:Rule val compose = createComposeRule()

  @Test
  fun `ordinary LRC sentences fill continuously without word timestamps`() {
    val line = assertNotNull(parseLyrics("[00:00.00]甲乙\n[00:02.00]")).lines.single()
    assertEquals(emptyList(), line.segments)
    val position = mutableLongStateOf(0L)
    compose.setContent {
      VoiceTheme {
        Box(Modifier.requiredWidth(240.dp)) {
          LyricText(line, true, position, {}, {}, Modifier.fillMaxWidth().testTag(TEXT))
        }
      }
    }
    val node = compose.onNodeWithTag(TEXT)
    val before = node.captureToImage()
    compose.runOnIdle { position.longValue = 500L }
    val partial = node.captureToImage()
    compose.runOnIdle { position.longValue = 1000L }
    val halfway = node.captureToImage()
    assertTrue(differentPixels(before, partial) > 0)
    assertTrue(differentPixels(partial, halfway) > 0)
    assertEquals(before.width, halfway.width)
    assertEquals(before.height, halfway.height)
  }

  @Test
  fun `late audio duration updates the final sentence sweep without changing its layout`() {
    val line = assertNotNull(parseLyrics("[00:05.00]尾句")).lines.single()
    val position = mutableLongStateOf(6000L)
    val duration = mutableLongStateOf(7000L)
    compose.setContent {
      VoiceTheme {
        Box(Modifier.requiredWidth(240.dp)) {
          LyricText(
            line,
            true,
            position,
            {},
            {},
            Modifier.fillMaxWidth().testTag(TEXT),
            fallbackEndTimeMs = duration.longValue,
          )
        }
      }
    }
    val node = compose.onNodeWithTag(TEXT)
    val before = node.captureToImage()
    compose.runOnIdle { duration.longValue = 9000L }
    val after = node.captureToImage()
    assertTrue(differentPixels(before, after) > 0)
    assertEquals(before.width, after.width)
    assertEquals(before.height, after.height)
  }

  @Test
  fun `words fill partway without moving and completed words stay bright`() {
    val line = assertNotNull(parseLyrics("[00:00.000]甲<00:01.000>乙[00:02.000]")).lines.single()
    val position = mutableLongStateOf(0L)
    compose.setContent {
      VoiceTheme {
        Box(Modifier.requiredWidth(240.dp)) {
          LyricText(line, true, position, {}, {}, Modifier.fillMaxWidth().testTag(TEXT))
        }
      }
    }
    val node = compose.onNodeWithTag(TEXT)
    val before = node.captureToImage()
    compose.runOnIdle { position.longValue = 500L }
    val halfway = node.captureToImage()
    compose.runOnIdle { position.longValue = 1000L }
    val firstComplete = node.captureToImage()
    val layouts = mutableListOf<TextLayoutResult>()
    node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
      val _ = it(layouts)
    }
    val firstWidth = layouts.single().getBoundingBox(0).right.toInt().coerceAtLeast(1)
    compose.runOnIdle { position.longValue = 1500L }
    val secondHalf = node.captureToImage()

    assertEquals(before.width, secondHalf.width)
    assertEquals(before.height, secondHalf.height)
    assertTrue(differentPixels(before, halfway) > 0)
    assertTrue(differentPixels(halfway, firstComplete) > 0)
    assertTrue(differentPixels(firstComplete, secondHalf) > 0)
    assertEquals(0, differentPixels(firstComplete, secondHalf, firstWidth))
  }

  @Test
  fun `turning off word highlighting keeps the whole sentence bright`() {
    val line = assertNotNull(parseLyrics("[00:00.000]甲<00:01.000>乙[00:02.000]")).lines.single()
    val position = mutableLongStateOf(500L)
    val continuous = mutableStateOf(false)
    compose.setContent {
      VoiceTheme {
        Box(Modifier.requiredWidth(240.dp)) {
          LyricText(
            line,
            true,
            position,
            {},
            {},
            Modifier.fillMaxWidth().testTag(TEXT),
            continuousLyrics = continuous.value,
          )
        }
      }
    }
    val node = compose.onNodeWithTag(TEXT)
    val wholeSentence = node.captureToImage()
    compose.runOnIdle { position.longValue = 1500L }
    assertEquals(0, differentPixels(wholeSentence, node.captureToImage()))

    compose.runOnIdle { continuous.value = true }
    val enabled = node.captureToImage()
    compose.runOnIdle { position.longValue = 500L }
    assertTrue(differentPixels(enabled, node.captureToImage()) > 0)
  }

  @Test
  fun `wrapped translations emoji and RTL text render with a timed fill`() {
    val line =
      assertNotNull(
          parseLyrics(
            "[00:00.000]Alpha<00:01.000> Beta[00:02.000]\n" +
              "[00:00.000]你好<00:01.000>🙂[00:02.000]\n" +
              "[00:00.000]مرحبا<00:01.000> بكم[00:02.000]",
          ),
        )
        .lines
        .single()
    val position = mutableLongStateOf(0L)
    compose.setContent {
      VoiceTheme {
        Box(Modifier.requiredWidth(160.dp)) {
          LyricText(line, true, position, {}, {}, Modifier.fillMaxWidth().testTag(TEXT))
        }
      }
    }
    val node = compose.onNodeWithTag(TEXT)
    val before = node.captureToImage()
    compose.runOnIdle { position.longValue = 750L }
    val after = node.captureToImage()
    val layouts = mutableListOf<TextLayoutResult>()
    node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
      val _ = it(layouts)
    }

    assertTrue(layouts.single().lineCount >= 3)
    assertEquals(before.width, after.width)
    assertEquals(before.height, after.height)
    assertTrue(differentPixels(before, after) > 0)
  }

  private fun differentPixels(
    first: ImageBitmap,
    second: ImageBitmap,
    width: Int = first.width,
  ): Int {
    val left = first.toPixelMap()
    val right = second.toPixelMap()
    var count = 0
    repeat(first.height) { y ->
      repeat(width.coerceAtMost(first.width)) { x ->
        if (left[x, y] != right[x, y]) count++
      }
    }
    return count
  }
}

private const val TEXT = "timedLyricsText"
