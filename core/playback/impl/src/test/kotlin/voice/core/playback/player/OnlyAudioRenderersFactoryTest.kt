package voice.core.playback.player

import android.os.Handler
import android.os.Looper
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertIs
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import voice.core.playback.AudioDecoderMode
import voice.core.playback.AudioDecoderModeStore
import voice.core.playback.MemoryDataStore

@RunWith(AndroidJUnit4::class)
class OnlyAudioRenderersFactoryTest {

  private val scope = TestScope()

  @Test
  fun ffmpegPrecedesAndroidAudioDecoder() = scope.runTest {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val selector =
      AudioDecoderTrackSelector(
        context,
        AudioDecoderModeStore(MemoryDataStore(AudioDecoderMode.Ffmpeg)),
        backgroundScope,
      )
    val renderers =
      OnlyAudioRenderersFactory(context, selector)
        .createRenderers(
          Handler(Looper.getMainLooper()),
          mockk(relaxed = true),
          mockk(relaxed = true),
          mockk(relaxed = true),
          mockk(relaxed = true),
        )

    assertIs<FfmpegModeRenderer>(renderers[0])
    assertIs<MediaCodecAudioRenderer>(renderers[1])
  }
}
