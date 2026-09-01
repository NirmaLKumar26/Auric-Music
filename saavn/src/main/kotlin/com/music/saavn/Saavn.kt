package com.music.saavn

import android.util.Base64
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.userAgent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * JioSaavn public web API used by Auric Tunes via the jisaavn content-resolver plugin.
 */
object Saavn {
    const val QUALITY_96 = "96"
    const val QUALITY_160 = "160"
    const val QUALITY_320 = "320"
    private const val SaavnQualityDefault = QUALITY_320
    private const val API = "https://www.jiosaavn.com/api.php"
    private const val DES_KEY = "38346591"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentEncoding) {
            gzip()
            deflate()
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 20_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 20_000
        }
        defaultRequest {
            userAgent(USER_AGENT)
            header(HttpHeaders.Accept, "application/json,text/plain,*/*")
            header(HttpHeaders.Referrer, "https://www.jiosaavn.com/")
        }
    }

    private val encryptedUrlCache = ConcurrentHashMap<String, String>()

    suspend fun search(query: String, page: Int = 1): Result<SaavnSearchResult> = runCatching {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@runCatching SaavnSearchResult()

        val songs = parseSongList(
            api(
                "__call" to "search.getResults",
                "q" to trimmed,
                "p" to page.toString(),
                "n" to "20",
                "api_version" to "4",
                "ctx" to "web6dot0",
            )
        )

        val extra = if (page == 1) {
            parseAutocomplete(
                api(
                    "__call" to "autocomplete.get",
                    "query" to trimmed,
                    "includeMetaTags" to "1",
                )
            )
        } else {
            SaavnSearchResult()
        }

        extra.copy(songs = (songs + extra.songs).distinctBy { it.id })
    }

    suspend fun song(id: String): Result<SaavnSong?> = runCatching {
        val pid = SaavnIds.songPid(id) ?: id
        parseSongList(
            api(
                "__call" to "song.getDetails",
                "pids" to pid,
                "cc" to "in",
            )
        ).firstOrNull() ?: parseSongList(
            api(
                "__call" to "webapi.get",
                "token" to pid,
                "type" to "song",
                "api_version" to "4",
                "ctx" to "web6dot0",
            )
        ).firstOrNull()
    }

    suspend fun album(id: String): Result<SaavnCollection> = runCatching {
        collection("album", id)
    }

    suspend fun playlist(id: String): Result<SaavnCollection> = runCatching {
        collection("playlist", id)
    }

    suspend fun artist(id: String): Result<SaavnCollection> = runCatching {
        collection("artist", id)
    }

    suspend fun collection(type: String, idOrToken: String): SaavnCollection {
        val token = idOrToken.substringAfterLast("/")
        val root = api(
            "__call" to "webapi.get",
            "token" to token,
            "type" to type,
            "include_media" to "true",
            "n" to "50",
            "api_version" to "4",
            "ctx" to "web6dot0",
        )
        var parsed = parseCollection(type, token, root)
        if (parsed.songs.isEmpty()) {
            val fallback = when (type) {
                "album" -> api(
                    "__call" to "content.getAlbumDetails",
                    "albumid" to token,
                )
                "playlist" -> api(
                    "__call" to "playlist.getDetails",
                    "listid" to token,
                )
                else -> null
            }
            if (fallback != null) {
                parsed = parseCollection(type, token, fallback)
            }
        }
        return parsed
    }

    private fun parseCollection(type: String, fallbackId: String, root: JsonElement): SaavnCollection {
        val obj = asObject(root) ?: JsonObject(emptyMap())
        val songs = parseSongList(obj["list"] ?: obj["songs"] ?: obj["topSongs"] ?: obj)
        val title = decode(obj.str("title") ?: obj.str("listname") ?: obj.str("name") ?: type).ifBlank { type }
        val subtitle = decode(
            obj.str("subtitle")
                ?: obj.str("header_desc")
                ?: artistsFrom(obj).joinToString { it.name }.ifBlank { null }
        ).ifBlank { null }
        val image = upgradeImage(obj.str("image") ?: obj.str("thumbnail") ?: "")
        return SaavnCollection(
            type = when (type) {
                "album" -> SaavnCollectionType.ALBUM
                "artist" -> SaavnCollectionType.ARTIST
                else -> SaavnCollectionType.PLAYLIST
            },
            id = obj.str("id") ?: obj.str("listid") ?: fallbackId,
            title = title,
            subtitle = subtitle,
            imageUrl = image.ifBlank { songs.firstOrNull()?.imageUrl },
            songs = songs,
        )
    }

    suspend fun streamUrl(
        mediaId: String,
        quality: String = SaavnQualityDefault,
    ): Result<String> = runCatching {
        val pid = SaavnIds.songPid(mediaId) ?: mediaId.removePrefix(SaavnIds.PREFIX)
        val encrypted = encryptedUrlCache[pid]
            ?: song(pid).getOrNull()?.encryptedMediaUrl
            ?: error("No JioSaavn stream for $mediaId")
        qualityUrl(decryptUrl(encrypted), quality)
    }

    suspend fun matchSong(title: String, artist: String? = null): Result<SaavnSong> = runCatching {
        val query = listOfNotNull(title.takeIf { it.isNotBlank() }, artist?.takeIf { it.isNotBlank() })
            .joinToString(" ")
        val results = search(query).getOrThrow().songs
        require(results.isNotEmpty()) { "No JioSaavn match for $title" }
        val needleTitle = normalize(title)
        val needleArtist = normalize(artist.orEmpty())
        fun score(song: SaavnSong): Int {
            var value = 0
            val songTitle = normalize(song.title)
            val songArtist = normalize(song.artists.joinToString { it.name })
            if (songTitle == needleTitle) value += 8
            else if (needleTitle.isNotEmpty() && (songTitle.contains(needleTitle) || needleTitle.contains(songTitle))) value += 4
            if (needleArtist.isNotEmpty() && (songArtist.contains(needleArtist) || needleArtist.contains(songArtist))) {
                value += 3
            }
            return value
        }
        val best = results.maxBy(::score)
        require(score(best) >= 4) { "No close JioSaavn match for $title" }
        best
    }

    fun parseUrl(raw: String): SaavnUrlTarget? {
        val uri = runCatching { URI(raw.trim()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase().orEmpty()
        if (host != "jiosaavn.com" && !host.endsWith(".jiosaavn.com") &&
            host != "saavn.com" && !host.endsWith(".saavn.com")
        ) {
            return null
        }
        val segments = uri.path.orEmpty().trim('/').split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return null
        val token = segments.last()
        val type = when {
            segments.any { it.equals("song", true) } -> SaavnCollectionType.SONG
            segments.any { it.equals("album", true) } -> SaavnCollectionType.ALBUM
            segments.any { it.equals("artist", true) } -> SaavnCollectionType.ARTIST
            segments.any { it.equals("featured", true) || it.equals("playlist", true) } ->
                SaavnCollectionType.PLAYLIST
            else -> return null
        }
        return SaavnUrlTarget(type, token)
    }

    private suspend fun api(vararg params: Pair<String, String>): JsonElement {
        val text = client.get(API) {
            parameter("__call", params.first { it.first == "__call" }.second)
            parameter("_format", "json")
            parameter("_marker", "0")
            parameter("cc", "in")
            params.filterNot { it.first == "__call" }.forEach { (k, v) ->
                parameter(k, v)
            }
        }.bodyAsText()
        val cleaned = text.substringAfter("-->").trim().ifEmpty { text.trim() }
        return json.parseToJsonElement(cleaned)
    }

    private fun parseAutocomplete(root: JsonElement): SaavnSearchResult {
        val obj = asObject(root) ?: return SaavnSearchResult()
        return SaavnSearchResult(
            songs = parseSongList(obj["songs"]),
            albums = parseAlbums(obj["albums"]),
            playlists = parsePlaylists(obj["playlists"]),
            artists = parseArtists(obj["artists"]),
        )
    }

    private fun parseSongList(element: JsonElement?): List<SaavnSong> {
        val items = songArray(element)
        return items.mapNotNull(::parseSong).distinctBy { it.id }
    }

    private fun songArray(element: JsonElement?): List<JsonObject> {
        if (element == null) return emptyList()
        val obj = asObject(element)
        val array = when {
            element is JsonArray -> element
            obj?.get("results") is JsonArray -> obj["results"]!!.jsonArray
            obj?.get("data") is JsonArray -> obj["data"]!!.jsonArray
            obj?.get("songs") is JsonArray -> obj["songs"]!!.jsonArray
            obj?.get("list") is JsonArray -> obj["list"]!!.jsonArray
            obj?.get("topSongs") is JsonObject && obj["topSongs"]!!.jsonObject["songs"] is JsonArray ->
                obj["topSongs"]!!.jsonObject["songs"]!!.jsonArray
            obj != null && obj.containsKey("id") && (obj.containsKey("title") || obj.containsKey("song")) ->
                return listOf(obj)
            else -> JsonArray(emptyList())
        }
        return array.mapNotNull { asObject(it) }
    }

    private fun parseSong(obj: JsonObject): SaavnSong? {
        val more = asObject(obj["more_info"]) ?: JsonObject(emptyMap())
        val id = obj.str("id") ?: more.str("encrypted_media_url")?.hashCode()?.toString() ?: return null
        val title = decode(obj.str("title") ?: obj.str("song") ?: return null).ifBlank { return null }
        val image = upgradeImage(obj.str("image") ?: more.str("image") ?: "")
        val encrypted = more.str("encrypted_media_url") ?: obj.str("encrypted_media_url")
        if (!encrypted.isNullOrBlank()) encryptedUrlCache[id] = encrypted
        val duration = (more.str("duration") ?: obj.str("duration"))?.toIntOrNull() ?: -1
        val album = decode(more.str("album") ?: obj.str("album")).ifBlank { null }
        val albumId = more.str("album_id") ?: obj.str("albumid")
        val artists = artistsFrom(more).ifEmpty { artistsFrom(obj) }.ifEmpty {
            decode(obj.str("subtitle") ?: more.str("music") ?: more.str("primary_artists"))
                .split(',', '·', '-')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.map { SaavnArtist(id = "", name = it) }
                .orEmpty()
        }
        return SaavnSong(
            id = id,
            title = title,
            artists = artists,
            album = album,
            albumId = albumId,
            duration = duration,
            imageUrl = image,
            permaUrl = obj.str("perma_url") ?: more.str("perma_url"),
            encryptedMediaUrl = encrypted,
            explicit = (obj.str("explicit_content") ?: more.str("explicit_content")) == "1",
        )
    }

    private fun parseAlbums(element: JsonElement?): List<SaavnAlbum> {
        val array = listArray(element)
        return array.mapNotNull { obj ->
            val more = asObject(obj["more_info"]) ?: JsonObject(emptyMap())
            val id = obj.str("id") ?: return@mapNotNull null
            SaavnAlbum(
                id = id,
                title = decode(obj.str("title") ?: return@mapNotNull null).ifBlank { return@mapNotNull null },
                artists = artistsFrom(more).ifEmpty { artistsFrom(obj) },
                year = (more.str("year") ?: obj.str("year"))?.toIntOrNull(),
                imageUrl = upgradeImage(obj.str("image") ?: ""),
                permaUrl = obj.str("perma_url"),
                songCount = (more.str("song_count") ?: obj.str("song_count"))?.toIntOrNull(),
            )
        }
    }

    private fun parsePlaylists(element: JsonElement?): List<SaavnPlaylist> {
        val array = listArray(element)
        return array.mapNotNull { obj ->
            val more = asObject(obj["more_info"]) ?: JsonObject(emptyMap())
            val id = obj.str("id") ?: return@mapNotNull null
            SaavnPlaylist(
                id = id,
                title = decode(obj.str("title") ?: return@mapNotNull null).ifBlank { return@mapNotNull null },
                subtitle = decode(obj.str("subtitle") ?: more.str("firstname")).ifBlank { null },
                imageUrl = upgradeImage(obj.str("image") ?: ""),
                permaUrl = obj.str("perma_url"),
                songCount = (more.str("song_count") ?: obj.str("song_count"))?.toIntOrNull(),
            )
        }
    }

    private fun parseArtists(element: JsonElement?): List<SaavnArtist> {
        val array = listArray(element)
        return array.mapNotNull { obj ->
            val name = decode(obj.str("title") ?: obj.str("name") ?: return@mapNotNull null).ifBlank { return@mapNotNull null }
            SaavnArtist(
                id = obj.str("id").orEmpty(),
                name = name,
                imageUrl = upgradeImage(obj.str("image") ?: "").ifBlank { null },
                permaUrl = obj.str("perma_url"),
            )
        }
    }

    private fun listArray(element: JsonElement?): List<JsonObject> {
        if (element == null) return emptyList()
        val obj = asObject(element)
        val array = when {
            element is JsonArray -> element
            obj?.get("data") is JsonArray -> obj["data"]!!.jsonArray
            else -> JsonArray(emptyList())
        }
        return array.mapNotNull { asObject(it) }
    }

    private fun artistsFrom(obj: JsonObject): List<SaavnArtist> {
        val map = asObject(obj["artistMap"])
        val primary = map?.get("primary_artists") ?: map?.get("artists")
        if (primary is JsonArray) {
            return primary.mapNotNull { el ->
                val artist = asObject(el) ?: return@mapNotNull null
                val name = decode(artist.str("name") ?: return@mapNotNull null).ifBlank { return@mapNotNull null }
                SaavnArtist(
                    id = artist.str("id").orEmpty(),
                    name = name,
                    imageUrl = artist.str("image")?.let(::upgradeImage),
                    permaUrl = artist.str("perma_url"),
                )
            }
        }
        val names = decode(obj.str("primary_artists") ?: obj.str("music") ?: obj.str("singers"))
        return names.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            .map { SaavnArtist(id = "", name = it) }
    }

    private fun decryptUrl(encrypted: String): String {
        val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(DES_KEY.toByteArray(Charsets.UTF_8), "DES"))
        val decoded = Base64.decode(encrypted, Base64.DEFAULT)
        return cipher.doFinal(decoded).toString(Charsets.UTF_8)
    }

    private fun qualityUrl(url: String, quality: String = SaavnQualityDefault): String {
        val bitrate = when (quality) {
            "96", "160", "320" -> quality
            else -> SaavnQualityDefault
        }
        val target = "_$bitrate."
        val upgraded = url
            .replace("_12.", target)
            .replace("_48.", target)
            .replace("_96.", target)
            .replace("_160.", target)
            .replace("_320.", target)
        return if (upgraded.startsWith("http")) upgraded else "https://$upgraded"
    }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("\\(.*?\\)|\\[.*?]|feat\\.?.*"), " ")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun upgradeImage(url: String): String =
        url.replace("http://", "https://")
            .replace("50x50", "500x500")
            .replace("150x150", "500x500")
            .replace("80x80", "500x500")

    private fun decode(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return value
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&apos;", "'")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&copy;", "©")
            .trim()
    }

    private fun asObject(element: JsonElement?): JsonObject? = when (element) {
        is JsonObject -> element
        else -> null
    }

    private fun JsonObject.str(key: String): String? {
        val el = this[key] ?: return null
        return when (el) {
            is JsonPrimitive -> el.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }
            else -> null
        }
    }
}
