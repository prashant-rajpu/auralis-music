package com.auralis.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds the resolved-stream cache. Downloaded tracks in `tracks` are left untouched.
 *
 * The statement is copied verbatim from the exported schema (app/schemas/…/3.json); Room compares
 * the real table against that schema on open and aborts if they differ. MigrationSqlTest keeps the
 * two in step.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(CREATE_STREAM_CACHE)
    }
}

internal const val CREATE_STREAM_CACHE =
    "CREATE TABLE IF NOT EXISTS `stream_cache` (`cacheKey` TEXT NOT NULL, `trackId` TEXT NOT NULL, " +
        "`quality` TEXT NOT NULL, `url` TEXT NOT NULL, `mimeType` TEXT, `expiresAtMs` INTEGER NOT NULL, " +
        "`resolvedAtMs` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))"
