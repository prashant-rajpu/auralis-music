package com.auralis.app.data.local

import com.google.gson.JsonParser
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Runs the real v4 → v5 migration against a real SQLite database holding real rows.
 *
 * Same approach and the same reasoning as [Migration3To4Test], one version on: by v4 an install
 * has downloads, playlists, likes and history in it, and adding a chat table must not disturb any
 * of them. The schema-identity check Room performs at open time is [MigrationSqlTest]'s job.
 */
class Migration4To5Test {

    private lateinit var connection: Connection
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        dbFile = File.createTempFile("auralis-v4-", ".db").apply { delete() }
        connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        createVersionFourDatabase()
    }

    @After
    fun tearDown() {
        connection.close()
        dbFile.delete()
    }

    /** Built from the exported v4 schema, so this is not a hand-copied approximation of it. */
    private fun createVersionFourDatabase() {
        val schema = JsonParser
            .parseString(File("schemas/com.auralis.app.data.local.AuralisDatabase/4.json").readText())
            .asJsonObject.getAsJsonObject("database")

        connection.createStatement().use { statement ->
            for (element in schema.getAsJsonArray("entities")) {
                val entity = element.asJsonObject
                val table = entity.get("tableName").asString
                statement.executeUpdate(entity.get("createSql").asString.replace("\${TABLE_NAME}", table))
                entity.getAsJsonArray("indices")?.forEach { index ->
                    statement.executeUpdate(
                        index.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}", table)
                    )
                }
            }
        }
    }

    /** A library worth losing: a download, a like, a playlist with a track in it. */
    private fun seedLibrary() {
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                "INSERT INTO tracks (id, title, artist, albumArtUrl, mediaUrl, durationMs, source, " +
                    "qualityBadge, syncedLyricsJson) VALUES ('yt_abc12345678', 'Saved Song', " +
                    "'Artist One', 'https://example.invalid/art.jpg', " +
                    "'/data/music/yt_abc12345678.mp3', 210000, 'Offline', 'Offline HQ', NULL)"
            )
            statement.executeUpdate("INSERT INTO liked_tracks (trackId, likedAtMs) VALUES ('yt_abc12345678', 10)")
            statement.executeUpdate(
                "INSERT INTO playlists (id, name, coverTrackId, createdAtMs, updatedAtMs, sortIndex) " +
                    "VALUES ('p1', 'Ours', NULL, 1, 1, 0)"
            )
            statement.executeUpdate(
                "INSERT INTO playlist_tracks (playlistId, trackId, position, addedAtMs) " +
                    "VALUES ('p1', 'yt_abc12345678', 0, 1)"
            )
        }
    }

    private fun migrate() {
        connection.createStatement().use { statement ->
            V5_STATEMENTS.forEach(statement::executeUpdate)
        }
    }

    private fun tableNames(): Set<String> {
        val names = mutableSetOf<String>()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT name FROM sqlite_master WHERE type = 'table'").use { rows ->
                while (rows.next()) names += rows.getString(1)
            }
        }
        return names
    }

    private fun countOf(table: String): Int =
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM $table").use { rows ->
                rows.next()
                rows.getInt(1)
            }
        }

    @Test
    fun migrationKeepsEverythingTheLibraryAlreadyHeld() {
        seedLibrary()
        migrate()

        assertEquals("Upgrading to v5 lost a downloaded track", 1, countOf("tracks"))
        assertEquals("Upgrading to v5 lost a like", 1, countOf("liked_tracks"))
        assertEquals("Upgrading to v5 lost a playlist", 1, countOf("playlists"))
        assertEquals("Upgrading to v5 lost a playlist's contents", 1, countOf("playlist_tracks"))
    }

    @Test
    fun migrationCreatesTheMessageTable() {
        migrate()
        assertTrue("together_messages missing after migration", tableNames().contains("together_messages"))
    }

    @Test
    fun migrationIsSafeToRunTwice() {
        seedLibrary()
        migrate()
        // Every statement is CREATE ... IF NOT EXISTS, so a retried upgrade must not throw.
        migrate()
        assertEquals(1, countOf("tracks"))
    }

    @Test
    fun theNewTableAcceptsTheWritesTheMailboxMakes() {
        migrate()
        connection.createStatement().use { statement ->
            statement.executeUpdate(
                "INSERT INTO together_messages (id, roomCode, senderId, senderName, fromMe, kind, " +
                    "payload, preview, atMs, pending, ciphertext) VALUES ('d1', 'AB2CD3', 'm1', " +
                    "'Her', 0, 'text', '{}', 'goodnight', 10, 0, NULL)"
            )
            // The outbox row: pending, and holding the bytes a retry would resend.
            statement.executeUpdate(
                "INSERT INTO together_messages (id, roomCode, senderId, senderName, fromMe, kind, " +
                    "payload, preview, atMs, pending, ciphertext) VALUES ('d2', 'AB2CD3', 'm2', " +
                    "'Me', 1, 'text', '{}', 'on my way', 11, 1, 'sealed')"
            )
        }
        assertEquals(2, countOf("together_messages"))
    }

    @Test
    fun aMessageArrivingTwiceIsStoredOnce() {
        migrate()
        connection.createStatement().use { statement ->
            val insert = "INSERT OR IGNORE INTO together_messages (id, roomCode, senderId, " +
                "senderName, fromMe, kind, payload, preview, atMs, pending, ciphertext) VALUES " +
                "('same', 'AB2CD3', 'm1', 'Her', 0, 'text', '{}', 'hi', 10, 0, NULL)"
            statement.executeUpdate(insert)
            // A rejoin replays the last fifty messages; the id is a digest of the ciphertext, so
            // the replayed copy collides with the live one instead of doubling the conversation.
            statement.executeUpdate(insert)
        }
        assertEquals(1, countOf("together_messages"))
    }
}
