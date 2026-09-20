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

/**
 * The library becomes real: likes, playlists, history, the saved queue, and the Together tables.
 *
 * Strictly additive. Nothing here alters, drops or copies `tracks` or `stream_cache`, so an
 * upgrade cannot lose a download — the failure mode that makes people distrust an app forever.
 * Every statement below is copied verbatim from the exported v4 schema, because Room compares the
 * migrated tables against that schema on open and aborts the app if they differ by so much as a
 * column order. MigrationSqlTest asserts the two stay identical; Migration3To4Test runs the whole
 * thing against a real v3 database and checks the downloads are still there afterwards.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        V4_STATEMENTS.forEach(db::execSQL)
    }
}

/**
 * Chat stops being something that only exists while a socket is open.
 *
 * Additive, like the one before it: one new table, nothing altered. The statements are copied
 * verbatim from the exported v5 schema for the reason at the top of MIGRATION_3_4 — Room compares
 * the migrated table against that schema on open and refuses to start if they differ.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        V5_STATEMENTS.forEach(db::execSQL)
    }
}

internal val V5_STATEMENTS: List<String> = listOf(
    CREATE_TOGETHER_MESSAGES,
    INDEX_TOGETHER_MESSAGES_ROOMCODE_ATMS,
    INDEX_TOGETHER_MESSAGES_PENDING,
)

/** Parents before children: playlist_tracks and jam_events carry foreign keys. */
internal val V4_STATEMENTS: List<String> = listOf(
    CREATE_CATALOG_TRACKS,
    CREATE_LIKED_TRACKS,
    CREATE_DISLIKED_TRACKS,
    CREATE_PLAYLISTS,
    INDEX_PLAYLISTS_SORTINDEX,
    CREATE_PLAYLIST_TRACKS,
    INDEX_PLAYLIST_TRACKS_PLAYLISTID,
    INDEX_PLAYLIST_TRACKS_TRACKID,
    CREATE_PLAY_HISTORY,
    INDEX_PLAY_HISTORY_TRACKID,
    INDEX_PLAY_HISTORY_STARTEDATMS,
    INDEX_PLAY_HISTORY_SESSIONID,
    CREATE_SHARED_PLAYS,
    INDEX_SHARED_PLAYS_TRACKID,
    INDEX_SHARED_PLAYS_JAMSESSIONID,
    INDEX_SHARED_PLAYS_PLAYEDATMS,
    CREATE_QUEUE_ITEMS,
    CREATE_PLAYER_STATE,
    CREATE_JAM_SESSIONS,
    INDEX_JAM_SESSIONS_STARTEDATMS,
    INDEX_JAM_SESSIONS_ROOMCODE,
    CREATE_JAM_EVENTS,
    INDEX_JAM_EVENTS_SESSIONID,
    INDEX_JAM_EVENTS_ATMS,
)

internal const val CREATE_STREAM_CACHE =
    "CREATE TABLE IF NOT EXISTS `stream_cache` (`cacheKey` TEXT NOT NULL, `trackId` TEXT NOT NULL, " +
        "`quality` TEXT NOT NULL, `url` TEXT NOT NULL, `mimeType` TEXT, `expiresAtMs` INTEGER NOT NULL, " +
        "`resolvedAtMs` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))"

internal const val CREATE_CATALOG_TRACKS =
    "CREATE TABLE IF NOT EXISTS `catalog_tracks` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, " +
        "`artist` TEXT NOT NULL, `albumArtUrl` TEXT, `mediaUrl` TEXT NOT NULL, `durationMs` " +
        "INTEGER NOT NULL, `source` TEXT NOT NULL, `qualityBadge` TEXT NOT NULL, `firstSeenAtMs` " +
        "INTEGER NOT NULL, `lastSeenAtMs` INTEGER NOT NULL, PRIMARY KEY(`id`))"

internal const val CREATE_LIKED_TRACKS =
    "CREATE TABLE IF NOT EXISTS `liked_tracks` (`trackId` TEXT NOT NULL, `likedAtMs` INTEGER " +
        "NOT NULL, PRIMARY KEY(`trackId`))"

internal const val CREATE_DISLIKED_TRACKS =
    "CREATE TABLE IF NOT EXISTS `disliked_tracks` (`trackId` TEXT NOT NULL, `dislikedAtMs` " +
        "INTEGER NOT NULL, PRIMARY KEY(`trackId`))"

internal const val CREATE_PLAYLISTS =
    "CREATE TABLE IF NOT EXISTS `playlists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, " +
        "`coverTrackId` TEXT, `createdAtMs` INTEGER NOT NULL, `updatedAtMs` INTEGER NOT NULL, " +
        "`sortIndex` INTEGER NOT NULL, PRIMARY KEY(`id`))"

internal const val INDEX_PLAYLISTS_SORTINDEX =
    "CREATE INDEX IF NOT EXISTS `index_playlists_sortIndex` ON `playlists` (`sortIndex`)"

internal const val CREATE_PLAYLIST_TRACKS =
    "CREATE TABLE IF NOT EXISTS `playlist_tracks` (`playlistId` TEXT NOT NULL, `trackId` TEXT " +
        "NOT NULL, `position` INTEGER NOT NULL, `addedAtMs` INTEGER NOT NULL, PRIMARY " +
        "KEY(`playlistId`, `trackId`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON " +
        "UPDATE NO ACTION ON DELETE CASCADE )"

internal const val INDEX_PLAYLIST_TRACKS_PLAYLISTID =
    "CREATE INDEX IF NOT EXISTS `index_playlist_tracks_playlistId` ON `playlist_tracks` " +
        "(`playlistId`)"

internal const val INDEX_PLAYLIST_TRACKS_TRACKID =
    "CREATE INDEX IF NOT EXISTS `index_playlist_tracks_trackId` ON `playlist_tracks` " +
        "(`trackId`)"

internal const val CREATE_PLAY_HISTORY =
    "CREATE TABLE IF NOT EXISTS `play_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT " +
        "NULL, `trackId` TEXT NOT NULL, `startedAtMs` INTEGER NOT NULL, `playedMs` INTEGER NOT " +
        "NULL, `completionRatio` REAL NOT NULL, `skipped` INTEGER NOT NULL, `context` TEXT NOT " +
        "NULL, `moodLabel` TEXT, `sessionId` TEXT, `hourOfDay` INTEGER NOT NULL, `dayOfWeek` " +
        "INTEGER NOT NULL)"

internal const val INDEX_PLAY_HISTORY_TRACKID =
    "CREATE INDEX IF NOT EXISTS `index_play_history_trackId` ON `play_history` (`trackId`)"

internal const val INDEX_PLAY_HISTORY_STARTEDATMS =
    "CREATE INDEX IF NOT EXISTS `index_play_history_startedAtMs` ON `play_history` " +
        "(`startedAtMs`)"

internal const val INDEX_PLAY_HISTORY_SESSIONID =
    "CREATE INDEX IF NOT EXISTS `index_play_history_sessionId` ON `play_history` " +
        "(`sessionId`)"

internal const val CREATE_SHARED_PLAYS =
    "CREATE TABLE IF NOT EXISTS `shared_plays` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT " +
        "NULL, `trackId` TEXT NOT NULL, `jamSessionId` TEXT NOT NULL, `partnerName` TEXT, " +
        "`playedAtMs` INTEGER NOT NULL, `addedByMe` INTEGER NOT NULL)"

internal const val INDEX_SHARED_PLAYS_TRACKID =
    "CREATE INDEX IF NOT EXISTS `index_shared_plays_trackId` ON `shared_plays` (`trackId`)"

internal const val INDEX_SHARED_PLAYS_JAMSESSIONID =
    "CREATE INDEX IF NOT EXISTS `index_shared_plays_jamSessionId` ON `shared_plays` " +
        "(`jamSessionId`)"

internal const val INDEX_SHARED_PLAYS_PLAYEDATMS =
    "CREATE INDEX IF NOT EXISTS `index_shared_plays_playedAtMs` ON `shared_plays` " +
        "(`playedAtMs`)"

internal const val CREATE_QUEUE_ITEMS =
    "CREATE TABLE IF NOT EXISTS `queue_items` (`position` INTEGER NOT NULL, `trackId` TEXT " +
        "NOT NULL, `isRecommendation` INTEGER NOT NULL, PRIMARY KEY(`position`))"

internal const val CREATE_PLAYER_STATE =
    "CREATE TABLE IF NOT EXISTS `player_state` (`id` INTEGER NOT NULL, `currentIndex` INTEGER " +
        "NOT NULL, `positionMs` INTEGER NOT NULL, `isShuffled` INTEGER NOT NULL, `repeatMode` " +
        "TEXT NOT NULL, `updatedAtMs` INTEGER NOT NULL, PRIMARY KEY(`id`))"

internal const val CREATE_JAM_SESSIONS =
    "CREATE TABLE IF NOT EXISTS `jam_sessions` (`id` TEXT NOT NULL, `roomCode` TEXT NOT NULL, " +
        "`partnerName` TEXT, `startedAtMs` INTEGER NOT NULL, `endedAtMs` INTEGER, `trackCount` " +
        "INTEGER NOT NULL, `wasHost` INTEGER NOT NULL, PRIMARY KEY(`id`))"

internal const val INDEX_JAM_SESSIONS_STARTEDATMS =
    "CREATE INDEX IF NOT EXISTS `index_jam_sessions_startedAtMs` ON `jam_sessions` " +
        "(`startedAtMs`)"

internal const val INDEX_JAM_SESSIONS_ROOMCODE =
    "CREATE INDEX IF NOT EXISTS `index_jam_sessions_roomCode` ON `jam_sessions` (`roomCode`)"

internal const val CREATE_JAM_EVENTS =
    "CREATE TABLE IF NOT EXISTS `jam_events` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT " +
        "NULL, `sessionId` TEXT NOT NULL, `type` TEXT NOT NULL, `trackId` TEXT, `payload` TEXT, " +
        "`fromMe` INTEGER NOT NULL, `atMs` INTEGER NOT NULL, FOREIGN KEY(`sessionId`) REFERENCES " +
        "`jam_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"

internal const val INDEX_JAM_EVENTS_SESSIONID =
    "CREATE INDEX IF NOT EXISTS `index_jam_events_sessionId` ON `jam_events` (`sessionId`)"

internal const val INDEX_JAM_EVENTS_ATMS =
    "CREATE INDEX IF NOT EXISTS `index_jam_events_atMs` ON `jam_events` (`atMs`)"

internal const val CREATE_TOGETHER_MESSAGES =
    "CREATE TABLE IF NOT EXISTS `together_messages` (`id` TEXT NOT NULL, `roomCode` TEXT NOT " +
        "NULL, `senderId` TEXT NOT NULL, `senderName` TEXT NOT NULL, `fromMe` INTEGER NOT NULL, " +
        "`kind` TEXT NOT NULL, `payload` TEXT NOT NULL, `preview` TEXT NOT NULL, `atMs` INTEGER " +
        "NOT NULL, `pending` INTEGER NOT NULL, `ciphertext` TEXT, PRIMARY KEY(`id`))"

internal const val INDEX_TOGETHER_MESSAGES_ROOMCODE_ATMS =
    "CREATE INDEX IF NOT EXISTS `index_together_messages_roomCode_atMs` ON `together_messages` " +
        "(`roomCode`, `atMs`)"

internal const val INDEX_TOGETHER_MESSAGES_PENDING =
    "CREATE INDEX IF NOT EXISTS `index_together_messages_pending` ON `together_messages` " +
        "(`pending`)"
