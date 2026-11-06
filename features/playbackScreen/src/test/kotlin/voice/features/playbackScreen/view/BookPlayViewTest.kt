package voice.features.playbackScreen.view

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.strings.R
import voice.core.ui.VoiceTheme
import voice.features.playbackScreen.BookPlayViewState
import voice.features.playbackScreen.lyrics.LyricLine
import voice.features.playbackScreen.lyrics.LyricsDocument
import voice.features.playbackScreen.lyrics.parseLyrics

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w1400dp-h1400dp")
class BookPlayViewTest {

  @get:Rule val compose = createComposeRule()
  @get:Rule val files = TemporaryFolder()

  private val context = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun `lyrics can open only after loading and close on a chapter without lyrics`() {
    val first = files.newFile("first.mp3")
    val second = files.newFile("second.mp3")
    files.newFile("first.lrc").writeText("[00:00.00]第一句\n[00:10.00]第二句")
    var state by mutableStateOf(viewState.copy(playing = false, lyricsChapterId = null))
    compose.setContent { VoiceTheme { Player(state) } }
    val lyricsLabel = context.getString(R.string.playback_lyrics)

    compose.onNodeWithContentDescription(lyricsLabel).assertDoesNotExist()
    compose.runOnIdle { state = state.copy(lyricsChapterId = ChapterId(first.toUri())) }
    compose.waitUntil(timeoutMillis = 10000) {
      compose
        .onAllNodes(androidx.compose.ui.test.hasContentDescription(lyricsLabel))
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    compose.onNodeWithContentDescription(lyricsLabel).performClick()
    compose.onNodeWithText("第一句").assertIsDisplayed()

    compose.runOnIdle { state = state.copy(lyricsChapterId = ChapterId(second.toUri())) }

    compose.onNodeWithText("第一句").assertDoesNotExist()
    compose.onNodeWithContentDescription(lyricsLabel).assertDoesNotExist()
    compose
      .onNodeWithContentDescription(context.getString(R.string.playback_action_play))
      .assertIsDisplayed()
  }

  @Test
  fun `compact lyrics keep dialogue and transport visible with large fonts`() {
    var window by mutableStateOf(DpSize(384.dp, 240.dp))
    val lyrics = LyricsDocument(listOf(LyricLine(0L, "广播剧台词")))
    compose.setContent {
      CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2F)) {
        VoiceTheme {
          Box(Modifier.requiredSize(window).testTag(WINDOW)) {
            LyricsContent(
              lyrics = lyrics,
              viewState = viewState.copy(playing = false),
              bookId = BookId("book"),
              clock = { 0F },
              contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 64.dp),
              onLineClick = {},
              onSeek = {},
              onPlayClick = {},
              onRewindClick = {},
              onFastForwardClick = {},
            )
          }
        }
      }
    }

    listOf(DpSize(384.dp, 240.dp), DpSize(384.dp, 300.dp), DpSize(640.dp, 360.dp)).forEach { size ->
      compose.runOnIdle { window = size }
      compose.onNodeWithText("广播剧台词").assertIsDisplayed()
      val bounds = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
      listOf(
          R.string.playback_action_rewind,
          R.string.playback_action_play,
          R.string.playback_action_fast_forward,
        )
        .forEach { label ->
          val button = compose.onNodeWithContentDescription(context.getString(label))
          button.assertIsDisplayed()
          val control = button.getBoundsInRoot()
          assertTrue(control.left >= bounds.left && control.right <= bounds.right)
          assertTrue(control.top >= bounds.top && control.bottom <= bounds.bottom)
        }
    }
  }

  @Test
  fun `SPL displays timed words and clears the sentence highlight during silence`() {
    val lyrics =
      assertNotNull(
        parseLyrics("[00:01.000]<00:02.000>甲<00:03.000>乙[00:04.000]\n[00:10.000]次句[00:11.000]"),
      )
    var state by mutableStateOf(viewState.copy(playing = false, lyricsPositionMs = 1500L))
    compose.setContent {
      VoiceTheme {
        Box(Modifier.requiredSize(412.dp, 915.dp)) {
          LyricsContent(
            lyrics = lyrics,
            viewState = state,
            bookId = BookId("book"),
            clock = { 0F },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(),
            onLineClick = { state = state.copy(lyricsPositionMs = it) },
            onSeek = {},
            onPlayClick = {},
            onRewindClick = {},
            onFastForwardClick = {},
          )
        }
      }
    }

    compose.onNodeWithText("甲乙").assertIsDisplayed().assertIsSelected()
    compose.runOnIdle { state = state.copy(lyricsPositionMs = 2500L) }
    compose.onNodeWithText("甲乙").assertIsDisplayed().assertIsSelected()
    compose.runOnIdle { state = state.copy(lyricsPositionMs = 3500L) }
    compose.onNodeWithText("甲乙").assertIsDisplayed().assertIsSelected()
    compose.runOnIdle { state = state.copy(lyricsPositionMs = 4000L) }
    compose.onNodeWithText("甲乙").assertIsDisplayed().assertIsNotSelected()
    compose.runOnIdle { state = state.copy(lyricsPositionMs = 10500L) }
    compose.onNodeWithText("次句").assertIsDisplayed().assertIsSelected()
    compose.onNodeWithText("次句").performClick()
    compose.runOnIdle { assertEquals(10000L, state.lyricsPositionMs) }
  }

  @Test
  @GraphicsMode(GraphicsMode.Mode.NATIVE)
  fun `following centers short and wrapped sentences and keeps tall beginnings readable`() {
    val paragraph = "A lantern moves beside a quiet river while the wind rests. ".repeat(4)
    val tallParagraph =
      "The path continues beyond the garden and returns beside the old bridge. ".repeat(30)
    val lyrics =
      LyricsDocument(
        listOf(
          LyricLine(1000L, "Opening sentence", 2000L),
          LyricLine(3000L, paragraph, 4000L),
          LyricLine(4000L, tallParagraph, 4500L),
          LyricLine(5000L, "Final sentence", 6000L),
        ),
      )
    var window by mutableStateOf(DpSize(412.dp, 915.dp))
    var fontScale by mutableFloatStateOf(1F)
    var state by mutableStateOf(viewState.copy(playing = false, lyricsPositionMs = 1000L))
    compose.setContent {
      CompositionLocalProvider(
        LocalDensity provides Density(LocalDensity.current.density, fontScale),
      ) {
        VoiceTheme {
          Box(Modifier.requiredSize(window)) {
            LyricsContent(
              lyrics = lyrics,
              viewState = state,
              bookId = BookId("book"),
              clock = { 0F },
              contentPadding = androidx.compose.foundation.layout.PaddingValues(),
              onLineClick = { state = state.copy(lyricsPositionMs = it) },
              onSeek = {},
              onPlayClick = {},
              onRewindClick = {},
              onFastForwardClick = {},
            )
          }
        }
      }
    }

    fun assertCentered(text: String) {
      val viewport = compose.onNode(hasScrollAction()).getBoundsInRoot()
      val row = compose.onNodeWithText(text).getBoundsInRoot()
      val delta =
        (row.top.value + row.bottom.value - viewport.top.value - viewport.bottom.value) / 2F
      assertTrue(kotlin.math.abs(delta) <= 2F)
    }

    assertCentered("Opening sentence")
    compose.runOnIdle { state = state.copy(lyricsPositionMs = 3000L) }
    assertCentered(paragraph)
    compose.runOnIdle { state = state.copy(lyricsPositionMs = 5000L) }
    assertCentered("Final sentence")
    compose.runOnIdle {
      window = DpSize(640.dp, 360.dp)
      fontScale = 2F
    }
    assertCentered("Final sentence")
    compose.runOnIdle { state = state.copy(lyricsPositionMs = 4000L) }
    val viewport = compose.onNode(hasScrollAction()).getBoundsInRoot()
    val coordinates =
      compose.onNodeWithText(tallParagraph).fetchSemanticsNode().layoutInfo.coordinates
    val height = with(compose.density) { coordinates.size.height.toDp().value }
    assertTrue(height > viewport.bottom.value - viewport.top.value)
    val top = with(compose.density) { coordinates.positionInRoot().y.toDp().value }
    assertTrue(top >= viewport.top.value && top <= viewport.top.value + 16F)
  }

  @Test
  fun `long lyrics keep a small visible window and resume following after manual scrolling`() {
    val count = 24000
    val lyrics =
      LyricsDocument(
        List(count) {
          LyricLine(it * 150L + 5000L, "台词 $it", endTimeMs = it * 150L + 5100L)
        },
      )
    var state by mutableStateOf(viewState.copy(playing = false, lyricsPositionMs = 5000L))
    compose.setContent {
      VoiceTheme {
        Box(Modifier.requiredSize(412.dp, 915.dp)) {
          LyricsContent(
            lyrics = lyrics,
            viewState = state,
            bookId = BookId("book"),
            clock = { 0F },
            contentPadding = androidx.compose.foundation.layout.PaddingValues(),
            onLineClick = { state = state.copy(lyricsPositionMs = it) },
            onSeek = {},
            onPlayClick = {},
            onRewindClick = {},
            onFastForwardClick = {},
          )
        }
      }
    }

    compose.onNodeWithText("台词 0").assertIsDisplayed().assertIsSelected()
    compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
    compose.onNodeWithText(context.getString(R.string.playback_lyrics_follow)).assertIsDisplayed()
    compose.runOnIdle { state = state.copy(lyricsPositionMs = (count - 1) * 150L + 5000L) }
    compose.onNodeWithText("台词 ${count - 1}").assertDoesNotExist()
    compose.onNodeWithText(context.getString(R.string.playback_lyrics_follow)).performClick()
    compose.onNodeWithText("台词 ${count - 1}").assertIsDisplayed().assertIsSelected()
    val rendered =
      compose
        .onAllNodes(androidx.compose.ui.test.hasText("台词", substring = true))
        .fetchSemanticsNodes()
    assertTrue(rendered.size < 80)
    compose.runOnIdle { state = state.copy(lyricsPositionMs = (count - 1) * 150L + 5125L) }
    compose.onNodeWithText("台词 ${count - 1}").assertIsDisplayed().assertIsNotSelected()
    compose.onNodeWithText("台词 0").assertDoesNotExist()
    compose.runOnIdle { state = state.copy(lyricsPositionMs = 0L) }
    compose.onNodeWithText("台词 0").assertIsDisplayed().assertIsNotSelected()
    compose.onNodeWithText("台词 0").performClick()
    compose.onNodeWithText("台词 0").assertIsDisplayed().assertIsSelected()
  }

  @Composable
  private fun Player(state: BookPlayViewState) {
    BookPlayView(
      viewState = state,
      bookId = BookId(files.root.toUri()),
      onPlayClick = {},
      onRewindClick = {},
      onFastForwardClick = {},
      onSeek = {},
      onSleepTimerClick = {},
      onBookmarkClick = {},
      onBookmarkLongClick = {},
      onSpeedChangeClick = {},
      onSkipSilenceClick = {},
      onVolumeBoostClick = {},
      onSkipToNext = {},
      onSkipToPrevious = {},
      onCloseClick = {},
      onBookActionsClick = {},
      onCurrentChapterClick = {},
      onJumpBack = {},
      onJumpBackExpire = {},
    )
  }

  @Test
  fun `transport and every control that's shown stay inside the window`() {
    var window by mutableStateOf(DpSize(412.dp, 915.dp))
    var fontScale by mutableFloatStateOf(1F)
    // the seek bar wave never goes idle while playing
    compose.mainClock.autoAdvance = false
    compose.setContent {
      CompositionLocalProvider(
        LocalDensity provides Density(LocalDensity.current.density, fontScale),
      ) {
        VoiceTheme {
          Box(Modifier.requiredSize(window).testTag(WINDOW)) {
            BookPlayView(
              viewState = viewState,
              bookId = BookId("book"),
              onPlayClick = {},
              onRewindClick = {},
              onFastForwardClick = {},
              onSeek = {},
              onSleepTimerClick = {},
              onBookmarkClick = {},
              onBookmarkLongClick = {},
              onSpeedChangeClick = {},
              onSkipSilenceClick = {},
              onVolumeBoostClick = {},
              onSkipToNext = {},
              onSkipToPrevious = {},
              onCloseClick = {},
              onBookActionsClick = {},
              onCurrentChapterClick = {},
              onJumpBack = {},
              onJumpBackExpire = {},
            )
          }
        }
      }
    }

    listOf(1F, 2F).forEach { scale ->
      listOf(
          DpSize(412.dp, 915.dp),
          DpSize(360.dp, 640.dp),
          // split screen panes on a phone
          DpSize(384.dp, 400.dp),
          DpSize(384.dp, 300.dp),
          DpSize(384.dp, 240.dp),
          DpSize(915.dp, 412.dp),
          DpSize(640.dp, 360.dp),
          DpSize(700.dp, 240.dp),
          DpSize(1280.dp, 800.dp),
          DpSize(800.dp, 1280.dp),
          DpSize(1920.dp, 1200.dp),
        )
        .forEach { size ->
          window = size
          fontScale = scale
          compose.mainClock.advanceTimeBy(2_000)

          val windowBounds = compose.onNodeWithTag(WINDOW).getBoundsInRoot()
          val shown = compose.onAllNodes(hasClickAction().and(isPlaced)).fetchSemanticsNodes()
          listOf(
              R.string.playback_action_rewind,
              R.string.playback_action_pause,
              R.string.playback_action_fast_forward,
            )
            .forEach { transport ->
              val id =
                compose
                  .onNodeWithContentDescription(context.getString(transport))
                  .fetchSemanticsNode()
                  .id
              assertTrue(shown.any { it.id == id }, "$size at ${scale}x")
            }
          shown.forEach { node ->
            // unclipped, so a control scrolled out of view counts as outside
            val coordinates = node.layoutInfo.coordinates
            val bounds =
              with(compose.density) {
                Rect(coordinates.positionInRoot(), coordinates.size.toSize()).let {
                  DpRect(it.left.toDp(), it.top.toDp(), it.right.toDp(), it.bottom.toDp())
                }
              }
            val inside =
              bounds.left >= windowBounds.left &&
                bounds.right <= windowBounds.right &&
                bounds.top >= windowBounds.top &&
                bounds.bottom <= windowBounds.bottom
            assertTrue(inside, "$size at ${scale}x: ${node.config}")
          }
        }
    }
  }

  private val isPlaced = SemanticsMatcher("is placed") { it.layoutInfo.isPlaced }

  private val viewState =
    BookPlayViewState(
      chapterName = "The Signal",
      showPreviousNextButtons = true,
      cover = null,
      duration = 42.minutes,
      playedTime = 17.minutes,
      playing = true,
      skipSilence = false,
      sleepTimerState = BookPlayViewState.SleepTimerViewState.Disabled,
      title = "Echoes of Tomorrow",
      author = "Daniel Hartwell",
      playbackSpeed = 1F,
      volumeBoostActive = false,
      chapterNumber = 5,
      chapterCount = 12,
      bookPlayedTime = 4.hours + 12.minutes,
      bookDuration = 9.hours + 30.minutes,
      chapterSegments = List(12) { 1F / 12 },
      skipSeconds = 20,
      jumpBack = null,
    )
}

private const val WINDOW = "window"
