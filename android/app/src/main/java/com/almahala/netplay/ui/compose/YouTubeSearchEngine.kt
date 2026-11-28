package com.almahala.netplay.ui.compose

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

object YouTubeSearchEngine {
    private const val TAG = "YouTubeSearchEngine"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // 1. Live Real-time YouTube Autocomplete Suggestions (Google Suggest API)
    suspend fun getLiveSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        try {
            val encodedQuery = URLEncoder.encode(q, "UTF-8")
            val url = "https://suggestqueries.google.com/complete/search?client=firefox&ds=yt&hl=ar&q=$encodedQuery"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36")
                .header("Accept-Language", "ar,en;q=0.9")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val jsonArray = JSONArray(body)
                if (jsonArray.length() > 1) {
                    val suggestionsArray = jsonArray.getJSONArray(1)
                    val result = mutableListOf<String>()
                    for (i in 0 until suggestionsArray.length()) {
                        val text = suggestionsArray.optString(i, "")
                        if (text.isNotEmpty()) {
                            result.add(text)
                        }
                    }
                    return@withContext result
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get live suggestions: ${e.message}")
        }
        return@withContext emptyList()
    }

    // 2. Live Real-time YouTube Search Results (+20-30 real videos without any restriction)
    suspend fun searchRealYouTube(query: String): List<YouTubeVideoItem> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty() || q == "الكل") return@withContext emptyList()

        // Strategy A: YouTube InnerTube JSON API (Fastest, direct JSON, no HTML parsing needed)
        try {
            val innerTubeResults = searchViaInnerTube(q)
            if (innerTubeResults.isNotEmpty()) {
                return@withContext innerTubeResults
            }
        } catch (e: Exception) {
            Log.w(TAG, "InnerTube search fallback triggered: ${e.message}")
        }

        // Strategy B: YouTube HTML search with multiline regex
        try {
            val htmlResults = searchViaHtml(q)
            if (htmlResults.isNotEmpty()) {
                return@withContext htmlResults
            }
        } catch (e: Exception) {
            Log.e(TAG, "HTML search failed: ${e.message}")
        }

        return@withContext emptyList()
    }

    private fun searchViaInnerTube(query: String): List<YouTubeVideoItem> {
        val url = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
        val payload = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", JSONObject().apply {
                    put("clientName", "WEB")
                    put("clientVersion", "2.20240101.01.00")
                    put("hl", "ar")
                    put("gl", "US")
                })
            })
            put("query", query)
        }

        val requestBody = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .header("Content-Type", "application/json")
            .header("Accept-Language", "ar,en;q=0.9")
            .header("Cookie", "PREF=hl=ar&gl=US; CONSENT=YES+cb")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            return parseYouTubeInitialData(json)
        }
    }

    private fun searchViaHtml(query: String): List<YouTubeVideoItem> {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.youtube.com/results?search_query=$encodedQuery"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .header("Accept-Language", "ar,en;q=0.9")
            .header("Cookie", "PREF=hl=ar&gl=US; CONSENT=YES+cb")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val html = response.body?.string() ?: return emptyList()

            // Support both single-line and multiline ytInitialData JSON
            val pattern = Regex("""ytInitialData\s*=\s*(\{[\s\S]+?\});\s*</script>""")
            val match = pattern.find(html)
            if (match != null) {
                val jsonStr = match.groupValues[1]
                val json = JSONObject(jsonStr)
                return parseYouTubeInitialData(json)
            }
        }
        return emptyList()
    }

    private fun parseYouTubeInitialData(json: JSONObject): List<YouTubeVideoItem> {
        val results = mutableListOf<YouTubeVideoItem>()
        try {
            val contents = json.optJSONObject("contents")
                ?.optJSONObject("twoColumnSearchResultsRenderer")
                ?.optJSONObject("primaryContents")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return emptyList()

            for (i in 0 until contents.length()) {
                val section = contents.optJSONObject(i)?.optJSONObject("itemSectionRenderer") ?: continue
                val items = section.optJSONArray("contents") ?: continue
                for (j in 0 until items.length()) {
                    val item = items.optJSONObject(j) ?: continue

                    // Direct video renderer
                    val v = item.optJSONObject("videoRenderer") ?: item.optJSONObject("compactVideoRenderer")
                    if (v != null) {
                        parseSingleVideo(v)?.let { results.add(it) }
                    }

                    // Shelf renderer (items inside shelves)
                    val shelfItems = item.optJSONObject("shelfRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("verticalListRenderer")
                        ?.optJSONArray("items")
                    if (shelfItems != null) {
                        for (k in 0 until shelfItems.length()) {
                            val subV = shelfItems.optJSONObject(k)?.optJSONObject("videoRenderer")
                            if (subV != null) {
                                parseSingleVideo(subV)?.let { results.add(it) }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing YouTube data: ${e.message}")
        }
        return results
    }

    private fun parseSingleVideo(v: JSONObject): YouTubeVideoItem? {
        val videoId = v.optString("videoId")
        if (videoId.isNullOrEmpty()) return null

        val titleObj = v.optJSONObject("title")
        val title = titleObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: titleObj?.optString("simpleText") ?: "فيديو يوتيوب"

        val ownerObj = v.optJSONObject("ownerText") ?: v.optJSONObject("shortBylineText")
        val channel = ownerObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: ownerObj?.optString("simpleText") ?: "قناة يوتيوب"

        val lengthObj = v.optJSONObject("lengthText")
        val duration = lengthObj?.optString("simpleText") ?: "فيديو"

        val viewObj = v.optJSONObject("viewCountText")
        val views = viewObj?.optString("simpleText")
            ?: viewObj?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: "مشاهدات عالية"

        val pubObj = v.optJSONObject("publishedTimeText")
        val time = pubObj?.optString("simpleText") ?: "مؤخراً"

        val thumbUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        return YouTubeVideoItem(
            id = videoId,
            title = title,
            channelTitle = channel,
            duration = duration,
            viewCount = views,
            publishedTime = time,
            thumbnailUrl = thumbUrl,
            category = "يوتيوب",
            isLive = duration.contains("بث") || duration.contains("مباشر") || duration.contains("Live", ignoreCase = true)
        )
    }

    data class ResolvedYouTubeStream(
        val url: String,
        val userAgent: String,
        val isHls: Boolean,
        val qualityLabel: String = ""
    )

    private data class InnerTubePlayerClient(
        val clientName: String,
        val clientVersion: String,
        val clientIdHeader: String,
        val userAgent: String,
        val osName: String? = null,
        val osVersion: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val androidSdkVersion: Int? = null,
        val includeThirdPartyEmbed: Boolean = false
    )

    // 3. Resolve a direct playable stream URL from user's device IP (supports authenticated YouTube cookies + SAPISIDHASH)
    suspend fun resolveDirectStreamUrl(
        videoId: String,
        youtubeCookies: String? = null
    ): ResolvedYouTubeStream? = withContext(Dispatchers.IO) {
        val cleanId = videoId.trim()
        if (cleanId.isEmpty()) return@withContext null

        val cleanCookies = youtubeCookies?.trim()?.takeIf { it.isNotEmpty() }
        val sapisidHash = cleanCookies?.let { computeSapisidHash(it) }

        val clients = buildList {
            add(
                InnerTubePlayerClient(
                    clientName = "ANDROID_VR",
                    clientVersion = "1.60.19",
                    clientIdHeader = "28",
                    userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                    osName = "Android",
                    osVersion = "12L",
                    deviceMake = "Oculus",
                    deviceModel = "Quest 3",
                    androidSdkVersion = 32
                )
            )
            add(
                InnerTubePlayerClient(
                    clientName = "IOS",
                    clientVersion = "20.10.4",
                    clientIdHeader = "5",
                    userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                    osName = "iPhone",
                    osVersion = "18.3.2.22D82",
                    deviceMake = "Apple",
                    deviceModel = "iPhone16,2"
                )
            )
            add(
                InnerTubePlayerClient(
                    clientName = "ANDROID",
                    clientVersion = "20.10.38",
                    clientIdHeader = "3",
                    userAgent = "com.google.android.youtube/20.10.38 (Linux; U; Android 15) gzip",
                    osName = "Android",
                    osVersion = "15",
                    androidSdkVersion = 35
                )
            )
            add(
                InnerTubePlayerClient(
                    clientName = "MWEB",
                    clientVersion = "2.20250312.01.00",
                    clientIdHeader = "2",
                    userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36"
                )
            )
            if (sapisidHash != null) {
                add(
                    InnerTubePlayerClient(
                        clientName = "WEB",
                        clientVersion = "2.20250312.01.00",
                        clientIdHeader = "1",
                        userAgent = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/134.0.0.0 Mobile Safari/537.36"
                    )
                )
            }
        }

        for (client in clients) {
            try {
                val resolved = fetchPlayerStreamForClient(cleanId, client, cleanCookies, sapisidHash)
                if (resolved != null) {
                    return@withContext resolved
                }
            } catch (e: Exception) {
                Log.w(TAG, "Player client ${client.clientName} failed: ${e.message}")
            }
        }
        return@withContext null
    }

    private fun extractCookieValue(cookies: String, key: String): String? {
        return cookies.split(";")
            .map { it.trim() }
            .firstOrNull { it.startsWith("$key=") }
            ?.substringAfter("=")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun computeSapisidHash(cookies: String, origin: String = "https://www.youtube.com"): String? {
        val sapisid = extractCookieValue(cookies, "SAPISID")
            ?: extractCookieValue(cookies, "__Secure-3PAPISID")
            ?: extractCookieValue(cookies, "__Secure-1PAPISID")
            ?: return null
        val timestamp = System.currentTimeMillis() / 1000L
        val input = "$timestamp $sapisid $origin"
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        val sha1Hex = digest.joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${timestamp}_${sha1Hex}"
    }

    private fun fetchPlayerStreamForClient(
        videoId: String,
        client: InnerTubePlayerClient,
        cookies: String?,
        sapisidHash: String?
    ): ResolvedYouTubeStream? {
        val url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
        val clientJson = JSONObject().apply {
            put("clientName", client.clientName)
            put("clientVersion", client.clientVersion)
            put("hl", "ar")
            put("gl", "IQ")
            client.osName?.let { put("osName", it) }
            client.osVersion?.let { put("osVersion", it) }
            client.deviceMake?.let { put("deviceMake", it) }
            client.deviceModel?.let { put("deviceModel", it) }
            client.androidSdkVersion?.let { put("androidSdkVersion", it) }
        }
        val contextJson = JSONObject().apply {
            put("client", clientJson)
            if (client.includeThirdPartyEmbed) {
                put("thirdParty", JSONObject().apply {
                    put("embedUrl", "https://www.youtube.com/")
                })
            }
        }
        val payload = JSONObject().apply {
            put("context", contextJson)
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }

        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val reqBuilder = Request.Builder()
            .url(url)
            .post(body)
            .header("User-Agent", client.userAgent)
            .header("X-YouTube-Client-Name", client.clientIdHeader)
            .header("X-YouTube-Client-Version", client.clientVersion)
            .header("Origin", "https://www.youtube.com")
            .header("X-Origin", "https://www.youtube.com")
            .header("Content-Type", "application/json")
            .header("Accept-Language", "ar,en;q=0.9")

        if (!cookies.isNullOrBlank()) {
            reqBuilder.header("Cookie", cookies)
        }
        if (!sapisidHash.isNullOrBlank()) {
            reqBuilder.header("Authorization", sapisidHash)
        }

        httpClient.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val raw = response.body?.string() ?: return null
            val json = JSONObject(raw)
            val status = json.optJSONObject("playabilityStatus")?.optString("status") ?: ""
            if (!status.equals("OK", ignoreCase = true)) return null

            val streamingData = json.optJSONObject("streamingData") ?: return null
            val candidates = mutableListOf<ResolvedYouTubeStream>()

            val formats = streamingData.optJSONArray("formats")
            if (formats != null) {
                val mp4Items = mutableListOf<Pair<Int, ResolvedYouTubeStream>>()
                for (i in 0 until formats.length()) {
                    val fmt = formats.optJSONObject(i) ?: continue
                    val streamUrl = fmt.optString("url", "")
                    if (streamUrl.isBlank()) continue
                    val mime = fmt.optString("mimeType", "")
                    if (!mime.contains("video/mp4", ignoreCase = true)) continue
                    val itag = fmt.optInt("itag", 0)
                    val qLabel = fmt.optString("qualityLabel", "")
                    val score = when (itag) {
                        22 -> 100 // 720p muxed MP4
                        18 -> 80  // 360p muxed MP4
                        else -> if (fmt.optInt("audioChannels", 0) > 0) 60 else 10
                    }
                    if (score > 10) {
                        mp4Items.add(score to ResolvedYouTubeStream(streamUrl, client.userAgent, isHls = false, qualityLabel = qLabel))
                    }
                }
                mp4Items.sortedByDescending { it.first }.forEach { candidates.add(it.second) }
            }

            val hlsUrl = streamingData.optString("hlsManifestUrl", "")
            if (hlsUrl.isNotBlank()) {
                candidates.add(ResolvedYouTubeStream(hlsUrl, client.userAgent, isHls = true, qualityLabel = "HLS"))
            }

            for (candidate in candidates) {
                if (probeStreamPlayable(candidate.url, candidate.userAgent, candidate.isHls)) {
                    return candidate
                }
            }
        }
        return null
    }

    private fun probeStreamPlayable(streamUrl: String, userAgent: String, isHls: Boolean): Boolean {
        return try {
            val probeClient = httpClient.newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
            val reqBuilder = Request.Builder()
                .url(streamUrl)
                .get()
                .header("User-Agent", userAgent)
            if (!isHls) {
                reqBuilder.header("Range", "bytes=0-1023")
            }
            probeClient.newCall(reqBuilder.build()).execute().use { resp ->
                resp.code == 200 || resp.code == 206
            }
        } catch (_: Exception) {
            false
        }
    }
}
