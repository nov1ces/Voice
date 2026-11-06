package voice.core.playback.player

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.metadata.MetadataOutput
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.video.VideoRendererEventListener
import dev.zacsweers.metro.Inject

@Inject
class OnlyAudioRenderersFactory(
  context: Context,
  private val audioDecoderTrackSelector: AudioDecoderTrackSelector,
) : DefaultRenderersFactory(context) {

  init {
    setExtensionRendererMode(EXTENSION_RENDERER_MODE_PREFER)
    setEnableDecoderFallback(true)
    setMediaCodecSelector(HardwareFirstMediaCodecSelector())
  }

  override fun buildAudioRenderers(
    context: Context,
    extensionRendererMode: Int,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    audioSink: AudioSink,
    eventHandler: Handler,
    eventListener: AudioRendererEventListener,
    out: ArrayList<Renderer>,
  ) {
    super.buildAudioRenderers(
      context,
      extensionRendererMode,
      mediaCodecSelector,
      enableDecoderFallback,
      audioSink,
      eventHandler,
      eventListener,
      out,
    )
    out.replaceAll { renderer ->
      if (renderer is FfmpegAudioRenderer) {
        FfmpegModeRenderer(renderer, audioDecoderTrackSelector)
      } else {
        renderer
      }
    }
  }

  override fun buildVideoRenderers(
    context: Context,
    extensionRendererMode: Int,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler,
    eventListener: VideoRendererEventListener,
    allowedVideoJoiningTimeMs: Long,
    out: ArrayList<Renderer>,
  ) {}

  override fun buildTextRenderers(
    context: Context,
    output: TextOutput,
    outputLooper: Looper,
    extensionRendererMode: Int,
    out: ArrayList<Renderer>,
  ) {}

  override fun buildMetadataRenderers(
    context: Context,
    output: MetadataOutput,
    outputLooper: Looper,
    extensionRendererMode: Int,
    out: ArrayList<Renderer>,
  ) {}

  override fun buildCameraMotionRenderers(
    context: Context,
    extensionRendererMode: Int,
    out: ArrayList<Renderer>,
  ) {}

  override fun buildMiscellaneousRenderers(
    context: Context,
    eventHandler: Handler,
    extensionRendererMode: Int,
    out: ArrayList<Renderer>,
  ) {}
}
