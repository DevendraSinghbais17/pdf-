package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

// --- ROOM DB MODEL ---

@Entity(tableName = "saved_media")
data class SavedMedia(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subtitle: String, // Author for books, Publisher for games
    val year: String,
    val description: String,
    val type: String, // "BOOK" or "GAME"
    val resourceUrl: String?,
    val instruction: String, // accessMethod or howToPlay
    val extraInfo: String?, // platforms for games, publisher details, etc.
    val timestamp: Long = System.currentTimeMillis()
)

// --- GEMINI REST API PAYLOAD MODELS ---

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig? = null,
    val systemInstruction: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val responseMimeType: String? = "application/json",
    val temperature: Double? = 0.4
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent?
)

// --- STRUCTURED OUTPUT DOMAIN MODELS ---

@JsonClass(generateAdapter = true)
data class BookResult(
    val title: String,
    val author: String,
    val year: String,
    val description: String,
    val isFreePdfAvailable: Boolean,
    val pdfResourceUrl: String?,
    val accessMethod: String
)

@JsonClass(generateAdapter = true)
data class BookSearchResponse(
    val results: List<BookResult>
)

@JsonClass(generateAdapter = true)
data class GameResult(
    val title: String,
    val publisher: String,
    val platforms: String,
    val year: String,
    val description: String,
    val isLegalFreeVersion: Boolean,
    val resourceType: String, // Browser Emulator, Legal Abandonware, Open Source, DRM-Free F2P
    val resourceUrl: String?,
    val howToPlay: String
)

@JsonClass(generateAdapter = true)
data class GameSearchResponse(
    val results: List<GameResult>
)

@JsonClass(generateAdapter = true)
data class TorrentResult(
    val title: String,
    val category: String,
    val size: String,
    val seeders: String,
    val leechers: String,
    val infoUrl: String?,
    val magnetUrl: String,
    val description: String
)

@JsonClass(generateAdapter = true)
data class TorrentSearchResponse(
    val results: List<TorrentResult>
)

@JsonClass(generateAdapter = true)
data class ReadBook(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val rating: Int = 5,
    val notes: String = "",
    val dateRead: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

