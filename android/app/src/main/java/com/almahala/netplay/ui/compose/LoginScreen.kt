package com.almahala.netplay.ui.compose

import android.accounts.AccountManager
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.SharedPreferences
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.almahala.netplay.UserProfile
import com.almahala.netplay.network.AppInfoHelper
import com.almahala.netplay.network.CloudflareClient
import com.almahala.netplay.network.WhatsAppOtpManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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

    val appName = remember { AppInfoHelper.getAppName(context) }
    val appVersion = remember { AppInfoHelper.getVersionName(context) }

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

    // Google Account Picker & Warnings Dialog State
    var showGoogleEmailDialog by remember { mutableStateOf(false) }
    var deviceGoogleEmails by remember { mutableStateOf<List<String>>(emptyList()) }
    var manualGoogleEmail by remember { mutableStateOf("") }
    var manualGoogleName by remember { mutableStateOf("") }
    var googleWarningsAccepted by remember { mutableStateOf(false) }
    var googleDialogError by remember { mutableStateOf<String?>(null) }

    // WhatsApp 3-Minute Server OTP Dialog State
    var showWhatsAppDialog by remember { mutableStateOf(false) }
    var waStep by remember { mutableStateOf(1) } // 1 = enter phone, 2 = verify OTP
    var waCountryCode by remember { mutableStateOf("+964") }
    var waPhoneInput by remember { mutableStateOf("") }
    var waDisplayName by remember { mutableStateOf("") }
    var waSentPhoneDisplay by remember { mutableStateOf("") }
    var waSenderHint by remember { mutableStateOf("") }
    var waOtpInput by remember { mutableStateOf("") }
    var waDialogError by remember { mutableStateOf<String?>(null) }
    var waRemainingSeconds by remember { mutableStateOf(0) }
    var waOtpNonce by remember { mutableStateOf(0) }
    var waBusy by remember { mutableStateOf(false) }

    val handleGoogleSignInEmail: (String, String?) -> Unit = { email, displayName ->
        errorMessage = null
        isLoading = true
        focusManager.clearFocus()
        CloudflareClient.loginOrRegisterWithGoogle(context, email, displayName) { success, errMsg, user ->
            isLoading = false
            if (success && user != null) {
                Toast.makeText(
                    context,
                    "مرحباً بك ${user.fullName.ifEmpty { user.username }} • يوزرك: @${user.username}",
                    Toast.LENGTH_LONG
                ).show()
                onLoginSuccess(user)
            } else {
                val msg = errMsg ?: "تعذر تسجيل الدخول بحساب جوجل"
                errorMessage = msg
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    val googleAccountLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val selectedEmail = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
            if (!selectedEmail.isNullOrBlank()) {
                manualGoogleEmail = selectedEmail.trim()
                if (manualGoogleName.isBlank()) {
                    manualGoogleName = manualGoogleEmail.substringBefore("@").replace(Regex("[._-]+"), " ").trim()
                }
                if (manualGoogleEmail !in deviceGoogleEmails) {
                    deviceGoogleEmails = listOf(manualGoogleEmail) + deviceGoogleEmails
                }
                googleDialogError = null
            }
        }
        showGoogleEmailDialog = true
    }

    val openGooglePickerWithWarnings = {
        errorMessage = null
        val foundAccounts = try {
            AccountManager.get(context).getAccountsByType("com.google")
                .mapNotNull { it.name?.trim() }
                .filter { it.contains("@") }
                .distinct()
        } catch (_: Exception) {
            emptyList()
        }
        deviceGoogleEmails = foundAccounts
        if (manualGoogleEmail.isBlank() && foundAccounts.isNotEmpty()) {
            manualGoogleEmail = foundAccounts.first()
            if (manualGoogleName.isBlank()) {
                manualGoogleName = manualGoogleEmail.substringBefore("@").replace(Regex("[._-]+"), " ").trim()
            }
        }
        googleWarningsAccepted = true
        googleDialogError = null
        showGoogleEmailDialog = true
    }

    val launchDeviceGoogleChooser = {
        try {
            @Suppress("DEPRECATION")
            val intent = AccountManager.newChooseAccountIntent(
                null,
                null,
                arrayOf("com.google"),
                false,
                null,
                null,
                null,
                null
            )
            googleAccountLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            googleDialogError = "يرجى كتابة بريد Gmail يدوياً في الحقل أدناه."
        } catch (_: Exception) {
            googleDialogError = "يرجى كتابة بريد Gmail يدوياً في الحقل أدناه."
        }
    }

    // Live 3-minute countdown ticker while WhatsApp verification step is active
    LaunchedEffect(showWhatsAppDialog, waStep, waOtpNonce) {
        if (showWhatsAppDialog && waStep == 2) {
            while (isActive) {
                val rem = WhatsAppOtpManager.getRemainingSeconds(context)
                waRemainingSeconds = rem
                if (rem <= 0) break
                delay(500L)
            }
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
            // Background subtle ambient glow circles
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

                // Brand Title
                Text(
                    text = "المَحَلَّة",
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = TajawalFontFamily,
                    color = Color(0xFF0F172A),
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "تسجيل الدخول أو إنشاء حساب سريع",
                    fontSize = 14.sp,
                    fontFamily = TajawalFontFamily,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
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
                        // Username Label
                        Text(
                            text = "اسم المستخدم",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = TajawalFontFamily,
                            color = Color(0xFF1E293B)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Username Input Field
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

                        // Password Label
                        Text(
                            text = "كلمة المرور",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = TajawalFontFamily,
                            color = Color(0xFF1E293B)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Password Input Field
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

                        // Remember Me Checkbox Row
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

                        // Error message if any
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

                        // Submit Button
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

                        // Divider for Quick Google / Social Sign-In
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFFE2E8F0)
                            )
                            Text(
                                text = "أو التسجيل السريع والربط المباشر",
                                fontSize = 12.sp,
                                fontFamily = TajawalFontFamily,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            )
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFFE2E8F0)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Prominent Google Sign-In Card + Sleek WhatsApp Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Primary Google Sign-In Button
                            Surface(
                                onClick = { if (!isLoading) openGooglePickerWithWarnings() },
                                enabled = !isLoading,
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.2.dp, Color(0xFFD5E3F8)),
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                            .border(1.dp, Color(0xFFE2E8F0), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Canvas(modifier = Modifier.size(20.dp)) {
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

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "المتابعة بحساب Google",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = TajawalFontFamily,
                                            color = Color(0xFF0F172A),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "ربط مباشر • إنشاء يوزر تلقائي",
                                            fontSize = 11.sp,
                                            fontFamily = TajawalFontFamily,
                                            color = Color(0xFF2563EB),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Default.ArrowBackIosNew,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            // Crisp WhatsApp Icon Badge
                            Surface(
                                onClick = {
                                    if (!isLoading) {
                                        val activeRem = WhatsAppOtpManager.getRemainingSeconds(context)
                                        val pendingPhone = WhatsAppOtpManager.getPendingPhone(context)
                                        if (activeRem > 0 && pendingPhone.isNotEmpty()) {
                                            waSentPhoneDisplay = pendingPhone
                                            waRemainingSeconds = activeRem
                                            waStep = 2
                                            waOtpNonce++
                                        } else {
                                            waStep = 1
                                            waOtpInput = ""
                                            waSenderHint = ""
                                        }
                                        waDialogError = null
                                        waBusy = false
                                        showWhatsAppDialog = true
                                    }
                                },
                                enabled = !isLoading,
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0xFF25D366),
                                border = BorderStroke(1.2.dp, Color(0xFF16A34A)),
                                shadowElevation = 3.dp,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Canvas(modifier = Modifier.size(28.dp)) {
                                        val w = size.width
                                        val h = size.height
                                        val r = w * 0.36f
                                        val cx = w * 0.52f
                                        val cy = h * 0.48f
                                        // Outer WhatsApp speech-bubble ring
                                        drawCircle(
                                            color = Color.White,
                                            radius = r,
                                            center = Offset(cx, cy),
                                            style = Stroke(width = w * 0.11f)
                                        )
                                        // Bubble tail (bottom-left)
                                        val tail = Path().apply {
                                            moveTo(cx - r * 0.65f, cy + r * 0.65f)
                                            lineTo(w * 0.14f, h * 0.86f)
                                            lineTo(cx - r * 0.18f, cy + r * 0.90f)
                                            close()
                                        }
                                        drawPath(path = tail, color = Color.White)
                                        // Handset inside bubble
                                        val phonePath = Path().apply {
                                            moveTo(cx - r * 0.36f, cy - r * 0.36f)
                                            quadraticBezierTo(
                                                cx - r * 0.48f, cy + r * 0.08f,
                                                cx + r * 0.05f, cy + r * 0.38f
                                            )
                                            quadraticBezierTo(
                                                cx + r * 0.34f, cy + r * 0.42f,
                                                cx + r * 0.40f, cy + r * 0.22f
                                            )
                                        }
                                        drawPath(
                                            path = phonePath,
                                            color = Color.White,
                                            style = Stroke(
                                                width = w * 0.105f,
                                                cap = StrokeCap.Round,
                                                join = StrokeJoin.Round
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Bottom Network / Server Status Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFF10B981), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "سيرفر NetPlay متصل • مشفّر وآمن",
                        fontSize = 12.sp,
                        fontFamily = TajawalFontFamily,
                        color = Color(0xFF64748B)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // App Name & Version Info Badge
                Text(
                    text = "تطبيق $appName • الإصدار v$appVersion",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = TajawalFontFamily,
                    color = Color(0xFF475569)
                )

                Spacer(modifier = Modifier.height(14.dp))
            }
        }

        // Sleek Google Account Picker & Instant App-Link Dialog
        if (showGoogleEmailDialog) {
            val previewHandle = remember(manualGoogleEmail) {
                CloudflareClient.previewGoogleUsername(manualGoogleEmail)
            }
            AlertDialog(
                onDismissRequest = { if (!isLoading) showGoogleEmailDialog = false },
                containerColor = Color.White,
                shape = RoundedCornerShape(26.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEFF6FF))
                                .border(1.dp, Color(0xFFBFDBFE), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(20.dp)) {
                                val strokeWidth = size.minDimension * 0.20f
                                val inset = strokeWidth / 2f
                                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                                val topLeft = Offset(inset, inset)
                                drawArc(Color(0xFFEA4335), -145f, 105f, false, topLeft, arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Butt))
                                drawArc(Color(0xFFFBBC05), 150f, 65f, false, topLeft, arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Butt))
                                drawArc(Color(0xFF34A853), 45f, 105f, false, topLeft, arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Butt))
                                drawArc(Color(0xFF4285F4), -5f, 52f, false, topLeft, arcSize, style = Stroke(strokeWidth, cap = StrokeCap.Butt))
                                drawLine(Color(0xFF4285F4), Offset(size.width * 0.50f, size.height * 0.50f), Offset(size.width - inset * 0.6f, size.height * 0.50f), strokeWidth, StrokeCap.Butt)
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "التسجيل بحساب Google",
                                fontFamily = TajawalFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "ربط مباشر بالتطبيق وإنشاء يوزر تلقائي",
                                fontFamily = TajawalFontFamily,
                                fontSize = 11.5.sp,
                                color = Color(0xFF2563EB)
                            )
                        }
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Device Google Emails Selector
                        if (deviceGoogleEmails.isNotEmpty()) {
                            Text(
                                text = "اختر حساب Google المسجل في جهازك:",
                                fontFamily = TajawalFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = Color(0xFF1E293B)
                            )
                            deviceGoogleEmails.forEach { acctEmail ->
                                val selected = manualGoogleEmail.trim().equals(acctEmail, ignoreCase = true)
                                val acctPreview = CloudflareClient.previewGoogleUsername(acctEmail)
                                Surface(
                                    onClick = {
                                        manualGoogleEmail = acctEmail
                                        if (manualGoogleName.isBlank()) {
                                            manualGoogleName = acctEmail.substringBefore("@").replace(Regex("[._-]+"), " ").trim()
                                        }
                                        googleDialogError = null
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (selected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                                    border = BorderStroke(
                                        1.3.dp,
                                        if (selected) Color(0xFF2563EB) else Color(0xFFE2E8F0)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(if (selected) Color(0xFF2563EB) else Color(0xFFE2E8F0)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = acctEmail.take(1).uppercase(),
                                                color = if (selected) Color.White else Color(0xFF334155),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = acctEmail,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F172A),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (acctPreview.isNotEmpty()) {
                                                Text(
                                                    text = "اليوزر التلقائي: @$acctPreview",
                                                    fontSize = 11.sp,
                                                    fontFamily = TajawalFontFamily,
                                                    color = Color(0xFF2563EB)
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (selected) Color(0xFF2563EB) else Color(0xFF94A3B8),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { launchDeviceGoogleChooser() },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFF8FAFF)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = null,
                                tint = Color(0xFF2563EB),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "اختيار حساب Google من الجهاز",
                                fontFamily = TajawalFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E3A8A)
                            )
                        }

                        OutlinedTextField(
                            value = manualGoogleEmail,
                            onValueChange = {
                                manualGoogleEmail = it
                                googleDialogError = null
                            },
                            label = { Text("بريد Google (Gmail)", fontFamily = TajawalFontFamily) },
                            placeholder = { Text("example@gmail.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = manualGoogleName,
                            onValueChange = { manualGoogleName = it },
                            label = { Text("الاسم الظاهر للأصدقاء (اختياري)", fontFamily = TajawalFontFamily) },
                            placeholder = { Text("مثال: أحمد الحلفي", fontFamily = TajawalFontFamily) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (previewHandle.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFFEFF6FF),
                                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.VerifiedUser,
                                            contentDescription = null,
                                            tint = Color(0xFF2563EB),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "يوزرك التلقائي: @$previewHandle",
                                            fontFamily = TajawalFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color(0xFF1D4ED8)
                                        )
                                    }
                                    Text(
                                        text = "سيتم ربط بريدك بالتطبيق مباشرة ويمكن لجميع أصدقائك البحث عنك وإضافتك عبر @$previewHandle أو اسمك.",
                                        fontFamily = TajawalFontFamily,
                                        fontSize = 11.5.sp,
                                        lineHeight = 16.sp,
                                        color = Color(0xFF1E40AF)
                                    )
                                }
                            }
                        }

                        if (googleDialogError != null) {
                            Text(
                                text = googleDialogError!!,
                                color = Color(0xFFDC2626),
                                fontSize = 12.sp,
                                fontFamily = TajawalFontFamily
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val cleanEmail = manualGoogleEmail.trim()
                            if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
                                googleDialogError = "يرجى اختيار أو إدخال بريد Google صحيح"
                            } else {
                                showGoogleEmailDialog = false
                                handleGoogleSignInEmail(cleanEmail, manualGoogleName.trim().ifEmpty { null })
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("متابعة والدخول بحساب Google", fontFamily = TajawalFontFamily, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGoogleEmailDialog = false }) {
                        Text("إلغاء", fontFamily = TajawalFontFamily, color = Color(0xFF64748B))
                    }
                }
            )
        }

        // WhatsApp 2-Step Verification Dialog (Real Server Delivery + Strict 3-Minute Expiry)
        if (showWhatsAppDialog) {
            val dispatchWhatsAppCode: (String) -> Unit = { targetPhone ->
                waBusy = true
                waDialogError = null
                CloudflareClient.requestWhatsAppOtp(
                    context = context,
                    rawPhone = targetPhone,
                    displayName = waDisplayName
                ) { ok, cleanPhone, expiresInSec, senderHint, err ->
                    waBusy = false
                    if (ok) {
                        waSentPhoneDisplay = cleanPhone
                        waSenderHint = senderHint
                        waRemainingSeconds = expiresInSec
                        waOtpInput = ""
                        waDialogError = null
                        waStep = 2
                        waOtpNonce++
                        Toast.makeText(
                            context,
                            "تم إرسال رمز التحقق إلى واتسابك (صالح 3 دقائق)",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        waDialogError = err ?: "تعذر إرسال رمز واتساب، تحقق من الرقم والاتصال."
                    }
                }
            }

            AlertDialog(
                onDismissRequest = {
                    if (!isLoading && !waBusy) showWhatsAppDialog = false
                },
                containerColor = Color.White,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDCFCE7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MarkChatRead,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (waStep == 1) "التسجيل عبر واتساب" else "أدخل رمز التحقق",
                            fontFamily = TajawalFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color(0xFF0F172A)
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (waStep == 1) {
                            Text(
                                text = "أدخل رقم هاتفك في واتساب؛ سيصلك كود مكوّن من 6 أرقام مباشرةً في تطبيق واتساب من رقم التطبيق الرسمي (صلاحيته 3 دقائق).",
                                fontFamily = TajawalFontFamily,
                                fontSize = 13.sp,
                                color = Color(0xFF475569)
                            )

                            // Quick Country Code Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("+964", "+966", "+20", "+971", "+962", "").forEach { code ->
                                    val selected = waCountryCode == code
                                    val label = if (code.isEmpty()) "مخصص" else code
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = if (selected) Color(0xFF16A34A) else Color(0xFFF1F5F9),
                                        border = BorderStroke(
                                            1.dp,
                                            if (selected) Color(0xFF16A34A) else Color(0xFFE2E8F0)
                                        ),
                                        modifier = Modifier.clickable {
                                            if (!waBusy) {
                                                waCountryCode = code
                                                waDialogError = null
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (selected) Color.White else Color(0xFF334155),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = waPhoneInput,
                                onValueChange = {
                                    waPhoneInput = it
                                    waDialogError = null
                                },
                                enabled = !waBusy,
                                label = {
                                    Text(
                                        if (waCountryCode.isNotEmpty()) "رقم الهاتف ($waCountryCode)" else "الرقم مع رمز الدولة",
                                        fontFamily = TajawalFontFamily
                                    )
                                },
                                placeholder = {
                                    Text(if (waCountryCode.isNotEmpty()) "7701234567" else "+9647701234567")
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = waDisplayName,
                                onValueChange = { waDisplayName = it },
                                enabled = !waBusy,
                                label = { Text("الاسم الكامل (اختياري)", fontFamily = TajawalFontFamily) },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            // Step 2: 6-digit OTP verification with live 3-minute timer
                            val mins = waRemainingSeconds / 60
                            val secs = waRemainingSeconds % 60
                            val formattedTimer = "%02d:%02d".format(mins, secs)
                            val isExpired = waRemainingSeconds <= 0
                            val timerBg = when {
                                isExpired -> Color(0xFFFEE2E2)
                                waRemainingSeconds <= 30 -> Color(0xFFFEF3C7)
                                else -> Color(0xFFDCFCE7)
                            }
                            val timerFg = when {
                                isExpired -> Color(0xFFDC2626)
                                waRemainingSeconds <= 30 -> Color(0xFFD97706)
                                else -> Color(0xFF15803D)
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF0FDF4),
                                border = BorderStroke(1.dp, Color(0xFFBBF7D0))
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = "تم إرسال رسالة إلى تطبيق واتساب على الرقم: $waSentPhoneDisplay",
                                        fontFamily = TajawalFontFamily,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF166534)
                                    )
                                    val senderLine = if (waSenderHint.isNotBlank()) {
                                        "افتح تطبيق واتساب لديك وستجد رسالة الكود مرسلة من رقم التطبيق ($waSenderHint)."
                                    } else {
                                        "افتح تطبيق واتساب لديك الآن لقراءة الكود المرسل من رقم التطبيق الرسمي."
                                    }
                                    Text(
                                        text = senderLine,
                                        fontFamily = TajawalFontFamily,
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF15803D)
                                    )
                                }
                            }

                            // Countdown Timer Banner
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(timerBg)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isExpired) Icons.Default.TimerOff else Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = timerFg,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isExpired) "انتهت صلاحية الرمز (3 دقائق)" else "الوقت المتبقي للصلاحية",
                                        fontFamily = TajawalFontFamily,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = timerFg
                                    )
                                }
                                Text(
                                    text = formattedTimer,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = timerFg
                                )
                            }

                            OutlinedTextField(
                                value = waOtpInput,
                                onValueChange = { input ->
                                    val digitsOnly = WhatsAppOtpManager.normalizeAsciiDigits(input)
                                        .filter { it.isDigit() }
                                        .take(6)
                                    waOtpInput = digitsOnly
                                    waDialogError = null
                                },
                                enabled = !isExpired && !waBusy,
                                label = { Text("كود التحقق (6 أرقام)", fontFamily = TajawalFontFamily) },
                                placeholder = {
                                    Text(
                                        "000000",
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = TextAlign.Center
                                    )
                                },
                                textStyle = TextStyle(
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 6.sp,
                                    textAlign = TextAlign.Center,
                                    color = Color(0xFF0F172A)
                                ),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            TextButton(
                                enabled = !waBusy,
                                onClick = { dispatchWhatsAppCode(waSentPhoneDisplay) },
                                modifier = Modifier.align(Alignment.Start)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color(0xFF16A34A),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "إعادة إرسال الكود عبر واتساب",
                                    fontFamily = TajawalFontFamily,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF16A34A)
                                )
                            }
                        }

                        if (waDialogError != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFEF2F2),
                                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = waDialogError!!,
                                    color = Color(0xFFDC2626),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    fontFamily = TajawalFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (waStep == 1) {
                        Button(
                            enabled = !waBusy,
                            onClick = {
                                val rawLocal = WhatsAppOtpManager.normalizeAsciiDigits(waPhoneInput)
                                    .trim()
                                    .replace(Regex("[^0-9+]"), "")
                                val codeDigits = waCountryCode.replace("+", "")
                                val combined = when {
                                    rawLocal.startsWith("+") || rawLocal.startsWith("00") -> rawLocal
                                    codeDigits.isNotEmpty() && rawLocal.startsWith(codeDigits) && rawLocal.length >= codeDigits.length + 8 ->
                                        "+$rawLocal"
                                    waCountryCode.isNotEmpty() ->
                                        "${waCountryCode}${rawLocal.trimStart('0')}"
                                    else -> rawLocal
                                }
                                dispatchWhatsAppCode(combined)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (waBusy) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = if (waBusy) "جاري الإرسال..." else "إرسال الكود",
                                fontFamily = TajawalFontFamily,
                                color = Color.White
                            )
                        }
                    } else {
                        val isExpired = waRemainingSeconds <= 0
                        Button(
                            onClick = {
                                waBusy = true
                                waDialogError = null
                                CloudflareClient.verifyWhatsAppOtp(
                                    context = context,
                                    rawPhone = waSentPhoneDisplay,
                                    otpCode = waOtpInput,
                                    displayName = waDisplayName
                                ) { ok, errMsg, user ->
                                    waBusy = false
                                    if (ok && user != null) {
                                        showWhatsAppDialog = false
                                        Toast.makeText(
                                            context,
                                            "تم التحقق بنجاح، أهلاً ${user.fullName.ifEmpty { user.username }}!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onLoginSuccess(user)
                                    } else {
                                        if (errMsg?.contains("انتهت صلاحية") == true) {
                                            waRemainingSeconds = 0
                                        }
                                        waDialogError = errMsg ?: "رمز التحقق غير صحيح"
                                    }
                                }
                            },
                            enabled = !isExpired && waOtpInput.length == 6 && !isLoading && !waBusy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF16A34A),
                                disabledContainerColor = Color(0xFFCBD5E1)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (waBusy) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = if (waBusy) "جاري التحقق..." else "تأكيد الكود والدخول",
                                fontFamily = TajawalFontFamily,
                                color = Color.White
                            )
                        }
                    }
                },
                dismissButton = {
                    if (waStep == 2) {
                        TextButton(
                            enabled = !waBusy,
                            onClick = {
                                WhatsAppOtpManager.clearPendingOtp(context)
                                waStep = 1
                                waDialogError = null
                            }
                        ) {
                            Text("تغيير الرقم", fontFamily = TajawalFontFamily, color = Color(0xFF64748B))
                        }
                    } else {
                        TextButton(
                            enabled = !waBusy,
                            onClick = { showWhatsAppDialog = false }
                        ) {
                            Text("إلغاء", fontFamily = TajawalFontFamily, color = Color(0xFF64748B))
                        }
                    }
                }
            )
        }
    }
}

