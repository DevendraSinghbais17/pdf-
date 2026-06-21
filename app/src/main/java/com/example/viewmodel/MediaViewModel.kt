package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.api.FirebaseBookSyncManager
import com.example.api.GeminiClient
import com.example.db.AppDatabase
import com.example.db.MediaRepository
import com.example.model.*
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SearchTab {
    BOOKS,
    TORRENTS,
    GAMES,
    BOOKMARKS,
    READ_LOG
}

class MediaViewModel(
    application: Application,
    private val repository: MediaRepository
) : AndroidViewModel(application) {

    val firebaseSync = FirebaseBookSyncManager(application)

    // --- State Observables ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("media_seeker_prefs", android.content.Context.MODE_PRIVATE)
    private val _recentQueries = MutableStateFlow<List<String>>(emptyList())
    val recentQueries: StateFlow<List<String>> = _recentQueries.asStateFlow()

    init {
        loadRecentQueries()
    }

    private fun loadRecentQueries() {
        val raw = sharedPrefs.getString("recent_queries_ordered", "") ?: ""
        if (raw.isNotEmpty()) {
            _recentQueries.value = raw.split("|||")
        } else {
            _recentQueries.value = emptyList()
        }
    }

    fun addRecentQuery(query: String) {
        val clean = query.trim()
        if (clean.isEmpty()) return
        val currentList = _recentQueries.value.toMutableList()
        currentList.remove(clean)
        currentList.add(0, clean)
        val limited = currentList.take(6)
        _recentQueries.value = limited
        sharedPrefs.edit().putString("recent_queries_ordered", limited.joinToString("|||")).apply()
    }

    fun removeRecentQuery(query: String) {
        val currentList = _recentQueries.value.toMutableList()
        currentList.remove(query)
        _recentQueries.value = currentList
        sharedPrefs.edit().putString("recent_queries_ordered", currentList.joinToString("|||")).apply()
    }

    fun clearRecentQueries() {
        _recentQueries.value = emptyList()
        sharedPrefs.edit().remove("recent_queries_ordered").apply()
    }

    private val _selectedTab = MutableStateFlow(SearchTab.BOOKS)
    val selectedTab: StateFlow<SearchTab> = _selectedTab.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _bookResults = MutableStateFlow<List<BookResult>>(emptyList())
    val bookResults: StateFlow<List<BookResult>> = _bookResults.asStateFlow()

    private val _torrentResults = MutableStateFlow<List<TorrentResult>>(emptyList())
    val torrentResults: StateFlow<List<TorrentResult>> = _torrentResults.asStateFlow()

    private val _gameResults = MutableStateFlow<List<GameResult>>(emptyList())
    val gameResults: StateFlow<List<GameResult>> = _gameResults.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val bookmarks: StateFlow<List<SavedMedia>> = repository.allBookmarks
        .flowOn(Dispatchers.IO)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Bookmarked states mapping (title_type to boolean) helper for lists
    private val _bookmarkedTitles = MutableStateFlow<Set<String>>(emptySet())
    val bookmarkedTitles: StateFlow<Set<String>> = _bookmarkedTitles.asStateFlow()

    init {
        // Automatically sync the bookmark check logic and back up bookmarks to firebase
        viewModelScope.launch {
            bookmarks.collect { list ->
                _bookmarkedTitles.value = list.map { "${it.title}_${it.type}" }.toSet()
                firebaseSync.syncBookmarksToFirebase(list)
            }
        }
    }

    private val _customApiKey = MutableStateFlow(sharedPrefs.getString("user_gemini_api_key", "") ?: "")
    val customApiKey: StateFlow<String> = _customApiKey.asStateFlow()

    fun updateCustomApiKey(key: String) {
        _customApiKey.value = key.trim()
        sharedPrefs.edit().putString("user_gemini_api_key", key.trim()).apply()
    }

    fun getActiveApiKey(): String {
        val customKey = _customApiKey.value
        if (customKey.isNotEmpty()) return customKey
        return BuildConfig.GEMINI_API_KEY
    }

    fun isApiKeyWorking(): Boolean {
        val key = getActiveApiKey()
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY" && key != "placeholder"
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectTab(tab: SearchTab) {
        _selectedTab.value = tab
        _errorMessage.value = null
        if (tab != SearchTab.BOOKMARKS && _searchQuery.value.trim().isNotEmpty()) {
            performSearch()
        }
    }

    fun clearResults() {
        _bookResults.value = emptyList()
        _gameResults.value = emptyList()
        _torrentResults.value = emptyList()
        _errorMessage.value = null
    }

    fun performSearch() {
        val query = _searchQuery.value.trim()
        if (query.isEmpty()) return

        addRecentQuery(query)

        _isLoading.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                if (isApiKeyWorking()) {
                    searchWithGemini(query)
                } else {
                    // Fallback to beautiful local mock database matching
                    searchOfflineData(query)
                }
            } catch (e: Exception) {
                Log.e("MediaViewModel", "Search error", e)
                // Gracefully fallback to offline database matching on failure
                searchOfflineData(query)
            } finally {
                _isLoading.value = false
            }
        }
    }

    // --- Bookmarking Interactions ---

    fun toggleBookmarked(book: BookResult) {
        viewModelScope.launch(Dispatchers.IO) {
            val key = "${book.title}_BOOK"
            if (_bookmarkedTitles.value.contains(key)) {
                repository.deleteByTitleAndType(book.title, "BOOK")
            } else {
                val entity = SavedMedia(
                    title = book.title,
                    subtitle = book.author,
                    year = book.year,
                    description = book.description,
                    type = "BOOK",
                    resourceUrl = book.pdfResourceUrl,
                    instruction = book.accessMethod,
                    extraInfo = null
                )
                repository.insert(entity)
            }
        }
    }

    fun toggleBookmarked(torrent: TorrentResult) {
        viewModelScope.launch(Dispatchers.IO) {
            val key = "${torrent.title}_TORRENT"
            if (_bookmarkedTitles.value.contains(key)) {
                repository.deleteByTitleAndType(torrent.title, "TORRENT")
            } else {
                val entity = SavedMedia(
                    title = torrent.title,
                    subtitle = torrent.category,
                    year = "Seeders: ${torrent.seeders}",
                    description = torrent.description,
                    type = "TORRENT",
                    resourceUrl = torrent.magnetUrl,
                    instruction = "Direct index portal or torrent/magnet link search.",
                    extraInfo = "Size: ${torrent.size} | Leechers: ${torrent.leechers}"
                )
                repository.insert(entity)
            }
        }
    }

    fun toggleBookmarked(game: GameResult) {
        viewModelScope.launch(Dispatchers.IO) {
            val key = "${game.title}_GAME"
            if (_bookmarkedTitles.value.contains(key)) {
                repository.deleteByTitleAndType(game.title, "GAME")
            } else {
                val entity = SavedMedia(
                    title = game.title,
                    subtitle = game.publisher,
                    year = game.year,
                    description = game.description,
                    type = "GAME",
                    resourceUrl = game.resourceUrl,
                    instruction = game.howToPlay,
                    extraInfo = game.platforms + " | " + game.resourceType
                )
                repository.insert(entity)
            }
        }
    }

    fun removeBookmark(savedMedia: SavedMedia) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(savedMedia)
        }
    }

    // --- Offline Mode Fallback & Mock Search ---

    private fun searchOfflineData(query: String) {
        _errorMessage.value = "Offline Mode / Demonstration Mode (No custom API Key configured in Secrets panel)."
        val searchLower = query.lowercase()

        when (_selectedTab.value) {
            SearchTab.BOOKS -> {
                val mockBooks = getMockBooks().filter {
                    it.title.lowercase().contains(searchLower) || it.author.lowercase().contains(searchLower)
                }
                _bookResults.value = mockBooks.ifEmpty {
                    listOf(
                        BookResult(
                            title = "No Match for '$query' Found (Offline)",
                            author = "Alternative Suggestions Available",
                            year = "N/A",
                            description = "We couldn't find a local catalog match for your query. To unlock unlimited real-time digital book & PDF searches, please enter your Gemini API Key in the Secrets panel in AI Studio.",
                            isFreePdfAvailable = true,
                            pdfResourceUrl = "https://gutenberg.org/",
                            accessMethod = "1. Visit Project Gutenberg (gutenberg.org) directly.\n2. Over 70,000 free digital eBooks are available legally."
                        )
                    )
                }
            }
            SearchTab.TORRENTS -> {
                val mockTorrents = getMockTorrents().filter {
                    it.title.lowercase().contains(searchLower) || it.category.lowercase().contains(searchLower)
                }
                _torrentResults.value = mockTorrents.ifEmpty {
                    listOf(
                        TorrentResult(
                            title = "No Match for '$query' Found (Offline)",
                            category = "Media Search",
                            size = "N/A",
                            seeders = "0",
                            leechers = "0",
                            infoUrl = null,
                            magnetUrl = "https://thepiratebay.org/",
                            description = "We couldn't find a local catalog match for your query. To unlock live decentralized search and global exploration powered by Google Gemini, enter your API Key in the Secrets panel."
                        )
                    )
                }
            }
            SearchTab.GAMES -> {
                val mockGames = getMockGames().filter {
                    it.title.lowercase().contains(searchLower) || it.publisher.lowercase().contains(searchLower)
                }
                _gameResults.value = mockGames.ifEmpty {
                    listOf(
                        GameResult(
                            title = "No Match for '$query' Found (Offline)",
                            publisher = "Alternative Options Available",
                            platforms = "Web Browser / DOS",
                            year = "N/A",
                            description = "We couldn't find a local catalog match. Enter your Gemini API Key to research free remake repositories, open source links, or browser emulators for any game dynamically.",
                            isLegalFreeVersion = true,
                            resourceType = "Browser Emulator",
                            resourceUrl = "https://archive.org/details/softwarelibrary",
                            howToPlay = "1. Visit the Internet Archive Software Library.\n2. Search games directly in the archive and play them right inside your web browser!"
                        )
                    )
                }
            }
            else -> {}
        }
    }

    // --- Gemini Search ---

    private suspend fun searchWithGemini(query: String) = withContext(Dispatchers.IO) {
        val apiKey = getActiveApiKey()
        val moshi = GeminiClient.moshiInstance

        when (_selectedTab.value) {
            SearchTab.BOOKS -> {
                val prompt = """
                    Find matches for the book or manga search query: "$query".
                    Look for 1 to 5 matches that have free digital formats (PDF/EPUB), preprints, public domain archives, or items available across digital shadow libraries and portals like:
                    - Library Genesis (Libgen)
                    - Z-Library
                    - Anna's Archive
                    - Sci-Hub (for academic papers)
                    - PDF Drive
                    - Bookzz / Bookfi
                    - Oceanofpdf (for novels/general books)
                    - MangaDex, Mangakakalot, Manganato, Readm (for manga/comics)
                    Provide a guide or search link to these platforms (e.g. Libgen, Z-Lib, Anna's Archive, Project Gutenberg).
                    Return details strictly in this JSON schema. Avoid wrapped characters other than standard json format:
                    {
                      "results": [
                        {
                          "title": "Exact Title of the Book or Manga",
                          "author": "Author / Creator Name(s)",
                          "year": "Publication Year (e.g., 1897)",
                          "description": "Short explanation of plot, concepts, and digital/shadow library details or reader recommendations.",
                          "isFreePdfAvailable": true,
                          "pdfResourceUrl": "Direct Gutenberg/Open Library or customized Libgen/Z-Library/Anna's Archive/MangaDex search query link",
                          "accessMethod": "Step-by-step download guide or portal exploration method."
                        }
                      ]
                    }
                """.trimIndent()

                val request = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                    generationConfig = GeminiGenerationConfig(responseMimeType = "application/json")
                )

                try {
                    val response = GeminiClient.service.generateContent(apiKey, request)
                    val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (jsonText != null) {
                        val adapter = moshi.adapter(BookSearchResponse::class.java)
                        val data = adapter.fromJson(cleanJsonString(jsonText))
                        if (data != null) {
                            _bookResults.value = data.results
                        } else {
                            _errorMessage.value = "Failed to parse search results. Please try again."
                        }
                    } else {
                        _errorMessage.value = "No response from search agent."
                    }
                } catch (e: Exception) {
                    Log.e("MediaViewModel", "Gemini book search failed, running fallback", e)
                    searchOfflineData(query)
                }
            }

            SearchTab.TORRENTS -> {
                val prompt = """
                    Find matches for the torrent/media/magnet search query: "$query".
                    Look for 1 to 5 matching files (movies, series, software, music, anime, or books) on major trackers and indexers like:
                    - General Torrent Indexers: The Pirate Bay, 1337x, RARBG (mirrors), Zooqle, Torlock, MagnetDL, Kickass Torrents (KAT/proxies), GloTorrents
                    - Dedicated Repacks & Media: YTS/YIFY (movies), EZTV (TV shows), TorrentGalaxy, LimeTorrents, Nyaa (anime/manga)
                    - Software/App repositories: GetIntoPC, KickassTo software section
                    - Streaming portals: 123Movies, FMovies, SolarMovie, Putlocker, Soap2day, GoMovies, Movierulz, Cmovies, Vumoo
                    Return safe standard magnets or custom redirect/search lookup links.
                    Return details strictly in this JSON schema:
                    {
                      "results": [
                        {
                          "title": "Exact Full Name of the Torrent Release",
                          "category": "Category (e.g. Movies, TV, Software, Anime, Music, Books)",
                          "size": "File size (e.g. 1.45 GB)",
                          "seeders": "Estimated seeders count",
                          "leechers": "Estimated leechers count",
                          "infoUrl": "Reference URL or search query link on LimeTorrents/PirateBay/1337x/Nyaa",
                          "magnetUrl": "magnet:?xt=urn:btih:... standard magnet format",
                          "description": "Information about release group, resolution (e.g. 1080p, x264), codec, language, audio, or instructions."
                        }
                      ]
                    }
                """.trimIndent()

                val request = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                    generationConfig = GeminiGenerationConfig(responseMimeType = "application/json")
                )

                try {
                    val response = GeminiClient.service.generateContent(apiKey, request)
                    val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (jsonText != null) {
                        val adapter = moshi.adapter(TorrentSearchResponse::class.java)
                        val data = adapter.fromJson(cleanJsonString(jsonText))
                        if (data != null) {
                            _torrentResults.value = data.results
                        } else {
                            _errorMessage.value = "Failed to parse torrent results. Please try again."
                        }
                    } else {
                        _errorMessage.value = "No response from search agent."
                    }
                } catch (e: Exception) {
                    Log.e("MediaViewModel", "Gemini torrent search failed, running fallback", e)
                    searchOfflineData(query)
                }
            }

            SearchTab.GAMES -> {
                val prompt = """
                    Find matches for the game/repack/emulator search query: "$query".
                    Look for 1 to 5 matching game download files, repositories, repacks, or web browser playable version portals.
                    Reference safe, trusted and well-known PC indexers/trackers and archives such as:
                    - PC Game Repackers/Releases: FitGirl Repacks, DODI Repacks, Online-Fix (multiplayer fixes), SteamUnlocked, IGG-Games, Skidrow Reloaded / SKIDROW Codex, CPY release mirrors
                    - Retro/Emulator directories: archive.org/details/softwarelibrary, itch.io, github.com (classic emulator projects and recreation repositories)
                    Format resourceUrl with direct search or download pages of these platforms.
                    Return details strictly in this JSON schema:
                    {
                      "results": [
                        {
                          "title": "Exact Name of the Game (including repack specs if applicable)",
                          "publisher": "Developer Studio / Release Group (e.g. FitGirl, DODI, SteamUnlocked)",
                          "platforms": "Original & Current platforms (e.g. PC, MS-DOS, Switch)",
                          "year": "Release/Repack Year",
                          "description": "Short overview of plot, gameplay specs, repack details (size, lossy details), and cracking group info.",
                          "isLegalFreeVersion": true,
                          "resourceType": "PC Repack, Web Emulator, Legal Abandonware, or Crack Fix",
                          "resourceUrl": "Safe external download or info query link",
                          "howToPlay": "Detailed step-by-step installation instructions: extracting archive, mounting ISO, copy/pasting crack, applying Online-Fix, or browser requisites."
                        }
                      ]
                    }
                """.trimIndent()

                val request = GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                    generationConfig = GeminiGenerationConfig(responseMimeType = "application/json")
                )

                try {
                    val response = GeminiClient.service.generateContent(apiKey, request)
                    val jsonText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                    if (jsonText != null) {
                        val adapter = moshi.adapter(GameSearchResponse::class.java)
                        val data = adapter.fromJson(cleanJsonString(jsonText))
                        if (data != null) {
                            _gameResults.value = data.results
                        } else {
                            _errorMessage.value = "Failed to parse game results. Please try again."
                        }
                    } else {
                        _errorMessage.value = "No response from search agent."
                    }
                } catch (e: Exception) {
                    Log.e("MediaViewModel", "Gemini game search failed, running fallback", e)
                    searchOfflineData(query)
                }
            }
            SearchTab.BOOKMARKS -> {}
            SearchTab.READ_LOG -> {}
        }
    }

    private fun cleanJsonString(input: String): String {
        // Strip markdown backticks if returned (e.g. ```json ... ```)
        var cleaned = input.trim()
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substringAfter("```json").substringBeforeLast("```")
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substringAfter("```").substringBeforeLast("```")
        }
        return cleaned.trim()
    }

    // --- Hardcoded Catalog of 12+ Beautiful Classics for Demonstration Mode ---

    private fun getMockBooks(): List<BookResult> {
        return listOf(
            BookResult(
                title = "The Great Gatsby",
                author = "F. Scott Fitzgerald",
                year = "1925",
                description = "An ultimate classic novel reflecting the Roaring Twenties, jazz, opulent wealth, and unrequited love in Long Island. It is now completely in the public domain globally.",
                isFreePdfAvailable = true,
                pdfResourceUrl = "https://gutenberg.org/ebooks/64317",
                accessMethod = "1. Visit Project Gutenberg (Ebook #64317).\n2. Download in PDF, EPUB, or read online, completely free and legal."
            ),
            BookResult(
                title = "Frankenstein",
                author = "Mary Shelley",
                year = "1818",
                description = "Mary Shelley's Gothic sci-fi masterpiece about creation, hubris, and what makes us human. Highly recommended. Out of copyright globally.",
                isFreePdfAvailable = true,
                pdfResourceUrl = "https://gutenberg.org/ebooks/84",
                accessMethod = "1. Seek Project Gutenberg (Ebook #84).\n2. Select your format of choice (MOBI, EPUB, or PDF reader) for immediate access."
            ),
            BookResult(
                title = "Pride and Prejudice",
                author = "Jane Austen",
                year = "1813",
                description = "The classic romantic comedy of manners about Elizabeth Bennet and Mr. Darcy. Completely free to read as a public domain eBook.",
                isFreePdfAvailable = true,
                pdfResourceUrl = "https://gutenberg.org/ebooks/1342",
                accessMethod = "1. Navigate to Project Gutenberg eBook #1342.\n2. Obtain a free legal PDF or read inside your phone browser."
            ),
            BookResult(
                title = "Dracula",
                author = "Bram Stoker",
                year = "1897",
                description = "The legendary Gothic horror novel that introduced Count Dracula. Rich atmospheric storytelling that defined modern vampire fiction.",
                isFreePdfAvailable = true,
                pdfResourceUrl = "https://gutenberg.org/ebooks/345",
                accessMethod = "1. Search Project Gutenberg book #345.\n2. Free ePUB, raw HTML, and high-quality PDF downloads are fully legal worldwide."
            ),
            BookResult(
                title = "Alice's Adventures in Wonderland",
                author = "Lewis Carroll",
                year = "1865",
                description = "The immortal children's fantasy novel detailing Alice falling through a rabbit hole into a whimsical, nonsensical underground realm.",
                isFreePdfAvailable = true,
                pdfResourceUrl = "https://gutenberg.org/ebooks/11",
                accessMethod = "1. Head to Project Gutenberg eBook #11.\n2. Instantly read online or download formatted PDF copy."
            ),
            BookResult(
                title = "The Adventures of Sherlock Holmes",
                author = "Arthur Conan Doyle",
                year = "1892",
                description = "A collection of twelve detective stories featuring Arthur Conan Doyle's legendary consulting detective. Highly engaging parlor mysteries.",
                isFreePdfAvailable = true,
                pdfResourceUrl = "https://gutenberg.org/ebooks/1661",
                accessMethod = "1. Visit the Project Gutenberg registry for eBook #1661.\n2. Download the legal PDF file containing all classic illustrations."
            )
        )
    }

    private fun getMockGames(): List<GameResult> {
        return listOf(
            GameResult(
                title = "Doom (1993 Shareware)",
                publisher = "id Software",
                platforms = "MS-DOS, PC",
                year = "1993",
                description = "The iconic first-person sci-fi shooter that revolutionized computer gaming with 3D graphics, multiplayer, and custom map support.",
                isLegalFreeVersion = true,
                resourceType = "Browser Emulator Archive",
                resourceUrl = "https://archive.org/details/doom-msdos",
                howToPlay = "1. Click the link to open the Game Archive on archive.org.\n2. Press 'Power' to boot the integrated DOSBox Emulator right in your web browser.\n3. Play instantly using keyboard/mouse without any setup!"
            ),
            GameResult(
                title = "Prince of Persia",
                publisher = "Jordan Mechner / Broderbund",
                platforms = "Apple II, MS-DOS, NES",
                year = "1989",
                description = "Legendary cinematic platformer featuring ground-breaking rotoscoped animations, puzzles, sword-fighting, and a 60-minute escape timer.",
                isLegalFreeVersion = true,
                resourceType = "Browser Emulator Archive",
                resourceUrl = "https://archive.org/details/msdos_Prince_of_Persia_1990",
                howToPlay = "1. Launch the Internet Archive Prince of Persia collection.\n2. Press spacebar to start the in-browser DOSBox system, and navigate obstacles with keyboard arrow keys."
            ),
            GameResult(
                title = "The Oregon Trail",
                publisher = "MECC",
                platforms = "Apple II, MS-DOS",
                year = "1990",
                description = "The beloved educational simulator tracking the hardships of a pioneer family traversing the trail in 1848, dealing with river crossings and dysentery.",
                isLegalFreeVersion = true,
                resourceType = "Browser Emulator Archive",
                resourceUrl = "https://archive.org/details/msdos_Oregon_Trail_The_1990",
                howToPlay = "1. Start the game via the Internet Archive MS-DOS portal.\n2. Play legally at no cost via your browser container!"
            ),
            GameResult(
                title = "SimCity Classic",
                publisher = "Will Wright / Maxis",
                platforms = "MS-DOS, Amiga, Macintosh",
                year = "1989",
                description = "The legendary city-building and planning simulation game that created a brand new genre, complete with natural disasters and taxation.",
                isLegalFreeVersion = true,
                resourceType = "Browser Emulator Archive",
                resourceUrl = "https://archive.org/details/msdos_SimCity_1989",
                howToPlay = "1. Boot the free emulator on Archive.org.\n2. Use standard mouse pointer on the digital interface to construct power lines, commercial zoning, and control budgets."
            ),
            GameResult(
                title = "Commander Keen: Invasion of the Vorticons",
                publisher = "id Software / Apogee",
                platforms = "MS-DOS, PC",
                year = "1990",
                description = "A famous platformer following Billy Blaze, an 8-year-old child genius who explores Mars to safeguard Earth from the Vorticons.",
                isLegalFreeVersion = true,
                resourceType = "Legal Shareware",
                resourceUrl = "https://archive.org/details/msdos_Commander_Keen_1_-_Invasion_of_the_Vorticons_1990",
                howToPlay = "1. Click to play on Archive.org's emulator.\n2. Use arrow keys to run, Alt to jump, Ctrl to bounce on your pogo-stick!"
            ),
            GameResult(
                title = "The Secret of Monkey Island",
                publisher = "LucasArts",
                platforms = "MS-DOS, PC, Amiga",
                year = "1990",
                description = "Atmospheric point-and-click adventure following Guybrush Threepwood in his quest to become a fearsome pirate on Melee Island.",
                isLegalFreeVersion = true,
                resourceType = "Browser Emulator Archive",
                resourceUrl = "https://archive.org/details/msdos_Secret_of_Monkey_Island_The_1990",
                howToPlay = "1. Visit the MS-DOS library on Internet Archive.\n2. Emulate directly with sound card support or use the open-source SCUMMVM framework to run retro game file backups legally."
            )
        )
    }

    private fun getMockTorrents(): List<TorrentResult> {
        return listOf(
            TorrentResult(
                title = "Ubuntu 24.04 LTS Desktop (Noble Numbat) ISO",
                category = "Software / Operating System",
                size = "4.15 GB",
                seeders = "1420",
                leechers = "45",
                infoUrl = "https://torrent.ubuntu.com/",
                magnetUrl = "magnet:?xt=urn:btih:3fa81b83df8a011bf49ec2a1f4961d76378eccc2&dn=ubuntu-24.04-desktop-amd64.iso",
                description = "Official Ubuntu Linux operating system desktop DVD image. Free, secure, highly customizable, and open-source."
            ),
            TorrentResult(
                title = "Sintel (2010 Open Source Movie) [1080p] [x264]",
                category = "Movies / Creative Commons",
                size = "1.20 GB",
                seeders = "310",
                leechers = "12",
                infoUrl = "https://durian.blender.org/",
                magnetUrl = "magnet:?xt=urn:btih:7a37df32f1441a4a4282054c256038479eebbcf4&dn=Sintel.2010.1080p.mkv",
                description = "The beautiful fantasy computer-animated short movie produced by the Blender Foundation. Released under the Creative Commons Attribution license."
            ),
            TorrentResult(
                title = "Night of the Living Dead (1968) [1080p] [BluRay] - Public Domain",
                category = "Movies / Public Domain Classics",
                size = "1.85 GB",
                seeders = "480",
                leechers = "22",
                infoUrl = "https://archive.org/details/night_of_the_living_dead",
                magnetUrl = "magnet:?xt=urn:btih:b5ad78a634568e6fbc66bc42a27d78dbafc8491d&dn=Night_of_the_Living_Dead_1968_1080p.mp4",
                description = "George A. Romero's revolutionary zombie horror classic. Celebrated worldwide, completely free of copyright worldwide."
            ),
            TorrentResult(
                title = "Blender Big Buck Bunny Short Film [2160p] [60fps]",
                category = "Movies / Creative Commons",
                size = "3.20 GB",
                seeders = "120",
                leechers = "5",
                infoUrl = "https://peach.blender.org/",
                magnetUrl = "magnet:?xt=urn:btih:dd8255ecdc7ca55fb0bbf81323d87062db1f6d1c&dn=BigBuckBunny_2160p_60fps.mp4",
                description = "Blender Foundation animated masterpiece starring a giant rabbit getting revenge on mischievous forest squirrels. Creative Commons Attribution 3.0."
            ),
            TorrentResult(
                title = "Tears of Steel (VFX Open Movie) [4K UltraHD] [x265]",
                category = "Movies / Science Fiction",
                size = "2.65 GB",
                seeders = "230",
                leechers = "8",
                infoUrl = "https://mango.blender.org/",
                magnetUrl = "magnet:?xt=urn:btih:08a478954316a7fbc66bc42a27d78dbafc849f1d&dn=Tears_Of_Steel_4K.mkv",
                description = "Stunning science fiction VFX open movie featuring futuristic warfare, robots, and giant alien portals set in Amsterdam. CC BY 3.0."
            ),
            TorrentResult(
                title = "Debian GNU/Linux 12.5.0 netinst x86_64",
                category = "Software / Linux Kernel",
                size = "628 MB",
                seeders = "2150",
                leechers = "35",
                infoUrl = "https://www.debian.org/",
                magnetUrl = "magnet:?xt=urn:btih:1fa478b54316a7fbc66bc42a27d78dbafc849d1e&dn=debian-12.5.0-amd64-netinst.iso",
                description = "Universal open-source kernel installer offering absolute stability, thousands of free packages, and total security."
            )
        )
    }

    fun saveReadBook(title: String, author: String, rating: Int, notes: String, dateRead: String) {
        firebaseSync.saveReadBook(title, author, rating, notes, dateRead)
    }

    fun deleteReadBook(bookId: String) {
        firebaseSync.deleteReadBook(bookId)
    }

    override fun onCleared() {
        super.onCleared()
        firebaseSync.cleanup()
    }
}

class MediaViewModelFactory(
    private val application: Application,
    private val repository: MediaRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MediaViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MediaViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
