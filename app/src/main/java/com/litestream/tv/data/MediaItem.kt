package com.litestream.tv.data

data class MediaItem(
    val id: String,
    val title: String,
    val type: MediaType,
    val year: Int,
    val rating: String,
    val description: String,
    val category: String,
    val posterColor: Long,
    val playbackUrl: String? = null,
    val isDemo: Boolean = true
)

enum class MediaType { MOVIE, SERIES }

enum class AppThemeMode { SPACE_GRAY, AMOLED_BLACK, DEEP_OCEAN }

object DemoCatalog {
    val items = listOf(
        MediaItem("cyberpunk", "Cyberpunk Odyssey 2099", MediaType.MOVIE, 2025, "8.4", "A neon-soaked journey through a city shaped by memory, machines, and rebellion.", "trending", 0xFF203C58),
        MediaItem("solaris", "Solaris Drift", MediaType.MOVIE, 2024, "7.9", "A deep-space crew encounters a signal that challenges everything they know.", "scifi", 0xFF36465C),
        MediaItem("shadow", "Shadow Realm", MediaType.SERIES, 2025, "8.1", "A hidden world emerges where every choice has a consequence.", "scifi", 0xFF40304F),
        MediaItem("ocean", "Deep Ocean Horizon", MediaType.MOVIE, 2023, "7.7", "An expedition descends into an unexplored part of the ocean.", "trending", 0xFF164B61),
        MediaItem("quantum", "The Quantum Protocol", MediaType.SERIES, 2025, "8.6", "A team of scientists races to contain a reality-bending experiment.", "action", 0xFF3D3A5B),
        MediaItem("neon", "Neon Velocity", MediaType.MOVIE, 2024, "7.8", "An underground racer discovers a conspiracy behind the city circuit.", "action", 0xFF5A304D)
    )
}
