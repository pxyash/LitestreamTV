package com.litestream.tv.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Stremio-compatible add-on client. It only requests documented manifest/catalog/meta/stream endpoints. */
data class InstalledAddon(
    val manifestUrl: String,
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val types: List<String>,
    val resources: List<String>,
    val catalogs: List<AddonCatalog>
)

data class AddonCatalog(val type: String, val id: String, val name: String)
data class StreamOption(val title: String, val url: String, val addonName: String, val mimeType: String? = null, val headers: Map<String, String> = emptyMap())

class AddonRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("litestream_addons", Context.MODE_PRIVATE)

    fun installed(): List<InstalledAddon> = runCatching {
        val array = JSONArray(prefs.getString("addons", "[]"))
        (0 until array.length()).mapNotNull { i -> runCatching { parseStoredAddon(array.getJSONObject(i)) }.getOrNull() }
    }.getOrDefault(emptyList())

    fun savedIds(): Set<String> = prefs.getStringSet("saved_items", emptySet())?.toSet() ?: emptySet()

    fun saveSavedIds(ids: Set<String>) { prefs.edit().putStringSet("saved_items", ids.toSet()).apply() }

    fun save(addons: List<InstalledAddon>) {
        val array = JSONArray()
        addons.forEach { addon ->
            array.put(JSONObject().apply {
                put("manifestUrl", addon.manifestUrl); put("id", addon.id); put("name", addon.name)
                put("version", addon.version); put("description", addon.description)
                put("types", JSONArray(addon.types)); put("resources", JSONArray(addon.resources))
                put("catalogs", JSONArray().apply { addon.catalogs.forEach { put(JSONObject().put("type", it.type).put("id", it.id).put("name", it.name)) } })
            })
        }
        prefs.edit().putString("addons", array.toString()).apply()
    }

    suspend fun install(manifestUrlInput: String): InstalledAddon = withContext(Dispatchers.IO) {
        val manifestUrl = normalizeManifestUrl(manifestUrlInput)
        val json = JSONObject(getText(manifestUrl))
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: error("Manifest is missing its id")
        val name = json.optString("name").takeIf { it.isNotBlank() } ?: id
        val types = json.optJSONArray("types").toStringList()
        val resources = json.optJSONArray("resources").let { arr ->
            (0 until (arr?.length() ?: 0)).mapNotNull { i ->
                when (val value = arr?.opt(i)) {
                    is String -> value
                    is JSONObject -> value.optString("name").takeIf { it.isNotBlank() }
                    else -> null
                }
            }
        }
        val catalogsJson = json.optJSONArray("catalogs")
        val catalogs = (0 until (catalogsJson?.length() ?: 0)).mapNotNull { i ->
            val c = catalogsJson?.optJSONObject(i) ?: return@mapNotNull null
            val type = c.optString("type"); val catalogId = c.optString("id")
            if (type.isBlank() || catalogId.isBlank()) null else AddonCatalog(type, catalogId, c.optString("name", catalogId))
        }
        InstalledAddon(manifestUrl, id, name, json.optString("version", "?"), json.optString("description"), types, resources, catalogs)
    }

    suspend fun loadCatalogs(addons: List<InstalledAddon>): List<MediaItem> = withContext(Dispatchers.IO) {
        val result = mutableListOf<MediaItem>()
        addons.forEach { addon ->
            addon.catalogs.forEach { catalog ->
                runCatching {
                    val endpoint = endpoint(addon.manifestUrl, "catalog/${segment(catalog.type)}/${segment(catalog.id)}.json")
                    val response = JSONObject(getText(endpoint))
                    val metas = response.optJSONArray("metas") ?: JSONArray()
                    for (i in 0 until metas.length()) {
                        val meta = metas.optJSONObject(i) ?: continue
                        val id = meta.optString("id").takeIf { it.isNotBlank() } ?: continue
                        val title = meta.optString("name").ifBlank { meta.optString("title") }.ifBlank { id }
                        val yearText = meta.optString("releaseInfo", meta.optString("year"))
                        val year = Regex("\\d{4}").find(yearText)?.value?.toIntOrNull() ?: 0
                        val rating = meta.optString("imdbRating", "—")
                        val genres = meta.optJSONArray("genres").toStringList().joinToString(" · ")
                        val description = meta.optString("description").ifBlank { genres.ifBlank { "Provided by ${addon.name}" } }
                        val mediaType = if (catalog.type == "series" || catalog.type == "tv") MediaType.SERIES else MediaType.MOVIE
                        result.add(MediaItem(
                            id = id, title = title, type = mediaType, year = year, rating = rating,
                            description = description, category = catalog.name, posterColor = 0xFF22364B,
                            playbackUrl = null, isDemo = false, stremioType = catalog.type,
                            addonManifestUrls = listOf(addon.manifestUrl), posterUrl = meta.optString("poster").takeIf { it.startsWith("http") },
                            addonName = addon.name
                        ))
                    }
                }
            }
        }
        // Deduplicate repeated titles/IDs across catalogs while preserving provider context.
        result.distinctBy { "${it.stremioType}:${it.id}:${it.addonManifestUrls.firstOrNull()}" }
    }

    suspend fun streams(item: MediaItem, addons: List<InstalledAddon>): List<StreamOption> = withContext(Dispatchers.IO) {
        val relevant = addons.filter { addon ->
            (addon.types.isEmpty() || item.stremioType in addon.types) &&
                addon.resources.any { it == "stream" }
        }.sortedBy { if (it.manifestUrl in item.addonManifestUrls) 0 else 1 }
        val output = mutableListOf<StreamOption>()
        relevant.forEach { addon ->
            runCatching {
                val endpoint = endpoint(addon.manifestUrl, "stream/${segment(item.stremioType)}/${segment(item.id)}.json")
                val response = JSONObject(getText(endpoint))
                val streams = response.optJSONArray("streams") ?: JSONArray()
                for (i in 0 until streams.length()) {
                    val stream = streams.optJSONObject(i) ?: continue
                    val url = stream.optString("url").takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: continue
                    val label = stream.optString("title").ifBlank { stream.optString("name") }.ifBlank { "Stream ${i + 1}" }
                    val headers = mutableMapOf<String, String>()
                    val requestHeaders = stream.optJSONObject("behaviorHints")?.optJSONObject("proxyHeaders")?.optJSONObject("request")
                    requestHeaders?.keys()?.forEach { key -> requestHeaders.optString(key).takeIf { it.isNotBlank() }?.let { headers[key] = it } }
                    val mime = when {
                        url.substringBefore('?').endsWith(".m3u8", true) -> "application/x-mpegURL"
                        url.substringBefore('?').endsWith(".mpd", true) -> "application/dash+xml"
                        else -> null
                    }
                    output.add(StreamOption(label, url, addon.name, mime, headers))
                }
            }
        }
        output.distinctBy { it.url }
    }

    private fun parseStoredAddon(json: JSONObject): InstalledAddon {
        val catalogsJson = json.optJSONArray("catalogs") ?: JSONArray()
        val catalogs = (0 until catalogsJson.length()).mapNotNull { i -> catalogsJson.optJSONObject(i)?.let { AddonCatalog(it.optString("type"), it.optString("id"), it.optString("name")) } }
        return InstalledAddon(json.optString("manifestUrl"), json.optString("id"), json.optString("name"), json.optString("version"), json.optString("description"), json.optJSONArray("types").toStringList(), json.optJSONArray("resources").toStringList(), catalogs)
    }

    private fun normalizeManifestUrl(input: String): String {
        val trimmed = input.trim()
        require(trimmed.startsWith("https://") || trimmed.startsWith("http://")) { "Enter a full http(s) manifest URL." }
        val path = trimmed.substringBefore('?').trimEnd('/')
        val query = trimmed.substringAfter('?', "")
        val manifest = if (path.endsWith("/manifest.json", true)) path else "$path/manifest.json"
        return if (query.isBlank()) manifest else "$manifest?$query"
    }

    private fun endpoint(manifestUrl: String, path: String): String {
        val raw = manifestUrl.substringBefore('?')
        val query = manifestUrl.substringAfter('?', "")
        val base = raw.removeSuffix("/manifest.json").trimEnd('/')
        val endpoint = "$base/$path"
        return if (query.isBlank()) endpoint else "$endpoint?$query"
    }

    private fun segment(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    private fun getText(urlText: String): String {
        val connection = URL(urlText).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "LITESTREAM-TV/2.0")
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("HTTP $code from add-on")
            body
        } finally { connection.disconnect() }
    }

    private fun JSONArray?.toStringList(): List<String> = this?.let { arr -> (0 until arr.length()).mapNotNull { i -> arr.optString(i).takeIf { it.isNotBlank() && it != "null" } } }.orEmpty()
}
