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
 * Runs the real v3 → v4 migration against a real SQLite database holding real rows.
 *
 * Room's own `MigrationTestHelper` needs an instrumented test and therefore a device, which CI does
 * not have. Driving the same SQL through `sqlite-jdbc` on the JVM checks the things that actually
 * break people's installs — does the SQL parse, do the tables appear, and are the downloads still
 * there afterwards — on every push instead of never.
 *
 * What this deliberately cannot check is Room's own schema-identity validation at open time.
 * [MigrationSqlTest] covers that by comparing the statements to the exported schema.
 */
class Migration3To4Test {

    private lateinit var connection: Connection
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        dbFile = File.createTempFile("auralis-v3-", ".db").apply { delete() }
        connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        createVersionThreeDatabase()
    }

    @After
    fun tearDown() {
        connection.close()
        dbFile.delete()
    }

    /** Builds a v3 database from the exported schema, so this is not a hand-copied approximation. */
    private fun createVersionThreeDatabase() {
        val schema = JsonParser
            .parseString(File("schemas/com.auralis.app.data.local.AuralisDatabase/3.json").readText())
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

    private fun seedDownloads() {
        connection.prepareStatement(
            "INSERT INTO tracks (id, title, artist, albumArtUrl, mediaUrl, durationMs, source, " +
                "qualityBadge, syncedLyricsJson) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        ).use { statement ->
            listOf(
                Triple("yt_abc12345678", "Saved Song", "Artist One"),
                Triple("local_42", "On Device", "Artist Two")
            ).forEach { (id, title, artist) ->
                statement.setString(1, id)
                statement.setString(2, title)
                statement.setString(3, artist)
                statement.setString(4, "https://example.invalid/art.jpg")
                statement.setString(5, "/data/music/$id.mp3")
                statement.setLong(6, 210_000L)
                statement.setString(7, "Offline")
                statement.setString(8, "Offline HQ")
                statement.setString(9, null)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    private fun migrate() {
        connection.createStatement().use { statement ->
            V4_STATEMENTS.forEach(statement::executeUpdate)
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
    fun migrationKeepsEveryDownloadedTrack() {
        seedDownloads()
        assertEquals(2, countOf("tracks"))

        migrate()

        assertEquals("Upgrading to v4 lost a downloaded track", 2, countOf("tracks"))
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT title, mediaUrl FROM tracks WHERE id = 'yt_abc12345678'").use { rows ->
                assertTrue("The downloaded row disappeared entirely", rows.next())
                assertEquals("Saved Song", rows.getString(1))
                assertEquals("/data/music/yt_abc12345678.mp3", rows.getString(2))
            }
        }
    }

    @Test
    fun migrationCreatesEveryNewTable() {
        migrate()

        val expected = setOf(
            "catalog_tracks", "liked_tracks", "disliked_tracks", "playlists", "playlist_tracks",
            "play_history", "shared_plays", "queue_items", "player_state", "jam_sessions", "jam_events"
        )
        assertTrue(
            "Missing after migration: ${expected - tableNames()}",
            tableNames().containsAll(expected)
        )
    }

    @Test
    fun migrationLeavesTheStreamCacheIntact() {
        connection.createStatement().use {
            it.executeUpdate(
                "INSERT INTO stream_cache (cacheKey, trackId, quality, url, mimeType, expiresAtMs, " +
                    "resolvedAtMs) VALUES ('k', 't', 'HIGH', 'https://example.invalid/a.m4a', " +
                    "'audio/mp4', 1, 0)"
            )
        }

        migrate()

        assertEquals(1, countOf("stream_cache"))
    }

    @Test
    fun migrationIsSafeToRunTwice() {
        seedDownloads()
        migrate()
        // Every statement is CREATE ... IF NOT EXISTS, so a retried upgrade must not throw.
        migrate()
        assertEquals(2, countOf("tracks"))
    }

    @Test
    fun newTablesActuallyAcceptTheWritesTheRepositoryMakes() {
        migrate()
        connection.createStatement().use { statement ->
            statement.executeUpdate("INSERT INTO liked_tracks (trackId, likedAtMs) VALUES ('yt_x', 10)")
            statement.executeUpdate(
                "INSERT INTO playlists (id, name, coverTrackId, createdAtMs, updatedAtMs, sortIndex) " +
                    "VALUES ('p1', 'Road trip', NULL, 1, 1, 0)"
            )
            statement.executeUpdate(
                "INSERT INTO playlist_tracks (playlistId, trackId, position, addedAtMs) " +
                    "VALUES ('p1', 'yt_x', 0, 1)"
            )
        }

        assertEquals(1, countOf("liked_tracks"))
        assertEquals(1, countOf("playlist_tracks"))
    }

    @Test
    fun deletingAPlaylistTakesItsTracksWithIt() {
        migrate()
        connection.createStatement().use { statement ->
            // Room enables foreign keys on the real database; SQLite defaults them off.
            statement.executeUpdate("PRAGMA foreign_keys = ON")
            statement.executeUpdate(
                "INSERT INTO playlists (id, name, coverTrackId, createdAtMs, updatedAtMs, sortIndex) " +
                    "VALUES ('p1', 'Road trip', NULL, 1, 1, 0)"
            )
            statement.executeUpdate(
                "INSERT INTO playlist_tracks (playlistId, trackId, position, addedAtMs) " +
                    "VALUES ('p1', 'yt_x', 0, 1)"
            )
            statement.executeUpdate("DELETE FROM playlists WHERE id = 'p1'")
        }

        assertEquals("Deleting a playlist left orphaned rows behind", 0, countOf("playlist_tracks"))
    }
}
