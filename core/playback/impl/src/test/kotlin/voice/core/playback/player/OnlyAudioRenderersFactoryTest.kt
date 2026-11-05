package voice.core.playback.player

import android.os.Handler
import android.os.Looper
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertIs
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnlyAudioRenderersFactoryTest {

  @Test
  fun ffmpegPrecedesAndroidAudioDecoder() {
    val renderers =
      OnlyAudioRenderersFactory(ApplicationProvider.getApplicationContext())
        .createRenderers(
          Handler(Looper.getMainLooper()),
          mockk(relaxed = true),
          mockk(relaxed = true),
          mockk(relaxed = true),
          mockk(relaxed = true),
        )

    assertIs<FfmpegAudioRenderer>(renderers[0])
    assertIs<MediaCodecAudioRenderer>(renderers[1])
  }
}
