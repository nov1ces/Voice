package voice.core.playback

import androidx.datastore.core.DataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import voice.core.datastore.VoiceDataStoreFactory

@SingleIn(AppScope::class)
class AudioDecoderModeStore(store: DataStore<AudioDecoderMode>) :
  DataStore<AudioDecoderMode> by store {

  @Inject
  constructor(
    factory: VoiceDataStoreFactory,
  ) : this(
    factory.create(
      serializer = AudioDecoderMode.serializer(),
      defaultValue = AudioDecoderMode.Ffmpeg,
      fileName = "audioDecoderMode",
    ),
  )
}
