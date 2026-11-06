package voice.core.playback.player

import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HardwareFirstMediaCodecSelectorTest {

  @Test
  fun `hardware codecs precede software codecs without removing fallbacks`() {
    val firstSoftware = codec("software.first", false)
    val firstHardware = codec("hardware.first", true)
    val secondHardware = codec("hardware.second", true)
    val secondSoftware = codec("software.second", false)
    val delegate =
      mockk<MediaCodecSelector> {
        every { getDecoderInfos(MimeTypes.AUDIO_AAC, false, false) } returns
          listOf(firstSoftware, firstHardware, secondHardware, secondSoftware)
      }

    val result =
      HardwareFirstMediaCodecSelector(delegate).getDecoderInfos(MimeTypes.AUDIO_AAC, false, false)

    assertEquals(listOf(firstHardware, secondHardware, firstSoftware, secondSoftware), result)
  }

  private fun codec(name: String, hardware: Boolean): MediaCodecInfo {
    return MediaCodecInfo.newInstance(
      name,
      MimeTypes.AUDIO_AAC,
      MimeTypes.AUDIO_AAC,
      null,
      hardware,
      !hardware,
      false,
      false,
      false,
    )
  }
}
