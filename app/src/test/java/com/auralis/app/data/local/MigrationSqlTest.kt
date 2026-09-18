package com.auralis.app.data.local

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Room aborts at startup when a migrated table does not match the schema it generated, which would
 * brick every existing install. This compares the hand-written migration against the exported
 * schema so the two cannot drift apart unnoticed.
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

    private fun createSql(version: Int, table: String): String {
        val entities = schema(version).getAsJsonArray("entities")
        for (element in entities) {
            val entity = element.asJsonObject
            if (entity.get("tableName").asString == table) {
                return entity.get("createSql").asString.replace("\${TABLE_NAME}", table)
            }
        }
        throw AssertionError("Schema v$version has no table '$table'")
    }

    @Test
    fun migrationCreatesExactlyTheTableRoomExpects() {
        assertEquals(createSql(3, "stream_cache"), CREATE_STREAM_CACHE)
    }

    @Test
    fun schemaVersionThreeStillCarriesTheDownloadsTable() {
        // The migration must add to the database, never replace it
        assertTrue(createSql(3, "tracks").contains("`id` TEXT NOT NULL"))
    }
}
