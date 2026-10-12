package com.litestream.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import com.litestream.tv.data.AppThemeMode
import com.litestream.tv.data.DemoCatalog
import com.litestream.tv.data.MediaItem
import com.litestream.tv.data.MediaType

private val PowderBlue = Color(0xFF9CC9F5)
private val TextMuted = Color(0xFFAAB6C5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LitestreamApp() }
    }
}

private enum class Screen { HOME, MOVIES, SERIES, SEARCH, LIBRARY, SETTINGS }

@Composable
private fun LitestreamApp() {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var theme by remember { mutableStateOf(AppThemeMode.SPACE_GRAY) }
    var selected by remember { mutableStateOf<MediaItem?>(null) }
    var query by remember { mutableStateOf("") }
    var playing by remember { mutableStateOf<MediaItem?>(null) }
    val saved = remember { mutableStateListOf<String>() }
    val bg = when (theme) {
        AppThemeMode.SPACE_GRAY -> Color(0xFF111318)
        AppThemeMode.AMOLED_BLACK -> Color.Black
        AppThemeMode.DEEP_OCEAN -> Color(0xFF0B1520)
    }
    val card = when (theme) {
        AppThemeMode.SPACE_GRAY -> Color(0xFF1C232D)
        AppThemeMode.AMOLED_BLACK -> Color(0xFF101010)
        AppThemeMode.DEEP_OCEAN -> Color(0xFF112030)
    }
    BackHandler(enabled = playing != null || selected != null || screen != Screen.HOME) {
        when {
            playing != null -> playing = null
            selected != null -> selected = null
            else -> screen = Screen.HOME
        }
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = PowderBlue, background = bg, surface = card, onBackground = Color.White, onSurface = Color.White)) {
        Surface(Modifier.fillMaxSize(), color = bg) {
            if (playing != null) {
                PlayerScreen(item = playing!!, onBack = { playing = null })
            } else if (selected != null) {
                DetailsScreen(item = selected!!, isSaved = saved.contains(selected!!.id), onBack = { selected = null }, onPlay = { item -> selected = null; playing = item }, onSave = { item -> if (saved.contains(item.id)) saved.remove(item.id) else saved.add(item.id) })
            } else {
                Row(Modifier.fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    Column(Modifier.width(160.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("LITESTREAM", color = PowderBlue, fontSize = 22.sp, fontWeight = FontWeight.Black)
                        Text("TV", color = TextMuted, fontSize = 12.sp, letterSpacing = 4.sp)
                        Spacer(Modifier.height(18.dp))
                        NavItem("Home", screen == Screen.HOME, card) { screen = Screen.HOME }
                        NavItem("Movies", screen == Screen.MOVIES, card) { screen = Screen.MOVIES }
                        NavItem("TV Shows", screen == Screen.SERIES, card) { screen = Screen.SERIES }
                        NavItem("Search", screen == Screen.SEARCH, card) { screen = Screen.SEARCH }
                        NavItem("My Library", screen == Screen.LIBRARY, card) { screen = Screen.LIBRARY }
                        NavItem("Settings", screen == Screen.SETTINGS, card) { screen = Screen.SETTINGS }
                        Spacer(Modifier.weight(1f))
                        Text("NATIVE TV EDITION", color = TextMuted, fontSize = 10.sp)
                    }
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                        when (screen) {
                            Screen.HOME -> {
                                Hero(item = DemoCatalog.items.first(), card = card, onDetails = { selected = it })
                                CatalogRow("Trending Now", DemoCatalog.items.filter { it.category == "trending" }, card) { selected = it }
                                CatalogRow("Sci-Fi & Futurism", DemoCatalog.items.filter { it.category == "scifi" || it.type == MediaType.SERIES }, card) { selected = it }
                                CatalogRow("Action Blockbusters", DemoCatalog.items.filter { it.category == "action" }, card) { selected = it }
                            }
                            Screen.MOVIES -> CatalogGrid("Movies", DemoCatalog.items.filter { it.type == MediaType.MOVIE }, card) { selected = it }
                            Screen.SERIES -> CatalogGrid("TV Shows", DemoCatalog.items.filter { it.type == MediaType.SERIES }, card) { selected = it }
                            Screen.SEARCH -> {
                                Text("Search", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                                OutlinedTextField(query, { query = it }, label = { Text("Title, genre, or keyword") }, singleLine = true, modifier = Modifier.fillMaxWidth(0.8f))
                                val results = DemoCatalog.items.filter { it.title.contains(query, true) || it.description.contains(query, true) || it.category.contains(query, true) }
                                CatalogRow("Results (${results.size})", results, card) { selected = it }
                            }
                            Screen.LIBRARY -> CatalogGrid("My Library", DemoCatalog.items.filter { saved.contains(it.id) }, card) { selected = it }
                            Screen.SETTINGS -> {
                                Text("Settings", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                                Text("Appearance", color = PowderBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("Choose a TV theme", color = TextMuted)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ThemeButton("Space Gray", theme == AppThemeMode.SPACE_GRAY, card) { theme = AppThemeMode.SPACE_GRAY }
                                    ThemeButton("AMOLED Black", theme == AppThemeMode.AMOLED_BLACK, card) { theme = AppThemeMode.AMOLED_BLACK }
                                    ThemeButton("Deep Ocean", theme == AppThemeMode.DEEP_OCEAN, card) { theme = AppThemeMode.DEEP_OCEAN }
                                }
                                Text("Playback", color = PowderBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("Video playback uses AndroidX Media3. A title needs a real, compatible media URL to play.", color = TextMuted)
                                Text("Providers", color = PowderBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("Provider integrations are not configured in this starter build. No fake stream sources are shown.", color = TextMuted)
                                Text("About LITESTREAM TV · Version 1.0.0", color = TextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun NavItem(label: String, active: Boolean, card: Color, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (active) card else Color.Transparent).border(if (focused) 2.dp else 1.dp, if (focused || active) PowderBlue else Color.Transparent, RoundedCornerShape(12.dp)).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable().padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = if (active || focused) PowderBlue else Color.White, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium, fontSize = 15.sp)
    }
}

@Composable private fun Hero(item: MediaItem, card: Color, onDetails: (MediaItem) -> Unit) {
    Column(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(22.dp)).background(card).padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("FEATURED · ${item.year} · ${item.rating} ★", color = PowderBlue, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(item.title, fontSize = 32.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Text(item.description, color = TextMuted, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(0.8f))
        Spacer(Modifier.height(16.dp))
        TvAction("View Details", onClick = { onDetails(item) })
    }
}

@Composable private fun CatalogRow(title: String, items: List<MediaItem>, card: Color, onSelect: (MediaItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(vertical = 5.dp)) {
            items(items, key = { it.id }) { item -> PosterCard(item, card, onSelect) }
        }
    }
}

@Composable private fun CatalogGrid(title: String, items: List<MediaItem>, card: Color, onSelect: (MediaItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        if (items.isEmpty()) Text("Nothing here yet. Add titles to your library from the details screen.", color = TextMuted)
        items.chunked(4).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { row.forEach { PosterCard(it, card, onSelect) } } }
    }
}

@Composable private fun PosterCard(item: MediaItem, card: Color, onSelect: (MediaItem) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(165.dp).clip(RoundedCornerShape(14.dp)).background(card).border(if (focused) 3.dp else 1.dp, if (focused) PowderBlue else Color(0xFF303945), RoundedCornerShape(14.dp)).onFocusChanged { focused = it.isFocused }.clickable { onSelect(item) }.focusable().padding(10.dp)) {
        Box(Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(10.dp)).background(Color(item.posterColor)), contentAlignment = Alignment.Center) {
            Text("LS", color = PowderBlue, fontSize = 32.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(10.dp))
        Text(item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
        Text("${item.year}  ·  ★ ${item.rating}", color = TextMuted, fontSize = 12.sp)
    }
}

@Composable private fun DetailsScreen(item: MediaItem, isSaved: Boolean, onBack: () -> Unit, onPlay: (MediaItem) -> Unit, onSave: (MediaItem) -> Unit) {
    Column(Modifier.fillMaxSize().padding(42.dp), verticalArrangement = Arrangement.Center) {
        Text("LITESTREAM  /  DETAILS", color = PowderBlue, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Text(item.title, fontSize = 34.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text("${item.type.name}  ·  ${item.year}  ·  ★ ${item.rating}", color = TextMuted)
        Spacer(Modifier.height(18.dp))
        Text(item.description, fontSize = 18.sp, color = TextMuted, modifier = Modifier.fillMaxWidth(0.8f))
        Spacer(Modifier.height(14.dp))
        Text("Demo catalog item · Playback is unavailable until a valid media URL is configured.", color = PowderBlue, fontSize = 14.sp)
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            TvAction("Play", enabled = item.playbackUrl != null) { onPlay(item) }
            TvAction(if (isSaved) "Remove from Library" else "Add to Library") { onSave(item) }
            TvAction("Back", onClick = onBack)
        }
    }
}

@Composable private fun PlayerScreen(item: MediaItem, onBack: () -> Unit) {
    val context = LocalContext.current
    val player = remember(item.playbackUrl) { ExoPlayer.Builder(context).build().apply { setMediaItem(PlayerMediaItem.fromUri(item.playbackUrl!!)); prepare(); playWhenReady = true } }
    DisposableEffect(player) { onDispose { player.release() } }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxSize())
        Column(Modifier.align(Alignment.TopStart).padding(24.dp)) {
            Text(item.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TvAction("Back", onClick = onBack)
        }
    }
}

@Composable private fun ThemeButton(label: String, active: Boolean, card: Color, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.clip(RoundedCornerShape(12.dp)).background(card).border(if (focused || active) 2.dp else 1.dp, if (focused || active) PowderBlue else Color(0xFF303945), RoundedCornerShape(12.dp)).onFocusChanged { focused = it.isFocused }.clickable(onClick = onClick).focusable().padding(16.dp)) {
        Text(label, color = if (active || focused) PowderBlue else Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun TvAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Text(label, color = if (enabled) Color.White else TextMuted, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(if (focused) Color(0xFF315273) else Color(0xFF253444)).border(if (focused) 3.dp else 1.dp, if (focused) PowderBlue else Color(0xFF3D5268), RoundedCornerShape(10.dp)).onFocusChanged { focused = it.isFocused }.clickable(enabled = enabled, onClick = onClick).focusable().padding(horizontal = 18.dp, vertical = 12.dp))
}
