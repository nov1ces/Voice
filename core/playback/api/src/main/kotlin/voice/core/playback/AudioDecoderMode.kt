package voice.core.playback

import kotlinx.serialization.Serializable

@Serializable
enum class AudioDecoderMode {
  Ffmpeg,
  System,
}
