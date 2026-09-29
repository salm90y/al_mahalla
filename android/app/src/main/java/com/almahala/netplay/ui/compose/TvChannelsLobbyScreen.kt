package com.almahala.netplay.ui.compose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.almahala.netplay.network.CloudflareClient
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TvChannelsLobbyScreen(
    onBack: () -> Unit,
    onEnterRoom: (roomId: String, streamUrl: String, roomTitle: String, roomCode: String, isStealth: Boolean) -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val currentUserId = remember { CloudflareClient.getCurrentUserId(context) }
    val currentUserName = remember { CloudflareClient.getCurrentUsername(context) }

    // Disable click sound effects
    val localView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(Unit) {
        val prevSound = localView.isSoundEffectsEnabled
        localView.isSoundEffectsEnabled = false
        onDispose { localView.isSoundEffectsEnabled = prevSound }
    }

    // App Owner detection
    val isAppOwner = remember { TvChannelsRoomManager.isAppOwner(context) }
    var isStealthModeActive by remember { mutableStateOf(false) }

    // Real active public rooms state
    val publicRooms = remember { mutableStateListOf<PublicTvRoom>() }
    var isLoadingRooms by remember { mutableStateOf(true) }

    // Dialog States
    var isCreateRoomDialogOpen by remember { mutableStateOf(false) }
    var isJoinRoomDialogOpen by remember { mutableStateOf(false) }
    var isCreatingRoom by remember { mutableStateOf(false) }
    var isVerifyingJoin by remember { mutableStateOf(false) }
    var joinErrorMessage by remember { mutableStateOf<String?>(null) }

    // Dialog Inputs
    var newRoomTitle by remember { mutableStateOf("") }
    var selectedChannel by remember { mutableStateOf<TvChannelItem?>(TvChannelsRoomManager.DEFAULT_TV_CHANNELS.firstOrNull()) }
    var customStreamUrl by remember { mutableStateOf("") }
    var newRoomPrivacy by remember { mutableStateOf<RoomPrivacyMode>(RoomPrivacyMode.PUBLIC) }
    var joinRoomCodeInput by remember { mutableStateOf("") }

    // Search query within active rooms
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    fun refreshRooms() {
        isLoadingRooms = true
        try {
            TvChannelsRoomManager.fetchRealPublicRooms(context) { rooms ->
                try {
                    val myRooms = TvChannelsRoomManager.loadRoomsLocally(context).filter {
                        it.hostId == currentUserId || (currentUserName.isNotBlank() && it.hostName.equals(currentUserName, ignoreCase = true))
                    }
                    val combined = (rooms + myRooms).mapIndexed { index, r ->
                        if (r.roomId.isBlank()) r.copy(roomId = "tv_room_${index}_${r.roomCode}") else r
                    }.distinctBy { it.roomId }
                    publicRooms.clear()
                    publicRooms.addAll(combined)
                } catch (_: Throwable) {}
                isLoadingRooms = false
            }
        } catch (_: Throwable) {
            isLoadingRooms = false
        }
    }

    LaunchedEffect(Unit) {
        refreshRooms()
    }

    val filteredRooms = remember(searchQuery, publicRooms.size) {
        if (searchQuery.trim().isEmpty()) {
            publicRooms.toList()
        } else {
            publicRooms.filter {
                it.title.contains(searchQuery.trim(), ignoreCase = true) ||
                it.currentChannelTitle.contains(searchQuery.trim(), ignoreCase = true) ||
                it.hostName.contains(searchQuery.trim(), ignoreCase = true) ||
                it.roomCode.contains(searchQuery.trim(), ignoreCase = true)
            }
        }
    }

    val totalOnlineViewers = remember(publicRooms.size) {
        publicRooms.sumOf { it.viewersCount }
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
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                // 1. TOP HEADER: Back Button + Title + Quick Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White, CircleShape)
                            .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = "رجوع",
                            tint = Color(0xFF0F172A),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "قنوات تلفزيونية فضائية",
                            fontFamily = TajawalFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "بث حي متزامن لجميع القنوات الفضائية والرياضية",
                            fontFamily = TajawalFontFamily,
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isAppOwner) {
                            IconButton(
                                onClick = {
                                    isStealthModeActive = !isStealthModeActive
                                    Toast.makeText(
                                        context,
                                        if (isStealthModeActive) "تم تفعيل وضع التخفي للمطور (مجهول)" else "تم إلغاء وضع التخفي",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isStealthModeActive) Color(0xFF0F172A) else Color.White,
                                        CircleShape
                                    )
                                    .border(1.dp, Color(0xFFCBD5E1), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isStealthModeActive) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "وضع التخفي للمطور",
                                    tint = if (isStealthModeActive) Color.White else Color(0xFF475569),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { refreshRooms() },
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White, CircleShape)
                                .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "تحديث",
                                tint = Color(0xFF0284C7),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // 2. HERO BANNER: Identity, Active Live Badge & Description
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF0284C7).copy(alpha = 0.08f),
                                        Color(0xFF0284C7).copy(alpha = 0.02f)
                                    )
                                )
                            )
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFF10B981), CircleShape)
                                    )
                                    Text(
                                        text = "بث فضائي متزامن ومباشر",
                                        fontFamily = TajawalFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFF0284C7)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "شاهد المباريات والقنوات مع أصدقائك",
                                    fontFamily = TajawalFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "بث حي مشترك عالي الدقة مع تواصل صوتي فوري بجهاز اللاسلكي وكاميرات حية.",
                                    fontFamily = TajawalFontFamily,
                                    fontSize = 11.sp,
                                    color = Color(0xFF64748B),
                                    lineHeight = 15.sp
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color(0xFFE0F2FE), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = Color(0xFF0284C7),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                // 3. ACTION BUTTONS: Create Room & Join by Code
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            newRoomTitle = ""
                            selectedChannel = TvChannelsRoomManager.DEFAULT_TV_CHANNELS.firstOrNull()
                            customStreamUrl = ""
                            newRoomPrivacy = RoomPrivacyMode.PUBLIC
                            isCreateRoomDialogOpen = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "إنشاء غرفة فضائية",
                            fontFamily = TajawalFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            joinRoomCodeInput = ""
                            joinErrorMessage = null
                            isJoinRoomDialogOpen = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF0F172A)
                        ),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tag,
                            contentDescription = null,
                            tint = Color(0xFF0284C7),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "انضمام برمز القناة",
                            fontFamily = TajawalFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF0F172A)
                        )
                    }
                }

                // 4. ACTIVE ROOMS HEADER & SEARCH
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "الغرف التلفزيونية النشطة الآن",
                            fontFamily = TajawalFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF0F172A)
                        )
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFE0F2FE), RoundedCornerShape(10.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "${filteredRooms.size}",
                                fontFamily = TajawalFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = Color(0xFF0284C7)
                            )
                        }
                    }

                    IconButton(
                        onClick = { isSearchActive = !isSearchActive },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "بحث",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Search Bar Expandable
                AnimatedVisibility(visible = isSearchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        placeholder = {
                            Text(
                                text = "ابحث باسم الغرفة أو القناة أو الرمز...",
                                fontFamily = TajawalFontFamily,
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
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
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedBorderColor = Color(0xFF0284C7),
                            unfocusedBorderColor = Color(0xFFE2E8F0)
                        ),
                        singleLine = true
                    )
                }

                // 5. ROOMS LIST
                if (isLoadingRooms) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF0284C7),
                            modifier = Modifier.size(32.dp),
                            strokeWidth = 3.dp
                        )
                    }
                } else if (filteredRooms.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.TvOff,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "لا توجد غرف قنوات حالياً",
                                fontFamily = TajawalFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = "كن أول من ينشئ غرفة بث فضائي ودعوة أصدقائك!",
                                fontFamily = TajawalFontFamily,
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Button(
                                onClick = { isCreateRoomDialogOpen = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("إنشاء غرفة الآن", fontFamily = TajawalFontFamily, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(filteredRooms, key = { room -> "${room.roomId}_${room.roomCode}" }) { room ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onEnterRoom(
                                            room.roomId,
                                            room.streamUrl,
                                            room.title,
                                            room.roomCode,
                                            isStealthModeActive
                                        )
                                    },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Channel Poster / Logo
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(0xFFF1F5F9))
                                    ) {
                                        AsyncImage(
                                            model = room.logoUrl,
                                            contentDescription = room.currentChannelTitle,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(4.dp)
                                                .background(Color(0xFFDC2626), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "مباشر",
                                                fontFamily = TajawalFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 9.sp,
                                                color = Color.White
                                            )
                                        }
                                    }

                                    // Room Details
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = room.title,
                                                fontFamily = TajawalFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp,
                                                color = Color(0xFF0F172A),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = room.roomCode,
                                                    fontFamily = TajawalFontFamily,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF475569)
                                                )
                                            }
                                        }

                                        Text(
                                            text = "القناة: ${room.currentChannelTitle}",
                                            fontFamily = TajawalFontFamily,
                                            fontSize = 11.sp,
                                            color = Color(0xFF0284C7),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = "المضيف: ${room.hostName}",
                                                fontFamily = TajawalFontFamily,
                                                fontSize = 10.sp,
                                                color = Color(0xFF64748B)
                                            )
                                            Text(
                                                text = "•",
                                                color = Color(0xFFCBD5E1),
                                                fontSize = 10.sp
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.People,
                                                    contentDescription = null,
                                                    tint = Color(0xFF10B981),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = "${room.viewersCount} مشاهد",
                                                    fontFamily = TajawalFontFamily,
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF10B981),
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    // Enter Room Action Button
                                    Button(
                                        onClick = {
                                            onEnterRoom(
                                                room.roomId,
                                                room.streamUrl,
                                                room.title,
                                                room.roomCode,
                                                isStealthModeActive
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "دخول",
                                            fontFamily = TajawalFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. CREATE ROOM DIALOG
            if (isCreateRoomDialogOpen) {
                Dialog(
                    onDismissRequest = { isCreateRoomDialogOpen = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .wrapContentHeight(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "إنشاء غرفة قنوات تلفزيونية",
                                fontFamily = TajawalFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF0F172A)
                            )

                            OutlinedTextField(
                                value = newRoomTitle,
                                onValueChange = { newRoomTitle = it },
                                label = { Text("عنوان الغرفة", fontFamily = TajawalFontFamily, fontSize = 12.sp) },
                                placeholder = { Text("مثال: سهرة الدوري الإسباني beIN Sports", fontFamily = TajawalFontFamily, fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            Text(
                                text = "اختر القناة الفضائية للبث:",
                                fontFamily = TajawalFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF475569)
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(TvChannelsRoomManager.DEFAULT_TV_CHANNELS) { ch ->
                                    val isSelected = selectedChannel?.id == ch.id
                                    Box(
                                        modifier = Modifier
                                            .width(130.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) Color(0xFF0284C7) else Color(0xFFE2E8F0),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .background(if (isSelected) Color(0xFFE0F2FE) else Color(0xFFF8FAFC))
                                            .clickable { selectedChannel = ch }
                                            .padding(8.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            AsyncImage(
                                                model = ch.logo,
                                                contentDescription = ch.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = ch.name,
                                                fontFamily = TajawalFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = if (isSelected) Color(0xFF0284C7) else Color(0xFF0F172A),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = ch.category,
                                                fontFamily = TajawalFontFamily,
                                                fontSize = 9.sp,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = customStreamUrl,
                                onValueChange = { customStreamUrl = it },
                                label = { Text("أو أدخل رابط بث خاص (m3u8 / IPTV / HLS)", fontFamily = TajawalFontFamily, fontSize = 11.sp) },
                                placeholder = { Text("https://example.com/live.m3u8", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        isCreatingRoom = true
                                        val finalUrl = customStreamUrl.trim().ifBlank {
                                            selectedChannel?.streamUrl ?: TvChannelsRoomManager.DEFAULT_TV_CHANNELS[0].streamUrl
                                        }
                                        val finalTitle = selectedChannel?.title ?: "بث تلفزيوني حي"
                                        val finalLogo = selectedChannel?.logo ?: TvChannelsRoomManager.DEFAULT_TV_CHANNELS[0].logo

                                        TvChannelsRoomManager.createRealRoom(
                                            context = context,
                                            title = newRoomTitle,
                                            initialStreamUrl = finalUrl,
                                            initialChannelTitle = finalTitle,
                                            initialLogoUrl = finalLogo,
                                            privacyMode = newRoomPrivacy,
                                            onSuccess = { createdRoom ->
                                                isCreatingRoom = false
                                                isCreateRoomDialogOpen = false
                                                refreshRooms()
                                                onEnterRoom(
                                                    createdRoom.roomId,
                                                    createdRoom.streamUrl,
                                                    createdRoom.title,
                                                    createdRoom.roomCode,
                                                    isStealthModeActive
                                                )
                                            },
                                            onError = {
                                                isCreatingRoom = false
                                            }
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (isCreatingRoom) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                    } else {
                                        Text("بدء البث", fontFamily = TajawalFontFamily, fontWeight = FontWeight.Bold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = { isCreateRoomDialogOpen = false },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("إلغاء", fontFamily = TajawalFontFamily)
                                }
                            }
                        }
                    }
                }
            }

            // 7. JOIN ROOM DIALOG
            if (isJoinRoomDialogOpen) {
                Dialog(
                    onDismissRequest = { isJoinRoomDialogOpen = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .wrapContentHeight(),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "الانضمام برمز القناة / الغرفة",
                                fontFamily = TajawalFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF0F172A)
                            )

                            OutlinedTextField(
                                value = joinRoomCodeInput,
                                onValueChange = {
                                    joinRoomCodeInput = it
                                    joinErrorMessage = null
                                },
                                label = { Text("رمز الغرفة (مثال: #TV-1234)", fontFamily = TajawalFontFamily, fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                isError = joinErrorMessage != null
                            )

                            if (joinErrorMessage != null) {
                                Text(
                                    text = joinErrorMessage ?: "",
                                    fontFamily = TajawalFontFamily,
                                    color = Color(0xFFEF4444),
                                    fontSize = 11.sp
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (joinRoomCodeInput.isBlank()) {
                                            joinErrorMessage = "يرجى كتابة رمز الغرفة"
                                            return@Button
                                        }
                                        isVerifyingJoin = true
                                        TvChannelsRoomManager.verifyRoomByCode(
                                            context = context,
                                            inputCode = joinRoomCodeInput,
                                            onFound = { foundRoom ->
                                                isVerifyingJoin = false
                                                isJoinRoomDialogOpen = false
                                                onEnterRoom(
                                                    foundRoom.roomId,
                                                    foundRoom.streamUrl,
                                                    foundRoom.title,
                                                    foundRoom.roomCode,
                                                    isStealthModeActive
                                                )
                                            },
                                            onError = { errMsg ->
                                                isVerifyingJoin = false
                                                joinErrorMessage = errMsg
                                            }
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (isVerifyingJoin) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp))
                                    } else {
                                        Text("دخول الغرفة", fontFamily = TajawalFontFamily, fontWeight = FontWeight.Bold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = { isJoinRoomDialogOpen = false },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("إلغاء", fontFamily = TajawalFontFamily)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
