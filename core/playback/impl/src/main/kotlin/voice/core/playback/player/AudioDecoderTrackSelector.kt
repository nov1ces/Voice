package voice.core.playback.player

import android.content.Context
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import voice.core.playback.AudioDecoderMode
import voice.core.playback.AudioDecoderModeStore
import voice.core.playback.di.PlaybackScope

@Inject
@SingleIn(PlaybackScope::class)
class AudioDecoderTrackSelector(
  context: Context,
  modeStore: AudioDecoderModeStore,
  scope: CoroutineScope,
) : DefaultTrackSelector(context) {

  @Volatile
  internal var ffmpegEnabled = runBlocking { modeStore.data.first() } == AudioDecoderMode.Ffmpeg
    private set

  init {
    scope.launch {
      modeStore.data.collect { mode ->
        val enabled = mode == AudioDecoderMode.Ffmpeg
        if (ffmpegEnabled != enabled) {
          ffmpegEnabled = enabled
          invalidate()
        }
      }
    }
  }
}
