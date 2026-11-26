package com.almahala.netplay.ui.compose

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.almahala.netplay.network.CloudflareClient

private data class PrivacyOptionUi(
    val mode: RoomPrivacyMode,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

private fun extractCreateRoomVideoId(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    if (trimmed.length == 11 && trimmed.matches(Regex("[a-zA-Z0-9_-]{11}"))) {
        return trimmed
    }
    val pattern = Regex("""(?:youtu\.be/|youtube\.com/(?:watch\?(?:.*&)?v=|embed/|v/|shorts/|live/))([a-zA-Z0-9_-]{11})""")
    return pattern.find(trimmed)?.groupValues?.get(1)
}

@Composable
fun YouTubeCreateRoomScreen(
    onBack: () -> Unit,
    onRoomCreated: (PublicYouTubeRoom, Boolean) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val currentUserName = remember { CloudflareClient.getCurrentUsername(context).ifBlank { "أحمد" } }
    val isAppOwner = remember { YouTubeRoomManager.isAppOwner(context) }

    var roomTitle by remember { mutableStateOf("غرفة $currentUserName") }
    var videoUrlInput by remember { mutableStateOf("") }
    var selectedPrivacy by remember { mutableStateOf(RoomPrivacyMode.PUBLIC) }
    var isStealthMode by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    var isScreenActive by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        onDispose {
            isScreenActive = false
        }
    }

    val extractedVideoId = remember(videoUrlInput) {
        extractCreateRoomVideoId(videoUrlInput)
    }

    val quickTitles = remember {
        listOf(
            "سهرة أفلام",
            "مباريات ورياضة",
            "تلاوات هادئة",
            "بث وموسيقى",
            "شروحات وتقنية"
        )
    }

    val privacyOptions = remember {
        listOf(
            PrivacyOptionUi(
                mode = RoomPrivacyMode.PUBLIC,
                title = "عامة للجميع",
                subtitle = "تظهر في ردهة الغرف",
                icon = Icons.Default.Public
            ),
            PrivacyOptionUi(
                mode = RoomPrivacyMode.FRIENDS,
                title = "الأصدقاء فقط",
                subtitle = "متاحة لقائمة أصدقائك",
                icon = Icons.Default.Group
            ),
            PrivacyOptionUi(
                mode = RoomPrivacyMode.INVITE_ONLY,
                title = "بدعوة خاصة",
                subtitle = "الدخول عبر الكود فقط",
                icon = Icons.Default.VpnKey
            ),
            PrivacyOptionUi(
                mode = RoomPrivacyMode.ONLY_ME,
                title = "خاصة لي",
                subtitle = "مشاهدة منفردة أو تجربة",
                icon = Icons.Default.Lock
            )
        )
    }

    val accentRed = Color(0xFFDC2626)
    val accentRedSoft = Color(0xFFFEF2F2)
    val accentRedBorder = Color(0xFFFECACA)
    val textPrimary = Color(0xFF0F172A)
    val textSecondary = Color(0xFF64748B)
    val cardBorder = Color(0xFFE2E8F0)

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(getAppScreenBackground()),
            containerColor = Color.Transparent,
            topBar = {
                Surface(
                    color = Color.White,
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            onClick = { if (!isCreating) onBack() },
                            shape = CircleShape,
                            color = Color(0xFFF8FAFC),
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = "رجوع",
                                    tint = textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "إنشاء غرفة يوتيوب",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = TajawalFontFamily,
                                color = textPrimary
                            )
                            Text(
                                text = "جهّز غرفتك وشارك الكود مع أصدقائك للمشاهدة معاً",
                                fontSize = 11.5.sp,
                                fontFamily = TajawalFontFamily,
                                color = textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(accentRedSoft),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SmartDisplay,
                                contentDescription = null,
                                tint = accentRed,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            },
            bottomBar = {
                Surface(
                    color = Color.White,
                    shadowElevation = 8.dp,
                    border = BorderStroke(0.8.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isCreating) return@Button
                                isCreating = true
                                try {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                } catch (_: Throwable) {}
                                val appCtx = context.applicationContext
                                val initialVideoId = extractedVideoId ?: "jfKfPfyJRdk"
                                val initialVideoTitle = if (extractedVideoId != null) {
                                    "فيديو يوتيوب مختار"
                                } else {
                                    "بث يوتيوب تفاعلي مباشر"
                                }
                                YouTubeRoomManager.createRealRoom(
                                    context = appCtx,
                                    title = roomTitle,
                                    privacyMode = selectedPrivacy,
                                    initialVideoId = initialVideoId,
                                    initialVideoTitle = initialVideoTitle
                                ) { result ->
                                    if (!isScreenActive) return@createRealRoom
                                    isCreating = false
                                    result.onSuccess { createdRoom ->
                                        try {
                                            Toast.makeText(
                                                appCtx,
                                                "تم إنشاء الغرفة بنجاح (${createdRoom.roomCode})",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } catch (_: Throwable) {}
                                        try {
                                            onRoomCreated(createdRoom, isStealthMode)
                                        } catch (_: Throwable) {}
                                    }.onFailure { err ->
                                        try {
                                            Toast.makeText(
                                                appCtx,
                                                err.message ?: "تعذر إنشاء الغرفة",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } catch (_: Throwable) {}
                                    }
                                }
                            },
                            enabled = !isCreating,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(54.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentRed,
                                disabledContainerColor = accentRed.copy(alpha = 0.65f)
                            )
                        ) {
                            if (isCreating) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "جاري تجهيز الغرفة...",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = TajawalFontFamily,
                                    color = Color.White
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "إنشاء الغرفة وبدء المشاهدة",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = TajawalFontFamily,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Card 1: Room Details & Quick Suggestions
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, cardBorder),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(accentRedSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = accentRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "اسم الغرفة",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = TajawalFontFamily,
                                color = textPrimary
                            )
                        }

                        OutlinedTextField(
                            value = roomTitle,
                            onValueChange = { roomTitle = it },
                            placeholder = {
                                Text(
                                    text = "مثال: سهرة أفلام الأصدقاء",
                                    fontSize = 12.5.sp,
                                    fontFamily = TajawalFontFamily,
                                    color = Color(0xFF94A3B8)
                                )
                            },
                            trailingIcon = {
                                if (roomTitle.isNotEmpty() && !isCreating) {
                                    IconButton(onClick = { roomTitle = "" }) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "مسح",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            enabled = !isCreating,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentRed,
                                unfocusedBorderColor = cardBorder,
                                focusedContainerColor = Color(0xFFFAFAFA),
                                unfocusedContainerColor = Color(0xFFFAFAFA),
                                cursorColor = accentRed
                            )
                        )

                        Text(
                            text = "اقتراحات سريعة:",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = TajawalFontFamily,
                            color = textSecondary
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                quickTitles.take(3).forEach { suggestion ->
                                    val selected = roomTitle.trim() == suggestion
                                    Surface(
                                        onClick = { if (!isCreating) roomTitle = suggestion },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) accentRedSoft else Color(0xFFF8FAFC),
                                        border = BorderStroke(1.dp, if (selected) accentRed else cardBorder),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = suggestion,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            fontFamily = TajawalFontFamily,
                                            color = if (selected) accentRed else Color(0xFF334155),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp)
                                        )
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                quickTitles.drop(3).forEach { suggestion ->
                                    val selected = roomTitle.trim() == suggestion
                                    Surface(
                                        onClick = { if (!isCreating) roomTitle = suggestion },
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (selected) accentRedSoft else Color(0xFFF8FAFC),
                                        border = BorderStroke(1.dp, if (selected) accentRed else cardBorder),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = suggestion,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            fontFamily = TajawalFontFamily,
                                            color = if (selected) accentRed else Color(0xFF334155),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Card 2: Privacy Mode (Organized 2x2 Grid with Icon + Title + Subtitle)
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, cardBorder),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(accentRedSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = accentRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = "خصوصية الغرفة",
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = TajawalFontFamily,
                                color = textPrimary
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            privacyOptions.chunked(2).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowItems.forEach { option ->
                                        val isSelected = selectedPrivacy == option.mode
                                        Surface(
                                            onClick = {
                                                if (!isCreating) {
                                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    selectedPrivacy = option.mode
                                                }
                                            },
                                            shape = RoundedCornerShape(16.dp),
                                            color = if (isSelected) accentRedSoft else Color(0xFFF8FAFC),
                                            border = BorderStroke(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) accentRed else cardBorder
                                            ),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .clip(RoundedCornerShape(10.dp))
                                                            .background(
                                                                if (isSelected) accentRed else Color.White
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = option.icon,
                                                            contentDescription = option.title,
                                                            tint = if (isSelected) Color.White else textSecondary,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    if (isSelected) {
                                                        Icon(
                                                            imageVector = Icons.Default.CheckCircle,
                                                            contentDescription = null,
                                                            tint = accentRed,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = option.title,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = TajawalFontFamily,
                                                    color = if (isSelected) Color(0xFF991B1B) else textPrimary
                                                )
                                                Text(
                                                    text = option.subtitle,
                                                    fontSize = 10.5.sp,
                                                    fontFamily = TajawalFontFamily,
                                                    color = textSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Card 3: Optional Initial YouTube Link
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, cardBorder),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(accentRedSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    tint = accentRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "رابط فيديو البداية (اختياري)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = TajawalFontFamily,
                                    color = textPrimary
                                )
                                Text(
                                    text = "يمكنك لصق رابط يوتيوب الآن أو البحث داخل الغرفة لاحقاً",
                                    fontSize = 10.5.sp,
                                    fontFamily = TajawalFontFamily,
                                    color = textSecondary
                                )
                            }
                        }

                        OutlinedTextField(
                            value = videoUrlInput,
                            onValueChange = { videoUrlInput = it },
                            placeholder = {
                                Text(
                                    text = "https://youtu.be/... أو معرّف الفيديو",
                                    fontSize = 12.sp,
                                    fontFamily = TajawalFontFamily,
                                    color = Color(0xFF94A3B8)
                                )
                            },
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        try {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = clipboard.primaryClip?.getItemAt(0)?.text?.toString().orEmpty()
                                            if (clip.isNotBlank()) {
                                                videoUrlInput = clip.trim()
                                            }
                                        } catch (_: Exception) {}
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentPaste,
                                        contentDescription = "لصق",
                                        tint = accentRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true,
                            enabled = !isCreating,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = accentRed,
                                unfocusedBorderColor = cardBorder,
                                focusedContainerColor = Color(0xFFFAFAFA),
                                unfocusedContainerColor = Color(0xFFFAFAFA),
                                cursorColor = accentRed
                            )
                        )

                        if (extractedVideoId != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, accentRedBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    AsyncImage(
                                        model = "https://img.youtube.com/vi/$extractedVideoId/hqdefault.jpg",
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(width = 76.dp, height = 46.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.Black)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "جاهز للتشغيل عند فتح الغرفة",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = TajawalFontFamily,
                                            color = accentRed
                                        )
                                        Text(
                                            text = "المعرّف: $extractedVideoId",
                                            fontSize = 11.sp,
                                            color = textSecondary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Card 4: Stealth Entry Switch for Owner
                if (isAppOwner) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isStealthMode) Color(0xFF0F172A) else Color.White,
                        border = BorderStroke(1.dp, if (isStealthMode) Color(0xFF334155) else cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "دخول بوضع التخفي",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = TajawalFontFamily,
                                    color = if (isStealthMode) Color.White else textPrimary
                                )
                                Text(
                                    text = "إخفاء اسمك من قائمة المشاهدين عند إنشاء الغرفة",
                                    fontSize = 11.sp,
                                    fontFamily = TajawalFontFamily,
                                    color = if (isStealthMode) Color(0xFF94A3B8) else textSecondary
                                )
                            }
                            Switch(
                                checked = isStealthMode,
                                onCheckedChange = { if (!isCreating) isStealthMode = it }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
