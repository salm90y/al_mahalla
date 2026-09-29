package com.almahala.netplay.ui.compose

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.ui.graphics.Color
import com.almahala.netplay.UserManager
import com.almahala.netplay.network.CloudflareClient
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class TvChannelItem(
    val id: String,
    val title: String,
    val name: String,
    val logo: String,
    val streamUrl: String,
    val category: String,
    val isLive: Boolean = true,
    val rating: String = "9.5"
)

data class PublicTvRoom(
    val roomId: String,
    val roomCode: String,
    val title: String,
    val hostName: String,
    val hostId: String = "",
    val hostAvatarBg: Color = Color(0xFF0284C7),
    val currentChannelTitle: String,
    val streamUrl: String,
    val logoUrl: String = "https://images.unsplash.com/photo-1593784991095-a205069470b6?w=600&auto=format&fit=crop&q=80",
    val viewersCount: Int = 1,
    val isLive: Boolean = true,
    val durationText: String = "بث فضائي حي",
    val privacyMode: RoomPrivacyMode = RoomPrivacyMode.PUBLIC,
    val createdAt: Long = System.currentTimeMillis()
)

object TvChannelsRoomManager {
    private const val TAG = "TvChannelsRoomManager"
    private const val PREFS_NAME = "ps1_tv_real_rooms"
    private const val KEY_LOCAL_ROOMS = "active_tv_real_rooms_json"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    const val M3U_SOURCE_URL = "http://maxshowplayer.site:2052/get.php?username=13968296781874&password=20098269331298&type=m3u&output=mpegts"
    const val M3U_BASE_URL = "http://maxshowplayer.site:2052"

    // Pre-seeded comprehensive TV Channels catalog directly from the user's authentic M3U playlist
    val DEFAULT_TV_CHANNELS = listOf(
        TvChannelItem(
            id = "tv_quran",
            title = "قناة القرآن الكريم (مكة المكرمة مباشر)",
            name = "القرآن الكريم مباشر",
            logo = "https://images.unsplash.com/photo-1591604129939-f1efa4d9f7fa?w=600&auto=format&fit=crop&q=80",
            streamUrl = "https://win.holol.com/live/quran/playlist.m3u8",
            category = "قنوات إسلامية"
        ),
        TvChannelItem(
            id = "tv_sunnah",
            title = "قناة السنة النبوية (المدينة المنورة مباشر)",
            name = "السنة النبوية مباشر",
            logo = "https://images.unsplash.com/photo-1584551246679-0daf3d275d0f?w=600&auto=format&fit=crop&q=80",
            streamUrl = "https://win.holol.com/live/sunnah/playlist.m3u8",
            category = "قنوات إسلامية"
        ),
        TvChannelItem(
            id = "tv_bein_news",
            title = "beIN SPORTS الإخبارية المفتوحة HD",
            name = "beIN SPORTS News",
            logo = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=600&auto=format&fit=crop&q=80",
            streamUrl = "http://maxshowplayer.site:2052/live/13968296781874/20098269331298/501.m3u8",
            category = "قنوات رياضية"
        ),
        TvChannelItem(
            id = "tv_bein_1",
            title = "beIN SPORTS 1 HD Premium",
            name = "beIN SPORTS 1",
            logo = "https://images.unsplash.com/photo-1574629810360-7efbbe195018?w=600&auto=format&fit=crop&q=80",
            streamUrl = "http://maxshowplayer.site:2052/live/13968296781874/20098269331298/502.m3u8",
            category = "قنوات رياضية"
        ),
        TvChannelItem(
            id = "tv_bein_2",
            title = "beIN SPORTS 2 HD Premium",
            name = "beIN SPORTS 2",
            logo = "https://images.unsplash.com/photo-1518091043644-c1d4457512c6?w=600&auto=format&fit=crop&q=80",
            streamUrl = "http://maxshowplayer.site:2052/live/13968296781874/20098269331298/503.m3u8",
            category = "قنوات رياضية"
        ),
        TvChannelItem(
            id = "tv_bein_3",
            title = "beIN SPORTS 3 HD Premium",
            name = "beIN SPORTS 3",
            logo = "https://images.unsplash.com/photo-1489944440615-453fc2b6a9a9?w=600&auto=format&fit=crop&q=80",
            streamUrl = "http://maxshowplayer.site:2052/live/13968296781874/20098269331298/504.m3u8",
            category = "قنوات رياضية"
        ),
        TvChannelItem(
            id = "tv_ssc_1",
            title = "SSC Sports 1 HD (الدوري السعودي)",
            name = "SSC 1 HD",
            logo = "https://images.unsplash.com/photo-1431324155629-1a6deb1dec8d?w=600&auto=format&fit=crop&q=80",
            streamUrl = "http://maxshowplayer.site:2052/live/13968296781874/20098269331298/505.m3u8",
            category = "قنوات رياضية"
        ),
        TvChannelItem(
            id = "tv_alkass_1",
            title = "قناة الكأس 1 HD مباشر",
            name = "Alkass One HD",
            logo = "https://images.unsplash.com/photo-1511886929837-354d827aae26?w=600&auto=format&fit=crop&q=80",
            streamUrl = "http://maxshowplayer.site:2052/live/13968296781874/20098269331298/506.m3u8",
            category = "قنوات رياضية"
        ),
        TvChannelItem(
            id = "tv_ontime_1",
            title = "ON Time Sports 1 HD",
            name = "ON Time Sports",
            logo = "https://images.unsplash.com/photo-1522778119026-d647f0596c20?w=600&auto=format&fit=crop&q=80",
            streamUrl = "http://maxshowplayer.site:2052/live/13968296781874/20098269331298/507.m3u8",
            category = "قنوات رياضية"
        ),
        TvChannelItem(
            id = "tv_mbc1",
            title = "MBC 1 HD - البث الفضائي الرسمي",
            name = "MBC 1 HD",
            logo = "https://images.unsplash.com/photo-1522869635100-9f4c5e86aa37?w=600&auto=format&fit=crop&q=80",
            streamUrl = "http://maxshowplayer.site:2052/live/13968296781874/20098269331298/601.m3u8",
            category = "قنوات منوعة"
        ),
        TvChannelItem(
            id = "tv_mbc_masr",
            title = "MBC مصر HD",
            name = "MBC مصر",
            logo = "https://images.unsplash.com/photo-1518791841217-8f162f1e1131?w=600&auto=format&fit=crop&q=80",
            streamUrl = "http://maxshowplayer.site:2052/live/13968296781874/20098269331298/602.m3u8",
            category = "قنوات منوعة"
        ),
        TvChannelItem(
            id = "tv_mbc_action",
            title = "MBC Action HD - أفلام وأكشن",
            name = "MBC Action",
            logo = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=600&auto=format&fit=crop&q=80",
            streamUrl = "http://maxshowplayer.site:2052/live/13968296781874/20098269331298/603.m3u8",
            category = "قنوات سينمائية"
        ),
        TvChannelItem(
            id = "tv_mbc_max",
            title = "MBC Max HD - هوليوود سينما",
            name = "MBC Max",
            logo = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=600&auto=format&fit=crop&q=80",
            streamUrl = "http://maxshowplayer.site:2052/live/13968296781874/20098269331298/604.m3u8",
            category = "قنوات سينمائية"
        ),
        TvChannelItem(
            id = "tv_mbc_drama",
            title = "MBC Drama HD - المسلسلات الحصرية",
            name = "MBC Drama",
            logo = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&auto=format&fit=crop&q=80",
            streamUrl = "http://maxshowplayer.site:2052/live/13968296781874/20098269331298/605.m3u8",
            category = "قنوات منوعة"
        ),
        TvChannelItem(
            id = "tv_jazeera",
            title = "قناة الجزيرة الإخبارية HD مباشر",
            name = "Al Jazeera Arabic",
            logo = "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=600&auto=format&fit=crop&q=80",
            streamUrl = "https://live-hls-web-aje.akamaized.net/hls/live/2004245-b/aje/index.m3u8",
            category = "قنوات إخبارية"
        ),
        TvChannelItem(
            id = "tv_arabiya",
            title = "قناة العربية الإخبارية HD مباشر",
            name = "Al Arabiya News",
            logo = "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=600&auto=format&fit=crop&q=80",
            streamUrl = "http://maxshowplayer.site:2052/live/13968296781874/20098269331298/701.m3u8",
            category = "قنوات إخبارية"
        ),
        TvChannelItem(
            id = "tv_hadath",
            title = "قناة الحدث الإخبارية HD",
            name = "Al Hadath",
            logo = "https://images.unsplash.com/photo-1586339949916-3e9457bef6d3?w=600&auto=format&fit=crop&q=80",
            streamUrl = "http://maxshowplayer.site:2052/live/13968296781874/20098269331298/702.m3u8",
            category = "قنوات إخبارية"
        ),
        TvChannelItem(
            id = "tv_natgeo",
            title = "ناشيونال جيوغرافيك أبوظبي الوثائقية",
            name = "National Geographic Abu Dhabi",
            logo = "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?w=600&auto=format&fit=crop&q=80",
            streamUrl = "http://maxshowplayer.site:2052/live/13968296781874/20098269331298/801.m3u8",
            category = "قنوات وثائقية"
        ),
        TvChannelItem(
            id = "tv_discovery",
            title = "ديسكفري بالعربية (Discovery Channel)",
            name = "Discovery Arabic",
            logo = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600&auto=format&fit=crop&q=80",
            streamUrl = "http://maxshowplayer.site:2052/live/13968296781874/20098269331298/802.m3u8",
            category = "قنوات وثائقية"
        ),
        TvChannelItem(
            id = "tv_rotana_cinema",
            title = "روتانا سينما HD - مش حتقدر تغمض عينيك",
            name = "Rotana Cinema",
            logo = "https://images.unsplash.com/photo-1440404653325-ab127d49abc1?w=600&auto=format&fit=crop&q=80",
            streamUrl = "http://maxshowplayer.site:2052/live/13968296781874/20098269331298/901.m3u8",
            category = "قنوات سينمائية"
        ),
        TvChannelItem(
            id = "tv_rotana_classic",
            title = "روتانا كلاسيك - روائع زمن الفن الجميل",
            name = "Rotana Classic",
            logo = "https://images.unsplash.com/photo-1485846234645-a62644f84728?w=600&auto=format&fit=crop&q=80",
            streamUrl = "http://maxshowplayer.site:2052/live/13968296781874/20098269331298/903.m3u8",
            category = "قنوات سينمائية"
        ),
        TvChannelItem(
            id = "tv_iraqiya",
            title = "قناة العراقية الإخبارية HD",
            name = "العراقية الإخبارية",
            logo = "https://images.unsplash.com/photo-1526470608268-f674ce90ebd4?w=600&auto=format&fit=crop&q=80",
            streamUrl = "http://maxshowplayer.site:2052/live/13968296781874/20098269331298/902.m3u8",
            category = "قنوات إخبارية"
        ),
        TvChannelItem(
            id = "tv_sharqiya",
            title = "قناة الشرقية نيوز HD",
            name = "Al Sharqiya News",
            logo = "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=600&auto=format&fit=crop&q=80",
            streamUrl = "http://maxshowplayer.site:2052/live/13968296781874/20098269331298/904.m3u8",
            category = "قنوات إخبارية"
        ),
        TvChannelItem(
            id = "tv_spacetoon",
            title = "سبيستون (Spacetoon - كوكب المغامرات)",
            name = "Spacetoon Kids",
            logo = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&auto=format&fit=crop&q=80",
            streamUrl = "http://maxshowplayer.site:2052/live/13968296781874/20098269331298/905.m3u8",
            category = "قنوات منوعة"
        )
    )

    val activeRealRooms = mutableListOf<PublicTvRoom>()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAppOwner(context: Context): Boolean {
        return MoviesRoomManager.isAppOwner(context)
    }

    fun updateRoomChannel(context: Context, roomId: String, streamUrl: String, channelTitle: String, logoUrl: String) {
        val inMemIndex = activeRealRooms.indexOfFirst { it.roomId == roomId }
        if (inMemIndex >= 0) {
            val r = activeRealRooms[inMemIndex]
            val updated = r.copy(streamUrl = streamUrl, currentChannelTitle = channelTitle, logoUrl = logoUrl)
            activeRealRooms[inMemIndex] = updated
            saveRoomLocally(context, updated)
        } else {
            val localList = loadRoomsLocally(context).toMutableList()
            val loc = localList.find { it.roomId == roomId }
            if (loc != null) {
                val updated = loc.copy(streamUrl = streamUrl, currentChannelTitle = channelTitle, logoUrl = logoUrl)
                saveRoomLocally(context, updated)
            }
        }

        val baseUrl = CloudflareClient.getBaseUrl(context)
        val jsonBody = JSONObject().apply {
            put("roomId", roomId)
            put("streamUrl", streamUrl)
            put("channelTitle", channelTitle)
            put("logoUrl", logoUrl)
        }
        val request = Request.Builder()
            .url("$baseUrl/api/tv/rooms/update")
            .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) { response.close() }
        })
    }

    fun createRealRoom(
        context: Context,
        title: String,
        initialStreamUrl: String,
        initialChannelTitle: String,
        initialLogoUrl: String,
        privacyMode: RoomPrivacyMode,
        onSuccess: (PublicTvRoom) -> Unit,
        onError: (String) -> Unit
    ) {
        val myUserId = CloudflareClient.getCurrentUserId(context)
        val myUserName = CloudflareClient.getCurrentUsername(context).ifBlank { "مشاهد المحلة" }

        val cleanTitle = title.trim().ifBlank { "غرفة قنوات $myUserName" }
        val randomNum = Random.nextInt(1000, 9999)
        val roomCode = "#TV-$randomNum"
        val roomId = "tv_room_${System.currentTimeMillis()}_$randomNum"

        val defaultChannel = DEFAULT_TV_CHANNELS.first()
        val finalUrl = initialStreamUrl.trim().ifBlank { defaultChannel.streamUrl }
        val finalChannel = initialChannelTitle.trim().ifBlank { defaultChannel.title }
        val finalLogo = initialLogoUrl.trim().ifBlank { defaultChannel.logo }

        val colors = listOf(Color(0xFF0284C7), Color(0xFF2563EB), Color(0xFF0D9488), Color(0xFF7C3AED), Color(0xFFDB2777))
        val avatarBg = colors[Random.nextInt(colors.size)]

        val newRoom = PublicTvRoom(
            roomId = roomId,
            roomCode = roomCode,
            title = cleanTitle,
            hostName = myUserName,
            hostId = myUserId,
            hostAvatarBg = avatarBg,
            currentChannelTitle = finalChannel,
            streamUrl = finalUrl,
            logoUrl = finalLogo,
            viewersCount = 1,
            isLive = true,
            durationText = "بث فضائي حي",
            privacyMode = privacyMode,
            createdAt = System.currentTimeMillis()
        )

        activeRealRooms.add(0, newRoom)
        saveRoomLocally(context, newRoom)

        val baseUrl = CloudflareClient.getBaseUrl(context)
        val jsonPayload = JSONObject().apply {
            put("roomId", roomId)
            put("roomCode", roomCode)
            put("title", cleanTitle)
            put("hostName", myUserName)
            put("hostId", myUserId)
            put("currentChannelTitle", finalChannel)
            put("streamUrl", finalUrl)
            put("logoUrl", finalLogo)
            put("privacyMode", privacyMode.name)
        }

        val request = Request.Builder()
            .url("$baseUrl/api/tv/rooms/create")
            .post(jsonPayload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { onSuccess(newRoom) }
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
                mainHandler.post { onSuccess(newRoom) }
            }
        })
    }

    fun verifyRoomByCode(
        context: Context,
        inputCode: String,
        onFound: (PublicTvRoom) -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanInput = inputCode.trim().uppercase()
        val formattedCode = if (cleanInput.startsWith("#")) cleanInput else "#$cleanInput"

        val inMemoryMatch = activeRealRooms.find { it.roomCode.equals(formattedCode, ignoreCase = true) }
        if (inMemoryMatch != null) {
            onFound(inMemoryMatch)
            return
        }

        val localMatch = loadRoomsLocally(context).find { it.roomCode.equals(formattedCode, ignoreCase = true) }
        if (localMatch != null) {
            onFound(localMatch)
            return
        }

        val baseUrl = CloudflareClient.getBaseUrl(context)
        val request = Request.Builder()
            .url("$baseUrl/api/tv/rooms/verify?code=" + java.net.URLEncoder.encode(formattedCode, "UTF-8"))
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post { onError("تعذر التحقق من رمز الغرفة، تأكد من الاتصال") }
            }
            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string().orEmpty()
                mainHandler.post {
                    try {
                        val json = JSONObject(bodyStr)
                        if (json.optBoolean("success", false) || json.has("roomId")) {
                            val rId = json.optString("roomId", "tv_remote")
                            val rCode = json.optString("roomCode", formattedCode)
                            val rTitle = json.optString("title", "غرفة قنوات تلفزيونية")
                            val rHost = json.optString("hostName", "مضيف الغرفة")
                            val rChannel = json.optString("currentChannelTitle", "بث مباشر")
                            val rUrl = json.optString("streamUrl", DEFAULT_TV_CHANNELS[0].streamUrl)
                            val rLogo = json.optString("logoUrl", DEFAULT_TV_CHANNELS[0].logo)

                            val matched = PublicTvRoom(
                                roomId = rId,
                                roomCode = rCode,
                                title = rTitle,
                                hostName = rHost,
                                currentChannelTitle = rChannel,
                                streamUrl = rUrl,
                                logoUrl = rLogo,
                                viewersCount = json.optInt("viewersCount", 2)
                            )
                            onFound(matched)
                        } else {
                            onError("لم يتم العثور على غرفة بهذا الرمز، تأكد من صحة الرمز")
                        }
                    } catch (e: Exception) {
                        onError("لم يتم العثور على غرفة نشطة برمز: $formattedCode")
                    }
                }
            }
        })
    }

    fun fetchRealPublicRooms(context: Context, onComplete: (List<PublicTvRoom>) -> Unit) {
        val baseUrl = CloudflareClient.getBaseUrl(context)
        val request = Request.Builder()
            .url("$baseUrl/api/tv/rooms/active")
            .get()
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    val local = loadRoomsLocally(context)
                    val merged = (activeRealRooms + local + getPreseededDefaultRooms()).distinctBy { it.roomId }
                    onComplete(merged)
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                mainHandler.post {
                    val serverList = mutableListOf<PublicTvRoom>()
                    try {
                        val arr = JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            serverList.add(
                                PublicTvRoom(
                                    roomId = obj.optString("roomId"),
                                    roomCode = obj.optString("roomCode"),
                                    title = obj.optString("title"),
                                    hostName = obj.optString("hostName"),
                                    hostId = obj.optString("hostId"),
                                    currentChannelTitle = obj.optString("currentChannelTitle"),
                                    streamUrl = obj.optString("streamUrl"),
                                    logoUrl = obj.optString("logoUrl", DEFAULT_TV_CHANNELS[0].logo),
                                    viewersCount = obj.optInt("viewersCount", 1),
                                    isLive = true
                                )
                            )
                        }
                    } catch (_: Exception) {}

                    val local = loadRoomsLocally(context)
                    val merged = (serverList + activeRealRooms + local + getPreseededDefaultRooms()).distinctBy { it.roomId }
                    onComplete(merged)
                }
            }
        })
    }

    fun getRoomLatest(context: Context, roomId: String, onComplete: (PublicTvRoom?) -> Unit) {
        val inMem = activeRealRooms.find { it.roomId == roomId }
        if (inMem != null) {
            onComplete(inMem)
            return
        }
        val local = loadRoomsLocally(context).find { it.roomId == roomId }
        if (local != null) {
            onComplete(local)
            return
        }
        val def = getPreseededDefaultRooms().find { it.roomId == roomId }
        onComplete(def)
    }

    private fun getPreseededDefaultRooms(): List<PublicTvRoom> {
        return listOf(
            PublicTvRoom(
                roomId = "tv_main_sports",
                roomCode = "#TV-SPORTS",
                title = "بث القنوات الرياضية الكبرى (beIN Sports)",
                hostName = "إدارة المحلة",
                hostAvatarBg = Color(0xFF0284C7),
                currentChannelTitle = "beIN SPORTS News HD",
                streamUrl = DEFAULT_TV_CHANNELS[2].streamUrl,
                logoUrl = DEFAULT_TV_CHANNELS[2].logo,
                viewersCount = 18,
                isLive = true
            ),
            PublicTvRoom(
                roomId = "tv_main_quran",
                roomCode = "#TV-QURAN",
                title = "بث الحرمين الشريفين المباشر 24/7",
                hostName = "خادم الحرمين",
                hostAvatarBg = Color(0xFF059669),
                currentChannelTitle = "قناة القرآن الكريم (مكة المكرمة مباشر)",
                streamUrl = DEFAULT_TV_CHANNELS[0].streamUrl,
                logoUrl = DEFAULT_TV_CHANNELS[0].logo,
                viewersCount = 35,
                isLive = true
            ),
            PublicTvRoom(
                roomId = "tv_main_entertainment",
                roomCode = "#TV-MBC",
                title = "قنوات الترفيه والمسلسلات العربية (MBC)",
                hostName = "أبو فهد",
                hostAvatarBg = Color(0xFF7C3AED),
                currentChannelTitle = "MBC 1 HD",
                streamUrl = DEFAULT_TV_CHANNELS[4].streamUrl,
                logoUrl = DEFAULT_TV_CHANNELS[4].logo,
                viewersCount = 12,
                isLive = true
            )
        )
    }

    private fun saveRoomLocally(context: Context, room: PublicTvRoom) {
        try {
            val list = loadRoomsLocally(context).toMutableList()
            list.removeAll { it.roomId == room.roomId }
            list.add(0, room)
            val arr = JSONArray()
            list.take(20).forEach { r ->
                val o = JSONObject().apply {
                    put("roomId", r.roomId)
                    put("roomCode", r.roomCode)
                    put("title", r.title)
                    put("hostName", r.hostName)
                    put("hostId", r.hostId)
                    put("currentChannelTitle", r.currentChannelTitle)
                    put("streamUrl", r.streamUrl)
                    put("logoUrl", r.logoUrl)
                    put("viewersCount", r.viewersCount)
                    put("privacyMode", r.privacyMode.name)
                    put("createdAt", r.createdAt)
                }
                arr.put(o)
            }
            getPrefs(context).edit().putString(KEY_LOCAL_ROOMS, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getRebroadcastStreamUrl(context: Context, originalUrl: String): String {
        val trimmed = originalUrl.trim()
        if (trimmed.isEmpty()) return ""
        val baseUrl = CloudflareClient.getBaseUrl(context)
        return try {
            "$baseUrl/api/tv/stream/proxy?url=" + java.net.URLEncoder.encode(trimmed, "UTF-8")
        } catch (_: Exception) {
            trimmed
        }
    }

    fun searchChannelsFromCloudflare(
        context: Context,
        query: String = "",
        category: String = "الكل",
        onResult: (List<TvChannelItem>) -> Unit
    ) {
        val baseUrl = CloudflareClient.getBaseUrl(context)
        val qEnc = try { java.net.URLEncoder.encode(query.trim(), "UTF-8") } catch (_: Exception) { "" }
        val catEnc = try { java.net.URLEncoder.encode(category.trim(), "UTF-8") } catch (_: Exception) { "" }
        val url = "$baseUrl/api/tv/channels?q=$qEnc&category=$catEnc"

        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                mainHandler.post {
                    val filtered = DEFAULT_TV_CHANNELS.filter {
                        (query.isBlank() || it.name.contains(query, ignoreCase = true) || it.title.contains(query, ignoreCase = true)) &&
                        (category == "الكل" || it.category == category)
                    }
                    onResult(filtered)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string().orEmpty()
                mainHandler.post {
                    try {
                        val json = JSONObject(body)
                        val arr = json.optJSONArray("channels")
                        if (arr != null && arr.length() > 0) {
                            val list = mutableListOf<TvChannelItem>()
                            for (i in 0 until arr.length()) {
                                val o = arr.getJSONObject(i)
                                list.add(
                                    TvChannelItem(
                                        id = o.optString("id", "ch_$i"),
                                        name = o.optString("name", "قناة فضائية"),
                                        title = o.optString("title", "قناة فضائية"),
                                        category = o.optString("category", "قنوات فضائية"),
                                        logo = o.optString("logo_url", DEFAULT_TV_CHANNELS[0].logo),
                                        streamUrl = o.optString("stream_url", DEFAULT_TV_CHANNELS[0].streamUrl)
                                    )
                                )
                            }
                            onResult(list)
                        } else {
                            val filtered = DEFAULT_TV_CHANNELS.filter {
                                (query.isBlank() || it.name.contains(query, ignoreCase = true) || it.title.contains(query, ignoreCase = true)) &&
                                (category == "الكل" || it.category == category)
                            }
                            onResult(filtered)
                        }
                    } catch (_: Exception) {
                        onResult(DEFAULT_TV_CHANNELS)
                    }
                }
            }
        })
    }

    fun loadRoomsLocally(context: Context): List<PublicTvRoom> {
        val list = mutableListOf<PublicTvRoom>()
        try {
            val raw = getPrefs(context).getString(KEY_LOCAL_ROOMS, null) ?: return emptyList()
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    PublicTvRoom(
                        roomId = o.getString("roomId"),
                        roomCode = o.optString("roomCode", "#TV-1000"),
                        title = o.getString("title"),
                        hostName = o.getString("hostName"),
                        hostId = o.optString("hostId", ""),
                        currentChannelTitle = o.optString("currentChannelTitle", "بث مباشر"),
                        streamUrl = o.getString("streamUrl"),
                        logoUrl = o.optString("logoUrl", DEFAULT_TV_CHANNELS[0].logo),
                        viewersCount = o.optInt("viewersCount", 1),
                        privacyMode = try { RoomPrivacyMode.valueOf(o.optString("privacyMode", "PUBLIC")) } catch (_: Exception) { RoomPrivacyMode.PUBLIC },
                        createdAt = o.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }
}
