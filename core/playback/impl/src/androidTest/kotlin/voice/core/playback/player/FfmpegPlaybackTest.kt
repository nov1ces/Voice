package voice.core.playback.player

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FfmpegPlaybackTest {

  @Test fun aacM4bUsesFfmpeg() = play("aac.m4b", "aac")

  @Test fun alacM4bUsesFfmpeg() = play("alac.m4b", "alac")

  private fun play(asset: String, codec: String) {
    assertTrue(FfmpegLibrary.isAvailable())
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val finished = CountDownLatch(1)
    val decoderName = AtomicReference<String>()
    val failure = AtomicReference<PlaybackException>()
    lateinit var player: ExoPlayer
    instrumentation.runOnMainSync {
      val context = instrumentation.context
      player = ExoPlayer.Builder(context, OnlyAudioRenderersFactory(context)).build()
      player.addAnalyticsListener(
        object : AnalyticsListener {
          override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            name: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
          ) {
            decoderName.set(name)
          }
        },
      )
      player.addListener(
        object : Player.Listener {
          override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) finished.countDown()
          }

          override fun onPlayerError(error: PlaybackException) {
            failure.set(error)
            finished.countDown()
          }
        },
      )
      player.setMediaItem(MediaItem.fromUri("asset:///$asset"))
      player.prepare()
      player.play()
    }
    try {
      assertTrue("Playback did not end", finished.await(20, TimeUnit.SECONDS))
      assertNull("Playback failed", failure.get())
      val name = decoderName.get().orEmpty()
      assertTrue("Unexpected decoder: $name", name.startsWith("ffmpeg") && name.endsWith("-$codec"))
    } finally {
      instrumentation.runOnMainSync { player.release() }
    }
  }
}
