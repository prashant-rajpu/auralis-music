package com.auralis.app.domain.model

enum class Provider(val id: String, val displayName: String) {
    LOCAL("local", "On this device"),
    AUDIUS("audius", "Audius"),
    JAMENDO("jamendo", "Jamendo"),
    YOUTUBE("youtube", "YouTube Music"),
    JIOSAAVN("jiosaavn", "JioSaavn"),
    IMPORTED("imported", "Imported");

    companion object {
        // Track ids still encode their origin in a prefix; decode it until ids carry the provider.
        fun infer(id: String, mediaUrl: String = ""): Provider = when {
            id.startsWith("yt_import_") -> IMPORTED
            id.startsWith("yt_") -> YOUTUBE
            id.startsWith("auralis_global_") -> AUDIUS
            id.startsWith("auralis_") -> JIOSAAVN
            id.startsWith("jamendo_") -> JAMENDO
            id.startsWith("local_") || mediaUrl.startsWith("content://") -> LOCAL
            else -> IMPORTED
        }
    }
}
