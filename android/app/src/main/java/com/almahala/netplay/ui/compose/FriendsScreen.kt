package com.almahala.netplay.ui.compose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.almahala.netplay.CallActivity
import com.almahala.netplay.UserManager
import com.almahala.netplay.UserProfile
import com.almahala.netplay.model.FriendItem
import com.almahala.netplay.model.FriendRequestItem
import com.almahala.netplay.network.CloudflareClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun FriendsScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var selectedTab by remember { mutableStateOf(0) } // 0: الأصدقاء, 1: طلبات الصداقة
    val tabs = listOf("الأصدقاء", "طلبات الصداقة")
    var friendsSearchQuery by remember { mutableStateOf("") }
    var friendsList by remember { mutableStateOf<List<FriendItem>>(CloudflareClient.getLocalFriends(context)) }
    var requestsList by remember { mutableStateOf<List<FriendRequestItem>>(CloudflareClient.getLocalIncomingRequests(context)) }

    // Thin search state directly below friend requests / tabs
    var accountSearchText by remember { mutableStateOf("") }
    var allUsers by remember { mutableStateOf<List<UserProfile>>(UserManager.getAllUsers(context)) }
    var searchResults by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var sentRequests by remember { mutableStateOf<Set<String>>(emptySet()) }

    val currentUserId = UserManager.getCurrentUser(context)?.id ?: ""
    val currentUsername = UserManager.getCurrentUser(context)?.username ?: ""

    fun loadData() {
        CloudflareClient.getFriendsList(context) { friends ->
            if (friends != null && friends.isNotEmpty()) {
                friendsList = friends
            } else if (friendsList.isEmpty()) {
                friendsList = CloudflareClient.getLocalFriends(context)
            }
        }
        CloudflareClient.getIncomingRequests(context) { requests ->
            if (requests != null) {
                requestsList = requests
            }
        }
    }

    LaunchedEffect(Unit) {
        CloudflareClient.searchUsers(context, "") { success, list ->
            if (success && list != null && list.isNotEmpty()) {
                allUsers = list
            } else {
                allUsers = UserManager.getAllUsers(context)
            }
        }
        while (isActive) {
            loadData()
            delay(3500)
        }
    }

    LaunchedEffect(selectedTab) {
        loadData()
    }

    fun performAccountSearch(query: String) {
        val q = query.trim().removePrefix("@")
        if (q.isBlank()) {
            searchResults = emptyList()
            isSearching = false
            return
        }
        isSearching = true
        val filtered = allUsers.filter { user ->
            (user.id != currentUserId && !user.username.equals(currentUsername, ignoreCase = true)) &&
            (user.username.contains(q, ignoreCase = true) ||
             user.fullName.contains(q, ignoreCase = true) ||
             user.email.contains(q, ignoreCase = true) ||
             user.id.contains(q, ignoreCase = true))
        }
        searchResults = filtered
    }

    LaunchedEffect(accountSearchText) {
        val q = accountSearchText.trim().removePrefix("@")
        if (q.isBlank()) {
            searchResults = emptyList()
            isSearching = false
            return@LaunchedEffect
        }
        isSearching = true
        delay(220)
        CloudflareClient.searchUsers(context, q) { success, remoteList ->
            val currentQueryNow = accountSearchText.trim().removePrefix("@")
            if (currentQueryNow.isBlank()) {
                searchResults = emptyList()
                isSearching = false
                return@searchUsers
            }
            val merged = if (success && remoteList != null) {
                remoteList.filter { user ->
                    user.id != currentUserId && !user.username.equals(currentUsername, ignoreCase = true)
                }
            } else {
                allUsers.filter { user ->
                    (user.id != currentUserId && !user.username.equals(currentUsername, ignoreCase = true)) &&
                    (user.username.contains(currentQueryNow, ignoreCase = true) ||
                     user.fullName.contains(currentQueryNow, ignoreCase = true) ||
                     user.email.contains(currentQueryNow, ignoreCase = true) ||
                     user.id.contains(currentQueryNow, ignoreCase = true))
                }
            }
            searchResults = merged
            isSearching = false
        }
    }

    val filteredFriends = friendsList.filter {
        friendsSearchQuery.isEmpty() || it.name.contains(friendsSearchQuery, ignoreCase = true)
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
            // 1. Unified Top Bar: Title "الأصدقاء"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الأصدقاء",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TajawalFontFamily,
                    color = Color(0xFF0F172A)
                )
            }

            // 2. Category Tabs (الأصدقاء / طلبات الصداقة)
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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tabName,
                                fontSize = 15.sp,
                                fontFamily = TajawalFontFamily,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFF2563EB) else Color(0xFF64748B)
                            )
                            if (index == 1 && requestsList.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .background(Color(0xFFEF4444), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${requestsList.size}",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = TajawalFontFamily
                                    )
                                }
                            }
                        }
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

            // 2.5 My Shareable @username Strip so friends can add this user easily
            if (currentUsername.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFEFF6FF),
                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "معرّفك لإضافة الأصدقاء",
                                fontSize = 11.sp,
                                fontFamily = TajawalFontFamily,
                                color = Color(0xFF64748B)
                            )
                            Text(
                                text = "@$currentUsername",
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1D4ED8),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                clipboard?.setPrimaryClip(ClipData.newPlainText("username", currentUsername))
                                Toast.makeText(context, "تم نسخ @$currentUsername", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "نسخ اليوزر",
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "أضفني على تطبيق المحلة عبر اليوزر: @$currentUsername")
                                }
                                try {
                                    context.startActivity(Intent.createChooser(shareIntent, "مشاركة حسابك"))
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "مشاركة",
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 3. Sleek, thin, elegant search field placed directly below tabs & requests
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
                        value = accountSearchText,
                        onValueChange = {
                            accountSearchText = it
                            performAccountSearch(it)
                            friendsSearchQuery = it
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                performAccountSearch(accountSearchText)
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
                            if (accountSearchText.isEmpty()) {
                                Text(
                                    text = "ابحث عن صديق بالاسم أو @اليوزر أو البريد...",
                                    fontSize = 13.sp,
                                    fontFamily = TajawalFontFamily,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                            innerTextField()
                        }
                    )

                    if (accountSearchText.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                accountSearchText = ""
                                friendsSearchQuery = ""
                                searchResults = emptyList()
                            },
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

            // 4. Dropdown search results list directly below the search field
            AnimatedVisibility(
                visible = accountSearchText.isNotBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF8FAFC))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(16.dp))
                        .padding(8.dp)
                ) {
                    if (isSearching) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF2563EB)
                            )
                        }
                    } else if (searchResults.isNotEmpty()) {
                        Text(
                            text = "نتائج البحث (${searchResults.size}):",
                            fontFamily = TajawalFontFamily,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(searchResults, key = { it.id }) { user ->
                                val isAlreadyFriend = friendsList.any {
                                    it.id.equals(user.id, ignoreCase = true) ||
                                    it.username.equals(user.username, ignoreCase = true)
                                }
                                val isRequestSent = sentRequests.contains(user.id) || sentRequests.contains(user.username)
                                val displayName = user.fullName.ifEmpty { user.username }
                                val encodedName = try {
                                    java.net.URLEncoder.encode(displayName, "UTF-8")
                                } catch (_: Exception) {
                                    displayName
                                }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White)
                                        .border(0.5.dp, Color(0xFFE2E8F0), RoundedCornerShape(10.dp))
                                        .clickable {
                                            navController.navigate("user_profile/${user.id}/$encodedName")
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Mini Avatar
                                    if (user.avatar.isNotBlank()) {
                                        AsyncImage(
                                            model = user.avatar,
                                            contentDescription = displayName,
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(Color(0xFFEEF4FB), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = displayName.take(1).uppercase().ifEmpty { "ص" },
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2563EB),
                                                fontFamily = TajawalFontFamily
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    // Display name and @username
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                navController.navigate("user_profile/${user.id}/$encodedName")
                                            }
                                    ) {
                                        Text(
                                            text = displayName,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0F172A),
                                            fontFamily = TajawalFontFamily,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "@${user.username}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF64748B),
                                            fontFamily = TajawalFontFamily,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    if (isAlreadyFriend) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFECFDF5),
                                            border = BorderStroke(1.dp, Color(0xFFA7F3D0))
                                        ) {
                                            Text(
                                                text = "صديق",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF059669),
                                                fontFamily = TajawalFontFamily,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    } else if (isRequestSent) {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(Color(0xFFDCFCE7), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "تم الإرسال",
                                                tint = Color(0xFF16A34A),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    } else {
                                        IconButton(
                                            onClick = {
                                                sentRequests = sentRequests + user.id + user.username
                                                val targetHandle = user.username.ifBlank { user.id }
                                                CloudflareClient.sendFriendRequest(context, targetHandle) { success, msg ->
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    loadData()
                                                }
                                            },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .background(Color(0xFF2563EB), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PersonAdd,
                                                contentDescription = "إرسال طلب",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // If no user in database matches, allow instant direct request icon
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "إرسال طلب مباشر لـ '$accountSearchText'",
                                fontFamily = TajawalFontFamily,
                                fontSize = 12.sp,
                                color = Color(0xFF0F172A),
                                fontWeight = FontWeight.Medium
                            )

                            IconButton(
                                onClick = {
                                    val target = accountSearchText.trim()
                                    if (target.isNotBlank()) {
                                        CloudflareClient.sendFriendRequest(context, target) { success, msg ->
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            loadData()
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFF2563EB), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PersonAdd,
                                    contentDescription = "إرسال",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 5. Main Content: Friends List or Friend Requests with clean empty states (No "إضافة صديق" button)
            val showEmpty = (selectedTab == 0 && filteredFriends.isEmpty() && accountSearchText.isBlank()) || (selectedTab == 1 && requestsList.isEmpty())

            if (showEmpty) {
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
                        // Clean Circular Icon
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
                                Icon(
                                    imageVector = Icons.Default.People,
                                    contentDescription = "Friends",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = if (selectedTab == 0) "لا يوجد أصدقاء بعد" else "لا توجد طلبات صداقة واردة",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = TajawalFontFamily,
                            color = Color(0xFF0F172A)
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (selectedTab == 0) "يمكنك البحث عن حسابات وإرسال طلبات صداقة من شريط البحث أعلاه." else "ستظهر طلبات الصداقة الجديدة هنا فور وصولها.",
                            fontSize = 13.sp,
                            fontFamily = TajawalFontFamily,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (selectedTab == 0) {
                        items(filteredFriends) { friend ->
                            val encodedName = try { java.net.URLEncoder.encode(friend.name.ifEmpty { friend.username }, "UTF-8") } catch (_: Exception) { friend.name }
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            navController.navigate("chat_detail/${friend.id}/$encodedName")
                                        }
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Right Side (Start in RTL): Avatar + Name & Online Status
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                navController.navigate("user_profile/${friend.id}/$encodedName")
                                            },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFEEF4FB)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (friend.avatarUrl.isNotBlank()) {
                                                    AsyncImage(
                                                        model = friend.avatarUrl,
                                                        contentDescription = friend.name,
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .clip(CircleShape),
                                                        contentScale = ContentScale.Crop
                                                    )
                                                } else {
                                                    Text(
                                                        text = friend.name.take(1).uppercase().ifEmpty { "ص" },
                                                        fontSize = 18.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF2563EB),
                                                        fontFamily = TajawalFontFamily
                                                    )
                                                }
                                            }

                                            // Presence Dot (Green if online, Red if offline)
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .align(Alignment.BottomEnd)
                                                    .background(if (friend.isOnline) Color(0xFF10B981) else Color(0xFFEF4444), CircleShape)
                                                    .border(2.dp, Color.White, CircleShape)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column {
                                            Text(
                                                text = friend.name,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A),
                                                fontFamily = TajawalFontFamily
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (friend.isOnline) "متصل" else "غير متصل",
                                                fontSize = 12.sp,
                                                color = if (friend.isOnline) Color(0xFF10B981) else Color(0xFFEF4444),
                                                fontFamily = TajawalFontFamily
                                            )
                                        }
                                    }

                                    // 2. Left Side (End in RTL): Actions (Call icon button + Message button)
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Small Call Icon Button
                                        IconButton(
                                            onClick = {
                                                try {
                                                    val myUserId = UserManager.getCurrentUser(context)?.username ?: "user_me"
                                                    val callRoomId = "call_" + listOf(myUserId, friend.id.ifBlank { "partner" }).sorted().joinToString("_") + "_" + System.currentTimeMillis()

                                                    if (friend.id.isNotEmpty()) {
                                                        CloudflareClient.sendCloudflareMessage(
                                                            context = context,
                                                            receiverId = friend.id,
                                                            text = "مكالمة صوتية واردة",
                                                            type = "audio_call",
                                                            mediaUrl = callRoomId,
                                                            fileName = friend.name
                                                        ) { _, _ -> }
                                                    }

                                                    val intent = Intent(context, CallActivity::class.java).apply {
                                                        putExtra("callID", callRoomId)
                                                        putExtra("isVideo", false)
                                                        putExtra("isIncoming", false)
                                                        putExtra("targetUserId", friend.id)
                                                        putExtra("targetUserName", friend.name)
                                                        putExtra("targetUserAvatar", friend.avatarUrl)
                                                    }
                                                    context.startActivity(intent)
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "جارٍ الاتصال بـ ${friend.name}...", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            modifier = Modifier
                                                .size(38.dp)
                                                .background(Color(0xFFEEF4FB), CircleShape)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Call,
                                                contentDescription = "اتصال",
                                                tint = Color(0xFF2563EB),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // Message Button
                                        Button(
                                            onClick = {
                                                navController.navigate("chat_detail/${friend.id}/$encodedName")
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEF4FB)),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text("مراسلة", color = Color(0xFF2563EB), fontFamily = TajawalFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 82.dp, end = 20.dp),
                                    thickness = 0.6.dp,
                                    color = Color(0xFFF1F5F9)
                                )
                            }
                        }
                    } else {
                        items(requestsList) { req ->
                            val reqDisplayName = req.fromUsername.ifEmpty { req.username }.ifEmpty { req.name }
                            val encodedReqName = try { java.net.URLEncoder.encode(reqDisplayName, "UTF-8") } catch (_: Exception) { reqDisplayName }
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Right Side (Start in RTL): Avatar + Name & Request Subtitle
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                navController.navigate("user_profile/${req.fromUserId}/$encodedReqName")
                                            },
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .background(Color(0xFFEEF4FB), CircleShape),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = reqDisplayName.take(1).uppercase().ifEmpty { "ص" },
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF2563EB),
                                                fontFamily = TajawalFontFamily
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column {
                                            Text(
                                                text = reqDisplayName,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A),
                                                fontFamily = TajawalFontFamily
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "يريد إضافتك كصديق",
                                                fontSize = 12.sp,
                                                color = Color(0xFF64748B),
                                                fontFamily = TajawalFontFamily
                                            )
                                        }
                                    }

                                    // 2. Left Side (End in RTL): Accept / Reject Buttons
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = {
                                                val friendToAdd = FriendItem(
                                                    id = req.fromUserId.ifEmpty { req.id },
                                                    username = reqDisplayName,
                                                    avatarUrl = req.avatarUrl,
                                                    status = "online",
                                                    createdAt = System.currentTimeMillis()
                                                )
                                                CloudflareClient.addLocalFriend(context, friendToAdd)
                                                friendsList = CloudflareClient.getLocalFriends(context)
                                                CloudflareClient.respondFriendRequest(context, req.id, "accept") { success ->
                                                    Toast.makeText(context, "تم قبول طلب الصداقة وأضيف إلى قائمة أصدقائك", Toast.LENGTH_SHORT).show()
                                                    loadData()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text("قبول", color = Color.White, fontFamily = TajawalFontFamily, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        }

                                        OutlinedButton(
                                            onClick = {
                                                CloudflareClient.respondFriendRequest(context, req.id, "reject") { success ->
                                                    Toast.makeText(context, "تم رفض طلب الصداقة", Toast.LENGTH_SHORT).show()
                                                    loadData()
                                                }
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                                        ) {
                                            Text("رفض", color = Color(0xFF64748B), fontFamily = TajawalFontFamily, fontSize = 13.sp)
                                        }
                                    }
                                }

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
}
