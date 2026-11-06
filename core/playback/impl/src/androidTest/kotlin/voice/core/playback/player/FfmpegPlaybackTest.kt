package voice.core.playback.player

import androidx.datastore.core.DataStore
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import voice.core.playback.AudioDecoderMode
import voice.core.playback.AudioDecoderModeStore

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
    val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    lateinit var player: ExoPlayer
    instrumentation.runOnMainSync {
      val context = instrumentation.context
      val mode = MutableStateFlow(AudioDecoderMode.Ffmpeg)
      val store =
        object : DataStore<AudioDecoderMode> {
          override val data: Flow<AudioDecoderMode> = mode

          override suspend fun updateData(
            transform: suspend (AudioDecoderMode) -> AudioDecoderMode,
          ): AudioDecoderMode {
            return transform(mode.value).also { mode.value = it }
          }
        }
      val selector = AudioDecoderTrackSelector(context, AudioDecoderModeStore(store), scope)
      player =
        ExoPlayer.Builder(context, OnlyAudioRenderersFactory(context, selector))
          .setTrackSelector(selector)
          .build()
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
      scope.cancel()
    }
  }
}
