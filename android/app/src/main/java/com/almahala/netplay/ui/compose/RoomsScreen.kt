package com.almahala.netplay.ui.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

enum class RoomType {
    QURAN,
    DUAS,
    LATMIYAT,
    MAJALIS,
    FATAWA,
    AFRAH,
    TV_CHANNELS,
    MOVIES_SERIES,
    MEDIA,
    YOUTUBE,
    GAMES
}

data class RoomItemData(
    val type: RoomType,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconColor: Color,
    val iconBgColor: Color,
    val badgeText: String = "نشط"
)

@Composable
fun RoomsScreen(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val isDark = isAppInDarkTheme()
    val cardBg = if (isDark) DarkSurface else Color.White
    val cardBorder = if (isDark) DarkBorder else Color(0xFFE2E8F0)
    val titleColor = if (isDark) DarkTextPrimary else Color(0xFF0F172A)
    val subtitleColor = if (isDark) DarkTextSecondary else Color(0xFF64748B)
    val searchBg = if (isDark) DarkSurface else Color(0xFFEEF4FB)
    val searchBorder = if (isDark) DarkBorder else Color(0xFFE2E8F0)

    var selectedTab by remember { mutableStateOf(0) } // 0: الكل, 1: بث وترفيه, 2: دينية ومجالس
    val tabs = listOf("الكل", "بث وترفيه", "دينية ومجالس")
    var searchQuery by remember { mutableStateOf("") }
    var selectedRoomForExperience by remember { mutableStateOf<RoomType?>(null) }

    val allContentRooms = remember {
        listOf(
            RoomItemData(
                type = RoomType.YOUTUBE,
                title = "غرفة اليوتيوب والسينما",
                subtitle = "مشاهدة جماعية وبحث سحابي 4K",
                icon = Icons.Default.PlayCircleFilled,
                iconColor = Color(0xFFDC2626),
                iconBgColor = Color(0xFFFEF2F2),
                badgeText = "YouTube"
            ),
            RoomItemData(
                type = RoomType.TV_CHANNELS,
                title = "قنوات تلفزيونية",
                subtitle = "بث القنوات الفضائية",
                icon = Icons.Default.Tv,
                iconColor = Color(0xFF0284C7),
                iconBgColor = Color(0xFFF0F9FF),
                badgeText = "بث حي"
            ),
            RoomItemData(
                type = RoomType.MOVIES_SERIES,
                title = "أفلام ومسلسلات",
                subtitle = "مكتبة وثائقية ومرئية",
                icon = Icons.Default.Theaters,
                iconColor = Color(0xFF475569),
                iconBgColor = Color(0xFFF1F5F9),
                badgeText = "HD"
            ),
            RoomItemData(
                type = RoomType.MEDIA,
                title = "ميديا وتغطيات",
                subtitle = "تغطيات وتقارير مصورة",
                icon = Icons.Default.SmartDisplay,
                iconColor = Color(0xFF0D9488),
                iconBgColor = Color(0xFFF0FDFA),
                badgeText = "فيديو"
            ),
            RoomItemData(
                type = RoomType.GAMES,
                title = "ألعاب وبلايستيشن",
                subtitle = "ألعاب جماعية وبطولات",
                icon = Icons.Default.SportsEsports,
                iconColor = Color(0xFF4F46E5),
                iconBgColor = Color(0xFFEEF2FF),
                badgeText = "PlayStation"
            ),
            RoomItemData(
                type = RoomType.QURAN,
                title = "القرآن الكريم",
                subtitle = "تلاوات واستماع مباشر",
                icon = Icons.Default.MenuBook,
                iconColor = Color(0xFF059669),
                iconBgColor = Color(0xFFECFDF5),
                badgeText = "مباشر"
            ),
            RoomItemData(
                type = RoomType.DUAS,
                title = "أدعية وزيارات",
                subtitle = "مفاتيح الجنان والأذكار",
                icon = Icons.Default.VolunteerActivism,
                iconColor = Color(0xFF2563EB),
                iconBgColor = Color(0xFFEFF6FF),
                badgeText = "متاح"
            ),
            RoomItemData(
                type = RoomType.MAJALIS,
                title = "مجالس حسينية",
                subtitle = "بثوث ومحاضرات مباشرة",
                icon = Icons.Default.Groups,
                iconColor = Color(0xFFD97706),
                iconBgColor = Color(0xFFFFFBEB),
                badgeText = "بث صوتي"
            ),
            RoomItemData(
                type = RoomType.LATMIYAT,
                title = "لطميات ومراثي",
                subtitle = "إصدارات ومقاطع صوتية",
                icon = Icons.Default.MusicNote,
                iconColor = Color(0xFFDC2626),
                iconBgColor = Color(0xFFFEF2F2),
                badgeText = "جديد"
            ),
            RoomItemData(
                type = RoomType.FATAWA,
                title = "فتاوى وأحكام",
                subtitle = "إجابات الأسئلة الشرعية",
                icon = Icons.Default.HelpOutline,
                iconColor = Color(0xFF7C3AED),
                iconBgColor = Color(0xFFF5F3FF),
                badgeText = "إرشادات"
            ),
            RoomItemData(
                type = RoomType.AFRAH,
                title = "أفراح ومناسبات",
                subtitle = "مواليد وأناشيد بهيجة",
                icon = Icons.Default.AutoAwesome,
                iconColor = Color(0xFFDB2777),
                iconBgColor = Color(0xFFFDF2F8),
                badgeText = "مناسبات"
            )
        )
    }

    val entertainmentTypes = remember {
        setOf(RoomType.YOUTUBE, RoomType.TV_CHANNELS, RoomType.MOVIES_SERIES, RoomType.MEDIA, RoomType.GAMES)
    }
    val religiousTypes = remember {
        setOf(RoomType.QURAN, RoomType.DUAS, RoomType.MAJALIS, RoomType.LATMIYAT, RoomType.FATAWA, RoomType.AFRAH)
    }

    fun openRoom(room: RoomItemData) {
        when (room.type) {
            RoomType.GAMES -> navController.navigate("games_room")
            RoomType.QURAN -> navController.navigate("quran_home")
            RoomType.YOUTUBE -> navController.navigate("youtube_lobby")
            RoomType.MOVIES_SERIES -> navController.navigate("movies_lobby")
            RoomType.TV_CHANNELS -> navController.navigate("tv_lobby")
            else -> selectedRoomForExperience = room.type
        }
    }

    // Informative Dialog for non-dedicated rooms
    if (selectedRoomForExperience != null) {
        val currentRoom = allContentRooms.find { it.type == selectedRoomForExperience }
        AlertDialog(
            onDismissRequest = { selectedRoomForExperience = null },
            confirmButton = {
                Button(
                    onClick = { selectedRoomForExperience = null },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("دخول الغرفة", fontFamily = TajawalFontFamily, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedRoomForExperience = null }) {
                    Text("رجوع", fontFamily = TajawalFontFamily, color = subtitleColor)
                }
            },
            title = {
                Text(
                    text = currentRoom?.title ?: "غرفة تفاعلية",
                    fontFamily = TajawalFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
            },
            text = {
                Text(
                    text = "مرحباً بك في غرفة ${currentRoom?.title}. الغرفة جاهزة ومجهزة بالبث والتواصل الصوتي والمرئي التفاعلي.",
                    fontFamily = TajawalFontFamily,
                    color = subtitleColor
                )
            },
            containerColor = cardBg,
            shape = RoundedCornerShape(20.dp)
        )
    }

    val trimmedQuery = searchQuery.trim()
    val filteredRooms = remember(trimmedQuery, selectedTab, allContentRooms) {
        allContentRooms.filter { room ->
            val matchesTab = when (selectedTab) {
                1 -> room.type in entertainmentTypes
                2 -> room.type in religiousTypes
                else -> true
            }
            val matchesSearch = trimmedQuery.isEmpty() ||
                room.title.contains(trimmedQuery, ignoreCase = true) ||
                room.subtitle.contains(trimmedQuery, ignoreCase = true) ||
                room.badgeText.contains(trimmedQuery, ignoreCase = true)
            matchesTab && matchesSearch
        }
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
            // 1. Unified Top Bar (Matches FriendsScreen & ChatListScreen)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "الغرف",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TajawalFontFamily,
                    color = titleColor
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) PrimaryBlue.copy(alpha = 0.16f) else Color(0xFFEEF4FB),
                    border = BorderStroke(1.dp, if (isDark) PrimaryBlue.copy(alpha = 0.35f) else Color(0xFFDBEAFE))
                ) {
                    Text(
                        text = "${allContentRooms.size} غرف نشطة",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TajawalFontFamily,
                        color = if (isDark) DarkPrimaryBlue else PrimaryBlue,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // 2. Unified Search Bar (Exact dimensions & style of FriendsScreen / ChatListScreen)
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
                        .background(searchBg)
                        .border(1.dp, searchBorder, RoundedCornerShape(21.dp))
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
                            onSearch = { focusManager.clearFocus() }
                        ),
                        singleLine = true,
                        textStyle = TextStyle(
                            fontSize = 13.5.sp,
                            fontFamily = TajawalFontFamily,
                            color = titleColor
                        ),
                        cursorBrush = SolidColor(PrimaryBlue),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "ابحث عن غرفة أو بث مباشر...",
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

            // 3. Unified Category Tabs (الكل / بث وترفيه / دينية ومجالس)
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
                            color = if (isSelected) PrimaryBlue else subtitleColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .height(2.5.dp)
                                .width(36.dp)
                                .background(if (isSelected) PrimaryBlue else Color.Transparent, CircleShape)
                        )
                    }
                }
            }

            // 4. Organized Rooms Content
            if (filteredRooms.isEmpty()) {
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
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .background(if (isDark) PrimaryBlue.copy(alpha = 0.14f) else Color(0xFFEBF3FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .shadow(4.dp, RoundedCornerShape(18.dp))
                                    .background(PrimaryBlue, RoundedCornerShape(18.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridView,
                                    contentDescription = "الغرف",
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "لا توجد غرف مطابقة",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = TajawalFontFamily,
                            color = titleColor
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "جرّب البحث باسم آخر أو تصفح جميع الأقسام.",
                            fontSize = 13.sp,
                            fontFamily = TajawalFontFamily,
                            color = subtitleColor,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val entertainmentRooms = remember(allContentRooms) {
                    allContentRooms.filter { it.type in entertainmentTypes }
                }
                val religiousRooms = remember(allContentRooms) {
                    allContentRooms.filter { it.type in religiousTypes }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 92.dp)
                ) {
                    if (trimmedQuery.isEmpty() && selectedTab == 0) {
                        // Section 1: Interactive & Live Broadcast Rooms
                        item(span = { GridItemSpan(2) }) {
                            RoomSectionHeader(
                                title = "الغرف التفاعلية والبث المباشر",
                                titleColor = titleColor
                            )
                        }

                        val featuredRoom = entertainmentRooms.firstOrNull()
                        val remainingEntertainment = entertainmentRooms.drop(1)

                        if (featuredRoom != null) {
                            item(span = { GridItemSpan(2) }, key = "featured_${featuredRoom.title}") {
                                FeaturedRoomCard(
                                    room = featuredRoom,
                                    onClick = { openRoom(featuredRoom) }
                                )
                            }
                        }

                        items(remainingEntertainment, key = { it.title }) { room ->
                            RoomGridCard(
                                room = room,
                                isDark = isDark,
                                cardBg = cardBg,
                                cardBorder = cardBorder,
                                titleColor = titleColor,
                                subtitleColor = subtitleColor,
                                onClick = { openRoom(room) }
                            )
                        }

                        // Section 2: Quran, Majalis & Islamic Library
                        item(span = { GridItemSpan(2) }) {
                            RoomSectionHeader(
                                title = "القرآن والمجالس والمحتوى الديني",
                                titleColor = titleColor,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }

                        items(religiousRooms, key = { it.title }) { room ->
                            RoomGridCard(
                                room = room,
                                isDark = isDark,
                                cardBg = cardBg,
                                cardBorder = cardBorder,
                                titleColor = titleColor,
                                subtitleColor = subtitleColor,
                                onClick = { openRoom(room) }
                            )
                        }
                    } else if (trimmedQuery.isEmpty() && selectedTab == 1) {
                        val featuredRoom = filteredRooms.firstOrNull()
                        val rest = filteredRooms.drop(1)
                        if (featuredRoom != null) {
                            item(span = { GridItemSpan(2) }, key = "featured_tab1_${featuredRoom.title}") {
                                FeaturedRoomCard(
                                    room = featuredRoom,
                                    onClick = { openRoom(featuredRoom) }
                                )
                            }
                        }
                        items(rest, key = { it.title }) { room ->
                            RoomGridCard(
                                room = room,
                                isDark = isDark,
                                cardBg = cardBg,
                                cardBorder = cardBorder,
                                titleColor = titleColor,
                                subtitleColor = subtitleColor,
                                onClick = { openRoom(room) }
                            )
                        }
                    } else {
                        items(filteredRooms, key = { it.title }) { room ->
                            RoomGridCard(
                                room = room,
                                isDark = isDark,
                                cardBg = cardBg,
                                cardBorder = cardBorder,
                                titleColor = titleColor,
                                subtitleColor = subtitleColor,
                                onClick = { openRoom(room) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomSectionHeader(
    title: String,
    titleColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(16.dp)
                .clip(CircleShape)
                .background(PrimaryBlue)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = TajawalFontFamily,
            color = titleColor
        )
    }
}

@Composable
private fun FeaturedRoomCard(
    room: RoomItemData,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(94.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xFF2563EB), Color(0xFF1D4ED8))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = room.icon,
                        contentDescription = room.title,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = room.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TajawalFontFamily,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = room.subtitle,
                        fontSize = 12.sp,
                        fontFamily = TajawalFontFamily,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = room.badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TajawalFontFamily,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomGridCard(
    room: RoomItemData,
    isDark: Boolean,
    cardBg: Color,
    cardBorder: Color,
    titleColor: Color,
    subtitleColor: Color,
    onClick: () -> Unit
) {
    val resolvedIconBg = if (isDark) room.iconColor.copy(alpha = 0.18f) else room.iconBgColor
    val resolvedBadgeTint = if (isDark) room.iconColor.copy(alpha = 0.95f) else room.iconColor

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(138.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 1.5.dp),
        border = BorderStroke(1.dp, cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Icon Box on Start (Right in RTL) + Badge on End (Left in RTL)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(resolvedIconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = room.icon,
                        contentDescription = room.title,
                        tint = resolvedBadgeTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(resolvedIconBg)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = room.badgeText,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = TajawalFontFamily,
                        color = resolvedBadgeTint,
                        maxLines = 1
                    )
                }
            }

            // Bottom Text Block: Title + Subtitle
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = room.title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TajawalFontFamily,
                    color = titleColor,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = room.subtitle,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    fontFamily = TajawalFontFamily,
                    color = subtitleColor,
                    textAlign = TextAlign.Start,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
