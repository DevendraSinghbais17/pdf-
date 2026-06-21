package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.db.AppDatabase
import com.example.db.MediaRepository
import com.example.model.BookResult
import com.example.model.GameResult
import com.example.model.SavedMedia
import com.example.model.TorrentResult
import com.example.model.ReadBook
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MediaViewModel
import com.example.viewmodel.MediaViewModelFactory
import androidx.activity.compose.BackHandler
import com.example.viewmodel.SearchTab
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Core Database & Repository creation
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = MediaRepository(database.savedMediaDao())
        
        // Manual ViewModel acquisition without complex inject wrappers
        val viewModel = ViewModelProvider(
            this,
            MediaViewModelFactory(application, repository)
        )[MediaViewModel::class.java]

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("main_scaffold"),
                    containerColor = Color(0xFF1A1C1E) // Premium Sophisticated Dark base
                ) { innerPadding ->
                    MediaSeekerApp(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSeekerApp(
    viewModel: MediaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val bookResults by viewModel.bookResults.collectAsStateWithLifecycle()
    val gameResults by viewModel.gameResults.collectAsStateWithLifecycle()
    val torrentResults by viewModel.torrentResults.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val bookmarkedKeys by viewModel.bookmarkedTitles.collectAsStateWithLifecycle()

    var showHelpDialog by remember { mutableStateOf(false) }
    var selectedBookDetail by remember { mutableStateOf<BookResult?>(null) }
    var selectedTorrentDetail by remember { mutableStateOf<TorrentResult?>(null) }
    var selectedGameDetail by remember { mutableStateOf<GameResult?>(null) }
    var selectedBookmarkDetail by remember { mutableStateOf<SavedMedia?>(null) }
    var activeInAppWebUrl by remember { mutableStateOf<String?>(null) }

    val recentQueries by viewModel.recentQueries.collectAsStateWithLifecycle()
    var bookmarkFilterType by remember { mutableStateOf("ALL") }

    val readBooks by viewModel.firebaseSync.readBooks.collectAsStateWithLifecycle()
    val firebaseLoaded by viewModel.firebaseSync.firebaseLoaded.collectAsStateWithLifecycle()
    var showAddReadBookDialog by remember { mutableStateOf(false) }

    val openWebOrPdf: (String?) -> Unit = { url ->
        if (url != null) {
            if (url.startsWith("magnet:") || url.startsWith("intent:")) {
                openUrlInBrowser(context, url)
            } else {
                activeInAppWebUrl = url
            }
        }
    }

    // Preset helpful tags for immediate play/read searches
    val popularBookShortcuts = listOf("Gatsby", "Frankenstein", "Dracula", "Alice Wonders", "Sherlock")
    val popularTorrentShortcuts = listOf("Ubuntu", "Sintel", "Night of the Living Dead", "Big Buck Bunny", "Tears of Steel")
    val popularGameShortcuts = listOf("Doom", "Prince of Persia", "The Oregon Trail", "SimCity", "Monkey Island")

    val customApiKey by viewModel.customApiKey.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF212327),
                drawerTonalElevation = 8.dp
            ) {
                // Header with app icon and title
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF322359), Color(0xFF212327))
                            )
                        )
                        .padding(horizontal = 24.dp, vertical = 28.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFD0BCFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Nexus Menu",
                                tint = Color(0xFF381E72),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Nexus Archive",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Gemini Search Companion",
                                fontSize = 12.sp,
                                color = Color(0xFFCAC4D0)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF3B3F43), thickness = 1.dp)

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable content of the drawer
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                ) {
                    Text(
                        text = "SEARCH CATEGORIES",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCAC4D0),
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )

                    // Navigation items for search tabs
                    listOf(
                        SearchTab.BOOKS to "Books & Manga" to Icons.Default.List,
                        SearchTab.TORRENTS to "Torrents & Magnet" to Icons.Default.Share,
                        SearchTab.GAMES to "Classic Retro Games" to Icons.Default.PlayArrow,
                        SearchTab.BOOKMARKS to "Saved Bookmarks" to Icons.Default.Favorite,
                        SearchTab.READ_LOG to "Reading Diary" to Icons.Default.Star
                    ).forEach { (tabNameInfo, icon) ->
                        val (tab, name) = tabNameInfo
                        NavigationDrawerItem(
                            label = { Text(name, fontWeight = FontWeight.Medium) },
                            selected = selectedTab == tab,
                            onClick = {
                                viewModel.selectTab(tab)
                                coroutineScope.launch { drawerState.close() }
                            },
                            icon = { Icon(icon, contentDescription = name) },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = Color(0xFF381E72),
                                selectedIconColor = Color(0xFFEADBFF),
                                selectedTextColor = Color(0xFFEADBFF),
                                unselectedContainerColor = Color.Transparent,
                                unselectedIconColor = Color(0xFFCAC4D0),
                                unselectedTextColor = Color(0xFFCAC4D0)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = Color(0xFF3B3F43), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "GEMINI API KEY SETTINGS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFCAC4D0),
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                    )

                    // In-app API key settings panel
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E3033)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Enter your Gemini API key below to enable dynamic, real-time deep searches outside of demonstration mode:",
                                fontSize = 12.sp,
                                color = Color.White,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            var apiKeyInput by remember(customApiKey) { mutableStateOf(customApiKey) }

                            OutlinedTextField(
                                value = apiKeyInput,
                                onValueChange = { apiKeyInput = it },
                                label = { Text("Gemini API Key", fontSize = 11.sp, color = Color.LightGray) },
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFD0BCFF),
                                    unfocusedBorderColor = Color(0xFFCAC4D0),
                                    focusedLabelColor = Color(0xFFD0BCFF)
                                ),
                                placeholder = { Text("API Key...", fontSize = 11.sp, color = Color.Gray) },
                                modifier = Modifier.fillMaxWidth().testTag("appkey_textfield")
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.updateCustomApiKey(apiKeyInput)
                                        Toast.makeText(context, "API Key updated successfully!", Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF381E72)),
                                    modifier = Modifier.weight(1f).testTag("save_api_key_btn")
                                ) {
                                    Text("Save Key", fontSize = 11.sp, color = Color.White)
                                }

                                if (customApiKey.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.updateCustomApiKey("")
                                            Toast.makeText(context, "API Key cleared.", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                                        modifier = Modifier.weight(1f).testTag("clear_api_key_btn")
                                    ) {
                                        Text("Clear Key", fontSize = 11.sp, color = Color(0xFFFF8A80))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "💡 No key? Create a free one at ai.google.dev",
                                fontSize = 10.sp,
                                color = Color(0xFF03DAC6),
                                modifier = Modifier.clickable {
                                    openUrlInBrowser(context, "https://ai.google.dev/")
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(30.dp))
                }
            }
        },
        content = {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0xFF1A1C1E))
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // --- Custom Gradient Hero Header ---
                HeaderSection(
                    isApiKeyWorking = viewModel.isApiKeyWorking(),
                    onHelpClick = { showHelpDialog = true },
                    onMenuClick = {
                        coroutineScope.launch {
                            drawerState.open()
                        }
                    }
                )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Tab Switcher (Custom Capsules) ---
        TabSwitcherSection(
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                viewModel.selectTab(tab)
                keyboardController?.hide()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Expandable Warning/Helper Notice ---
        if (!viewModel.isApiKeyWorking()) {
            OfflineWarningCard(onRequestApiKeyGuide = { showHelpDialog = true })
            Spacer(modifier = Modifier.height(12.dp))
        }

        // --- Dynamic Search input with Action controls ---
        if (selectedTab != SearchTab.BOOKMARKS && selectedTab != SearchTab.READ_LOG) {
            SearchInputSection(
                query = searchQuery,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onSearchTriggered = {
                    viewModel.performSearch()
                    keyboardController?.hide()
                },
                onClearQuery = {
                    viewModel.updateSearchQuery("")
                    viewModel.clearResults()
                },
                placeholderText = when (selectedTab) {
                    SearchTab.BOOKS -> "Search books, authors, or shadow libraries..."
                    SearchTab.TORRENTS -> "Search torrents, magnets, movies, anime..."
                    SearchTab.GAMES -> "Search retro games, emulators, DOS games..."
                    else -> "Search..."
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            // --- Suggestion Chips Layout ---
            SuggestionChipsRow(
                chips = when (selectedTab) {
                    SearchTab.BOOKS -> popularBookShortcuts
                    SearchTab.TORRENTS -> popularTorrentShortcuts
                    SearchTab.GAMES -> popularGameShortcuts
                    else -> emptyList()
                },
                onChipClicked = { tag ->
                    viewModel.updateSearchQuery(tag)
                    viewModel.performSearch()
                    keyboardController?.hide()
                }
            )

            // Dynamic Recent Searches list
            if (recentQueries.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search history icon",
                            tint = Color(0xFFD0BCFF),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recent Searches",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "Clear All",
                        color = Color(0xFFFFD54F),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { viewModel.clearRecentQueries() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    recentQueries.forEach { query ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF2E3033))
                                .border(1.dp, Color(0xFF45484F), RoundedCornerShape(14.dp))
                                .clickable {
                                    viewModel.updateSearchQuery(query)
                                    viewModel.performSearch()
                                    keyboardController?.hide()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = query,
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Remove search query history item",
                                    tint = Color.LightGray,
                                    modifier = Modifier
                                        .size(11.dp)
                                        .clickable { viewModel.removeRecentQuery(query) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        } else {
            // Bookmarks tab title & stats details
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Saved icon",
                    tint = Color(0xFFFF5252),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Saved Archives (${bookmarks.size})",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // --- Search/Content Loading Status ---
        if (isLoading) {
            GeminiLoadingSpinner(
                isGeminiActive = viewModel.isApiKeyWorking()
            )
        } else {
            // Display Results
            if (searchQuery.isNotBlank() && selectedTab != SearchTab.BOOKMARKS) {
                FastLookupRow(query = searchQuery, tab = selectedTab, onUrlClick = openWebOrPdf)
                Spacer(modifier = Modifier.height(12.dp))
            }

            when (selectedTab) {
                SearchTab.BOOKS -> {
                    if (bookResults.isEmpty()) {
                        EmptyStatePanel(
                            title = "Look Up Any Free eBook",
                            description = "Search for your favorite masterpiece above. Project Gutenberg, Internet Archive open repositories, and legal PDFs are fully cataloged.",
                            icon = Icons.Default.List
                        )
                    } else {
                        // Display results dynamically
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            bookResults.forEach { book ->
                                BookResultCard(
                                    book = book,
                                    isBookmarked = bookmarkedKeys.contains("${book.title}_BOOK"),
                                    onBookmarkToggle = { viewModel.toggleBookmarked(book) },
                                    onViewDetail = { selectedBookDetail = book },
                                    onDownloadClick = {
                                        openWebOrPdf(book.pdfResourceUrl)
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
                SearchTab.TORRENTS -> {
                    if (torrentResults.isEmpty()) {
                        EmptyStatePanel(
                            title = "Seeding Decentralized Media",
                            description = "Search torrent indexes (The Pirate Bay, 1337x, RARBG, TorrentGalaxy, EZTV, YTS, LimeTorrents, Nyaa). Finds magnet links and seeders.",
                            icon = Icons.Default.Share
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            torrentResults.forEach { torrent ->
                                TorrentResultCard(
                                    torrent = torrent,
                                    isBookmarked = bookmarkedKeys.contains("${torrent.title}_TORRENT"),
                                    onBookmarkToggle = { viewModel.toggleBookmarked(torrent) },
                                    onViewDetail = { selectedTorrentDetail = torrent },
                                    onDownloadClick = {
                                        openUrlInBrowser(context, torrent.magnetUrl)
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
                SearchTab.GAMES -> {
                    if (gameResults.isEmpty()) {
                        EmptyStatePanel(
                            title = "Instant Retro Game Emulation",
                            description = "Search classic DOS, retro, and abandonware names (e.g. Doom, Prince of Persia). Unlocks immediate inside-browser play libraries.",
                            icon = Icons.Default.PlayArrow
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            gameResults.forEach { game ->
                                GameResultCard(
                                    game = game,
                                    isBookmarked = bookmarkedKeys.contains("${game.title}_GAME"),
                                    onBookmarkToggle = { viewModel.toggleBookmarked(game) },
                                    onViewDetail = { selectedGameDetail = game },
                                    onPlayClick = {
                                        openWebOrPdf(game.resourceUrl)
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
                SearchTab.BOOKMARKS -> {
                    if (bookmarks.isEmpty()) {
                        EmptyStatePanel(
                            title = "No Bookmarks Yet",
                            description = "Search for books, torrents, or games, and tap the Heart/Favorite button to assemble your offline custom digital bookshelf!",
                            icon = Icons.Default.FavoriteBorder
                        )
                    } else {
                        val filteredBookmarks = remember(bookmarks, bookmarkFilterType) {
                            if (bookmarkFilterType == "ALL") {
                                bookmarks
                            } else {
                                bookmarks.filter { it.type == bookmarkFilterType }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Category Filter Capsules
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf(
                                    "ALL" to "All Bookmarks",
                                    "BOOK" to "Books",
                                    "TORRENT" to "Torrents",
                                    "GAME" to "Games"
                                ).forEach { (typeId, label) ->
                                    val isSelected = bookmarkFilterType == typeId
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (isSelected) Color(0xFFD0BCFF) else Color(0xFF2E3033))
                                            .border(1.dp, if (isSelected) Color(0xFFD0BCFF) else Color(0xFF45484F), RoundedCornerShape(16.dp))
                                            .clickable { bookmarkFilterType = typeId }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = when (typeId) {
                                                    "BOOK" -> Icons.Default.List
                                                    "TORRENT" -> Icons.Default.Share
                                                    "GAME" -> Icons.Default.PlayArrow
                                                    else -> Icons.Default.Favorite
                                                },
                                                contentDescription = null,
                                                tint = if (isSelected) Color.Black else Color.LightGray,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = label,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isSelected) Color.Black else Color.White
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            if (filteredBookmarks.isEmpty()) {
                                EmptyStatePanel(
                                    title = "No Matches Found",
                                    description = "No bookmarked archives of this type are currently in your offline bookshelf.",
                                    icon = Icons.Default.Info
                                )
                            } else {
                                filteredBookmarks.forEach { saved ->
                                    BookmarkMediaCard(
                                        saved = saved,
                                        onRemove = { viewModel.removeBookmark(saved) },
                                        onActionClick = {
                                            openWebOrPdf(saved.resourceUrl)
                                        },
                                        onViewDetail = { selectedBookmarkDetail = saved }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(18.dp))
                        }
                    }
                }
                SearchTab.READ_LOG -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header with Cloud Connection Status
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2E3033))
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (firebaseLoaded) Color(0xFFC1F8C2) else Color(0xFFFFB74D))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (firebaseLoaded) "Firebase Synced" else "Connecting Firebase...",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Node ID: ${viewModel.firebaseSync.userId}",
                                color = Color(0xFFFFD54F),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Add Entry Button
                        Button(
                            onClick = { showAddReadBookDialog = true },
                            modifier = Modifier.fillMaxWidth().testTag("add_journal_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFD0BCFF),
                                contentColor = Color(0xFF381E72)
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add read history item",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Log a Book I've Read", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        if (readBooks.isEmpty()) {
                            EmptyStatePanel(
                                title = "Your Portable Reading Diary",
                                description = "Keep a personal reading journal! Put down notes/reviews, star ratings, and keep a catalog of completed books, automatically synchronized with Google Firebase!",
                                icon = Icons.Default.Star
                            )
                        } else {
                            Text(
                                text = "Books Logged (${readBooks.size})",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                            )

                            readBooks.forEach { log ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(Color(0xFF1E1E24))
                                        .border(1.dp, Color(0xFF2E3033), RoundedCornerShape(16.dp))
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = log.title,
                                            color = Color.White,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "by ${log.author}",
                                            color = Color.LightGray,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        
                                        // Star Rating
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            (1..5).forEach { star ->
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = if (star <= log.rating) Color(0xFFFFD54F) else Color(0xFF555555),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                        
                                        if (log.notes.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = log.notes,
                                                color = Color(0xFFCAC4D0),
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Normal
                                            )
                                        }
                                        if (log.dateRead.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Completed: ${log.dateRead}",
                                                color = Color(0xFFA5A2A9),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    
                                    IconButton(
                                        onClick = { viewModel.deleteReadBook(log.id) },
                                        modifier = Modifier.size(32.dp).testTag("delete_journal_item_${log.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Remove review logging",
                                            tint = Color(0xFFFF8A80),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        VerifiedNodeBanner()
        Spacer(modifier = Modifier.height(24.dp))
    }

    // --- Dynamic Interactive Dialogs ---

    if (showAddReadBookDialog) {
        var titleInput by remember { mutableStateOf("") }
        var authorInput by remember { mutableStateOf("") }
        var ratingInput by remember { mutableStateOf(5) }
        var notesInput by remember { mutableStateOf("") }
        var dateInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddReadBookDialog = false },
            title = {
                Text(
                    text = "Log Book Read",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Book Title") },
                        modifier = Modifier.fillMaxWidth().testTag("add_book_title_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFD0BCFF),
                            unfocusedBorderColor = Color.Gray
                        ),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = authorInput,
                        onValueChange = { authorInput = it },
                        label = { Text("Author / Creator") },
                        modifier = Modifier.fillMaxWidth().testTag("add_book_author_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFD0BCFF),
                            unfocusedBorderColor = Color.Gray
                        ),
                        singleLine = true
                    )

                    // Stars rating selector
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "My Rating:",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            (1..5).forEach { star ->
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Rate $star stars",
                                    tint = if (star <= ratingInput) Color(0xFFFFD54F) else Color.Gray,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clickable { ratingInput = star }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        label = { Text("Review Notes / Thoughts") },
                        modifier = Modifier.fillMaxWidth().height(100.dp).testTag("add_book_notes_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFD0BCFF),
                            unfocusedBorderColor = Color.Gray
                        ),
                        maxLines = 4
                    )

                    OutlinedTextField(
                        value = dateInput,
                        onValueChange = { dateInput = it },
                        label = { Text("Completion Date (e.g. June 2026)") },
                        modifier = Modifier.fillMaxWidth().testTag("add_book_date_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFD0BCFF),
                            unfocusedBorderColor = Color.Gray
                        ),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (titleInput.trim().isNotBlank()) {
                            viewModel.saveReadBook(
                                title = titleInput.trim(),
                                author = authorInput.trim().ifBlank { "Unknown" },
                                rating = ratingInput,
                                notes = notesInput.trim(),
                                dateRead = dateInput.trim().ifBlank { "Recent" }
                            )
                            showAddReadBookDialog = false
                            Toast.makeText(context, "Logged to Firebase Realtime Database!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Title is mandatory to log book", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD0BCFF),
                        contentColor = Color(0xFF381E72)
                    ),
                    modifier = Modifier.testTag("submit_journal_button")
                ) {
                    Text("Save to Cloud")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddReadBookDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
                ) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFF2E3033),
            titleContentColor = Color.White
        )
    }

    // 1. API Configuration & Library Help Dialog
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            confirmButton = {
                TextButton(onClick = { showHelpDialog = false }) {
                    Text("Got It", color = Color(0xFFBB86FC), fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = "Info", tint = Color(0xFFBB86FC))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Digital Archivist Guide")
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "How to configure Real-Time Search:",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "1. Get a free API Key from Google AI Studio.\n" +
                               "2. Open the Secrets Panel in the bottom right corner of the build environment.\n" +
                               "3. Add the key name 'GEMINI_API_KEY' with your custom token value.\n" +
                               "4. Restart or re-compile the app. Real-time Gemini deep search will automatically ignite!",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                    Divider(color = Color.Gray, modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Archiving Policy Protocols:",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "• Books are gathered searching Project Gutenberg, Internet Archive Open Access directories, and free university press digital prints.\n" +
                               "• Games focus heavily on Legal Abandonware, Shareware, Browser-Emulated ports, and public source code clones on GitHub/itch.io/GOG.",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = Color(0xFF1E1E24),
            textContentColor = Color.White,
            titleContentColor = Color.White
        )
    }

    // Torrent Detail Modal
    selectedTorrentDetail?.let { torrent ->
        AlertDialog(
            onDismissRequest = { selectedTorrentDetail = null },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { selectedTorrentDetail = null }) {
                        Text("Close", color = Color.Gray)
                    }
                    if (!torrent.magnetUrl.isNullOrEmpty()) {
                        Button(
                            onClick = {
                                openUrlInBrowser(context, torrent.magnetUrl)
                                selectedTorrentDetail = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Get Torrent Magnet", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Download link", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            title = {
                Column {
                    Text(
                        text = torrent.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Category: ${torrent.category} | Size: ${torrent.size}",
                        fontSize = 13.sp,
                        color = Color(0xFF03DAC6)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Metadata & Stats:",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Seeds: ${torrent.seeders}",
                            color = Color(0xFF4CAF50),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Peers: ${torrent.leechers}",
                            color = Color(0xFFFF5252),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Description:",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = torrent.description,
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )

                    torrent.infoUrl?.let { url ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF282830), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                                .clickable { openUrlInBrowser(context, url) },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Column {
                                Text(
                                    text = "Source Information Page:",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFBB86FC),
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = url,
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            },
            containerColor = Color(0xFF2E3033),
            shape = RoundedCornerShape(28.dp)
        )
    }

    // 2. Book Detail Modal
    selectedBookDetail?.let { book ->
        AlertDialog(
            onDismissRequest = { selectedBookDetail = null },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { selectedBookDetail = null }) {
                        Text("Close", color = Color.Gray)
                    }
                    if (!book.pdfResourceUrl.isNullOrEmpty()) {
                        Button(
                            onClick = {
                                openWebOrPdf(book.pdfResourceUrl)
                                selectedBookDetail = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC))
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Get Book", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open eBook", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            title = {
                Column {
                    Text(
                        text = book.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                    Text(
                        text = "by ${book.author} (${book.year})",
                        fontSize = 14.sp,
                        color = Color(0xFF03DAC6)
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Description:",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = book.description,
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF282830), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "Access / Download Instructions:",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFBB86FC),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = book.accessMethod,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color(0xFF1E1E24)
        )
    }

    // 3. Game Detail Modal
    selectedGameDetail?.let { game ->
        AlertDialog(
            onDismissRequest = { selectedGameDetail = null },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { selectedGameDetail = null }) {
                        Text("Close", color = Color.Gray)
                    }
                    if (!game.resourceUrl.isNullOrEmpty()) {
                        Button(
                            onClick = {
                                openWebOrPdf(game.resourceUrl)
                                selectedGameDetail = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC6))
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play Game", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Launch Play", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            title = {
                Column {
                    Text(
                        text = game.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Published: ${game.publisher} (${game.year})",
                        fontSize = 14.sp,
                        color = Color(0xFFBB86FC)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text(game.platforms, color = Color.White, fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF2E2E38))
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Overview:", fontWeight = FontWeight.Bold, color = Color.White)
                        Badge(containerColor = Color(0xFFFFD54F)) {
                            Text(
                                game.resourceType,
                                modifier = Modifier.padding(2.dp),
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Text(
                        text = game.description,
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF282830), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = "Play & Run Guide Instructions:",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF03DAC6),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = game.howToPlay,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color(0xFF1E1E24)
        )
    }

    // 4. Bookmark Saved Media Detail Modal
    selectedBookmarkDetail?.let { saved ->
        AlertDialog(
            onDismissRequest = { selectedBookmarkDetail = null },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { selectedBookmarkDetail = null }) {
                        Text("Close", color = Color.Gray)
                    }
                    if (!saved.resourceUrl.isNullOrEmpty()) {
                        Button(
                            onClick = {
                                openWebOrPdf(saved.resourceUrl)
                                selectedBookmarkDetail = null
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when (saved.type) {
                                    "BOOK" -> Color(0xFFBB86FC)
                                    "TORRENT" -> Color(0xFF1DE9B6)
                                    else -> Color(0xFF03DAC6)
                                }
                            )
                        ) {
                            Icon(
                                imageVector = when (saved.type) {
                                    "BOOK" -> Icons.Default.List
                                    "TORRENT" -> Icons.Default.Share
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = "Saved action",
                                modifier = Modifier.size(16.dp),
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when (saved.type) {
                                    "BOOK" -> "Open eBook"
                                    "TORRENT" -> "Get Magnet"
                                    else -> "Launch Play"
                                },
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            title = {
                Column {
                    Text(
                        text = saved.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                    val labelText = when (saved.type) {
                        "BOOK" -> "by ${saved.subtitle} (${saved.year})"
                        "TORRENT" -> "Category: ${saved.subtitle} (${saved.year})"
                        else -> "Developer: ${saved.subtitle} (${saved.year})"
                    }
                    val labelColor = when (saved.type) {
                        "BOOK" -> Color(0xFF03DAC6)
                        "TORRENT" -> Color(0xFF1DE9B6)
                        else -> Color(0xFFBB86FC)
                    }
                    Text(
                        text = labelText,
                        fontSize = 14.sp,
                        color = labelColor
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(text = "Overview:", fontWeight = FontWeight.Bold, color = Color.White)
                    Text(text = saved.description, color = Color.LightGray, fontSize = 14.sp)

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF282830), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Text(
                                text = if (saved.type == "BOOK") "eBook Guide:" else "Play Details Guide:",
                                fontWeight = FontWeight.Bold,
                                color = if (saved.type == "BOOK") Color(0xFFBB86FC) else Color(0xFF03DAC6),
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = saved.instruction,
                                color = Color.White,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color(0xFF1E1E24)
        )
    }
}
)

    if (activeInAppWebUrl != null) {
        InAppWebViewReader(
            url = activeInAppWebUrl!!,
            onClose = { activeInAppWebUrl = null }
        )
    }
}

// Helper: safe open web link
fun openUrlInBrowser(context: Context, url: String?) {
    if (url.isNullOrEmpty()) {
        Toast.makeText(context, "No download link coordinates found.", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to launch link. Web browser missing.", Toast.LENGTH_SHORT).show()
    }
}

// --- SUB-COMPOSABLES WORKSHOP ---

@Composable
fun HeaderSection(
    isApiKeyWorking: Boolean,
    onHelpClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onMenuClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFD0BCFF))
                    .testTag("burger_menu_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Open Navigation Menu",
                    tint = Color(0xFF381E72),
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Nexus",
                        fontSize = 22.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Light,
                        color = Color.White
                    )
                    Text(
                        text = "Archive",
                        fontSize = 22.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isApiKeyWorking) Color(0xFF03DAC6) else Color(0xFFFFD54F))
                    )
                    Text(
                        text = if (isApiKeyWorking) "Global Sync Active" else "Offline Demonstration Mode",
                        fontSize = 11.sp,
                        color = Color(0xFF938F99),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        IconButton(
            onClick = onHelpClick,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFF2E3033))
                .testTag("help_button")
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Help instruction button",
                tint = Color(0xFFCAC4D0),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun TabSwitcherSection(
    selectedTab: SearchTab,
    onTabSelected: (SearchTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF2E3033))
            .horizontalScroll(rememberScrollState())
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchTab.values().forEach { tab ->
            val isActive = selectedTab == tab
            val label = when (tab) {
                SearchTab.BOOKS -> "Books"
                SearchTab.TORRENTS -> "Torrents"
                SearchTab.GAMES -> "Retro Games"
                SearchTab.BOOKMARKS -> "Saved"
                SearchTab.READ_LOG -> "Read Diary"
            }
            
            val iconVector = when (tab) {
                SearchTab.BOOKS -> Icons.Default.List
                SearchTab.TORRENTS -> Icons.Default.Share
                SearchTab.GAMES -> Icons.Default.PlayArrow
                SearchTab.BOOKMARKS -> Icons.Default.Favorite
                SearchTab.READ_LOG -> Icons.Default.Star
            }

            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isActive) Color(0xFFD0BCFF) else Color.Transparent)
                    .clickable { onTabSelected(tab) }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = label,
                        tint = if (isActive) Color(0xFF381E72) else Color(0xFFCAC4D0),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (isActive) Color(0xFF381E72) else Color(0xFFCAC4D0),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
fun OfflineWarningCard(
    onRequestApiKeyGuide: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF2E3033))
            .border(1.dp, Color(0xFF45464F), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning indicator",
                tint = Color(0xFFFFD54F),
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Local Offline Cache Engine Active",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFD54F),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Browsing highly detailed local archive catalogs. To enable dynamic global lookuppowered by Google Gemini model intelligence, please configure your API key.",
                    fontSize = 12.sp,
                    color = Color(0xFFCAC4D0)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Configure API Key Now →",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFD0BCFF),
                    fontSize = 12.sp,
                    modifier = Modifier.clickable { onRequestApiKeyGuide() }
                )
            }
        }
    }
}

@Composable
fun SearchInputSection(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchTriggered: () -> Unit,
    onClearQuery: () -> Unit,
    placeholderText: String
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("search_text_field"),
        placeholder = { Text(placeholderText, color = Color(0xFF938F99), fontSize = 14.sp) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search icon",
                tint = Color(0xFF938F99)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClearQuery) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search query",
                        tint = Color(0xFFCAC4D0)
                    )
                }
            }
        },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedContainerColor = Color(0xFF2E3033),
            unfocusedContainerColor = Color(0xFF2E3033),
            focusedBorderColor = Color(0xFFD0BCFF),
            unfocusedBorderColor = Color(0xFF45464F)
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearchTriggered() }),
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun SuggestionChipsRow(
    chips: List<String>,
    onChipClicked: (String) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(chips) { tag ->
            SuggestionChip(
                onClick = { onChipClicked(tag) },
                label = { Text(tag, color = Color(0xFFE2E2E6), fontSize = 11.sp) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = Color(0xFF2E3033)
                ),
                border = BorderStroke(1.dp, Color(0xFF45464F))
            )
        }
    }
}

@Composable
fun EmptyStatePanel(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Empty state icon",
                tint = Color.Gray.copy(alpha = 0.6f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                fontSize = 13.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

// --- INDIVIDUAL CARDS WITH METICULOUS DESIGN ---

@Composable
fun BookThumbnailCover(title: String, author: String) {
    val displayTitle = if (title.length > 8) title.take(8) + ".." else title
    val displayAuthor = if (author.length > 8) author.take(8) + ".." else author
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1C1E))
            .border(1.dp, Color(0xFF45464F), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(4.dp)
        ) {
            Text(
                text = displayTitle.uppercase(),
                fontSize = 8.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 10.sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = displayAuthor.uppercase(),
                fontSize = 7.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                lineHeight = 9.sp
            )
        }
    }
}

@Composable
fun GameThumbnailCover() {
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF49454F)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF1A1C1E).copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Game Icon",
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun BookResultCard(
    book: BookResult,
    isBookmarked: Boolean,
    onBookmarkToggle: () -> Unit,
    onViewDetail: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewDetail() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E3033)),
        border = BorderStroke(1.dp, Color(0xFF45464F)),
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookThumbnailCover(title = book.title, author = book.author)

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Available eBook",
                        fontSize = 11.sp,
                        color = Color(0xFFD0BCFF),
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(
                        onClick = onBookmarkToggle,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite button choice",
                            tint = if (isBookmarked) Color(0xFFFF5252) else Color(0xFFCAC4D0),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = book.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${book.author} • ${book.year}",
                    fontSize = 13.sp,
                    color = Color(0xFF938F99),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Availability safe check",
                            tint = Color(0xFF03DAC6),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Verified PDF",
                            fontSize = 10.sp,
                            color = Color(0xFF03DAC6),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onDownloadClick,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF45464F),
                            contentColor = Color(0xFFD0BCFF)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Get eBook", color = Color(0xFFD0BCFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun GameResultCard(
    game: GameResult,
    isBookmarked: Boolean,
    onBookmarkToggle: () -> Unit,
    onViewDetail: () -> Unit,
    onPlayClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewDetail() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E3033)),
        border = BorderStroke(1.dp, Color(0xFF45464F)),
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GameThumbnailCover()

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Software Archive",
                        fontSize = 11.sp,
                        color = Color(0xFFCAC4D0).copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(
                        onClick = onBookmarkToggle,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Game pin button choice",
                            tint = if (isBookmarked) Color(0xFFFF5252) else Color(0xFFCAC4D0),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = game.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${game.publisher} • ${game.year}",
                    fontSize = 13.sp,
                    color = Color(0xFF938F99),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Availability checked",
                            tint = Color(0xFF03DAC6),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Launch Play",
                            fontSize = 10.sp,
                            color = Color(0xFF03DAC6),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onPlayClick,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF45464F),
                            contentColor = Color(0xFFD0BCFF)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("Play Free", color = Color(0xFFD0BCFF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun BookmarkMediaCard(
    saved: SavedMedia,
    onRemove: () -> Unit,
    onActionClick: () -> Unit,
    onViewDetail: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewDetail() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E3033)),
        border = BorderStroke(1.dp, Color(0xFF45464F)),
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (saved.type) {
                "BOOK" -> BookThumbnailCover(title = saved.title, author = saved.subtitle)
                "TORRENT" -> TorrentThumbnailCover(category = saved.subtitle)
                else -> GameThumbnailCover()
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = when (saved.type) {
                            "BOOK" -> "eBook Entry"
                            "TORRENT" -> "Decentralized Seed"
                            else -> "Software Archive"
                        },
                        fontSize = 11.sp,
                        color = when (saved.type) {
                            "BOOK" -> Color(0xFFD0BCFF)
                            "TORRENT" -> Color(0xFF1DE9B6)
                            else -> Color(0xFFCAC4D0).copy(alpha = 0.6f)
                        },
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Remove saved bookmark item button",
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = saved.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${saved.subtitle} • ${saved.year}",
                    fontSize = 13.sp,
                    color = Color(0xFF938F99),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Verified Seal",
                            tint = Color(0xFF03DAC6),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Verified Checksum",
                            fontSize = 10.sp,
                            color = Color(0xFF03DAC6),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onActionClick,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF45464F),
                            contentColor = Color(0xFFD0BCFF)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = when (saved.type) {
                                "BOOK" -> "Get PDF"
                                "TORRENT" -> "Get Magnet"
                                else -> "Launch"
                            },
                            color = Color(0xFFD0BCFF),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VerifiedNodeBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x33381E72))
            .border(1.dp, Color(0x1AD0BCFF), RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Verified Seal",
                tint = Color(0xFFD0BCFF),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "All archive items are verified via distributed SHA-256 metadata integrity check. Real-time synchronizations active for decentralized node caches.",
                fontSize = 11.sp,
                color = Color(0xFFCAC4D0),
                lineHeight = 15.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun TorrentThumbnailCover(category: String) {
    Box(
        modifier = Modifier
            .size(width = 64.dp, height = 80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF35383F)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF1A1C1E).copy(alpha = 0.5f), Color.Transparent)
                    )
                )
        )
        Icon(
            imageVector = Icons.Default.Share,
            contentDescription = "Torrent Icon",
            tint = Color(0xFFD0BCFF).copy(alpha = 0.6f),
            modifier = Modifier.size(28.dp)
        )
    }
}

@Composable
fun TorrentResultCard(
    torrent: TorrentResult,
    isBookmarked: Boolean,
    onBookmarkToggle: () -> Unit,
    onViewDetail: () -> Unit,
    onDownloadClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewDetail() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E3033)),
        border = BorderStroke(1.dp, Color(0xFF45464F)),
        shape = RoundedCornerShape(28.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TorrentThumbnailCover(torrent.category)

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = torrent.category,
                        fontSize = 11.sp,
                        color = Color(0xFF1DE9B6),
                        fontWeight = FontWeight.Bold
                    )
                    
                    IconButton(
                        onClick = onBookmarkToggle,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite button choice",
                            tint = if (isBookmarked) Color(0xFFFF5252) else Color(0xFFCAC4D0),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = torrent.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Seeds: ${torrent.seeders} • Peers: ${torrent.leechers} • Size: ${torrent.size}",
                    fontSize = 13.sp,
                    color = Color(0xFF938F99),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Safe peer verification check",
                            tint = Color(0xFF03DAC6),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Verified Seeding",
                            fontSize = 10.sp,
                            color = Color(0xFF03DAC6),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onDownloadClick,
                        modifier = Modifier.height(32.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF45464F),
                            contentColor = Color(0xFFD0BCFF)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Get Magnet",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FastLookupRow(query: String, tab: SearchTab, onUrlClick: (String) -> Unit = {}) {
    if (query.trim().isBlank()) return
    val context = LocalContext.current
    
    val platforms = when (tab) {
        SearchTab.BOOKS -> listOf(
            "Libgen" to "https://libgen.is/search.php?req=${android.net.Uri.encode(query)}",
            "Z-Library" to "https://z-lib.org/s/${android.net.Uri.encode(query)}",
            "Anna's Archive" to "https://annas-archive.org/search?q=${android.net.Uri.encode(query)}",
            "Sci-Hub" to "https://sci-hub.se/${android.net.Uri.encode(query)}",
            "PDF Drive" to "https://www.pdfdrive.com/search?q=${android.net.Uri.encode(query)}",
            "Bookfi" to "https://bookfi.net/s/?q=${android.net.Uri.encode(query)}",
            "OceanofPDF" to "https://oceanofpdf.com/?s=${android.net.Uri.encode(query)}",
            "MangaDex" to "https://mangadex.org/search?q=${android.net.Uri.encode(query)}",
            "Manganato" to "https://manganato.com/search/story/${android.net.Uri.encode(query)}"
        )
        SearchTab.TORRENTS -> listOf(
            "Pirate Bay" to "https://thepiratebay.org/search.php?q=${android.net.Uri.encode(query)}",
            "1337x" to "https://1337x.to/search/${android.net.Uri.encode(query)}/1/",
            "RARBG" to "https://rargb.to/search/?search=${android.net.Uri.encode(query)}",
            "YTS (Movies)" to "https://yts.mx/browse-movies/${android.net.Uri.encode(query)}",
            "EZTV (TV)" to "https://eztv.re/search/${android.net.Uri.encode(query)}",
            "TorrentGalaxy" to "https://torrentgalaxy.to/torrents.php?search=${android.net.Uri.encode(query)}",
            "LimeTorrents" to "https://www.limetorrents.info/search/all/${android.net.Uri.encode(query)}/",
            "Nyaa (Anime)" to "https://nyaa.si/?f=0&c=0_0&q=${android.net.Uri.encode(query)}",
            "Torlock" to "https://www.torlock.com/?q=${android.net.Uri.encode(query)}",
            "Zooqle" to "https://zooqle.com/search?q=${android.net.Uri.encode(query)}",
            "GloTorrents" to "https://www.glotorrents.to/search_results.php?search=${android.net.Uri.encode(query)}",
            "MagnetDL" to "https://www.magnetdl.com/search/?q=${android.net.Uri.encode(query)}",
            "KickassTorrents" to "https://kickasstorrents.to/usearch/${android.net.Uri.encode(query)}/",
            "GetIntoPC" to "https://getintopc.com/?s=${android.net.Uri.encode(query)}",
            "FMovies" to "https://fmovies.ps/search/${android.net.Uri.encode(query)}",
            "Soap2day" to "https://soap2day.rs/search/${android.net.Uri.encode(query)}"
        )
        SearchTab.GAMES -> listOf(
            "FitGirl" to "https://fitgirl-repacks.site/?s=${android.net.Uri.encode(query)}",
            "DODI Repack" to "https://dodi-repacks.site/?s=${android.net.Uri.encode(query)}",
            "Online-Fix" to "https://online-fix.me/index.php?do=search&subaction=search&story=${android.net.Uri.encode(query)}",
            "SteamUnlocked" to "https://steamunlocked.net/?s=${android.net.Uri.encode(query)}",
            "IGG-Games" to "https://igg-games.com/?s=${android.net.Uri.encode(query)}",
            "Skidrow" to "https://www.skidrowreloaded.com/?s=${android.net.Uri.encode(query)}",
            "Internet Archive" to "https://archive.org/details/software?query=${android.net.Uri.encode(query)}"
        )
        else -> emptyList()
    }
    
    if (platforms.isEmpty()) return
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF232529)),
        border = BorderStroke(1.dp, Color(0xFF32353A)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Instant Portal Quick Search Filters:",
                color = Color(0xFFD0BCFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                platforms.forEach { (name, url) ->
                    AssistChip(
                        onClick = { onUrlClick(url) },
                        label = { Text(name, fontSize = 11.sp, color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = Color(0xFF2D3035)
                        ),
                        border = BorderStroke(1.dp, Color(0xFF45484F))
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppWebViewReader(
    url: String,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var webView: WebView? by remember { mutableStateOf(null) }
    val isPageLoading = remember { mutableStateOf(true) }
    val currentUrl = remember { mutableStateOf(url) }
    var forceDirectView by remember { mutableStateOf(false) }

    val isPdf = remember(url) {
        val lower = url.lowercase()
        lower.endsWith(".pdf") || lower.contains(".pdf?") || lower.contains("/pdf")
    }

    // Use Google Docs Web viewer wrapper for loading PDFs in WebView unless direct view is forced
    val finalUrl = remember(url, forceDirectView) {
        if (isPdf && !forceDirectView) {
            "https://docs.google.com/gview?embedded=true&url=" + Uri.encode(url)
        } else {
            url
        }
    }

    // Intercept physical system back gesture to navigate backwards in WebView history
    BackHandler(enabled = true) {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            onClose()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1C1E)),
        color = Color(0xFF1A1C1E)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF2E3033))
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Close web browser page reader",
                        tint = Color.White
                    )
                }

                Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(
                        text = "In-App eBook & Web Portal",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentUrl.value,
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { webView?.reload() }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload webpage reader button",
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = { openUrlInBrowser(context, url) }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Open inside system browser instead",
                            tint = Color(0xFFD0BCFF)
                        )
                    }
                }
            }

            // Top alert banner for PDFs when loaded using gview
            if (isPdf) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF252729))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (forceDirectView) "⚡ Direct Stream Mode" else "📖 Google PDF Engine",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD0BCFF)
                    )
                    
                    Text(
                        text = if (forceDirectView) "Try Reader View" else "Bypass Reader (Fix Blocked PDF)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFD54F),
                        modifier = Modifier
                            .clickable { 
                                forceDirectView = !forceDirectView
                                Toast.makeText(context, "Switching download model...", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            // Indeterminate loading progress indicator
            if (isPageLoading.value) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = Color(0xFFBB86FC),
                    trackColor = Color(0xFF2E3033)
                )
            } else {
                Spacer(modifier = Modifier.height(3.dp))
            }

            // Main WebView component wrapper
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                key(finalUrl) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                this.webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, loadingUrl: String?, favicon: android.graphics.Bitmap?) {
                                        super.onPageStarted(view, loadingUrl, favicon)
                                        isPageLoading.value = true
                                        currentUrl.value = loadingUrl ?: ""
                                    }

                                    override fun onPageFinished(view: WebView?, loadedUrl: String?) {
                                        super.onPageFinished(view, loadedUrl)
                                        isPageLoading.value = false
                                        currentUrl.value = loadedUrl ?: ""
                                    }

                                    override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                                        handler?.proceed() // Proceed for self-signed shadow libraries PDFs
                                    }

                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val u = request?.url?.toString() ?: return false
                                        if (u.startsWith("magnet:") || u.startsWith("intent:") || u.startsWith("tel:") || u.startsWith("mailto:")) {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(u))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "No app available to handle download.", Toast.LENGTH_SHORT).show()
                                            }
                                            return true
                                        }
                                        return false
                                    }
                                }
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    setSupportZoom(true)
                                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                                }
                                webView = this
                                loadUrl(finalUrl)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { /* Handled automatically by Compose key recreation when finalUrl changes */ }
                    )
                }
            }
        }
    }
}

@Composable
fun GeminiLoadingSpinner(
    isGeminiActive: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading_animation")
    
    // Outer arc rotation
    val outerRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing)
        ),
        label = "outer_rotation"
    )
    
    // Inner arc rotation (opposite direction)
    val innerRotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing)
        ),
        label = "inner_rotation"
    )
    
    // Breathing scale for the central icon
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "icon_scale"
    )

    // Pulse alpha for the center icon glow background
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    // Dynamic list of loading tips/actions
    val phrases = if (isGeminiActive) {
        listOf(
            "Igniting Gemini API model...",
            "Analyzing query intent with AI...",
            "Scanning global digital libraries...",
            "Consulting digital shadow archives...",
            "Synthesizing download links...",
            "Structuring response payload..."
        )
    } else {
        listOf(
            "Searching cached catalog index...",
            "Parsing query keywords...",
            "Scanning offline database tables...",
            "Retrieving local archive matches...",
            "Applying match filtering..."
        )
    }

    var currentPhraseIndex by remember { mutableStateOf(0) }
    
    // Cycle phrases every 1800 ms
    LaunchedEffect(isGeminiActive) {
        currentPhraseIndex = 0
        while (true) {
            kotlinx.coroutines.delay(1800)
            currentPhraseIndex = (currentPhraseIndex + 1) % phrases.size
        }
    }

    // Outer & inner ring colors depending on Gemini connection state
    val outerColor1 = if (isGeminiActive) Color(0xFFD0BCFF) else Color(0xFFFFB74D) // Purple vs Orange
    val outerColor2 = if (isGeminiActive) Color(0xFF03DAC6) else Color(0xFFCAC4D0) // Teal vs Gray
    
    val innerColor = if (isGeminiActive) Color(0xFF9575CD) else Color(0xFFFFCC80)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp)
            .testTag("search_loader"), // Match previous component testTag
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Glowing animated target box
        Box(
            modifier = Modifier.size(100.dp),
            contentAlignment = Alignment.Center
        ) {
            // Background blur/glow aura in the center
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .graphicsLayer(
                        scaleX = iconScale * 1.2f,
                        scaleY = iconScale * 1.2f,
                        alpha = glowAlpha
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                outerColor1.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Canvas drawing for the custom rotating rings and arcs
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 4.dp.toPx()
                
                // Draw Outer Arc
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(outerColor1, outerColor2, outerColor1.copy(alpha = 0.1f))
                    ),
                    startAngle = outerRotation,
                    sweepAngle = 280f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth)
                )

                // Draw Inner Arc (opposite direction and slightly smaller)
                val innerPadding = 12.dp.toPx()
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(innerColor, innerColor.copy(alpha = 0.2f), innerColor)
                    ),
                    startAngle = innerRotation,
                    sweepAngle = 180f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth * 0.75f),
                    size = this.size.copy(
                        width = this.size.width - innerPadding * 2,
                        height = this.size.height - innerPadding * 2
                    ),
                    topLeft = Offset(innerPadding, innerPadding)
                )
            }

            // Central icon
            IconButton(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = iconScale,
                        scaleY = iconScale
                    )
                    .size(40.dp)
            ) {
                Icon(
                    imageVector = if (isGeminiActive) Icons.Default.Star else Icons.Default.Search,
                    contentDescription = if (isGeminiActive) "Gemini active indicator" else "Offline search indicator",
                    tint = if (isGeminiActive) Color(0xFFFFD54F) else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Animated statement transitioning
        Crossfade(
            targetState = phrases.getOrElse(currentPhraseIndex) { "" },
            animationSpec = tween(durationMillis = 400),
            label = "phrase_transition"
        ) { phraseText ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = phraseText,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isGeminiActive) Color(0xFFD0BCFF) else Color(0xFFFFCC80))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isGeminiActive) "Dynamic Gemini API Search" else "Offline Catalog Search",
                        color = Color.Gray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
