package voice.core.playback.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RendererCapabilities

internal class FfmpegModeRenderer(
  renderer: Renderer,
  selector: AudioDecoderTrackSelector,
) : Renderer by renderer {

  private val capabilities =
    object : RendererCapabilities by renderer.capabilities {
      override fun supportsFormat(format: Format): Int {
        return if (selector.ffmpegEnabled) {
          renderer.capabilities.supportsFormat(format)
        } else {
          C.FORMAT_UNSUPPORTED_TYPE
        }
      }
    }

  override fun getCapabilities(): RendererCapabilities = capabilities
}
