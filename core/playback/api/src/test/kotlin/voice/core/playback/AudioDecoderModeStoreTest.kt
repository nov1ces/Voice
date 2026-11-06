package voice.core.playback

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.runner.RunWith
import voice.core.datastore.VoiceDataStoreFactory

@RunWith(AndroidJUnit4::class)
class AudioDecoderModeStoreTest {

  @Test
  fun `software is the default and the system choice is saved to disk`() = runTest {
    val application: Application = ApplicationProvider.getApplicationContext()
    val store = AudioDecoderModeStore(VoiceDataStoreFactory(Json.Default, application))
    assertEquals(AudioDecoderMode.Ffmpeg, store.data.first())

    store.updateData { AudioDecoderMode.System }

    val file = File(application.filesDir, "datastore/audioDecoderMode")
    assertEquals(AudioDecoderMode.System, Json.decodeFromString<AudioDecoderMode>(file.readText()))
  }
}
