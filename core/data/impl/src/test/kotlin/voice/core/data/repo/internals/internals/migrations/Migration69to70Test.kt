package voice.core.data.repo.internals.internals.migrations

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.runner.RunWith
import voice.core.data.repo.internals.AppDb
import voice.core.data.repo.internals.allMigrations
import voice.core.data.repo.internals.getLong
import voice.core.data.repo.internals.getString
import voice.core.data.repo.internals.getStringOrNull
import voice.core.data.repo.internals.mapRows

@RunWith(AndroidJUnit4::class)
class Migration69to70Test {

  @Rule
  @JvmField
  val helper =
    MigrationTestHelper(
      InstrumentationRegistry.getInstrumentation(),
      AppDb::class.java,
    )

  @Test
  fun `word highlighting starts enabled and the library keeps its data`() {
    val dbName = "testDb"
    helper.createDatabase(dbName, 69).use { db ->
      db.insert(
        "content2",
        SQLiteDatabase.CONFLICT_FAIL,
        ContentValues().apply {
          put("id", "content://dune")
          put("playbackSpeed", 1F)
          put("skipSilence", 0)
          put("isActive", 1)
          put("lastPlayedAt", "1970-01-01T00:00:00Z")
          put("addedAt", "1970-01-01T00:00:00Z")
          put("chapters", "[\"content://dune/1.mp3\"]")
          put("currentChapter", "content://dune/1.mp3")
          put("positionInChapter", 1234L)
          put("name", "Dune")
          put("author", "Herbert")
        },
      )
      db.insert(
        "customMetadata",
        SQLiteDatabase.CONFLICT_FAIL,
        ContentValues().apply {
          put("bookId", "content://dune")
          put("name", "My Dune")
          put("author", "Frank Herbert")
          put("cover", "/covers/dune.webp")
          put("series", "Dune Chronicles")
          put("part", "1")
          put("hidden", 1)
        },
      )
      db.insert(
        "bookmark2",
        SQLiteDatabase.CONFLICT_FAIL,
        ContentValues().apply {
          put("bookId", "content://dune")
          put("chapterId", "content://dune/1.mp3")
          put("title", "The desert")
          put("time", 2345L)
          put("addedAt", "1970-01-01T00:00:00Z")
          put("id", "bookmark")
        },
      )
    }

    val migrated = helper.runMigrationsAndValidate(dbName, 70, true, *allMigrations())

    assertEquals(
      listOf(
        listOf("My Dune", "Frank Herbert", "/covers/dune.webp", "Dune Chronicles", "1", 1L, 1L),
      ),
      migrated.query("SELECT * FROM customMetadata").mapRows {
        listOf(
          getStringOrNull("name"),
          getStringOrNull("author"),
          getStringOrNull("cover"),
          getStringOrNull("series"),
          getStringOrNull("part"),
          getLong("hidden"),
          getLong("continuousLyrics"),
        )
      },
    )
    assertEquals(
      listOf(listOf("content://dune", "Dune", 1234L)),
      migrated.query("SELECT * FROM content2").mapRows {
        listOf(getString("id"), getString("name"), getLong("positionInChapter"))
      },
    )
    assertEquals(
      listOf(listOf("bookmark", "content://dune", "The desert", 2345L)),
      migrated.query("SELECT * FROM bookmark2").mapRows {
        listOf(getString("id"), getString("bookId"), getStringOrNull("title"), getLong("time"))
      },
    )
    migrated.update(
      "customMetadata",
      SQLiteDatabase.CONFLICT_FAIL,
      ContentValues().apply { put("continuousLyrics", 0) },
      "bookId = ?",
      arrayOf("content://dune"),
    )
    assertEquals(
      listOf(0L),
      migrated.query("SELECT continuousLyrics FROM customMetadata").mapRows {
        getLong("continuousLyrics")
      },
    )
  }
}
