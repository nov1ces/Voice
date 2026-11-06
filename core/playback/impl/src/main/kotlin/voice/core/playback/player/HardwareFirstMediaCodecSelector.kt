package voice.core.playback.player

import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

internal class HardwareFirstMediaCodecSelector(
  private val delegate: MediaCodecSelector = MediaCodecSelector.DEFAULT,
) : MediaCodecSelector {

  override fun getDecoderInfos(
    mimeType: String,
    requiresSecureDecoder: Boolean,
    requiresTunnelingDecoder: Boolean,
  ): List<MediaCodecInfo> {
    return delegate
      .getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
      .sortedByDescending { it.hardwareAccelerated }
  }
}
