package com.auralis.app.data.local

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Room aborts at startup when a migrated table does not match the schema it generated, which would
 * brick every existing install. This compares the hand-written migrations against the exported
 * schemas so the two cannot drift apart unnoticed.
 *
 * Gson rather than org.json: the latter is an Android stub on the unit-test classpath and quietly
 * returns null for everything.
 */
class MigrationSqlTest {

    private val schemaDir = File("schemas/com.auralis.app.data.local.AuralisDatabase")

    private fun schema(version: Int): JsonObject {
        val file = File(schemaDir, "$version.json")
        assertTrue(
            "Missing exported schema ${file.path}; is room.schemaLocation still set?",
            file.exists()
        )
        return JsonParser.parseString(file.readText()).asJsonObject.getAsJsonObject("database")
    }

    private fun entity(version: Int, table: String): JsonObject {
        val entities = schema(version).getAsJsonArray("entities")
        for (element in entities) {
            val candidate = element.asJsonObject
            if (candidate.get("tableName").asString == table) return candidate
        }
        throw AssertionError("Schema v$version has no table '$table'")
    }

    private fun createSql(version: Int, table: String): String =
        entity(version, table).get("createSql").asString.replace("\${TABLE_NAME}", table)

    private fun indexSql(version: Int, table: String): List<String> =
        entity(version, table).getAsJsonArray("indices")
            ?.map { it.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}", table) }
            ?: emptyList()

    private val v4Tables = listOf(
        "catalog_tracks", "liked_tracks", "disliked_tracks", "playlists", "playlist_tracks",
        "play_history", "shared_plays", "queue_items", "player_state", "jam_sessions", "jam_events"
    )

    @Test
    fun migrationTwoToThreeCreatesExactlyTheTableRoomExpects() {
        assertEquals(createSql(3, "stream_cache"), CREATE_STREAM_CACHE)
    }

    @Test
    fun schemaVersionThreeStillCarriesTheDownloadsTable() {
        // The migration must add to the database, never replace it
        assertTrue(createSql(3, "tracks").contains("`id` TEXT NOT NULL"))
    }

    @Test
    fun everyV4TableIsCreatedExactlyAsRoomGeneratedIt() {
        v4Tables.forEach { table ->
            assertTrue(
                "MIGRATION_3_4 does not create '$table' with the exact SQL Room expects",
                V4_STATEMENTS.contains(createSql(4, table))
            )
        }
    }

    @Test
    fun everyV4IndexIsCreatedExactlyAsRoomGeneratedIt() {
        v4Tables.forEach { table ->
            indexSql(4, table).forEach { sql ->
                assertTrue("MIGRATION_3_4 is missing an index on '$table': $sql", V4_STATEMENTS.contains(sql))
            }
        }
    }

    @Test
    fun migrationRunsNothingBeyondTheSchema() {
        val expected = v4Tables.flatMap { listOf(createSql(4, it)) + indexSql(4, it) }.toSet()
        val extra = V4_STATEMENTS.toSet() - expected
        assertTrue("MIGRATION_3_4 runs statements the schema does not describe: $extra", extra.isEmpty())
        assertEquals("MIGRATION_3_4 has duplicate statements", V4_STATEMENTS.size, V4_STATEMENTS.toSet().size)
    }

    @Test
    fun parentTablesAreCreatedBeforeTheChildrenThatReferenceThem() {
        fun indexOfCreate(table: String) = V4_STATEMENTS.indexOfFirst { it.contains("CREATE TABLE IF NOT EXISTS `$table`") }
        assertTrue(indexOfCreate("playlists") < indexOfCreate("playlist_tracks"))
        assertTrue(indexOfCreate("jam_sessions") < indexOfCreate("jam_events"))
    }

    /**
     * The property that matters most: upgrading must not be able to lose a download. Any ALTER,
     * DROP or DELETE touching the two tables that existed in v3 would do exactly that.
     */
    @Test
    fun migrationNeverTouchesTheTablesThatAlreadyHeldUserData() {
        V4_STATEMENTS.forEach { sql ->
            assertTrue("MIGRATION_3_4 runs a non-CREATE statement: $sql", sql.startsWith("CREATE "))
            listOf("tracks", "stream_cache").forEach { existing ->
                assertFalse(
                    "MIGRATION_3_4 must not touch the pre-existing '$existing' table: $sql",
                    sql.contains("`$existing`")
                )
            }
        }
    }

    @Test
    fun v4LeavesTheV3TablesByteIdentical() {
        listOf("tracks", "stream_cache").forEach { table ->
            assertEquals(
                "Table '$table' changed shape between v3 and v4; that needs a real data migration",
                createSql(3, table),
                createSql(4, table)
            )
        }
    }

    // --- v5: chat that outlives the socket ---

    @Test
    fun theV5TableIsCreatedExactlyAsRoomGeneratedIt() {
        assertEquals(createSql(5, "together_messages"), CREATE_TOGETHER_MESSAGES)
        indexSql(5, "together_messages").forEach { sql ->
            assertTrue("MIGRATION_4_5 is missing an index: $sql", V5_STATEMENTS.contains(sql))
        }
    }

    @Test
    fun migrationFourToFiveRunsNothingBeyondTheSchema() {
        val expected = (listOf(createSql(5, "together_messages")) + indexSql(5, "together_messages")).toSet()
        val extra = V5_STATEMENTS.toSet() - expected
        assertTrue("MIGRATION_4_5 runs statements the schema does not describe: $extra", extra.isEmpty())
        assertEquals("MIGRATION_4_5 has duplicate statements", V5_STATEMENTS.size, V5_STATEMENTS.toSet().size)
    }

    /**
     * Same property as v4, one version on: someone upgrading from v4 has a library and a download
     * folder by now, and none of it may be touched to make room for a chat table.
     */
    @Test
    fun migrationFourToFiveNeverTouchesAnythingThatAlreadyHeldUserData() {
        V5_STATEMENTS.forEach { sql ->
            assertTrue("MIGRATION_4_5 runs a non-CREATE statement: $sql", sql.startsWith("CREATE "))
        }
        val untouched = listOf("tracks", "stream_cache") + v4Tables
        untouched.forEach { existing ->
            V5_STATEMENTS.forEach { sql ->
                assertFalse(
                    "MIGRATION_4_5 must not touch the pre-existing '$existing' table: $sql",
                    sql.contains("`$existing`")
                )
            }
        }
    }

    @Test
    fun v5LeavesEveryEarlierTableByteIdentical() {
        (listOf("tracks", "stream_cache") + v4Tables).forEach { table ->
            assertEquals(
                "Table '$table' changed shape between v4 and v5; that needs a real data migration",
                createSql(4, table),
                createSql(5, table)
            )
        }
    }
}
