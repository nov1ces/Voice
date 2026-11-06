package voice.features.playbackScreen.lyrics

import android.content.Context
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import voice.core.data.BookId
import voice.core.data.ChapterId

@RunWith(AndroidJUnit4::class)
class LyricsReaderTest {

  @get:Rule val folder = TemporaryFolder()

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val reader = LyricsReader(context)

  @Test
  fun `reads only the matching sidecar in the audio folder`() = runTest {
    val audio = folder.newFile("广播剧 第01集.mp3")
    folder.newFile("广播剧 第02集.lrc").writeText("[00:00.00]错误的章节")
    folder.newFile("广播剧 第01集.LRC").writeText("[00:00.00]正确的章节")

    val lyrics = assertNotNull(read(audio))

    assertEquals("正确的章节", lyrics.lines.single().text)
  }

  @Test
  fun `missing empty and invalid lyrics leave the feature unavailable`() = runTest {
    val audio = folder.newFile("episode.mp3")

    assertNull(read(audio))
    val lyrics = folder.newFile("episode.lrc")
    assertNull(read(audio))
    lyrics.writeText("[ti:Episode]\n[00:00.00]\n[00:03.00] ")
    assertNull(read(audio))
    lyrics.writeText("unrelated text")
    assertNull(read(audio))
  }

  @Test
  fun `a different chapter never reuses the previous lyric document`() = runTest {
    val first = folder.newFile("first.mp3")
    val second = folder.newFile("second.mp3")
    folder.newFile("first.lrc").writeText("[00:00.00]第一集")

    assertNotNull(read(first))
    assertNull(read(second))
  }

  private suspend fun read(audio: File): LyricsDocument? =
    reader.read(BookId(folder.root.toUri()), ChapterId(audio.toUri()))
}
