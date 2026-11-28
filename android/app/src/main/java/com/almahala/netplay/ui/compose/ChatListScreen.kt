package com.almahala.netplay.ui.compose

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.layout.ContentScale
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.almahala.netplay.SettingsActivity
import com.almahala.netplay.UserManager
import com.almahala.netplay.network.CloudflareClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

data class ChatConversation(
    val id: String,
    val name: String,
    val lastMsg: String,
    val time: String,
    val avatar: String,
    val isOnline: Boolean = true,
    val unreadCount: Int = 0,
    val isRead: Boolean = true,
    val category: String = "All"
)

@Composable
fun ChatListScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var selectedTab by remember { mutableStateOf(0) } // 0: الكل, 1: المجموعات, 2: القنوات
    val tabs = listOf("الكل", "المجموعات", "القنوات")
    var searchQuery by remember { mutableStateOf("") }
    val initialConversations = remember {
        CloudflareClient.getLocalConversations(context).map { c ->
            val timeStr = if (c.lastMessageAt > 0) {
                try {
                    val diff = System.currentTimeMillis() - c.lastMessageAt
                    if (diff < 60_000) "الآن"
                    else if (diff < 3600_000) "${diff / 60_000} د"
                    else java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(c.lastMessageAt))
                } catch (_: Exception) { "الآن" }
            } else "الآن"
            val resolvedAvatar = CloudflareClient.normalizeAvatarUrl(context, c.otherAvatar)
                .ifBlank { CloudflareClient.getCachedUserAvatar(context, c.otherUserId, c.otherUsername) }
            ChatConversation(
                id = c.otherUserId,
                name = c.otherUsername.ifBlank { "مستخدم" },
                lastMsg = c.lastMessageText.ifBlank { "محادثة جديدة" },
                time = timeStr,
                avatar = resolvedAvatar,
                isOnline = c.isOnline,
                unreadCount = c.unreadCount,
                isRead = c.unreadCount == 0,
                category = "All"
            )
        }
    }
    var conversations by remember { mutableStateOf<List<ChatConversation>>(initialConversations) }
    var friendAvatarMap by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val attemptedAvatarLookups = remember { mutableStateMapOf<String, Boolean>() }
    var showAdminDialog by remember { mutableStateOf(false) }

    var currentUser by remember { mutableStateOf(UserManager.getCurrentUser(context)) }
    val isAdmin = remember(currentUser) {
        val u = currentUser
        u != null && (u.isAdmin || u.role == "مشرف" || u.role == "مدير" || u.username.equals("ahmed", ignoreCase = true))
    }

    // Proactively resolve any missing contact avatars from /users/status so avatars show in the list immediately
    LaunchedEffect(conversations) {
        conversations.forEach { conv ->
            val lookupKey = conv.id.ifBlank { conv.name }.lowercase()
            if (conv.avatar.isBlank() && lookupKey.isNotBlank() && attemptedAvatarLookups[lookupKey] != true) {
                attemptedAvatarLookups[lookupKey] = true
                CloudflareClient.fetchUserAvatarAndStatus(context, conv.id, conv.name) { fetchedAvatar, online ->
                    if (fetchedAvatar.isNotBlank()) {
                        friendAvatarMap = friendAvatarMap + buildMap {
                            if (conv.id.isNotBlank()) put(conv.id, fetchedAvatar)
                            if (conv.name.isNotBlank()) put(conv.name.lowercase(), fetchedAvatar)
                        }
                        conversations = conversations.map { item ->
                            if (item.id.equals(conv.id, ignoreCase = true) || item.name.equals(conv.name, ignoreCase = true)) {
                                item.copy(avatar = fetchedAvatar, isOnline = online || item.isOnline)
                            } else item
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        CloudflareClient.getFriendsList(context) { friends ->
            if (friends.isNotEmpty()) {
                val map = mutableMapOf<String, String>()
                friends.forEach { f ->
                    val norm = CloudflareClient.normalizeAvatarUrl(context, f.avatarUrl)
                    if (norm.isNotBlank()) {
                        if (f.id.isNotBlank()) map[f.id] = norm
                        if (f.username.isNotBlank()) map[f.username.lowercase()] = norm
                    }
                }
                if (map.isNotEmpty()) {
                    friendAvatarMap = friendAvatarMap + map
                    conversations = conversations.map { conv ->
                        if (conv.avatar.isBlank()) {
                            val fromFriend = map[conv.id] ?: map[conv.name.lowercase()] ?: ""
                            if (fromFriend.isNotBlank()) conv.copy(avatar = fromFriend) else conv
                        } else conv
                    }
                }
            }
        }

        var inFlight = false
        var lastAttemptMs = 0L
        while (isActive) {
            currentUser = UserManager.getCurrentUser(context)
            val nowMs = System.currentTimeMillis()
            if (!inFlight || (nowMs - lastAttemptMs > 8000L)) {
                inFlight = true
                lastAttemptMs = nowMs
                CloudflareClient.fetchConversations(context) { list ->
                    inFlight = false
                    if (list.isNotEmpty()) {
                        val mapped = list.map { c ->
                            val timeStr = if (c.lastMessageAt > 0) {
                                try {
                                    val diff = System.currentTimeMillis() - c.lastMessageAt
                                    if (diff < 60_000) "الآن"
                                    else if (diff < 3600_000) "${diff / 60_000} د"
                                    else java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(c.lastMessageAt))
                                } catch (_: Exception) { "الآن" }
                            } else "الآن"
                            val resolvedAvatar = CloudflareClient.normalizeAvatarUrl(context, c.otherAvatar)
                                .ifBlank { friendAvatarMap[c.otherUserId].orEmpty() }
                                .ifBlank { friendAvatarMap[c.otherUsername.lowercase()].orEmpty() }
                                .ifBlank { CloudflareClient.getCachedUserAvatar(context, c.otherUserId, c.otherUsername) }
                            ChatConversation(
                                id = c.otherUserId,
                                name = c.otherUsername.ifBlank { "مستخدم" },
                                lastMsg = c.lastMessageText.ifBlank { "محادثة جديدة" },
                                time = timeStr,
                                avatar = resolvedAvatar,
                                isOnline = c.isOnline,
                                unreadCount = c.unreadCount,
                                isRead = c.unreadCount == 0,
                                category = "All"
                            )
                        }
                        conversations = mapped
                    }
                }
            }
            delay(2500)
        }
    }

    val filteredConversations = conversations.filter {
        searchQuery.isEmpty() || it.name.contains(searchQuery, ignoreCase = true) || it.lastMsg.contains(searchQuery, ignoreCase = true)
    }

    if (showAdminDialog) {
        AlertDialog(
            onDismissRequest = { showAdminDialog = false },
            confirmButton = {
                Button(
                    onClick = { showAdminDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("إغلاق", fontFamily = TajawalFontFamily, color = Color.White)
                }
            },
            title = {
                Text("لوحة التحكم", fontFamily = TajawalFontFamily, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            },
            text = {
                Column {
                    Text("لوحة إدارة النظام والحسابات نشطة ومهيأة بأعلى معايير الأداء والسرعة.", fontFamily = TajawalFontFamily, color = Color(0xFF64748B))
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp)
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(getAppScreenBackground())
            .statusBarsPadding(),
        containerColor = getAppScreenBackground()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 1. Unified Top Bar: Title "الدردشات" on Right (Start in RTL), Action Icons on Left (End in RTL)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Right: Title "الدردشات"
                Text(
                    text = "الدردشات",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TajawalFontFamily,
                    color = Color(0xFF0F172A)
                )

                // Left Action Icons (Admin Dashboard, Notifications & Settings) - Free/flat icons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Control Panel / Dashboard Icon Button (Visible ONLY to Admin)
                    if (isAdmin) {
                        IconButton(
                            onClick = {
                                navController.navigate("admin_dashboard")
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Dashboard,
                                contentDescription = "لوحة التحكم",
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // 2. Notifications Icon Button (Small icon, visible to everyone)
                    IconButton(
                        onClick = {
                            navController.navigate("notifications")
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "الإشعارات",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 3. Settings Icon Button (Free Icon, small symbol)
                    IconButton(
                        onClick = {
                            val intent = Intent(context, SettingsActivity::class.java)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "الإعدادات",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // 2. Search Bar (Placed above tabs, unified dimensions & styling matching FriendsScreen)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .background(Color(0xFFEEF4FB))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(21.dp))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "بحث",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                focusManager.clearFocus()
                            }
                        ),
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 13.5.sp,
                            fontFamily = TajawalFontFamily,
                            color = Color(0xFF0F172A)
                        ),
                        cursorBrush = SolidColor(Color(0xFF2563EB)),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "ابحث عن محادثة",
                                    fontSize = 13.sp,
                                    fontFamily = TajawalFontFamily,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "مسح",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // 3. Category Tabs (الكل / المجموعات / القنوات - Unified dimensions matching FriendsScreen)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                tabs.forEachIndexed { index, tabName ->
                    val isSelected = selectedTab == index
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                selectedTab = index
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = tabName,
                            fontSize = 15.sp,
                            fontFamily = TajawalFontFamily,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .height(2.5.dp)
                                .width(36.dp)
                                .background(if (isSelected) Color(0xFF2563EB) else Color.Transparent, CircleShape)
                        )
                    }
                }
            }

            // 4. Content (Empty State or Free-form Conversations List)
            if (filteredConversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    ) {
                        // Circular Illustration (Unified size matching FriendsScreen)
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .background(Color(0xFFEBF3FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .shadow(4.dp, RoundedCornerShape(18.dp))
                                    .background(Color(0xFF2563EB), RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    repeat(3) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .background(Color.White, CircleShape)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "لا توجد محادثات",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = TajawalFontFamily,
                            color = Color(0xFF0F172A)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "ابدأ محادثة جديدة للتواصل مع أصدقائك.",
                            fontSize = 13.sp,
                            fontFamily = TajawalFontFamily,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { /* Start new chat */ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(21.dp),
                            modifier = Modifier
                                .height(42.dp)
                                .padding(horizontal = 16.dp)
                        ) {
                            Text(
                                text = "محادثة جديدة",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = TajawalFontFamily,
                                color = Color.White
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                items(filteredConversations, key = { it.id.ifBlank { it.name } }) { conv ->
                    val encodedName = try { java.net.URLEncoder.encode(conv.name, "UTF-8") } catch (_: Exception) { conv.name }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                navController.navigate("chat_detail/${conv.id}/$encodedName")
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. Right Side (Start in RTL): Avatar + Name and Last Message
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clickable {
                                            navController.navigate("user_profile/${conv.id}/$encodedName")
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CircleShape)
                                            .background(Color(0xFFEEF4FB)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val rawAvatar = conv.avatar.ifBlank {
                                            friendAvatarMap[conv.id]
                                                ?: friendAvatarMap[conv.name.lowercase()]
                                                ?: CloudflareClient.getCachedUserAvatar(context, conv.id, conv.name)
                                        }
                                        val displayAvatar = CloudflareClient.normalizeAvatarUrl(context, rawAvatar)
                                        val initialChar = conv.name.take(1).uppercase().ifEmpty { "ص" }
                                        if (displayAvatar.isNotBlank()) {
                                            val avatarRequest = remember(displayAvatar) {
                                                ImageRequest.Builder(context)
                                                    .data(displayAvatar)
                                                    .crossfade(true)
                                                    .diskCachePolicy(CachePolicy.ENABLED)
                                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                                    .networkCachePolicy(CachePolicy.ENABLED)
                                                    .build()
                                            }
                                            SubcomposeAsyncImage(
                                                model = avatarRequest,
                                                contentDescription = conv.name,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape),
                                                contentScale = ContentScale.Crop,
                                                loading = {
                                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = initialChar,
                                                            fontSize = 18.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF2563EB),
                                                            fontFamily = TajawalFontFamily
                                                        )
                                                    }
                                                },
                                                error = {
                                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = initialChar,
                                                            fontSize = 18.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFF2563EB),
                                                            fontFamily = TajawalFontFamily
                                                        )
                                                    }
                                                }
                                            )
                                        } else {
                                            Text(
                                                text = initialChar,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2563EB),
                                                fontFamily = TajawalFontFamily
                                            )
                                        }
                                    }

                                    // Presence Dot: Green if online, Red if offline
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .align(Alignment.BottomEnd)
                                            .background(if (conv.isOnline) Color(0xFF10B981) else Color(0xFFEF4444), CircleShape)
                                            .border(2.dp, Color.White, CircleShape)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text(
                                        text = conv.name,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A),
                                        fontFamily = TajawalFontFamily
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = conv.lastMsg,
                                        fontSize = 12.sp,
                                        color = Color(0xFF64748B),
                                        fontFamily = TajawalFontFamily,
                                        maxLines = 1
                                    )
                                }
                            }

                            // 2. Left Side (End in RTL): Time & Indicator
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = conv.time,
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8),
                                    fontFamily = TajawalFontFamily
                                )
                            }
                        }

                        // Hairline subtle divider for clean free-form layout
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 82.dp, end = 20.dp),
                            thickness = 0.6.dp,
                            color = Color(0xFFF1F5F9)
                        )
                    }
                }
            }
        }
        }
    }
}
