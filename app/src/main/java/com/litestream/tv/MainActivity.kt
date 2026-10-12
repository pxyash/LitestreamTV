package com.litestream.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.litestream.tv.data.AddonRepository
import com.litestream.tv.data.AppThemeMode
import com.litestream.tv.data.InstalledAddon
import com.litestream.tv.data.MediaItem
import com.litestream.tv.data.MediaType
import com.litestream.tv.data.StreamOption
import kotlinx.coroutines.launch

private val Ice = Color(0xFFB8DEFF)
private val IceMuted = Color(0xFF9DAFC2)
private val GlassStroke = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.07f)))
private val TransparentEdge = Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))
private val GlassFill = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.15f), Color.White.copy(alpha = 0.045f), Color(0xFF8BCBFF).copy(alpha = 0.075f)))
private val GlassEdge = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.48f), Color(0xFF91CFFF).copy(alpha = 0.22f), Color.White.copy(alpha = 0.08f)))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LitestreamApp() }
    }
}

private enum class Screen { HOME, MOVIES, SERIES, SEARCH, LIBRARY, ADDONS, SETTINGS }

@Composable
private fun LitestreamApp() {
    val context = LocalContext.current
    val repository = remember { AddonRepository(context) }
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.HOME) }
    var theme by remember { mutableStateOf(AppThemeMode.SPACE_GRAY) }
    var selected by remember { mutableStateOf<MediaItem?>(null) }
    var query by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf<StreamOption?>(null) }
    var playingTitle by remember { mutableStateOf("") }
    var addons by remember { mutableStateOf(repository.installed()) }
    var catalogItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var streams by remember { mutableStateOf<List<StreamOption>>(emptyList()) }
    var loadingStreams by remember { mutableStateOf(false) }
    var streamError by remember { mutableStateOf<String?>(null) }
    var manifestInput by remember { mutableStateOf("") }
    var installing by remember { mutableStateOf(false) }
    var addonMessage by remember { mutableStateOf<String?>(null) }
    var addonError by remember { mutableStateOf<String?>(null) }
    val saved = remember { repository.savedIds().toMutableStateList() }

    val bg = when (theme) {
        AppThemeMode.SPACE_GRAY -> Color(0xFF080E18)
        AppThemeMode.AMOLED_BLACK -> Color.Black
        AppThemeMode.DEEP_OCEAN -> Color(0xFF04131E)
    }
    val backdrop = Brush.linearGradient(listOf(bg, Color(0xFF13243A), bg, Color(0xFF0B1C2B)))

    LaunchedEffect(addons) {
        catalogItems = repository.loadCatalogs(addons)
    }
    LaunchedEffect(selected?.id, selected?.stremioType, addons) {
        val item = selected
        if (item != null) {
            loadingStreams = true
            streams = emptyList()
            streamError = null
            val loaded = runCatching { repository.streams(item, addons) }
            streams = loaded.getOrDefault(emptyList())
            if (loaded.isFailure) streamError = loaded.exceptionOrNull()?.message ?: "Unable to load streams."
            loadingStreams = false
        } else {
            streams = emptyList()
            loadingStreams = false
            streamError = null
        }
    }

    BackHandler(enabled = playing != null || selected != null || screen != Screen.HOME) {
        when {
            playing != null -> playing = null
            selected != null -> selected = null
            else -> screen = Screen.HOME
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Ice, background = bg, surface = Color(0xFF172638), onBackground = Color.White, onSurface = Color.White)) {
        Box(Modifier.fillMaxSize().background(backdrop)) {
            // Decorative aurora pools give the background depth without relying on device-specific blur.
            Row(Modifier.fillMaxWidth().padding(top = 0.dp), horizontalArrangement = Arrangement.End) {
                Box(Modifier.size(300.dp).background(Brush.radialGradient(listOf(Color(0xFF70BFFF).copy(alpha = 0.12f), Color.Transparent))))
            }
            when {
                playing != null -> PlayerScreen(title = playingTitle, stream = playing!!, onBack = { playing = null })
                selected != null -> DetailsScreen(
                    item = selected!!,
                    isSaved = saved.contains("${selected!!.stremioType}:${selected!!.id}"),
                    streams = streams,
                    loadingStreams = loadingStreams,
                    streamError = streamError,
                    onBack = { selected = null },
                    onPlay = { stream -> playingTitle = selected!!.title; selected = null; playing = stream },
                    onSave = { item -> val key = "${item.stremioType}:${item.id}"; if (saved.contains(key)) saved.remove(key) else saved.add(key); repository.saveSavedIds(saved.toSet()) }
                )
                else -> Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 22.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Column(Modifier.width(172.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("LITESTREAM", color = Ice, fontSize = 21.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp)
                        Text("CINEMA · TV", color = IceMuted, fontSize = 10.sp, letterSpacing = 3.sp)
                        Spacer(Modifier.height(18.dp))
                        NavItem("⌂   Home", screen == Screen.HOME) { screen = Screen.HOME }
                        NavItem("▣   Movies", screen == Screen.MOVIES) { screen = Screen.MOVIES }
                        NavItem("▤   TV Shows", screen == Screen.SERIES) { screen = Screen.SERIES }
                        NavItem("⌕   Search", screen == Screen.SEARCH) { screen = Screen.SEARCH }
                        NavItem("♡   My Library", screen == Screen.LIBRARY) { screen = Screen.LIBRARY }
                        NavItem("✧   Add-ons", screen == Screen.ADDONS) { screen = Screen.ADDONS }
                        NavItem("⚙   Settings", screen == Screen.SETTINGS) { screen = Screen.SETTINGS }
                        Spacer(Modifier.weight(1f))
                        Text("LIQUID GLASS EDITION", color = IceMuted.copy(alpha = .72f), fontSize = 9.sp, letterSpacing = 1.4.sp)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        when (screen) {
                            Screen.HOME -> {
                                if (catalogItems.isNotEmpty()) {
                                    Hero(item = catalogItems.first(), addonCount = addons.size, onDetails = { selected = it })
                                    catalogItems.groupBy { it.category }.forEach { (name, items) -> CatalogRow(name.ifBlank { "From your add-ons" }, items, onSelect = { selected = it }) }
                                } else {
                                    WelcomePanel(addonCount = addons.size, onAddons = { screen = Screen.ADDONS })
                                }
                            }
                            Screen.MOVIES -> CatalogGrid("Movies", catalogItems.filter { it.type == MediaType.MOVIE }, onSelect = { selected = it })
                            Screen.SERIES -> CatalogGrid("TV Shows", catalogItems.filter { it.type == MediaType.SERIES }, onSelect = { selected = it })
                            Screen.SEARCH -> {
                                Text("Search your library", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Title, genre, or keyword") }, singleLine = true, modifier = Modifier.fillMaxWidth(0.75f))
                                val results = catalogItems.filter { it.title.contains(query, true) || it.description.contains(query, true) || it.category.contains(query, true) }
                                if (query.isNotBlank()) CatalogGrid("Results (${results.size})", results, onSelect = { selected = it })
                                else Text("Search across catalogs loaded from your installed add-ons.", color = IceMuted)
                            }
                            Screen.LIBRARY -> CatalogGrid("My Library", catalogItems.filter { saved.contains("${it.stremioType}:${it.id}") }, onSelect = { selected = it })
                            Screen.ADDONS -> AddonsScreen(
                                addons = addons, manifestInput = manifestInput, installing = installing,
                                message = addonMessage, error = addonError,
                                onInput = { manifestInput = it; addonError = null; addonMessage = null },
                                onInstall = {
                                    scope.launch {
                                        installing = true; addonError = null; addonMessage = null
                                        runCatching { repository.install(manifestInput) }
                                            .onSuccess { newAddon ->
                                                val updated = addons.filterNot { it.manifestUrl == newAddon.manifestUrl } + newAddon
                                                repository.save(updated); addons = repository.installed()
                                                manifestInput = ""; addonMessage = "Installed ${newAddon.name} · ${newAddon.catalogs.size} catalogs discovered."
                                            }
                                            .onFailure { addonError = it.message ?: "Could not install this add-on manifest." }
                                        installing = false
                                    }
                                },
                                onRemove = { addon -> repository.save(addons.filterNot { it.manifestUrl == addon.manifestUrl }); addons = repository.installed(); addonMessage = "Removed ${addon.name}."; addonError = null }
                            )
                            Screen.SETTINGS -> SettingsScreen(theme = theme, onTheme = { theme = it }, addonCount = addons.size)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun GlassPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.shadow(18.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(GlassFill).border(1.dp, GlassEdge, RoundedCornerShape(24.dp)).padding(24.dp), content = content)
}

@Composable private fun NavItem(label: String, active: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(if (active) GlassFill else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent))).border(if (focused) 2.dp else 1.dp, if (focused || active) GlassEdge else TransparentEdge, RoundedCornerShape(15.dp)).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable().padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (active || focused) Ice else Color.White.copy(alpha = .84f), fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, fontSize = 14.sp)
    }
}

@Composable private fun WelcomePanel(addonCount: Int, onAddons: () -> Unit) {
    GlassPanel(Modifier.fillMaxWidth().height(320.dp)) {
        Spacer(Modifier.height(10.dp))
        Text("YOUR STREAMING UNIVERSE", color = Ice, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.2.sp)
        Spacer(Modifier.height(18.dp))
        Text("One home for\nyour add-ons.", fontSize = 40.sp, fontWeight = FontWeight.Black, lineHeight = 45.sp)
        Spacer(Modifier.height(14.dp))
        Text("Install a Stremio-compatible manifest to populate LITESTREAM with real catalogs and stream choices.", color = IceMuted, fontSize = 16.sp, modifier = Modifier.fillMaxWidth(.76f))
        Spacer(Modifier.height(22.dp))
        GlassAction("Manage add-ons  →", onClick = onAddons)
        Spacer(Modifier.height(12.dp))
        Text("$addonCount add-ons installed", color = IceMuted, fontSize = 12.sp)
    }
}

@Composable private fun Hero(item: MediaItem, addonCount: Int, onDetails: (MediaItem) -> Unit) {
    GlassPanel(Modifier.fillMaxWidth().height(260.dp)) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text("YOUR CATALOGS  ·  $addonCount ADD-ONS", color = Ice, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
                Spacer(Modifier.height(12.dp))
                Text(item.title, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                Text(item.description, color = IceMuted, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(16.dp))
                GlassAction("View details", onClick = { onDetails(item) })
            }
            if (item.posterUrl != null) AsyncImage(model = item.posterUrl, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.width(145.dp).fillMaxHeight().clip(RoundedCornerShape(18.dp)))
            else Box(Modifier.width(145.dp).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(Color(0xFF24466A), Color(0xFF101C30), Color(0xFF4A4772)))), contentAlignment = Alignment.Center) {
                Text("LS", color = Ice.copy(alpha = .8f), fontSize = 42.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable private fun CatalogRow(title: String, items: List<MediaItem>, onSelect: (MediaItem) -> Unit) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 7.dp, horizontal = 2.dp)) {
            items(items, key = { "${it.stremioType}:${it.id}:${it.addonName}" }) { item -> PosterCard(item, onSelect) }
        }
    }
}

@Composable private fun CatalogGrid(title: String, items: List<MediaItem>, onSelect: (MediaItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        if (items.isEmpty()) Text("No titles found. Install add-ons that provide catalogs, then refresh this screen.", color = IceMuted)
        items.chunked(4).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { row.forEach { PosterCard(it, onSelect) } } }
    }
}

@Composable private fun PosterCard(item: MediaItem, onSelect: (MediaItem) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(158.dp).shadow(if (focused) 16.dp else 6.dp, RoundedCornerShape(17.dp)).clip(RoundedCornerShape(17.dp)).background(GlassFill).border(if (focused) 2.dp else 1.dp, if (focused) GlassEdge else GlassStroke, RoundedCornerShape(17.dp)).onFocusChanged { focused = it.isFocused }.clickable { onSelect(item) }.focusable().padding(9.dp)) {
        Box(Modifier.fillMaxWidth().height(142.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(listOf(Color(0xFF243B55), Color(0xFF101A2B))))) {
            if (item.posterUrl != null) AsyncImage(model = item.posterUrl, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("L", color = Ice.copy(alpha = .72f), fontSize = 34.sp, fontWeight = FontWeight.Black) }
        }
        Spacer(Modifier.height(9.dp))
        Text(item.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(item.year.takeIf { it > 0 }?.toString(), item.rating.takeIf { it != "—" }).joinToString("  ·  ").ifBlank { item.addonName }, color = IceMuted, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable private fun DetailsScreen(item: MediaItem, isSaved: Boolean, streams: List<StreamOption>, loadingStreams: Boolean, streamError: String?, onBack: () -> Unit, onPlay: (StreamOption) -> Unit, onSave: (MediaItem) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(34.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("LITESTREAM  /  DETAILS", color = Ice, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.Top) {
            if (item.posterUrl != null) AsyncImage(model = item.posterUrl, contentDescription = item.title, contentScale = ContentScale.Crop, modifier = Modifier.width(160.dp).height(220.dp).clip(RoundedCornerShape(18.dp)))
            GlassPanel(Modifier.weight(1f)) {
                Text(item.title, fontSize = 32.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(7.dp))
                Text("${item.stremioType.uppercase()}  ·  ${item.year.takeIf { it > 0 } ?: "Year unknown"}  ·  ${item.addonName}", color = IceMuted, fontSize = 13.sp)
                Spacer(Modifier.height(14.dp))
                Text(item.description, fontSize = 16.sp, color = IceMuted)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassAction(if (isSaved) "♥ Saved" else "♡ Add to Library", onClick = { onSave(item) })
                    GlassAction("Back", onClick = onBack)
                }
            }
        }
        Text("STREAM SOURCES", color = Ice, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
        when {
            loadingStreams -> Text("Checking installed add-ons for playable streams…", color = IceMuted)
            streamError != null -> Text(streamError, color = Color(0xFFFFB6B6))
            streams.isEmpty() -> Text("No direct HTTP(S) stream was returned. Some add-ons provide torrent hashes, external players, or resolver links that need a separate compatible resolver.", color = IceMuted, modifier = Modifier.fillMaxWidth(.86f))
            else -> streams.forEachIndexed { index, stream ->
                GlassPanel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(stream.title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${stream.addonName}  ·  ${stream.mimeType ?: "Auto-detected media"}", color = IceMuted, fontSize = 12.sp)
                        }
                        GlassAction("Play ${index + 1}  ▶", onClick = { onPlay(stream) })
                    }
                }
            }
        }
    }
}

@Composable private fun AddonsScreen(addons: List<InstalledAddon>, manifestInput: String, installing: Boolean, message: String?, error: String?, onInput: (String) -> Unit, onInstall: () -> Unit, onRemove: (InstalledAddon) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Add-on manager", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Text("Install Stremio-compatible add-ons using their manifest URL. Catalogs are fetched from each manifest's declared catalog endpoints.", color = IceMuted, fontSize = 14.sp, modifier = Modifier.fillMaxWidth(.9f))
        GlassPanel(Modifier.fillMaxWidth()) {
            Text("INSTALL ADD-ON", color = Ice, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = manifestInput, onValueChange = onInput, modifier = Modifier.fillMaxWidth(.88f), label = { Text("https://example.com/manifest.json") }, placeholder = { Text("Paste manifest URL") }, singleLine = true, enabled = !installing)
            Spacer(Modifier.height(12.dp))
            GlassAction(if (installing) "Checking manifest…" else "Install add-on  +", enabled = !installing && manifestInput.isNotBlank(), onClick = onInstall)
            if (message != null) { Spacer(Modifier.height(10.dp)); Text(message, color = Color(0xFFB7F5D1), fontSize = 13.sp) }
            if (error != null) { Spacer(Modifier.height(10.dp)); Text(error, color = Color(0xFFFFB5B5), fontSize = 13.sp) }
        }
        Text("INSTALLED  ·  ${addons.size}", color = Ice, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        if (addons.isEmpty()) Text("No add-ons installed yet.", color = IceMuted)
        addons.forEach { addon ->
            GlassPanel(Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(addon.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${addon.catalogs.size} catalogs · ${addon.resources.joinToString()}", color = IceMuted, fontSize = 12.sp)
                        Text(addon.manifestUrl, color = IceMuted.copy(alpha = .8f), fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (addon.description.isNotBlank()) Text(addon.description, color = IceMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    GlassAction("Remove", onClick = { onRemove(addon) })
                }
            }
        }
        Text("Only install add-ons you trust. Add-ons can return external URLs and metadata; playback depends on the URL, codecs, DRM and your device.", color = IceMuted, fontSize = 11.sp, modifier = Modifier.fillMaxWidth(.9f))
    }
}

@Composable private fun SettingsScreen(theme: AppThemeMode, onTheme: (AppThemeMode) -> Unit, addonCount: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("Settings", fontSize = 30.sp, fontWeight = FontWeight.Black)
        GlassPanel(Modifier.fillMaxWidth()) {
            Text("APPEARANCE", color = Ice, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Text("Choose the glass tint and background", color = IceMuted, fontSize = 13.sp)
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ThemeChoice("Space Gray", theme == AppThemeMode.SPACE_GRAY) { onTheme(AppThemeMode.SPACE_GRAY) }
                ThemeChoice("AMOLED Black", theme == AppThemeMode.AMOLED_BLACK) { onTheme(AppThemeMode.AMOLED_BLACK) }
                ThemeChoice("Deep Ocean", theme == AppThemeMode.DEEP_OCEAN) { onTheme(AppThemeMode.DEEP_OCEAN) }
            }
        }
        GlassPanel(Modifier.fillMaxWidth()) {
            Text("PLAYBACK ENGINE", color = Ice, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Spacer(Modifier.height(8.dp))
            Text("Media3 ExoPlayer with HLS and DASH modules, progressive HTTP/HTTPS playback, adaptive tracks and built-in audio/subtitle controls where the stream exposes them.", color = IceMuted, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Text("HDR is device-dependent: Android version, decoder, display pipeline, codec profile, bit depth, and stream packaging must all be compatible. DRM streams may need additional DRM configuration.", color = IceMuted, fontSize = 13.sp)
        }
        GlassPanel(Modifier.fillMaxWidth()) {
            Text("ADD-ONS", color = Ice, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            Text("$addonCount installed · Manifest and catalog data are saved locally on this device.", color = IceMuted, fontSize = 14.sp)
        }
        Text("LITESTREAM TV · 2.0 · Liquid Glass", color = IceMuted, fontSize = 11.sp)
    }
}

@Composable private fun ThemeChoice(label: String, active: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.clip(RoundedCornerShape(14.dp)).background(GlassFill).border(if (active || focused) 2.dp else 1.dp, if (active || focused) GlassEdge else GlassStroke, RoundedCornerShape(14.dp)).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable().padding(15.dp)) {
        Text(label, color = if (active || focused) Ice else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Text(if (active) "Selected" else "Select", color = IceMuted, fontSize = 11.sp)
    }
}

@Composable private fun GlassAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(label, color = if (enabled) Color.White else IceMuted.copy(alpha = .5f), fontWeight = FontWeight.Bold, fontSize = 13.sp,
        modifier = Modifier.clip(RoundedCornerShape(13.dp)).background(if (focused) Brush.linearGradient(listOf(Color(0xFF3A719D).copy(alpha = .82f), Color(0xFF315079).copy(alpha = .9f))) else GlassFill).border(if (focused) 2.dp else 1.dp, if (focused) GlassEdge else GlassStroke, RoundedCornerShape(13.dp)).onFocusChanged { focused = it.isFocused }.clickable(enabled = enabled, onClick = onClick).focusable().padding(horizontal = 17.dp, vertical = 12.dp))
}

@Composable private fun PlayerScreen(title: String, stream: StreamOption, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember(stream.url) {
        val httpFactory = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(stream.headers)
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        ExoPlayer.Builder(context).setMediaSourceFactory(mediaSourceFactory).build().apply {
            val builder = PlayerMediaItem.Builder().setUri(stream.url)
            val mime = stream.mimeType ?: when {
                stream.url.substringBefore('?').endsWith(".m3u8", true) -> MimeTypes.APPLICATION_M3U8
                stream.url.substringBefore('?').endsWith(".mpd", true) -> MimeTypes.APPLICATION_MPD
                else -> null
            }
            if (mime != null) builder.setMimeType(mime)
            setMediaItem(builder.build())
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true; controllerAutoShow = true; keepScreenOn = true; setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING) } }, update = { it.player = player }, modifier = Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.TopStart).padding(20.dp).clip(RoundedCornerShape(15.dp)).background(Color.Black.copy(alpha = .48f)).padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${stream.addonName} · ${stream.title}", color = Color.White.copy(alpha = .75f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            GlassAction("← Back", onClick = onBack)
        }
    }
}
