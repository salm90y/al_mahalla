package com.almahala.netplay.ui.compose

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.view.TextureView
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
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
import com.almahala.netplay.ui.RoomCameraHelper
import kotlinx.coroutines.launch

// ----------------------------------------------------
// 1. DATA MODELS & ENUMS FOR TV CHANNELS ROOM (100% Identical to Movies & Series)
// ----------------------------------------------------
enum class TvRoomSubTab {
    PLAYER,
    CHAT,
    CAMERAS,
    INTERCOM,
    USERS,
    SETTINGS
}

data class TvRoomChatMessage(
    val id: String,
    val sender: String,
    val text: String,
    val time: String,
    val isMe: Boolean = false,
    val avatarColor: Color = Color(0xFF0284C7),
    val imageUrl: String? = null
)

data class TvRoomUserItem(
    val id: String,
    val name: String,
    var role: String,
    val isHost: Boolean = false,
    val isOnline: Boolean = true,
    val isSpeaking: Boolean = false,
    val hasCameraActive: Boolean = false,
    val isFrontCamera: Boolean = true,
    val avatarBg: Color = Color(0xFF0284C7),
    val canChangeVideo: Boolean = true,
    val isMutedVoice: Boolean = false,
    val isMutedChat: Boolean = false
)

// ----------------------------------------------------
// 2. MAIN COMPOSABLE: TvChannelsRoomScreen (100% Matching Movies Theme & Design)
// ----------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvChannelsRoomScreen(
    roomId: String = "tv_main_sports",
    initialStreamUrl: String = "https://live.kwikmotion.com/smcquranlive/quranradiolive/playlist.m3u8",
    roomTitle: String = "بث القنوات الفضائية والرياضية",
    roomCode: String = "#TV-SPORTS",
    isStealthMode: Boolean = false,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Disable click sound effects globally in room
    val localView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        var prevSound = true
        try {
            prevSound = localView.isSoundEffectsEnabled
            localView.isSoundEffectsEnabled = false
        } catch (_: Throwable) {}
        onDispose {
            try {
                localView.isSoundEffectsEnabled = prevSound
            } catch (_: Throwable) {}
        }
    }

    // Identity and Roles
    val currentUserId = remember { CloudflareClient.getCurrentUserId(context) }
    val currentUserName = remember {
        if (isStealthMode) "مجهول (المطور)" else CloudflareClient.getCurrentUsername(context).ifBlank { "مشاهد المحلة" }
    }
    val isAppOwner = remember { TvChannelsRoomManager.isAppOwner(context) }
    val isHost = remember {
        isAppOwner ||
        roomTitle.contains(currentUserName) ||
        roomId.contains(currentUserId) ||
        roomId.startsWith("tv_main_") ||
        TvChannelsRoomManager.activeRealRooms.find { it.roomId == roomId }?.let {
            it.hostId == currentUserId || it.hostName == currentUserName
        } == true
    }

    // Sub-tab selection (PLAYER default)
    var activeSubTab by remember { mutableStateOf(TvRoomSubTab.PLAYER) }

    // Channel Catalog populated from authentic M3U playlist
    val channelsCatalog = remember {
        mutableStateListOf<TvChannelItem>().apply {
            addAll(TvChannelsRoomManager.DEFAULT_TV_CHANNELS)
        }
    }

    // Active currently playing TV channel (Crash-proof fallback)
    val defaultChannel = remember {
        TvChannelsRoomManager.DEFAULT_TV_CHANNELS.firstOrNull() ?: TvChannelItem(
            id = "tv_quran",
            title = "قناة القرآن الكريم",
            name = "القرآن الكريم مباشر",
            logo = "https://images.unsplash.com/photo-1591604129939-f1efa4d9f7fa?w=600&auto=format&fit=crop&q=80",
            streamUrl = "https://live.kwikmotion.com/smcquranlive/quranradiolive/playlist.m3u8",
            category = "قنوات إسلامية"
        )
    }
    var currentChannel by remember(initialStreamUrl) {
        val found = channelsCatalog.find { it.streamUrl == initialStreamUrl }
        mutableStateOf(
            found ?: TvChannelItem(
                id = "m3u_tv_active",
                title = roomTitle.ifBlank { defaultChannel.title },
                name = roomTitle.ifBlank { defaultChannel.name },
                logo = defaultChannel.logo,
                streamUrl = initialStreamUrl.ifBlank { defaultChannel.streamUrl },
                category = "بث فضائي مباشر"
            )
        )
    }

    // Playback and Synchronization States
    var isPlaying by remember { mutableStateOf(true) }
    var videoVolume by remember { mutableFloatStateOf(1.0f) }
    var isMuted by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Instant volume sync to player
    LaunchedEffect(videoVolume) {
        val volInt = (videoVolume * 100).toInt().coerceIn(0, 100)
        webViewRef?.evaluateJavascript(
            "if (typeof setPlayerVolume === 'function') { setPlayerVolume($volInt); }",
            null
        )
    }

    // Search Bar & Modal States
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("الكل") }
    val channelCategories = listOf("الكل", "قنوات رياضية", "قنوات إسلامية", "قنوات إخبارية", "قنوات منوعة", "قنوات وثائقية", "قنوات سينمائية")
    var isExitConfirmDialogOpen by remember { mutableStateOf(false) }

    // Walkie-Talkie Intercom State (Real Zego Engine + Loudspeaker)
    var isIntercomTalking by remember { mutableStateOf(false) }
    var isIntercomLoudspeaker by remember { mutableStateOf(true) }
    var isNoiseSuppressionEnabled by remember { mutableStateOf(true) }
    var isEchoCancellationEnabled by remember { mutableStateOf(true) }

    // Cameras Configuration & State
    var isCameraActive by remember { mutableStateOf(false) }
    var isFrontCamera by remember { mutableStateOf(true) }
    var cameraBoxSize by remember { mutableStateOf<CameraBoxSize>(CameraBoxSize.SMALL) }
    var cameraBoxShape by remember { mutableStateOf<CameraBoxShape>(CameraBoxShape.ROUNDED) }

    // Dialog & Permission States
    var selectedUserForPermissions by remember { mutableStateOf<TvRoomUserItem?>(null) }

    // Room Participants state (Real authentic users only)
    val roomUsers = remember {
        mutableStateListOf<TvRoomUserItem>().apply {
            if (!isStealthMode) {
                add(
                    TvRoomUserItem(
                        id = currentUserId,
                        name = currentUserName,
                        role = if (isHost) "مضيف الغرفة 👑" else if (isAppOwner) "مالك التطبيق 🛡️" else "مشاهد",
                        isHost = isHost,
                        isOnline = true,
                        isSpeaking = false,
                        hasCameraActive = false,
                        isFrontCamera = true,
                        avatarBg = Color(0xFF0284C7),
                        canChangeVideo = true
                    )
                )
            }
        }
    }

    // Keep camera status in roomUsers updated for local user
    LaunchedEffect(isCameraActive, isFrontCamera) {
        val idx = roomUsers.indexOfFirst { it.id == currentUserId }
        if (idx >= 0) {
            roomUsers[idx] = roomUsers[idx].copy(hasCameraActive = isCameraActive, isFrontCamera = isFrontCamera)
        }
    }

    // Live Room Chat Messages State
    val chatMessages = remember {
        mutableStateListOf(
            TvRoomChatMessage("1", "النظام", "مرحباً بك في غرفة $roomTitle! البث الفضائي والصوت متزامن بالكامل ⚡", "الآن", false, Color(0xFF0284C7))
        )
    }
    val chatListState = rememberLazyListState()
    var chatInputText by remember { mutableStateOf("") }

    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            chatListState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    // Search channels from Cloudflare backend or authentic local M3U
    LaunchedEffect(searchQuery, selectedCategory) {
        TvChannelsRoomManager.searchChannelsFromCloudflare(
            context = context,
            query = searchQuery,
            category = selectedCategory
        ) { results ->
            if (results.isNotEmpty()) {
                channelsCatalog.clear()
                channelsCatalog.addAll(results)
            }
        }
    }

    // REAL-TIME WEBSOCKET SYNCHRONIZATION CLIENT
    val syncSocket = remember(roomId) {
        MoviesSyncWebSocket(
            context = context,
            roomId = roomId,
            isStealthMode = isStealthMode,
            onMovieChangeReceived = { stream, title, poster ->
                currentChannel = TvChannelItem(
                    id = "synced_${System.currentTimeMillis()}",
                    title = title.ifBlank { "قناة فضائية متزامنة" },
                    name = title.ifBlank { "قناة فضائية" },
                    logo = poster.ifBlank { defaultChannel.logo },
                    streamUrl = stream,
                    category = "بث متزامن"
                )
                isPlaying = true
                webViewRef?.evaluateJavascript(
                    "if (typeof loadStream === 'function') { loadStream('$stream'); }",
                    null
                )
                Toast.makeText(context, "تم تغيير القناة للغرفة: ${title.take(30)} 📺", Toast.LENGTH_SHORT).show()
            },
            onPlaybackStateReceived = { playState, _ ->
                if (playState && !isPlaying) {
                    isPlaying = true
                    webViewRef?.evaluateJavascript("if (typeof playVideo === 'function') { playVideo(); }", null)
                } else if (!playState && isPlaying) {
                    isPlaying = false
                    webViewRef?.evaluateJavascript("if (typeof pauseVideo === 'function') { pauseVideo(); }", null)
                }
            },
            onChatMessageReceived = { newMsg ->
                if (newMsg.text.startsWith("[IMAGE]:")) {
                    val imgUri = newMsg.text.removePrefix("[IMAGE]:")
                    chatMessages.add(TvRoomChatMessage(newMsg.id, newMsg.sender, "📷 صورة", newMsg.time, newMsg.isMe, newMsg.avatarColor, imgUri))
                } else {
                    chatMessages.add(TvRoomChatMessage(newMsg.id, newMsg.sender, newMsg.text, newMsg.time, newMsg.isMe, newMsg.avatarColor))
                }
            },
            onStateRequested = { client ->
                client.broadcastMovieChange(currentChannel.streamUrl, currentChannel.title, currentChannel.logo)
                client.broadcastPlaybackState(isPlaying, 0f)
            },
            onMemberActionReceived = { targetId, action ->
                if (targetId == currentUserId) {
                    when (action) {
                        "KICK" -> {
                            Toast.makeText(context, "تم إخراجك من الغرفة بواسطة المشرف", Toast.LENGTH_LONG).show()
                            onBack()
                        }
                        "MUTE_VOICE" -> {
                            isIntercomTalking = false
                            ZegoCallManager.stopPublishingAudio()
                            Toast.makeText(context, "تم كتم صوت المايكروفون الخاص بك من قبل المشرف 🔇", Toast.LENGTH_SHORT).show()
                        }
                        "ALLOW_VIDEO" -> {
                            val idx = roomUsers.indexOfFirst { it.id == currentUserId }
                            if (idx >= 0) roomUsers[idx] = roomUsers[idx].copy(canChangeVideo = true)
                            Toast.makeText(context, "منحك المشرف صلاحية تبديل القنوات 📺", Toast.LENGTH_SHORT).show()
                        }
                        "RESTRICT_VIDEO" -> {
                            val idx = roomUsers.indexOfFirst { it.id == currentUserId }
                            if (idx >= 0) roomUsers[idx] = roomUsers[idx].copy(canChangeVideo = false)
                            Toast.makeText(context, "تم تقييد صلاحية تبديل القنوات 🔒", Toast.LENGTH_SHORT).show()
                        }
                        "SET_MODERATOR" -> {
                            val idx = roomUsers.indexOfFirst { it.id == currentUserId }
                            if (idx >= 0) roomUsers[idx] = roomUsers[idx].copy(role = "مشرف 🛡️", canChangeVideo = true)
                            Toast.makeText(context, "تمت ترقيتك إلى مشرف الغرفة 🛡️", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            },
            onVoiceStateReceived = { uId, _, isTalking ->
                val idx = roomUsers.indexOfFirst { it.id == uId }
                if (idx >= 0) {
                    roomUsers[idx] = roomUsers[idx].copy(isSpeaking = isTalking)
                }
            },
            onCameraStateReceived = { uId, _, isCamActive, isFront ->
                val idx = roomUsers.indexOfFirst { it.id == uId }
                if (idx >= 0) {
                    roomUsers[idx] = roomUsers[idx].copy(hasCameraActive = isCamActive, isFrontCamera = isFront)
                }
            }
        )
    }

    // REAL ZEGO WALKIE-TALKIE AUDIO ROOM INITIALIZATION IN SAFE AUDIENCE MODE
    val zegoAudioRoomId = remember(roomId) { "tv_room_${roomId.replace(Regex("[^a-zA-Z0-9_]"), "_").take(30)}" }

    // Permission launchers
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isCameraActive = true
            syncSocket.broadcastCameraState(true, isFrontCamera)
            Toast.makeText(context, "تم تفعيل الكاميرا 📹", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "يرجى منح إذن الكاميرا للمتابعة 🔒", Toast.LENGTH_SHORT).show()
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isIntercomTalking = true
            ZegoCallManager.startPublishingAudio(context, zegoAudioRoomId, currentUserId)
            ZegoCallManager.setSpeakerEnabled(context, true)
            syncSocket.broadcastVoiceState(true)
            Toast.makeText(context, "تم تشغيل المايكروفون 🎙️", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "يرجى منح إذن المايكروفون للتحدث 🔒", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggleCameraWithPermission() {
        if (isCameraActive) {
            isCameraActive = false
            syncSocket.broadcastCameraState(false, isFrontCamera)
        } else {
            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                isCameraActive = true
                syncSocket.broadcastCameraState(true, isFrontCamera)
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    fun toggleIntercomWithPermission() {
        if (isIntercomTalking) {
            isIntercomTalking = false
            ZegoCallManager.stopPublishingAudio()
            syncSocket.broadcastVoiceState(false)
        } else {
            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                isIntercomTalking = true
                ZegoCallManager.startPublishingAudio(context, zegoAudioRoomId, currentUserId)
                ZegoCallManager.setSpeakerEnabled(context, true)
                syncSocket.broadcastVoiceState(true)
            } else {
                audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Image Picker for Chat
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val imgMsg = TvRoomChatMessage(
                id = System.currentTimeMillis().toString(),
                sender = currentUserName,
                text = "📷 صورة",
                time = "الآن",
                isMe = true,
                avatarColor = Color(0xFF0284C7),
                imageUrl = uri.toString()
            )
            chatMessages.add(imgMsg)
            syncSocket.broadcastChatMessage("[IMAGE]:$uri")
        }
    }

    // Initialize Zego audio room
    DisposableEffect(Unit) {
        try {
            ZegoCallManager.initEngine(context)
            ZegoCallManager.setSpeakerEnabled(context, true)
        } catch (_: Throwable) {}
        onDispose {
            try {
                ZegoCallManager.stopPublishingAudio()
                syncSocket.disconnect()
            } catch (_: Throwable) {}
        }
    }

    // Connect WebSocket and fetch authoritative initial state
    LaunchedEffect(roomId) {
        try {
            syncSocket.connect()
            TvChannelsRoomManager.getRoomLatest(context, roomId) { latestRoom: PublicTvRoom? ->
                if (latestRoom != null && latestRoom.streamUrl.isNotBlank()) {
                    currentChannel = TvChannelItem(
                        id = "synced_${System.currentTimeMillis()}",
                        title = latestRoom.currentChannelTitle.ifBlank { latestRoom.title },
                        name = latestRoom.currentChannelTitle.ifBlank { latestRoom.title },
                        logo = latestRoom.logoUrl.ifBlank { defaultChannel.logo },
                        streamUrl = latestRoom.streamUrl,
                        category = "بث فضائي متزامن"
                    )
                }
            }
        } catch (_: Exception) {}
    }

    // Play/Pause & Channel Switch Handler
    fun playSelectedChannel(channel: TvChannelItem) {
        val myUser = roomUsers.find { it.id == currentUserId }
        val canControl = isHost || isAppOwner || (myUser?.canChangeVideo == true) || roomUsers.size <= 1
        if (!canControl) {
            Toast.makeText(context, "التبديل مخصص للمشرفين، تم التغيير محلياً لك 📺", Toast.LENGTH_SHORT).show()
            currentChannel = channel
            isPlaying = true
            webViewRef?.evaluateJavascript("if (typeof loadStream === 'function') { loadStream('${channel.streamUrl}'); }", null)
            return
        }
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        currentChannel = channel
        isPlaying = true

        val jsCmd = "if (typeof loadStream === 'function') { loadStream('${channel.streamUrl}'); }"
        webViewRef?.evaluateJavascript(jsCmd, null)

        TvChannelsRoomManager.updateRoomChannel(context, roomId, channel.streamUrl, channel.title, channel.logo)
        syncSocket.broadcastMovieChange(channel.streamUrl, channel.title, channel.logo)
        syncSocket.broadcastPlaybackState(true, 0f)
        Toast.makeText(context, "جاري بث: ${channel.title.take(35)}... 📺", Toast.LENGTH_SHORT).show()
    }

    fun togglePlayback() {
        val myUser = roomUsers.find { it.id == currentUserId }
        val canControl = isHost || isAppOwner || (myUser?.canChangeVideo == true) || roomUsers.size <= 1
        if (!canControl) {
            Toast.makeText(context, "التحكم في البث مخصص للمشرفين 🔒", Toast.LENGTH_SHORT).show()
            return
        }
        val nextPlay = !isPlaying
        isPlaying = nextPlay
        if (nextPlay) {
            webViewRef?.evaluateJavascript("if (typeof playVideo === 'function') { playVideo(); }", null)
        } else {
            webViewRef?.evaluateJavascript("if (typeof pauseVideo === 'function') { pauseVideo(); }", null)
        }
        syncSocket.broadcastPlaybackState(nextPlay, 0f)
    }

    // Camera box shape converter
    val activeBoxShape = when (cameraBoxShape) {
        CameraBoxShape.ROUNDED -> RoundedCornerShape(12.dp)
        CameraBoxShape.SQUARE -> RoundedCornerShape(2.dp)
        CameraBoxShape.CIRCLE -> CircleShape
    }

    // Full RTL Root Layout matching Movies & Series Screen
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
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val isDark = isAppInDarkTheme()

                // ====================================================
                // 1. DEDICATED TOP VIDEO PLAYER BOX (265dp, Framed & Clean, Identical to Movies)
                // ====================================================
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, start = 4.dp, end = 4.dp, bottom = 6.dp)
                        .height(265.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .border(1.5.dp, if (isDark) DarkBorder else Color(0xFFCBD5E1), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
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
                                        databaseEnabled = true
                                        mediaPlaybackRequiresUserGesture = false
                                        loadWithOverviewMode = true
                                        useWideViewPort = true
                                        allowContentAccess = true
                                        allowFileAccess = true
                                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                        cacheMode = WebSettings.LOAD_DEFAULT
                                        userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                                    }
                                    webChromeClient = WebChromeClient()
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
                                    }
                                    webViewRef = this

                                    val initialUrl = currentChannel.streamUrl
                                    val initialVolPercent = (videoVolume * 100).toInt()
                                    val playerHtml = """
                                        <!DOCTYPE html>
                                        <html lang="ar">
                                        <head>
                                            <meta charset="utf-8">
                                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                                            <style>
                                                * { margin: 0; padding: 0; box-sizing: border-box; }
                                                html, body {
                                                    width: 100%;
                                                    height: 100%;
                                                    background: #000000;
                                                    overflow: hidden;
                                                    display: flex;
                                                    align-items: center;
                                                    justify-content: center;
                                                }
                                                #player-container {
                                                    width: 100%;
                                                    height: 100%;
                                                    position: relative;
                                                    overflow: hidden;
                                                    background: #000000;
                                                }
                                                video {
                                                    width: 100% !important;
                                                    height: 100% !important;
                                                    object-fit: cover !important;
                                                    background: #000000;
                                                    transform: scale(1.02);
                                                    transform-origin: center center;
                                                }
                                                #touch-shield {
                                                    position: absolute;
                                                    top: 0; left: 0;
                                                    width: 100%; height: 100%;
                                                    z-index: 10;
                                                    background: transparent;
                                                    pointer-events: none;
                                                }
                                            </style>
                                        </head>
                                        <body>
                                            <div id="player-container">
                                                <video id="video-player" playsinline autoplay webkit-playsinline></video>
                                                <div id="touch-shield"></div>
                                            </div>
                                            <script>
                                                // Continuous Background Playback: Prevent stream pausing on app minimize or switching apps
                                                try {
                                                    Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                                                    Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                                                    document.addEventListener('visibilitychange', function(e) { e.stopImmediatePropagation(); }, true);
                                                    window.addEventListener('blur', function(e) { e.stopImmediatePropagation(); }, true);
                                                    window.addEventListener('pagehide', function(e) { e.stopImmediatePropagation(); }, true);
                                                } catch(e) {}
                                            </script>
                                            <script src="https://cdn.jsdelivr.net/npm/hls.js@latest"></script>
                                            <script>
                                                var video = document.getElementById('video-player');
                                                var hls = null;
                                                var currentUrl = '$initialUrl';
                                                var pendingVolume = $initialVolPercent;

                                                function loadStream(url) {
                                                    if (!url) return;
                                                    currentUrl = url;
                                                    try {
                                                        if (hls) { hls.destroy(); hls = null; }
                                                        if (url.indexOf('.m3u8') !== -1 && Hls.isSupported()) {
                                                            hls = new Hls({
                                                                enableWorker: true,
                                                                lowLatencyMode: true,
                                                                backBufferLength: 90
                                                            });
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

                                                function playVideo() {
                                                    try { video.play(); } catch(e) {}
                                                }

                                                function pauseVideo() {
                                                    try { video.pause(); } catch(e) {}
                                                }

                                                function setPlayerVolume(vol) {
                                                    try {
                                                        if (video) {
                                                            video.volume = vol / 100.0;
                                                            video.muted = (vol <= 0);
                                                        }
                                                    } catch(e) {}
                                                }

                                                if (currentUrl) {
                                                    loadStream(currentUrl);
                                                    setPlayerVolume(pendingVolume);
                                                }
                                            </script>
                                        </body>
                                        </html>
                                    """.trimIndent()
                                    loadDataWithBaseURL("http://maxshowplayer.site:2052", playerHtml, "text/html", "UTF-8", null)
                                }
                            } catch (_: Throwable) {
                                android.view.View(ctx).apply {
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                }
                            }
                        },
                        update = { webView ->
                            if (webView is WebView && webViewRef == null) {
                                webViewRef = webView
                            }
                        },
                        onRelease = { view ->
                            try {
                                (view as? WebView)?.destroy()
                            } catch (_: Throwable) {}
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlaid Top Bar (Back, Title, Code, Viewers)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xCC000000), Color.Transparent)
                                )
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { isExitConfirmDialogOpen = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "رجوع",
                                tint = Color.White
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = currentChannel.title,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$roomCode • $roomTitle",
                                fontSize = 9.sp,
                                color = Color(0xFF94A3B8),
                                maxLines = 1
                            )
                        }

                        // Live Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xCCDC2626),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(Color.White, CircleShape)
                                )
                                Text(
                                    text = "مباشر",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Overlaid Bottom Bar (Play/Pause, Volume, Fullscreen)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xCC000000))
                                )
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { togglePlayback() },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "إيقاف" else "تشغيل",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    isMuted = !isMuted
                                    val targetVol = if (isMuted) 0.0f else 1.0f
                                    videoVolume = targetVol
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMuted || videoVolume <= 0f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "كتم/تشغيل الصوت",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Text(
                                text = currentChannel.category,
                                fontSize = 10.sp,
                                color = Color(0xFFE2E8F0),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Participants counter
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0x801E293B)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "${roomUsers.size} مشاهدين",
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ====================================================
                // 2. SUB-TABS DOCK BAR (Slim, Identical to Movies Room)
                // ====================================================
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) DarkSurface else Color.White,
                    border = BorderStroke(0.5.dp, if (isDark) DarkBorder else Color(0xFFE2EAFD)),
                    shadowElevation = 0.5.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Catalog / Channels
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            TvDockIconButton(
                                icon = Icons.Default.LiveTv,
                                isActive = activeSubTab == TvRoomSubTab.PLAYER,
                                isDark = isDark,
                                onClick = {
                                    activeSubTab = TvRoomSubTab.PLAYER
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                        }

                        // 2. Chat
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            TvDockIconButton(
                                icon = Icons.Default.ChatBubbleOutline,
                                isActive = activeSubTab == TvRoomSubTab.CHAT,
                                isDark = isDark,
                                badgeCount = if (activeSubTab != TvRoomSubTab.CHAT) chatMessages.size else 0,
                                onClick = {
                                    activeSubTab = TvRoomSubTab.CHAT
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                        }

                        // 3. Cameras
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            TvDockIconButton(
                                icon = Icons.Default.Videocam,
                                isActive = activeSubTab == TvRoomSubTab.CAMERAS,
                                isDark = isDark,
                                onClick = {
                                    activeSubTab = TvRoomSubTab.CAMERAS
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                        }

                        // 4. Intercom / Walkie-Talkie
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            TvDockIconButton(
                                icon = Icons.Default.Mic,
                                isActive = activeSubTab == TvRoomSubTab.INTERCOM,
                                isDark = isDark,
                                onClick = {
                                    activeSubTab = TvRoomSubTab.INTERCOM
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                        }

                        // 5. Users
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            TvDockIconButton(
                                icon = Icons.Default.PeopleOutline,
                                isActive = activeSubTab == TvRoomSubTab.USERS,
                                isDark = isDark,
                                badgeCount = roomUsers.size,
                                onClick = {
                                    activeSubTab = TvRoomSubTab.USERS
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                        }

                        // 6. Settings
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            TvDockIconButton(
                                icon = Icons.Default.Settings,
                                isActive = activeSubTab == TvRoomSubTab.SETTINGS,
                                isDark = isDark,
                                onClick = {
                                    activeSubTab = TvRoomSubTab.SETTINGS
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ====================================================
                // 3. SUB-TAB VIEW CONTENT (100% Matching Movies Room)
                // ====================================================
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (activeSubTab) {
                        // ----------------------------------------------------
                        // 3A. CHANNELS CATALOG SUB-VIEW
                        // ----------------------------------------------------
                        TvRoomSubTab.PLAYER -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Search Input Field
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = {
                                        Text(
                                            "ابحث في القنوات التلفزيونية والرياضية...",
                                            fontSize = 11.sp,
                                            color = if (isDark) DarkTextSecondary else Color(0xFF94A3B8)
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = Color(0xFF0284C7),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    trailingIcon = {
                                        if (searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { searchQuery = "" }) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "مسح",
                                                    tint = Color(0xFF94A3B8),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = if (isDark) DarkSurface else Color.White,
                                        unfocusedContainerColor = if (isDark) DarkSurface else Color.White,
                                        focusedBorderColor = Color(0xFF0284C7),
                                        unfocusedBorderColor = if (isDark) DarkBorder else Color(0xFFE2E8F0),
                                        focusedTextColor = if (isDark) DarkTextPrimary else Color(0xFF0F172A),
                                        unfocusedTextColor = if (isDark) DarkTextPrimary else Color(0xFF0F172A)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                )

                                // Category Filter Chips
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(channelCategories) { cat ->
                                        val isSelected = cat == selectedCategory
                                        Surface(
                                            shape = RoundedCornerShape(20.dp),
                                            color = if (isSelected) Color(0xFF0284C7) else (if (isDark) DarkSurface else Color(0xFFF1F5F9)),
                                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF0284C7) else (if (isDark) DarkBorder else Color(0xFFE2E8F0))),
                                            modifier = Modifier.clickable {
                                                selectedCategory = cat
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        ) {
                                            Text(
                                                text = cat,
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else (if (isDark) DarkTextPrimary else Color(0xFF475569)),
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }

                                // Channels List
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f).fillMaxWidth()
                                ) {
                                    items(channelsCatalog, key = { it.id }) { channel ->
                                        val isCurrent = channel.streamUrl == currentChannel.streamUrl
                                        Surface(
                                            onClick = { playSelectedChannel(channel) },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isCurrent) (if (isDark) Color(0xFF0C4A6E) else Color(0xFFE0F2FE)) else (if (isDark) DarkSurface else Color.White),
                                            border = BorderStroke(
                                                width = if (isCurrent) 1.5.dp else 0.5.dp,
                                                color = if (isCurrent) Color(0xFF0284C7) else (if (isDark) DarkBorder else Color(0xFFE2EAFD))
                                            ),
                                            shadowElevation = 0.5.dp,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                // Channel Logo
                                                Box(
                                                    modifier = Modifier
                                                        .size(54.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(Color(0xFF0F172A)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    AsyncImage(
                                                        model = channel.logo,
                                                        contentDescription = channel.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }

                                                // Channel Details
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = channel.title,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (isCurrent) Color(0xFF0284C7) else (if (isDark) DarkTextPrimary else Color(0xFF0F172A)),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    ) {
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                                                        ) {
                                                            Text(
                                                                text = channel.category,
                                                                fontSize = 9.sp,
                                                                color = if (isDark) DarkTextSecondary else Color(0xFF64748B),
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                            )
                                                        }
                                                        Text(
                                                            text = "• بث فضائي حي",
                                                            fontSize = 9.sp,
                                                            color = Color(0xFF10B981),
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }

                                                // Play / Active Indicator
                                                if (isCurrent) {
                                                    Icon(
                                                        imageVector = Icons.Default.Equalizer,
                                                        contentDescription = "مشغل الآن",
                                                        tint = Color(0xFF0284C7),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayCircle,
                                                        contentDescription = "تشغيل",
                                                        tint = Color(0xFF0284C7),
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ----------------------------------------------------
                        // 3B. CHAT SUB-VIEW (RTL, Bubbles, Images)
                        // ----------------------------------------------------
                        TvRoomSubTab.CHAT -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .imePadding()
                            ) {
                                LazyColumn(
                                    state = chatListState,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(chatMessages, key = { it.id }) { msg ->
                                        if (msg.sender == "النظام") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(vertical = 3.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (isDark) Color(0xFF131B2E) else Color(0xFFF8FAFC),
                                                    border = BorderStroke(1.dp, if (isDark) DarkBorder else Color(0xFFE2E8F0))
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Info,
                                                            contentDescription = null,
                                                            tint = Color(0xFF0284C7),
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Text(
                                                            text = msg.text,
                                                            fontSize = 10.sp,
                                                            color = if (isDark) DarkTextSecondary else Color(0xFF475569)
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                                horizontalArrangement = if (msg.isMe) Arrangement.Start else Arrangement.End
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(
                                                        topStart = 14.dp,
                                                        topEnd = 14.dp,
                                                        bottomStart = if (msg.isMe) 14.dp else 3.dp,
                                                        bottomEnd = if (msg.isMe) 3.dp else 14.dp
                                                    ),
                                                    color = if (msg.isMe) Color(0xFF0284C7) else (if (isDark) DarkSurface else Color.White),
                                                    border = BorderStroke(
                                                        width = if (msg.imageUrl != null) 0.5.dp else 1.dp,
                                                        color = if (msg.isMe) Color(0xFF0369A1) else (if (isDark) DarkBorder else Color(0xFFE2E8F0))
                                                    ),
                                                    shadowElevation = 0.5.dp,
                                                    modifier = Modifier.widthIn(max = 280.dp)
                                                ) {
                                                    Column(
                                                        modifier = Modifier.padding(
                                                            horizontal = if (msg.imageUrl != null) 3.dp else 10.dp,
                                                            vertical = if (msg.imageUrl != null) 3.dp else 6.dp
                                                        )
                                                    ) {
                                                        if (!msg.isMe) {
                                                            Text(
                                                                text = msg.sender,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color(0xFF0284C7),
                                                                modifier = Modifier.padding(bottom = 2.dp)
                                                            )
                                                        }
                                                        if (msg.imageUrl != null) {
                                                            AsyncImage(
                                                                model = msg.imageUrl,
                                                                contentDescription = "صورة",
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .height(160.dp)
                                                                    .clip(RoundedCornerShape(10.dp))
                                                            )
                                                        } else {
                                                            Text(
                                                                text = msg.text,
                                                                fontSize = 12.sp,
                                                                color = if (msg.isMe) Color.White else (if (isDark) DarkTextPrimary else Color(0xFF0F172A))
                                                            )
                                                        }
                                                        Text(
                                                            text = msg.time,
                                                            fontSize = 8.sp,
                                                            color = if (msg.isMe) Color(0xFFBAE6FD) else (if (isDark) DarkTextSecondary else Color(0xFF94A3B8)),
                                                            modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Chat Input Bar
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    IconButton(
                                        onClick = { imagePickerLauncher.launch("image/*") },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(if (isDark) DarkSurface else Color.White, CircleShape)
                                            .border(1.dp, if (isDark) DarkBorder else Color(0xFFE2E8F0), CircleShape)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Image,
                                            contentDescription = "إرسال صورة",
                                            tint = Color(0xFF0284C7),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    OutlinedTextField(
                                        value = chatInputText,
                                        onValueChange = { chatInputText = it },
                                        placeholder = {
                                            Text(
                                                "اكتب رسالة للمتواجدين...",
                                                fontSize = 11.sp,
                                                color = if (isDark) DarkTextSecondary else Color(0xFF94A3B8)
                                            )
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(20.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = if (isDark) DarkSurface else Color.White,
                                            unfocusedContainerColor = if (isDark) DarkSurface else Color.White,
                                            focusedBorderColor = Color(0xFF0284C7),
                                            unfocusedBorderColor = if (isDark) DarkBorder else Color(0xFFE2E8F0),
                                            focusedTextColor = if (isDark) DarkTextPrimary else Color(0xFF0F172A),
                                            unfocusedTextColor = if (isDark) DarkTextPrimary else Color(0xFF0F172A)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(42.dp)
                                    )

                                    IconButton(
                                        onClick = {
                                            val t = chatInputText.trim()
                                            if (t.isNotEmpty()) {
                                                val m = TvRoomChatMessage(
                                                    id = System.currentTimeMillis().toString(),
                                                    sender = currentUserName,
                                                    text = t,
                                                    time = "الآن",
                                                    isMe = true,
                                                    avatarColor = Color(0xFF0284C7)
                                                )
                                                chatMessages.add(m)
                                                syncSocket.broadcastChatMessage(t)
                                                chatInputText = ""
                                            }
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
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

                        // ----------------------------------------------------
                        // 3C. CAMERAS SUB-VIEW
                        // ----------------------------------------------------
                        TvRoomSubTab.CAMERAS -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Camera Controls Toolbar
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isDark) DarkSurface else Color.White,
                                    border = BorderStroke(0.5.dp, if (isDark) DarkBorder else Color(0xFFE2E8F0)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Button(
                                                onClick = { toggleCameraWithPermission() },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isCameraActive) Color(0xFFDC2626) else Color(0xFF0284C7)
                                                ),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isCameraActive) Icons.Default.VideocamOff else Icons.Default.Videocam,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (isCameraActive) "إيقاف كاميرتي" else "تشغيل كاميرتي",
                                                    fontSize = 11.sp,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            if (isCameraActive) {
                                                IconButton(
                                                    onClick = {
                                                        isFrontCamera = !isFrontCamera
                                                        syncSocket.broadcastCameraState(true, isFrontCamera)
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.FlipCameraAndroid,
                                                        contentDescription = "تبديل الكاميرا",
                                                        tint = Color(0xFF0284C7)
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = "${roomUsers.count { it.hasCameraActive }} كاميرات نشطة",
                                            fontSize = 10.sp,
                                            color = if (isDark) DarkTextSecondary else Color(0xFF64748B)
                                        )
                                    }
                                }

                                // Cameras Grid
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // 1. My Camera Box
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .size(cameraBoxSize.sizeDp)
                                                .clip(activeBoxShape)
                                                .background(if (isDark) Color(0xFF0F172A) else Color(0xFFF8FAFC))
                                                .border(2.dp, if (isCameraActive) Color(0xFF0284C7) else (if (isDark) DarkBorder else Color(0xFFCBD5E1)), activeBoxShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isCameraActive) {
                                                AndroidView(
                                                    factory = { ctx ->
                                                        TextureView(ctx).apply {
                                                            val helper = RoomCameraHelper(ctx)
                                                            helper.startCamera(this, front = isFrontCamera)
                                                            this.tag = helper
                                                        }
                                                    },
                                                    onRelease = { view ->
                                                        (view.tag as? RoomCameraHelper)?.closeCamera()
                                                    },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xCC000000),
                                                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp)
                                                ) {
                                                    Text(
                                                        text = "أنت (مباشر)",
                                                        fontSize = 8.sp,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            } else {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.VideocamOff,
                                                        contentDescription = null,
                                                        tint = Color(0xFF94A3B8),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Text(
                                                        text = "كاميرتك مغلقة",
                                                        fontSize = 9.sp,
                                                        color = Color(0xFF94A3B8)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // 2. Participants Cameras
                                    items(roomUsers.filter { it.id != currentUserId }) { participant ->
                                        Box(
                                            modifier = Modifier
                                                .size(cameraBoxSize.sizeDp)
                                                .clip(activeBoxShape)
                                                .background(if (participant.hasCameraActive) Color(0xFF0F172A) else (if (isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC)))
                                                .border(2.dp, if (participant.isSpeaking) Color(0xFF10B981) else if (participant.hasCameraActive) Color(0xFF38BDF8) else (if (isDark) DarkBorder else Color(0xFFE2E8F0)), activeBoxShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .background(participant.avatarBg, CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = participant.name.take(1),
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                                Text(
                                                    text = participant.name,
                                                    fontSize = 8.sp,
                                                    color = if (isDark) DarkTextPrimary else Color(0xFF0F172A),
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ----------------------------------------------------
                        // 3D. INTERCOM / WALKIE-TALKIE SUB-VIEW (Zego Loudspeaker)
                        // ----------------------------------------------------
                        TvRoomSubTab.INTERCOM -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                val myUser = roomUsers.find { it.id == currentUserId }
                                val isMutedByMod = myUser?.isMutedVoice == true

                                Box(
                                    modifier = Modifier
                                        .size(135.dp)
                                        .background(
                                            if (isMutedByMod) Color(0xFF64748B) else if (isIntercomTalking) Color(0xFF10B981) else Color(0xFF0284C7),
                                            CircleShape
                                        )
                                        .border(4.dp, Color.White, CircleShape)
                                        .shadow(10.dp, CircleShape)
                                        .clickable {
                                            if (isMutedByMod) {
                                                Toast.makeText(context, "المايكروفون مكتوم من قبل المشرف 🔇", Toast.LENGTH_SHORT).show()
                                                return@clickable
                                            }
                                            toggleIntercomWithPermission()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isMutedByMod) Icons.Default.MicOff else if (isIntercomTalking) Icons.Default.Mic else Icons.Default.MicNone,
                                        contentDescription = "تحدث",
                                        tint = Color.White,
                                        modifier = Modifier.size(52.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(
                                    text = if (isMutedByMod) "المايك مكتوم من قبل المشرف 🔇" else if (isIntercomTalking) "الميكروفون مفتوح - البث مباشر لجميع الأعضاء 🎙️" else "اضغط للتحدث في الهوكي توكي الصوتي المباشر",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isIntercomTalking) Color(0xFF10B981) else Color(0xFF0369A1)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "الصوت مباشر من مكبر الصوت لجميع المتواجدين (Zego Audio Engine)",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }

                        // ----------------------------------------------------
                        // 3E. USERS SUB-VIEW (Real Participants & Roles)
                        // ----------------------------------------------------
                        TvRoomSubTab.USERS -> {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(roomUsers, key = { it.id }) { user ->
                                    val isMe = user.id == currentUserId
                                    val isSelected = selectedUserForPermissions?.id == user.id
                                    val canManage = (isHost || isAppOwner) && !isMe

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isDark) DarkSurface else Color.White,
                                        border = BorderStroke(0.5.dp, if (isDark) DarkBorder else Color(0xFFE2EAFD)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                selectedUserForPermissions = if (isSelected) null else user
                                            }
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .background(user.avatarBg, CircleShape),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text(
                                                            text = user.name.take(1),
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 13.sp
                                                        )
                                                    }
                                                    Column {
                                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                            Text(
                                                                text = user.name,
                                                                fontSize = 12.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (isDark) DarkTextPrimary else Color(0xFF0F172A)
                                                            )
                                                            if (isMe) {
                                                                Text(
                                                                    text = "(أنت)",
                                                                    fontSize = 10.sp,
                                                                    color = Color(0xFF0284C7),
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                            }
                                                        }
                                                        Text(
                                                            text = user.role,
                                                            fontSize = 9.sp,
                                                            color = if (user.isHost) Color(0xFF0284C7) else (if (isDark) DarkTextSecondary else Color(0xFF64748B))
                                                        )
                                                    }
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    if (user.isSpeaking) {
                                                        Icon(
                                                            imageVector = Icons.Default.VolumeUp,
                                                            contentDescription = "يتحدث",
                                                            tint = Color(0xFF10B981),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    Icon(
                                                        imageVector = if (isSelected) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                        contentDescription = null,
                                                        tint = Color(0xFF94A3B8),
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }

                                            // Direct permission chips if expanded
                                            AnimatedVisibility(visible = isSelected) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 6.dp),
                                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    if (canManage) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            TvInlinePermissionChip(
                                                                label = if (user.role.contains("مشرف")) "إلغاء الإشراف" else "ترقية لمشرف 🛡️",
                                                                icon = Icons.Default.Shield,
                                                                color = Color(0xFF0284C7),
                                                                modifier = Modifier.weight(1f),
                                                                onClick = {
                                                                    val newRole = if (user.role.contains("مشرف")) "مشاهد" else "مشرف 🛡️"
                                                                    val updated = user.copy(role = newRole)
                                                                    val idx = roomUsers.indexOfFirst { it.id == user.id }
                                                                    if (idx >= 0) roomUsers[idx] = updated
                                                                    syncSocket.broadcastMemberAction(user.id, if (newRole.contains("مشرف")) "SET_MODERATOR" else "DEMOTE")
                                                                    Toast.makeText(context, "تم تعديل رتبة ${user.name}", Toast.LENGTH_SHORT).show()
                                                                }
                                                            )
                                                            TvInlinePermissionChip(
                                                                label = if (user.canChangeVideo) "منع التبديل 🔒" else "سماح بالتبديل 📺",
                                                                icon = Icons.Default.LiveTv,
                                                                color = if (user.canChangeVideo) Color(0xFFDC2626) else Color(0xFF10B981),
                                                                modifier = Modifier.weight(1f),
                                                                onClick = {
                                                                    val updated = user.copy(canChangeVideo = !user.canChangeVideo)
                                                                    val idx = roomUsers.indexOfFirst { it.id == user.id }
                                                                    if (idx >= 0) roomUsers[idx] = updated
                                                                    syncSocket.broadcastMemberAction(user.id, if (updated.canChangeVideo) "ALLOW_VIDEO" else "RESTRICT_VIDEO")
                                                                    Toast.makeText(context, "تم تعديل صلاحية التبديل لـ ${user.name}", Toast.LENGTH_SHORT).show()
                                                                }
                                                            )
                                                        }
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                        ) {
                                                            TvInlinePermissionChip(
                                                                label = if (user.isMutedVoice) "تشغيل المايك 🎙️" else "كتم المايك 🔇",
                                                                icon = if (user.isMutedVoice) Icons.Default.Mic else Icons.Default.MicOff,
                                                                color = Color(0xFFD97706),
                                                                modifier = Modifier.weight(1f),
                                                                onClick = {
                                                                    val updated = user.copy(isMutedVoice = !user.isMutedVoice)
                                                                    val idx = roomUsers.indexOfFirst { it.id == user.id }
                                                                    if (idx >= 0) roomUsers[idx] = updated
                                                                    syncSocket.broadcastMemberAction(user.id, if (updated.isMutedVoice) "MUTE_VOICE" else "UNMUTE_VOICE")
                                                                }
                                                            )
                                                            TvInlinePermissionChip(
                                                                label = "طرد العضو 🚪",
                                                                icon = Icons.Default.ExitToApp,
                                                                color = Color(0xFFEF4444),
                                                                modifier = Modifier.weight(1f),
                                                                onClick = {
                                                                    roomUsers.removeAll { it.id == user.id }
                                                                    syncSocket.broadcastMemberAction(user.id, "KICK")
                                                                    selectedUserForPermissions = null
                                                                    Toast.makeText(context, "تم طرد ${user.name} من الغرفة", Toast.LENGTH_SHORT).show()
                                                                }
                                                            )
                                                        }
                                                    } else if (isMe) {
                                                        TvInlinePermissionChip(
                                                            label = "مغادرة الغرفة 🚪",
                                                            icon = Icons.Default.Logout,
                                                            color = Color(0xFFEF4444),
                                                            modifier = Modifier.fillMaxWidth(),
                                                            onClick = { isExitConfirmDialogOpen = true }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ----------------------------------------------------
                        // 3F. SETTINGS SUB-VIEW
                        // ----------------------------------------------------
                        TvRoomSubTab.SETTINGS -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // 1. Room Name & Identity Card
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isDark) DarkSurface else Color.White,
                                    border = BorderStroke(1.dp, if (isDark) DarkBorder else Color(0xFFE2EAFD)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Icon(
                                                    imageVector = Icons.Default.LiveTv,
                                                    contentDescription = null,
                                                    tint = Color(0xFF0284C7),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = roomTitle,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDark) DarkTextPrimary else Color(0xFF0F172A)
                                                )
                                            }
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isHost) (if (isDark) Color(0xFF0C4A6E) else Color(0xFFE0F2FE)) else (if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9))
                                            ) {
                                                Text(
                                                    text = if (isHost) "مضيف الغرفة 👑" else "مشاهد متصل 👤",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isHost) Color(0xFF0284C7) else (if (isDark) DarkTextSecondary else Color(0xFF64748B)),
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "معرّف الغرفة: $roomId • كود الانضمام: $roomCode",
                                            fontSize = 10.sp,
                                            color = if (isDark) DarkTextSecondary else Color(0xFF64748B)
                                        )
                                    }
                                }

                                // 2. Walkie-Talkie Audio Settings Card
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isDark) DarkSurface else Color.White,
                                    border = BorderStroke(1.dp, if (isDark) DarkBorder else Color(0xFFE2EAFD)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Icon(
                                                imageVector = Icons.Default.RecordVoiceOver,
                                                contentDescription = null,
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "إعدادات صوت الهوكي توكي 🎙️",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isDark) DarkTextPrimary else Color(0xFF0F172A)
                                            )
                                        }

                                        TvSettingsSwitchRow(
                                            title = "إلغاء الضوضاء الذكي (AI Noise Suppression)",
                                            subtitle = "تصفية صوت التشويش المحيط بالميكروفون",
                                            checked = isNoiseSuppressionEnabled,
                                            isDark = isDark,
                                            onCheckedChange = { checked ->
                                                isNoiseSuppressionEnabled = checked
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        )

                                        TvSettingsSwitchRow(
                                            title = "مانع الصدى الصوتي (Echo Cancellation)",
                                            subtitle = "منع ارتداد صوت القناة داخل الميكروفون",
                                            checked = isEchoCancellationEnabled,
                                            isDark = isDark,
                                            onCheckedChange = { checked ->
                                                isEchoCancellationEnabled = checked
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        )

                                        TvSettingsSwitchRow(
                                            title = "إخراج الصوت عبر مكبر الصوت الخارجي",
                                            subtitle = "صوت الهوكي توكي يخرج من مكبر الصوت الرئيسي",
                                            checked = isIntercomLoudspeaker,
                                            isDark = isDark,
                                            onCheckedChange = { checked ->
                                                isIntercomLoudspeaker = checked
                                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                ZegoCallManager.setSpeakerEnabled(context, checked)
                                            }
                                        )
                                    }
                                }

                                // 3. Exit Room Button
                                Button(
                                    onClick = { isExitConfirmDialogOpen = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFDC2626),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Logout,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "مغادرة الغرفة",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ====================================================
            // EXIT ROOM CONFIRMATION DIALOG (100% Matching Movies Room)
            // ====================================================
            if (isExitConfirmDialogOpen) {
                Dialog(
                    onDismissRequest = { isExitConfirmDialogOpen = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .clip(RoundedCornerShape(22.dp)),
                        shape = RoundedCornerShape(22.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shadowElevation = 10.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .background(Color(0xFFFEF2F2), CircleShape)
                                    .border(1.dp, Color(0xFFFECACA), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Logout,
                                    contentDescription = null,
                                    tint = Color(0xFFDC2626),
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Text(
                                text = if (isHost) "هل تريد الخروج وإنهاء الغرفة؟" else "هل تود الخروج من الغرفة؟",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = if (isHost)
                                    "سيتم إغلاق الغرفة للجميع وإنهاء البث المتزامن."
                                else
                                    "يمكنك العودة للانضمام إلى الغرفة لاحقاً.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                textAlign = TextAlign.Center
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { isExitConfirmDialogOpen = false },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF1F5F9),
                                        contentColor = Color(0xFF334155)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                                ) {
                                    Text(
                                        text = "لا",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Button(
                                    onClick = {
                                        isExitConfirmDialogOpen = false
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        syncSocket.disconnect()
                                        ZegoCallManager.endCall(context, zegoAudioRoomId)
                                        onBack()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFDC2626),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text(
                                        text = "نعم",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// DEDICATED HELPER COMPOSABLES FOR TV ROOM
// ----------------------------------------------------
@Composable
private fun TvDockIconButton(
    icon: ImageVector,
    isActive: Boolean,
    badgeCount: Int? = null,
    isDark: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(36.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) Color(0xFF0284C7) else (if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)),
                modifier = Modifier.size(if (isActive) 21.dp else 19.dp)
            )
        }
        if (badgeCount != null && badgeCount > 0) {
            Text(
                text = "$badgeCount",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFDC2626),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
            )
        }
    }
}

@Composable
private fun TvInlinePermissionChip(
    label: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun TvSettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    isDark: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isDark) DarkTextPrimary else Color(0xFF0F172A)
            )
            Text(
                text = subtitle,
                fontSize = 9.sp,
                color = if (isDark) DarkTextSecondary else Color(0xFF64748B)
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF0284C7),
                uncheckedThumbColor = Color(0xFFCBD5E1),
                uncheckedTrackColor = if (isDark) DarkBorder else Color(0xFFE2EAFD)
            )
        )
    }
}
