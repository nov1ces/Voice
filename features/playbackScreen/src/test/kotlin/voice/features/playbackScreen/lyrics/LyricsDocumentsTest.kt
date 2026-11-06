package voice.features.playbackScreen.lyrics

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
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
import org.robolectric.Robolectric
import voice.core.data.BookId
import voice.core.data.ChapterId

@RunWith(AndroidJUnit4::class)
class LyricsDocumentsTest {

  @get:Rule val files = TemporaryFolder()

  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val reader = LyricsReader(context)

  @Test
  fun `single file books find sidecars when document IDs are opaque`() = runTest {
    val provider = provider()
    provider.folders["root"] = listOf(Node("folder", "Story", true))
    provider.folders["folder"] = listOf(Node("audio", "Episode.mp3"), Node("lyrics", "Episode.LRC"))
    val audio = uri("audio")

    val lyrics = assertNotNull(reader.read(BookId(audio), ChapterId(audio)))

    assertEquals("来自存储提供程序的台词", lyrics.lines.single().text)
    assertEquals(listOf("root", "folder"), provider.queries)
  }

  @Test
  fun `opaque document IDs prefer spl and fall back to lrc`() = runTest {
    val provider = provider()
    provider.folders["root"] = listOf(Node("folder", "Story", true))
    provider.folders["folder"] =
      listOf(Node("audio", "Episode.mp3"), Node("lrc", "episode.LRC"), Node("spl", "EPISODE.SpL"))
    val spl = files.newFile("preferred.spl").apply { writeText("[00:01.000]这是 SPL 台词[00:02.000]") }
    provider.sidecars["spl"] = spl
    provider.sidecars["lrc"] = provider.lyrics
    val audio = uri("audio")

    assertEquals(
      "这是 SPL 台词",
      assertNotNull(reader.read(BookId(audio), ChapterId(audio))).lines.single().text,
    )
    spl.writeText("invalid lyrics")
    assertEquals(
      "来自存储提供程序的台词",
      assertNotNull(reader.read(BookId(audio), ChapterId(audio))).lines.single().text,
    )
    provider.sidecars.remove("spl")
    assertEquals(
      "来自存储提供程序的台词",
      assertNotNull(reader.read(BookId(audio), ChapterId(audio))).lines.single().text,
    )
  }

  @Test
  fun `an inaccessible sibling folder does not hide the audio sidecar`() = runTest {
    val provider = provider()
    provider.folders["root"] =
      listOf(Node("folder", "Story", true), Node("denied", "Private", true))
    provider.folders["folder"] = listOf(Node("audio", "Episode.mp3"), Node("lyrics", "Episode.lrc"))
    val audio = uri("audio")

    assertNotNull(reader.read(BookId(audio), ChapterId(audio)))
    assertEquals(listOf("root", "denied", "folder"), provider.queries)
  }

  @Test
  fun `a verified audio folder without lyrics does not scan the library`() = runTest {
    val provider = provider()
    provider.folders["root"] =
      listOf(Node("root/episode.mp3", "Episode.mp3"), Node("other", "Other book", true))
    val audio = uri("root/episode.mp3")

    assertNull(reader.read(BookId(uri("root")), ChapterId(audio)))
    assertEquals(listOf("root"), provider.queries)
  }

  private fun provider(): TestDocumentsProvider {
    val provider = Robolectric.setupContentProvider(TestDocumentsProvider::class.java, AUTHORITY)
    provider.lyrics =
      files.newFile("episode.lrc").apply {
        writeText("[00:00.00]来自存储提供程序的台词")
      }
    return provider
  }

  private fun uri(id: String): Uri =
    DocumentsContract.buildDocumentUriUsingTree(
      DocumentsContract.buildTreeDocumentUri(AUTHORITY, "root"),
      id,
    )

  data class Node(val id: String, val name: String, val directory: Boolean = false)

  class TestDocumentsProvider : ContentProvider() {
    val folders = mutableMapOf<String, List<Node>>()
    val queries = mutableListOf<String>()
    lateinit var lyrics: File
    val sidecars = mutableMapOf<String, File>()

    override fun onCreate(): Boolean = true

    override fun query(
      uri: Uri,
      projection: Array<out String>?,
      selection: String?,
      selectionArgs: Array<out String>?,
      sortOrder: String?,
    ): Cursor {
      val id = DocumentsContract.getDocumentId(uri)
      queries += id
      val nodes = folders[id] ?: throw SecurityException("Folder is unavailable")
      return MatrixCursor(projection).apply {
        nodes.forEach { node ->
          addRow(
            arrayOf(
              node.id,
              node.name,
              if (node.directory) DocumentsContract.Document.MIME_TYPE_DIR
              else "application/octet-stream",
            ),
          )
        }
      }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor =
      ParcelFileDescriptor.open(
        if (sidecars.isEmpty()) lyrics
        else
          sidecars[DocumentsContract.getDocumentId(uri)] ?: throw java.io.FileNotFoundException(),
        ParcelFileDescriptor.MODE_READ_ONLY,
      )

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
      uri: Uri,
      values: ContentValues?,
      selection: String?,
      selectionArgs: Array<out String>?,
    ): Int = 0
  }
}

private const val AUTHORITY = "voice.test.lyrics.documents"
