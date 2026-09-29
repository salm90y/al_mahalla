package com.almahala.netplay.ui.compose

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.almahala.netplay.network.CloudflareClient
import com.almahala.netplay.network.ZegoCallManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class TvRoomSubTab {
    CHANNELS,
    CHAT,
    CAMERAS,
    INTERCOM,
    USERS,
    SETTINGS
}

data class TvChatMessage(
    val id: String,
    val sender: String,
    val text: String,
    val time: String,
    val isMe: Boolean = false,
    val avatarColor: Color = Color(0xFF0284C7)
)

data class TvRoomUser(
    val id: String,
    val name: String,
    var role: String,
    val isHost: Boolean = false,
    val isOnline: Boolean = true,
    val isSpeaking: Boolean = false,
    val avatarBg: Color = Color(0xFF0284C7)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvChannelsRoomScreen(
    roomId: String = "tv_main_sports",
    initialStreamUrl: String = "",
    roomTitle: String = "بث القنوات الفضائية والرياضية",
    roomCode: String = "#TV-SPORTS",
    isStealthMode: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val currentUserId = remember { CloudflareClient.getCurrentUserId(context) }
    val currentUserName = remember {
        if (isStealthMode) "مجهول (المطور)" else CloudflareClient.getCurrentUsername(context).ifBlank { "مشاهد المحلة" }
    }
    val isAppOwner = remember { TvChannelsRoomManager.isAppOwner(context) }
    val isHost = remember {
        roomId.contains(currentUserId) || isAppOwner || roomId.startsWith("tv_main_")
    }

    // Current Playing Channel state
    val defaultChannel = TvChannelsRoomManager.DEFAULT_TV_CHANNELS.first()
    var currentChannel by remember {
        val matched = TvChannelsRoomManager.DEFAULT_TV_CHANNELS.find { it.streamUrl == initialStreamUrl }
        mutableStateOf(
            matched ?: TvChannelItem(
                id = "custom_channel",
                title = if (initialStreamUrl.isNotBlank()) "قناة فضائية مباشرة" else defaultChannel.title,
                name = if (initialStreamUrl.isNotBlank()) "قناة مباشرة" else defaultChannel.name,
                logo = defaultChannel.logo,
                streamUrl = initialStreamUrl.ifBlank { defaultChannel.streamUrl },
                category = "بث فضائي"
            )
        )
    }

    // Playback & UI States
    var isPlaying by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(TvRoomSubTab.CHANNELS) }
    var isCustomStreamDialogOpen by remember { mutableStateOf(false) }
    var customStreamInput by remember { mutableStateOf("") }
    var customChannelTitleInput by remember { mutableStateOf("") }

    // Intercom / Walkie-Talkie States
    var isIntercomTalking by remember { mutableStateOf(false) }
    var isSpeakerEnabled by remember { mutableStateOf(true) }

    // Active Category Filter for Channels Tab
    val channelCategories = listOf("الكل", "قنوات رياضية", "قنوات إسلامية", "قنوات إخبارية", "قنوات منوعة", "قنوات وثائقية", "قنوات سينمائية")
    var selectedCategory by remember { mutableStateOf("الكل") }
    var channelSearchQuery by remember { mutableStateOf("") }
    var isSearchingD1 by remember { mutableStateOf(false) }
    val d1ChannelsList = remember { mutableStateListOf<TvChannelItem>() }

    // Fetch channels from Cloudflare D1
    LaunchedEffect(channelSearchQuery, selectedCategory) {
        isSearchingD1 = true
        TvChannelsRoomManager.searchChannelsFromCloudflare(
            context = context,
            query = channelSearchQuery,
            category = selectedCategory
        ) { results ->
            d1ChannelsList.clear()
            d1ChannelsList.addAll(results)
            isSearchingD1 = false
        }
    }

    // Chat messages
    val chatMessages = remember {
        mutableStateListOf(
            TvChatMessage(
                id = "msg_welcome",
                sender = "إدارة البث",
                text = "مرحباً بكم في غرفة البث التلفزيوني الحي! يمكنكم استخدام جهاز اللاسلكي للتحدث والتبديل بين القنوات بحرية.",
                time = "الآن",
                isMe = false,
                avatarColor = Color(0xFF0284C7)
            )
        )
    }
    var chatInputText by remember { mutableStateOf("") }

    // Connected Users
    val roomUsers = remember {
        mutableStateListOf(
            TvRoomUser(
                id = currentUserId,
                name = currentUserName,
                role = if (isHost) "مضيف الغرفة" else "مشاهد",
                isHost = isHost,
                isOnline = true,
                avatarBg = Color(0xFF0284C7)
            ),
            TvRoomUser(
                id = "user_2",
                name = "أبو فهد",
                role = "مشاهد",
                isHost = false,
                isOnline = true,
                avatarBg = Color(0xFF059669)
            ),
            TvRoomUser(
                id = "user_3",
                name = "كابتن سيف",
                role = "مشرف",
                isHost = false,
                isOnline = true,
                avatarBg = Color(0xFF7C3AED)
            )
        )
    }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // REAL ZEGO WALKIE-TALKIE INITIALIZATION IN SAFE AUDIENCE MODE (NO CRASH)
    val zegoAudioRoomId = remember(roomId) { "tv_room_${roomId.replace(Regex("[^a-zA-Z0-9_]"), "_").take(30)}" }

    // Record audio permission launcher
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isIntercomTalking = true
            ZegoCallManager.startPublishingAudio(context, zegoAudioRoomId, currentUserId)
            ZegoCallManager.setSpeakerEnabled(context, true)
            Toast.makeText(context, "الميكروفون قيد البث الآن 🎙️", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "يلزم السماح بالميكروفون لاستخدام جهاز اللاسلكي", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleIntercom() {
        if (isIntercomTalking) {
            isIntercomTalking = false
            ZegoCallManager.stopPublishingAudio()
        } else {
            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                isIntercomTalking = true
                ZegoCallManager.startPublishingAudio(context, zegoAudioRoomId, currentUserId)
                ZegoCallManager.setSpeakerEnabled(context, true)
            } else {
                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    DisposableEffect(roomId, zegoAudioRoomId) {
        onDispose {
            try {
                if (isIntercomTalking) {
                    ZegoCallManager.stopPublishingAudio()
                    ZegoCallManager.endCall(context, zegoAudioRoomId)
                }
                webViewRef?.destroy()
                webViewRef = null
            } catch (_: Throwable) {}
        }
    }

    // Channel Switch Function
    fun switchChannel(ch: TvChannelItem) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        currentChannel = ch
        isPlaying = true
        val stream = ch.streamUrl.trim()
        val rebroadcastUrl = TvChannelsRoomManager.getRebroadcastStreamUrl(context, stream)
        val jsCmd = """
            (function() {
                if (typeof loadStream === 'function') {
                    loadStream('$rebroadcastUrl');
                }
            })();
        """.trimIndent()
        webViewRef?.evaluateJavascript(jsCmd, null)
        TvChannelsRoomManager.updateRoomChannel(context, roomId, stream, ch.title, ch.logo)
        Toast.makeText(context, "تم التحويل إلى: ${ch.name} عبر البث المسرّع 📺", Toast.LENGTH_SHORT).show()
    }

    val filteredChannels = remember(selectedCategory) {
        if (selectedCategory == "الكل") {
            TvChannelsRoomManager.DEFAULT_TV_CHANNELS
        } else {
            TvChannelsRoomManager.DEFAULT_TV_CHANNELS.filter { it.category == selectedCategory }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(getAppScreenBackground())
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (isFullscreen) 0.dp else 10.dp, vertical = if (isFullscreen) 0.dp else 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. TOP HEADER (Hidden when fullscreen)
                if (!isFullscreen) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color.White, CircleShape)
                                .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = "رجوع",
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = currentChannel.name,
                                    fontFamily = TajawalFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF0F172A),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFFDC2626), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "LIVE",
                                        fontFamily = TajawalFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        color = Color.White
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = roomCode,
                                    fontFamily = TajawalFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = Color(0xFF0284C7)
                                )
                                Text(text = "•", color = Color(0xFFCBD5E1), fontSize = 10.sp)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Color(0xFF10B981), CircleShape)
                                    )
                                    Text(
                                        text = "${roomUsers.size} متصل",
                                        fontFamily = TajawalFontFamily,
                                        fontSize = 10.sp,
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cb.setPrimaryClip(ClipData.newPlainText("TV Room Code", roomCode))
                                    Toast.makeText(context, "تم نسخ رمز الغرفة: $roomCode 📋", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color.White, CircleShape)
                                    .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "نسخ الرمز",
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(Color(0xFFFEE2E2), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = "مغادرة",
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // 2. VIDEO PLAYER BOX (Pure Live Stream in 16:9 or Fullscreen)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isFullscreen) Modifier.fillMaxSize()
                            else Modifier
                                .aspectRatio(16f / 9.4f)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                        )
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            try {
                                WebView(ctx).apply {
                                try {
                                    val cookieMgr = CookieManager.getInstance()
                                    cookieMgr.setAcceptCookie(true)
                                    cookieMgr.setAcceptThirdPartyCookies(this, true)
                                } catch (_: Throwable) {}
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    mediaPlaybackRequiresUserGesture = false
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    allowFileAccess = false
                                    cacheMode = WebSettings.LOAD_DEFAULT
                                    userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                }
                                webChromeClient = WebChromeClient()
                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
                                }

                                val initialUrl = TvChannelsRoomManager.getRebroadcastStreamUrl(context, currentChannel.streamUrl)
                                val playerHtml = """
                                    <!DOCTYPE html>
                                    <html lang="ar">
                                    <head>
                                        <meta charset="utf-8">
                                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                        <style>
                                            * { margin: 0; padding: 0; box-sizing: border-box; }
                                            html, body {
                                                width: 100%; height: 100%;
                                                background: #000000;
                                                overflow: hidden;
                                                display: flex; align-items: center; justify-content: center;
                                            }
                                            #player-container {
                                                width: 100%; height: 100%;
                                                position: relative;
                                                background: #000000;
                                            }
                                            video {
                                                width: 100% !important;
                                                height: 100% !important;
                                                object-fit: contain;
                                                background: #000000;
                                            }
                                        </style>
                                    </head>
                                    <body>
                                        <div id="player-container">
                                            <video id="video-player" playsinline autoplay webkit-playsinline></video>
                                        </div>
                                        <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
                                        <script>
                                            var video = document.getElementById('video-player');
                                            var hls = null;

                                            function loadStream(url) {
                                                if (!url) return;
                                                try {
                                                    if (hls) { hls.destroy(); hls = null; }
                                                    if (url.indexOf('.m3u8') !== -1 && Hls.isSupported()) {
                                                        hls = new Hls();
                                                        hls.loadSource(url);
                                                        hls.attachMedia(video);
                                                        hls.on(Hls.Events.MANIFEST_PARSED, function() {
                                                            video.play().catch(function(){});
                                                        });
                                                    } else {
                                                        video.src = url;
                                                        video.load();
                                                        video.play().catch(function(){});
                                                    }
                                                } catch(e) {}
                                            }

                                            function togglePlay(play) {
                                                try {
                                                    if (play) video.play();
                                                    else video.pause();
                                                } catch(e) {}
                                            }

                                            function setMute(muted) {
                                                try { video.muted = muted; } catch(e) {}
                                            }

                                            loadStream('$initialUrl');
                                        </script>
                                    </body>
                                    </html>
                                """.trimIndent()

                                loadDataWithBaseURL("https://tv.almahala.com", playerHtml, "text/html", "UTF-8", null)
                                webViewRef = this
                            }
                        } catch (_: Throwable) {
                            android.view.View(ctx).apply {
                                setBackgroundColor(android.graphics.Color.BLACK)
                            }
                        }
                    },
                    update = { webView ->
                        if (webView is WebView) {
                            webViewRef = webView
                        }
                    },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay Controls (Fullscreen, Mute, Refresh, Custom Stream)
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                isMuted = !isMuted
                                webViewRef?.evaluateJavascript("setMute($isMuted);", null)
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                contentDescription = "كتم الصوت",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                val s = currentChannel.streamUrl
                                webViewRef?.evaluateJavascript("loadStream('$s');", null)
                                Toast.makeText(context, "جاري تحديث البث... 🔄", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "تحديث البث",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = { isFullscreen = !isFullscreen },
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = "ملء الشاشة",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // If fullscreen, stop here
                if (isFullscreen) return@Column

                // 3. WALKIE-TALKIE / INTERCOM COMPACT STRIP
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(
                                        if (isIntercomTalking) Color(0xFFDC2626) else Color(0xFFE0F2FE),
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isIntercomTalking) Icons.Default.Mic else Icons.Default.Radio,
                                    contentDescription = null,
                                    tint = if (isIntercomTalking) Color.White else Color(0xFF0284C7),
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = if (isIntercomTalking) "الميكروفون قيد البث (الجميع يستمع إليك)" else "جهاز اللاسلكي الفضائي (Intercom)",
                                    fontFamily = TajawalFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (isIntercomTalking) Color(0xFFDC2626) else Color(0xFF0F172A)
                                )
                                Text(
                                    text = if (isIntercomTalking) "اضغط مرة أخرى لكتم الصوت" else "تحدث مع رفاق الغرفة بصوت فوري وواضح",
                                    fontFamily = TajawalFontFamily,
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        Button(
                            onClick = { toggleIntercom() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isIntercomTalking) Color(0xFFDC2626) else Color(0xFF0284C7)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (isIntercomTalking) "كتم اللاسلكي" else "تحدث الآن",
                                fontFamily = TajawalFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }
                    }
                }

                // 4. SUB-TABS NAVIGATION BAR
                ScrollableTabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = Color.Transparent,
                    contentColor = Color(0xFF0284C7),
                    edgePadding = 0.dp,
                    indicator = {},
                    divider = {},
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val tabs = listOf(
                        TvRoomSubTab.CHANNELS to ("القنوات" to Icons.Default.Tv),
                        TvRoomSubTab.CHAT to ("المحادثة" to Icons.Default.ChatBubble),
                        TvRoomSubTab.USERS to ("المشاهدون" to Icons.Default.People),
                        TvRoomSubTab.INTERCOM to ("اللاسلكي" to Icons.Default.SettingsVoice),
                        TvRoomSubTab.SETTINGS to ("الإعدادات" to Icons.Default.Settings)
                    )

                    tabs.forEach { (tab, pair) ->
                        val isSelected = selectedTab == tab
                        Tab(
                            selected = isSelected,
                            onClick = { selectedTab = tab },
                            modifier = Modifier
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFF0284C7) else Color.White)
                                .border(1.dp, if (isSelected) Color(0xFF0284C7) else Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = pair.second,
                                    contentDescription = null,
                                    tint = if (isSelected) Color.White else Color(0xFF64748B),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = pair.first,
                                    fontFamily = TajawalFontFamily,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 11.sp,
                                    color = if (isSelected) Color.White else Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }

                // 5. TAB CONTENT
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 4.dp)
                ) {
                    when (selectedTab) {
                        TvRoomSubTab.CHANNELS -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Search Input in Cloudflare D1 Channels
                                OutlinedTextField(
                                    value = channelSearchQuery,
                                    onValueChange = { channelSearchQuery = it },
                                    placeholder = {
                                        Text(
                                            text = "ابحث في قنوات Cloudflare D1 (beIN, MBC, أخبار، كورة...)",
                                            fontFamily = TajawalFontFamily,
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    },
                                    leadingIcon = {
                                        if (isSearchingD1) {
                                            CircularProgressIndicator(color = Color(0xFF0284C7), modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Search,
                                                contentDescription = "بحث",
                                                tint = Color(0xFF0284C7),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    },
                                    trailingIcon = {
                                        if (channelSearchQuery.isNotEmpty()) {
                                            IconButton(onClick = { channelSearchQuery = "" }) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "مسح",
                                                    tint = Color(0xFF64748B),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = Color.White,
                                        unfocusedContainerColor = Color.White,
                                        focusedBorderColor = Color(0xFF0284C7),
                                        unfocusedBorderColor = Color(0xFFE2E8F0)
                                    )
                                )

                                // Category Filters & Add Stream Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        items(channelCategories) { cat ->
                                            val isCatSelected = selectedCategory == cat
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isCatSelected) Color(0xFFE0F2FE) else Color.White)
                                                    .border(1.dp, if (isCatSelected) Color(0xFF0284C7) else Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                                    .clickable { selectedCategory = cat }
                                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                                            ) {
                                                Text(
                                                    text = cat,
                                                    fontFamily = TajawalFontFamily,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isCatSelected) Color(0xFF0284C7) else Color(0xFF475569)
                                                )
                                            }
                                        }
                                    }

                                    IconButton(
                                        onClick = { isCustomStreamDialogOpen = true },
                                        modifier = Modifier
                                            .size(30.dp)
                                            .background(Color(0xFFE0F2FE), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AddLink,
                                            contentDescription = "رابط مخصص",
                                            tint = Color(0xFF0284C7),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                val displayChannels = if (d1ChannelsList.isNotEmpty()) d1ChannelsList else filteredChannels

                                // Channels Grid
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 12.dp)
                                ) {
                                    items(displayChannels, key = { it.id }) { ch ->
                                        val isCurrent = currentChannel.id == ch.id
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { switchChannel(ch) },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isCurrent) Color(0xFFE0F2FE) else Color.White
                                            ),
                                            border = BorderStroke(
                                                width = if (isCurrent) 2.dp else 1.dp,
                                                color = if (isCurrent) Color(0xFF0284C7) else Color(0xFFE2E8F0)
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                AsyncImage(
                                                    model = ch.logo,
                                                    contentDescription = ch.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = ch.name,
                                                        fontFamily = TajawalFontFamily,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = if (isCurrent) Color(0xFF0284C7) else Color(0xFF0F172A),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = ch.category,
                                                        fontFamily = TajawalFontFamily,
                                                        fontSize = 9.sp,
                                                        color = Color(0xFF64748B)
                                                    )
                                                }
                                                if (isCurrent) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayCircle,
                                                        contentDescription = null,
                                                        tint = Color(0xFF0284C7),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        TvRoomSubTab.CHAT -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    contentPadding = PaddingValues(vertical = 6.dp)
                                ) {
                                    items(chatMessages, key = { it.id }) { msg ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (msg.isMe) Color(0xFFE0F2FE) else Color.White
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(28.dp)
                                                        .background(msg.avatarColor, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = msg.sender.take(1),
                                                        fontFamily = TajawalFontFamily,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 11.sp,
                                                        color = Color.White
                                                    )
                                                }
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = msg.sender,
                                                            fontFamily = TajawalFontFamily,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.sp,
                                                            color = Color(0xFF0F172A)
                                                        )
                                                        Text(
                                                            text = msg.time,
                                                            fontFamily = TajawalFontFamily,
                                                            fontSize = 9.sp,
                                                            color = Color(0xFF94A3B8)
                                                        )
                                                    }
                                                    Text(
                                                        text = msg.text,
                                                        fontFamily = TajawalFontFamily,
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF334155)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedTextField(
                                        value = chatInputText,
                                        onValueChange = { chatInputText = it },
                                        placeholder = { Text("اكتب رسالة لرواد الغرفة...", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = Color.White,
                                            unfocusedContainerColor = Color.White
                                        )
                                    )
                                    IconButton(
                                        onClick = {
                                            val t = chatInputText.trim()
                                            if (t.isNotEmpty()) {
                                                chatMessages.add(
                                                    TvChatMessage(
                                                        id = "msg_${System.currentTimeMillis()}",
                                                        sender = currentUserName,
                                                        text = t,
                                                        time = "الآن",
                                                        isMe = true,
                                                        avatarColor = Color(0xFF0284C7)
                                                    )
                                                )
                                                chatInputText = ""
                                            }
                                        },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .background(Color(0xFF0284C7), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Send,
                                            contentDescription = "إرسال",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        TvRoomSubTab.USERS -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                contentPadding = PaddingValues(vertical = 6.dp)
                            ) {
                                items(roomUsers) { user ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.White)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(user.avatarBg, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = user.name.take(1),
                                                    fontFamily = TajawalFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = Color.White
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = user.name,
                                                    fontFamily = TajawalFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = Color(0xFF0F172A)
                                                )
                                                Text(
                                                    text = user.role,
                                                    fontFamily = TajawalFontFamily,
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(Color(0xFF10B981), CircleShape)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        TvRoomSubTab.INTERCOM -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "إعدادات جهاز اللاسلكي الفضائي",
                                            fontFamily = TajawalFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = "نظام اتصال صوتي فوري يعتمد على بروتوكول Zego Cloud فائق السرعة، مصمم لسهرات مشاهدة المباريات والقنوات الحية مع الأصدقاء.",
                                            fontFamily = TajawalFontFamily,
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B),
                                            lineHeight = 16.sp
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "مكبر الصوت الخارجي (Loudspeaker)",
                                                fontFamily = TajawalFontFamily,
                                                fontSize = 12.sp,
                                                color = Color(0xFF0F172A)
                                            )
                                            Switch(
                                                checked = isSpeakerEnabled,
                                                onCheckedChange = {
                                                    isSpeakerEnabled = it
                                                    ZegoCallManager.setSpeakerEnabled(context, it)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        TvRoomSubTab.SETTINGS -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color.White)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "معلومات الغرفة الفضائية",
                                            fontFamily = TajawalFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = "عنوان الغرفة: $roomTitle",
                                            fontFamily = TajawalFontFamily,
                                            fontSize = 11.sp,
                                            color = Color(0xFF475569)
                                        )
                                        Text(
                                            text = "رمز المشاركة: $roomCode",
                                            fontFamily = TajawalFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color(0xFF0284C7)
                                        )
                                        Text(
                                            text = "القناة الحالية: ${currentChannel.title}",
                                            fontFamily = TajawalFontFamily,
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B)
                                        )

                                        Button(
                                            onClick = {
                                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                cb.setPrimaryClip(ClipData.newPlainText("TV Room Code", roomCode))
                                                Toast.makeText(context, "تم نسخ رمز الغرفة بنجاح 📋", Toast.LENGTH_SHORT).show()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("مشاركة رمز الغرفة مع الأصدقاء", fontFamily = TajawalFontFamily, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }

                        TvRoomSubTab.CAMERAS -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "ميزة الكاميرات الحية التفاعلية متاحة في جلسات المشاهدة الجماعية",
                                    fontFamily = TajawalFontFamily,
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // 6. CUSTOM STREAM URL DIALOG
            if (isCustomStreamDialogOpen) {
                Dialog(onDismissRequest = { isCustomStreamDialogOpen = false }) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .wrapContentHeight(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "إضافة رابط قناة فضائية خاصة",
                                fontFamily = TajawalFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF0F172A)
                            )
                            OutlinedTextField(
                                value = customChannelTitleInput,
                                onValueChange = { customChannelTitleInput = it },
                                label = { Text("اسم القناة", fontSize = 11.sp) },
                                placeholder = { Text("مثال: قناة الرياضية 1", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                            OutlinedTextField(
                                value = customStreamInput,
                                onValueChange = { customStreamInput = it },
                                label = { Text("رابط البث (m3u8 / IPTV / HLS)", fontSize = 11.sp) },
                                placeholder = { Text("https://example.com/live.m3u8", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val url = customStreamInput.trim()
                                        if (url.isNotEmpty()) {
                                            val title = customChannelTitleInput.trim().ifBlank { "قناة مخصصة" }
                                            val customCh = TvChannelItem(
                                                id = "custom_${System.currentTimeMillis()}",
                                                title = title,
                                                name = title,
                                                logo = TvChannelsRoomManager.DEFAULT_TV_CHANNELS[0].logo,
                                                streamUrl = url,
                                                category = "قنوات مخصصة"
                                            )
                                            switchChannel(customCh)
                                            isCustomStreamDialogOpen = false
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("تشغيل القناة", fontFamily = TajawalFontFamily, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { isCustomStreamDialogOpen = false },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("إلغاء", fontFamily = TajawalFontFamily, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
