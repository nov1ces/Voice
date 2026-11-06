package voice.core.playback.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.Renderer
import androidx.media3.test.utils.FakeMediaPeriod
import androidx.media3.test.utils.FakeMediaSource
import androidx.media3.test.utils.FakeRenderer
import androidx.media3.test.utils.FakeTimeline
import androidx.media3.test.utils.TestExoPlayerBuilder
import androidx.media3.test.utils.robolectric.TestPlayerRunHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import voice.core.playback.AudioDecoderMode
import voice.core.playback.AudioDecoderModeStore
import voice.core.playback.MemoryDataStore

@RunWith(AndroidJUnit4::class)
class AudioDecoderTrackSelectorTest {

  private val scope = TestScope()
  private val store = MemoryDataStore(AudioDecoderMode.Ffmpeg)

  @Test
  fun `saved system mode selects the system renderer before playback`() = scope.runTest {
    store.updateData { AudioDecoderMode.System }
    val selector = selector()
    val software = FakeRenderer(C.TRACK_TYPE_AUDIO)
    val system = FakeRenderer(C.TRACK_TYPE_AUDIO)
    val player =
      TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
        .setTrackSelector(selector)
        .setRenderers(FfmpegModeRenderer(software, selector), system)
        .build()
    try {
      player.setMediaSource(source())
      player.prepare()
      TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)

      assertEquals(Renderer.STATE_DISABLED, software.state)
      assertEquals(Renderer.STATE_ENABLED, system.state)
    } finally {
      player.release()
    }
  }

  @Test
  fun `switching decoders preserves the paused position speed silence setting and queue`() =
    scope.runTest {
      val selector = selector()
      val software = FakeRenderer(C.TRACK_TYPE_AUDIO)
      val system = FakeRenderer(C.TRACK_TYPE_AUDIO)
      val player =
        TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
          .setTrackSelector(selector)
          .setRenderers(FfmpegModeRenderer(software, selector), system)
          .build()
      try {
        player.setMediaSources(listOf(source(), source()), 0, 5_000)
        player.playbackParameters = PlaybackParameters(1.5F, 1.1F)
        player.skipSilenceEnabled = true
        player.prepare()
        TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
        assertEquals(Renderer.STATE_ENABLED, software.state)

        store.updateData { AudioDecoderMode.System }
        runCurrent()
        TestPlayerRunHelper.runUntilPendingCommandsAreFullyHandled(player)

        assertEquals(Renderer.STATE_DISABLED, software.state)
        assertEquals(Renderer.STATE_ENABLED, system.state)
        assertEquals(5_000L, player.currentPosition)
        assertEquals(0, player.currentMediaItemIndex)
        assertEquals(2, player.mediaItemCount)
        assertEquals(PlaybackParameters(1.5F, 1.1F), player.playbackParameters)
        assertEquals(true, player.skipSilenceEnabled)
        assertEquals(false, player.playWhenReady)

        store.updateData { AudioDecoderMode.Ffmpeg }
        runCurrent()
        TestPlayerRunHelper.runUntilPendingCommandsAreFullyHandled(player)

        assertEquals(Renderer.STATE_ENABLED, software.state)
        assertEquals(Renderer.STATE_DISABLED, system.state)
        assertEquals(5_000L, player.currentPosition)
        assertEquals(false, player.playWhenReady)
      } finally {
        player.release()
      }
    }

  @Test
  fun `switching decoders does not pause playback`() = scope.runTest {
    val selector = selector()
    val software = FakeRenderer(C.TRACK_TYPE_AUDIO)
    val system = FakeRenderer(C.TRACK_TYPE_AUDIO)
    val player =
      TestExoPlayerBuilder(ApplicationProvider.getApplicationContext())
        .setTrackSelector(selector)
        .setRenderers(FfmpegModeRenderer(software, selector), system)
        .build()
    try {
      player.setMediaSource(source(), 5_000)
      player.prepare()
      TestPlayerRunHelper.runUntilPlaybackState(player, Player.STATE_READY)
      player.play()
      store.updateData { AudioDecoderMode.System }
      runCurrent()
      TestPlayerRunHelper.runUntilPendingCommandsAreFullyHandled(player)

      assertEquals(Renderer.STATE_DISABLED, software.state)
      assertEquals(Renderer.STATE_STARTED, system.state)
      assertEquals(true, player.playWhenReady)
      assertTrue(player.currentPosition >= 5_000)
      assertTrue(player.currentPosition < 6_000)
    } finally {
      player.release()
    }
  }

  private fun selector() =
    AudioDecoderTrackSelector(
      ApplicationProvider.getApplicationContext(),
      AudioDecoderModeStore(store),
      scope.backgroundScope,
    )

  private fun source(): FakeMediaSource {
    val timeline =
      FakeTimeline(
        FakeTimeline.TimelineWindowDefinition.Builder()
          .setDurationUs(60_000_000)
          .setSeekable(true)
          .build(),
      )
    return FakeMediaSource.Builder()
      .setTimeline(timeline)
      .setTrackDataFactory(
        FakeMediaPeriod.TrackDataFactory.samplesWithRateDurationAndKeyframeInterval(
          0,
          100F,
          60_000_000,
          1,
        ),
      )
      .setFormats(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build())
      .build()
  }
}
