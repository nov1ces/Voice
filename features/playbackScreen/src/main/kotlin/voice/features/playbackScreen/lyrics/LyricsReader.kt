package voice.features.playbackScreen.lyrics

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import voice.core.data.BookId
import voice.core.data.ChapterId
import voice.core.data.toUri

internal class LyricsReader(private val context: Context) {
  suspend fun read(bookId: BookId, chapterId: ChapterId): LyricsDocument? =
    withContext(Dispatchers.IO) {
      try {
        val audio = chapterId.toUri()
        val lyrics =
          when (audio.scheme) {
            "file" -> findFile(audio)
            "content" -> findDocument(bookId.toUri(), audio)
            else -> null
          } ?: return@withContext null
        val bytes =
          context.contentResolver.openInputStream(lyrics)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
              currentCoroutineContext().ensureActive()
              val count = input.read(buffer)
              if (count < 0) break
              if (output.size() > MAX_LYRICS_BYTES - count) return@withContext null
              output.write(buffer, 0, count)
            }
            output.toByteArray()
          } ?: return@withContext null
        currentCoroutineContext().ensureActive()
        val coroutineContext = currentCoroutineContext()
        parseLyrics(decodeLyrics(bytes)) { coroutineContext.ensureActive() }
      } catch (cancelled: CancellationException) {
        throw cancelled
      } catch (_: Exception) {
        null
      }
    }

  private fun findFile(audio: Uri): Uri? {
    val file = File(audio.path ?: return null)
    val expected = file.nameWithoutExtension + ".lrc"
    return file.parentFile
      ?.listFiles()
      ?.firstOrNull {
        it.isFile && it.name.equals(expected, ignoreCase = true)
      }
      ?.let { Uri.fromFile(it) }
  }

  private suspend fun findDocument(book: Uri, audio: Uri): Uri? {
    if (!DocumentsContract.isTreeUri(audio)) return null
    val audioId = DocumentsContract.getDocumentId(audio)
    // Some providers use paths as IDs. Verify the parent before using this fast path.
    if ('/' in audioId) {
      val parent =
        DocumentsContract.buildDocumentUriUsingTree(audio, audioId.substringBeforeLast('/'))
      findInFolder(parent, audioId)?.let {
        return it.lyrics
      }
    }
    val root =
      if (book != audio && book.authority == audio.authority && DocumentsContract.isTreeUri(book)) {
        book
      } else {
        DocumentsContract.buildDocumentUriUsingTree(
          audio,
          DocumentsContract.getTreeDocumentId(audio),
        )
      }
    val pending = ArrayDeque<Uri>()
    pending.add(root)
    val visited = mutableSetOf<String>()
    while (pending.isNotEmpty()) {
      currentCoroutineContext().ensureActive()
      val folder = pending.removeLast()
      val folderId =
        try {
          DocumentsContract.getDocumentId(folder)
        } catch (_: IllegalArgumentException) {
          DocumentsContract.getTreeDocumentId(folder)
        }
      if (!visited.add(folderId)) continue
      val children =
        try {
          children(folder, folderId)
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (_: Exception) {
          continue
        }
      val audioFile = children.firstOrNull { it.id == audioId }
      if (audioFile != null) return matchingLyrics(folder, children, audioFile.name)
      children
        .filter { it.directory }
        .forEach {
          pending.add(DocumentsContract.buildDocumentUriUsingTree(folder, it.id))
        }
    }
    return null
  }

  private suspend fun findInFolder(folder: Uri, audioId: String): FolderMatch? {
    return try {
      val children = children(folder, DocumentsContract.getDocumentId(folder))
      val audio = children.firstOrNull { it.id == audioId } ?: return null
      FolderMatch(matchingLyrics(folder, children, audio.name))
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      null
    }
  }

  private fun matchingLyrics(folder: Uri, children: List<Document>, audioName: String): Uri? {
    val expected = audioName.substringBeforeLast('.') + ".lrc"
    val lyrics = children.firstOrNull {
      !it.directory && it.name.equals(expected, ignoreCase = true)
    }
    return lyrics?.let { DocumentsContract.buildDocumentUriUsingTree(folder, it.id) }
  }

  private suspend fun children(folder: Uri, folderId: String): List<Document> {
    val result = mutableListOf<Document>()
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder, folderId)
    context.contentResolver.query(children, COLUMNS, null, null, null)?.use { cursor ->
      while (cursor.moveToNext()) {
        currentCoroutineContext().ensureActive()
        result +=
          Document(
            id = cursor.getString(0) ?: continue,
            name = cursor.getString(1) ?: continue,
            directory = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
          )
      }
    }
    return result
  }

  private data class FolderMatch(val lyrics: Uri?)

  private data class Document(val id: String, val name: String, val directory: Boolean)
}

internal fun decodeLyrics(bytes: ByteArray): String {
  if (bytes.size >= 2) {
    if (bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte())
      return bytes.toString(Charsets.UTF_16)
    if (bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte())
      return bytes.toString(Charsets.UTF_16)
  }
  return try {
    Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
  } catch (_: CharacterCodingException) {
    bytes.toString(Charset.forName("GB18030"))
  }
}

private const val MAX_LYRICS_BYTES = 32 * 1024 * 1024
private val COLUMNS =
  arrayOf(
    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
    DocumentsContract.Document.COLUMN_MIME_TYPE,
  )
