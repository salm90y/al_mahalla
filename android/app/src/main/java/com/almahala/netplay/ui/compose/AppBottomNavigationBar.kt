package com.almahala.netplay.ui.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun AppBottomNavigationBar(
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: BottomTab.Chat.route

    fun navigateTo(tab: BottomTab) {
        if (currentRoute != tab.route) {
            navController.navigate(tab.route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val isDark = isAppInDarkTheme()
    val barBg = if (isDark) DarkSurface else Color.White
    val barBorder = if (isDark) DarkBorder else Color(0xFFE2E8F0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(barBg)
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Main Navigation Bar Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(barBg)
                .border(
                    width = 1.dp,
                    color = barBorder
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // 1. الدردشات (Chat) - Right side in RTL
                BottomNavItem(
                    tab = BottomTab.Chat,
                    isSelected = currentRoute == BottomTab.Chat.route,
                    onClick = { navigateTo(BottomTab.Chat) }
                )

                // 2. الحالات (Stories) - Next to Chat
                BottomNavItem(
                    tab = BottomTab.Stories,
                    isSelected = currentRoute == BottomTab.Stories.route,
                    onClick = { navigateTo(BottomTab.Stories) }
                )

                // Space for Center FAB (الغرف)
                Spacer(modifier = Modifier.width(64.dp))

                // 4. المكالمات (Calls)
                BottomNavItem(
                    tab = BottomTab.Calls,
                    isSelected = currentRoute == BottomTab.Calls.route,
                    onClick = { navigateTo(BottomTab.Calls) }
                )

                // 5. الأصدقاء (Friends) - Left side in RTL
                BottomNavItem(
                    tab = BottomTab.Friends,
                    isSelected = currentRoute == BottomTab.Friends.route,
                    onClick = { navigateTo(BottomTab.Friends) }
                )
            }
        }

        // Center Floating Action Button (الغرف)
        val isRoomsSelected = currentRoute == BottomTab.Rooms.route
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .offset(y = (-12).dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    navigateTo(BottomTab.Rooms)
                }
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .shadow(if (isRoomsSelected) 12.dp else 8.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = if (isRoomsSelected) {
                                listOf(Color(0xFF3B82F6), Color(0xFF2563EB), Color(0xFF1E40AF))
                            } else {
                                listOf(Color(0xFF3B82F6), Color(0xFF2563EB), Color(0xFF1D4ED8))
                            }
                        )
                    )
                    .border(
                        width = 2.5.dp,
                        color = if (isRoomsSelected) Color(0xFFDBEAFE) else barBg,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Multi-content Rooms Emblem: 3 room tiles + 1 live broadcast node
                Canvas(modifier = Modifier.size(24.dp)) {
                    val w = size.width
                    val gap = w * 0.14f
                    val tile = (w - gap) / 2f
                    val cr = CornerRadius(w * 0.14f, w * 0.14f)

                    // Top-left room tile
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(0f, 0f),
                        size = Size(tile, tile),
                        cornerRadius = cr
                    )
                    // Bottom-left room tile
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.88f),
                        topLeft = Offset(0f, tile + gap),
                        size = Size(tile, tile),
                        cornerRadius = cr
                    )
                    // Bottom-right room tile
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(tile + gap, tile + gap),
                        size = Size(tile, tile),
                        cornerRadius = cr
                    )
                    // Top-right live broadcast / stream node
                    val liveCenter = Offset(tile + gap + tile / 2f, tile / 2f)
                    drawCircle(
                        color = Color.White,
                        radius = tile * 0.48f,
                        center = liveCenter,
                        style = Stroke(width = w * 0.085f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = tile * 0.20f,
                        center = liveCenter
                    )
                }
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = "الغرف",
                color = if (isRoomsSelected) Color(0xFF2563EB) else if (isDark) DarkTextSecondary else Color(0xFF64748B),
                fontSize = 11.sp,
                fontFamily = TajawalFontFamily,
                fontWeight = if (isRoomsSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    tab: BottomTab,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isDark = isAppInDarkTheme()
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF2563EB) else if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
        label = "nav_color"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.title,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = tab.title,
            color = contentColor,
            fontSize = 11.sp,
            fontFamily = TajawalFontFamily,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
