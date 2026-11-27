package com.almahala.netplay.ui.compose

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almahala.netplay.UserProfile
import com.almahala.netplay.network.CloudflareClient

private const val PREFS_NAME = "ps1_auth_prefs"
private const val KEY_REMEMBER_ME = "remember_me"
private const val KEY_SAVED_USERNAME = "saved_username"
private const val KEY_SAVED_PASSWORD = "saved_password"

@Composable
fun LoginScreen(
    onLoginSuccess: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val prefs: SharedPreferences = remember {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var username by remember {
        mutableStateOf(prefs.getString(KEY_SAVED_USERNAME, "") ?: "")
    }
    var password by remember {
        mutableStateOf(prefs.getString(KEY_SAVED_PASSWORD, "") ?: "")
    }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isRememberMeChecked by remember {
        mutableStateOf(prefs.getBoolean(KEY_REMEMBER_ME, true))
    }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val openOAuthUrl: (String) -> Unit = { targetUrl ->
        errorMessage = null
        focusManager.clearFocus()
        try {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(browserIntent)
        } catch (_: Exception) {
            Toast.makeText(context, "تعذر فتح المتصفح", Toast.LENGTH_SHORT).show()
        }
    }

    val performLogin = {
        val trimmedUser = username.trim()
        val trimmedPass = password.trim()

        if (trimmedUser.length < 3) {
            errorMessage = "اسم المستخدم يجب أن يتكون من 3 أحرف على الأقل"
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        } else if (trimmedPass.length < 4) {
            errorMessage = "كلمة المرور يجب أن تتكون من 4 أحرف/أرقام على الأقل"
            Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
        } else {
            errorMessage = null
            isLoading = true
            focusManager.clearFocus()

            if (isRememberMeChecked) {
                prefs.edit()
                    .putString(KEY_SAVED_USERNAME, trimmedUser)
                    .putString(KEY_SAVED_PASSWORD, trimmedPass)
                    .putBoolean(KEY_REMEMBER_ME, true)
                    .apply()
            } else {
                prefs.edit()
                    .remove(KEY_SAVED_USERNAME)
                    .remove(KEY_SAVED_PASSWORD)
                    .putBoolean(KEY_REMEMBER_ME, false)
                    .apply()
            }

            CloudflareClient.login(context, trimmedUser, trimmedPass) { success, errorMsg, user ->
                isLoading = false
                if (success && user != null) {
                    Toast.makeText(context, "مرحباً بك ${user.username}!", Toast.LENGTH_SHORT).show()
                    onLoginSuccess(user)
                } else {
                    val msg = errorMsg ?: "بيانات الدخول غير صحيحة"
                    errorMessage = msg
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF0F6FF))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color(0xFFE2EDFE),
                    radius = 180.dp.toPx(),
                    center = Offset(size.width * 0.9f, size.height * 0.15f)
                )
                drawCircle(
                    color = Color(0xFFEBF3FF),
                    radius = 160.dp.toPx(),
                    center = Offset(size.width * 0.1f, size.height * 0.85f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // 1. Hero Brand / Logo Card
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF3B82F6), Color(0xFF1D4ED8))
                            )
                        )
                        .shadow(8.dp, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = "المحلة",
                        tint = Color.White,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "المَحَلَّة",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = TajawalFontFamily,
                    color = Color(0xFF0F172A),
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(22.dp))

                // 2. Main Login Form Container
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, RoundedCornerShape(28.dp)),
                    shape = RoundedCornerShape(28.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(22.dp)
                    ) {
                        Text(
                            text = "اسم المستخدم",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = TajawalFontFamily,
                            color = Color(0xFF1E293B)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(25.dp))
                                .background(Color(0xFFF1F6FB))
                                .border(1.dp, Color(0xFFE2EAFD), RoundedCornerShape(25.dp))
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(20.dp)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                BasicTextField(
                                    value = username,
                                    onValueChange = {
                                        username = it
                                        errorMessage = null
                                    },
                                    textStyle = TextStyle(
                                        fontSize = 15.sp,
                                        fontFamily = TajawalFontFamily,
                                        color = Color(0xFF0F172A),
                                        fontWeight = FontWeight.Medium
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Text,
                                        imeAction = ImeAction.Next
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                    ),
                                    cursorBrush = SolidColor(Color(0xFF2563EB)),
                                    modifier = Modifier.fillMaxWidth(),
                                    decorationBox = { innerTextField ->
                                        if (username.isEmpty()) {
                                            Text(
                                                text = "أدخل اسم المستخدم",
                                                fontSize = 14.sp,
                                                fontFamily = TajawalFontFamily,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "كلمة المرور",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = TajawalFontFamily,
                            color = Color(0xFF1E293B)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .clip(RoundedCornerShape(25.dp))
                                .background(Color(0xFFF1F6FB))
                                .border(1.dp, Color(0xFFE2EAFD), RoundedCornerShape(25.dp))
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF2563EB),
                                    modifier = Modifier.size(20.dp)
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                BasicTextField(
                                    value = password,
                                    onValueChange = {
                                        password = it
                                        errorMessage = null
                                    },
                                    textStyle = TextStyle(
                                        fontSize = 15.sp,
                                        fontFamily = TajawalFontFamily,
                                        color = Color(0xFF0F172A),
                                        fontWeight = FontWeight.Medium
                                    ),
                                    singleLine = true,
                                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = { performLogin() }
                                    ),
                                    cursorBrush = SolidColor(Color(0xFF2563EB)),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { innerTextField ->
                                        if (password.isEmpty()) {
                                            Text(
                                                text = "أدخل كلمة المرور",
                                                fontSize = 14.sp,
                                                fontFamily = TajawalFontFamily,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                        innerTextField()
                                    }
                                )

                                IconButton(
                                    onClick = { isPasswordVisible = !isPasswordVisible },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "تبديل الرؤية",
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    indication = null,
                                    interactionSource = remember { MutableInteractionSource() }
                                ) {
                                    isRememberMeChecked = !isRememberMeChecked
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isRememberMeChecked) Color(0xFF2563EB) else Color(0xFFE2E8F0)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isRememberMeChecked) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))

                            Text(
                                text = "تذكرني في هذا الجهاز",
                                fontSize = 13.sp,
                                fontFamily = TajawalFontFamily,
                                color = Color(0xFF475569),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        AnimatedVisibility(
                            visible = errorMessage != null,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            errorMessage?.let { msg ->
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = msg,
                                    color = Color(0xFFDC2626),
                                    fontSize = 13.sp,
                                    fontFamily = TajawalFontFamily,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = { performLogin() },
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2563EB),
                                disabledContainerColor = Color(0xFF93C5FD)
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Text(
                                    text = "تسجيل الدخول",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = TajawalFontFamily,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFFE2E8F0)
                            )
                            Text(
                                text = "أو",
                                fontSize = 13.sp,
                                fontFamily = TajawalFontFamily,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFFE2E8F0)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Official Google & Facebook Login Icons Side-by-Side
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Google OAuth Icon Button
                            Surface(
                                onClick = {
                                    if (!isLoading) openOAuthUrl(CloudflareClient.getGoogleOAuthStartUrl(context))
                                },
                                enabled = !isLoading,
                                shape = CircleShape,
                                color = Color.White,
                                border = BorderStroke(1.2.dp, Color(0xFFE2E8F0)),
                                shadowElevation = 3.dp,
                                modifier = Modifier.size(58.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Canvas(modifier = Modifier.size(26.dp)) {
                                        val strokeWidth = size.minDimension * 0.20f
                                        val inset = strokeWidth / 2f
                                        val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                                        val topLeft = Offset(inset, inset)
                                        drawArc(
                                            color = Color(0xFFEA4335),
                                            startAngle = -145f,
                                            sweepAngle = 105f,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcSize,
                                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                        )
                                        drawArc(
                                            color = Color(0xFFFBBC05),
                                            startAngle = 150f,
                                            sweepAngle = 65f,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcSize,
                                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                        )
                                        drawArc(
                                            color = Color(0xFF34A853),
                                            startAngle = 45f,
                                            sweepAngle = 105f,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcSize,
                                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                        )
                                        drawArc(
                                            color = Color(0xFF4285F4),
                                            startAngle = -5f,
                                            sweepAngle = 52f,
                                            useCenter = false,
                                            topLeft = topLeft,
                                            size = arcSize,
                                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                                        )
                                        drawLine(
                                            color = Color(0xFF4285F4),
                                            start = Offset(size.width * 0.50f, size.height * 0.50f),
                                            end = Offset(size.width - inset * 0.6f, size.height * 0.50f),
                                            strokeWidth = strokeWidth,
                                            cap = StrokeCap.Butt
                                        )
                                    }
                                }
                            }

                            // Facebook OAuth Icon Button
                            Surface(
                                onClick = {
                                    if (!isLoading) openOAuthUrl(CloudflareClient.getFacebookOAuthStartUrl(context))
                                },
                                enabled = !isLoading,
                                shape = CircleShape,
                                color = Color(0xFF1877F2),
                                border = BorderStroke(1.2.dp, Color(0xFF166FE5)),
                                shadowElevation = 3.dp,
                                modifier = Modifier.size(58.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "f",
                                        color = Color.White,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}
