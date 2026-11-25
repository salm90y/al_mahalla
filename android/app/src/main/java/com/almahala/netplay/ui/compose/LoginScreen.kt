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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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

    // Google Fallback Email Dialog State
    var showGoogleEmailDialog by remember { mutableStateOf(false) }
    var manualGoogleEmail by remember { mutableStateOf("") }
    var manualGoogleName by remember { mutableStateOf("") }
    var googleDialogError by remember { mutableStateOf<String?>(null) }

    // WhatsApp 3-Minute OTP Dialog State
    var showWhatsAppDialog by remember { mutableStateOf(false) }
    var waStep by remember { mutableStateOf(1) } // 1 = enter phone, 2 = verify OTP
    var waCountryCode by remember { mutableStateOf("+964") }
    var waPhoneInput by remember { mutableStateOf("") }
    var waDisplayName by remember { mutableStateOf("") }
    var waSentPhoneDisplay by remember { mutableStateOf("") }
    var waOtpInput by remember { mutableStateOf("") }
    var waDialogError by remember { mutableStateOf<String?>(null) }
    var waRemainingSeconds by remember { mutableStateOf(0) }
    var waOtpNonce by remember { mutableStateOf(0) }
    var waOpenedExternal by remember { mutableStateOf(true) }

    val handleGoogleSignInEmail: (String, String?) -> Unit = { email, displayName ->
        errorMessage = null
        isLoading = true
        focusManager.clearFocus()
        CloudflareClient.loginOrRegisterWithGoogle(context, email, displayName) { success, errMsg, user ->
            isLoading = false
            if (success && user != null) {
                Toast.makeText(context, "مرحباً بك ${user.fullName.ifEmpty { user.username }}!", Toast.LENGTH_SHORT).show()
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
                handleGoogleSignInEmail(selectedEmail, null)
            } else {
                showGoogleEmailDialog = true
            }
        } else {
            // If device has no Google account configured, allow manual Google email entry
            val existingGoogleAccounts = try {
                AccountManager.get(context).getAccountsByType("com.google")
            } catch (_: Exception) {
                emptyArray()
            }
            if (existingGoogleAccounts.isEmpty()) {
                showGoogleEmailDialog = true
            }
        }
    }

    val launchGoogleSignIn = {
        errorMessage = null
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
            showGoogleEmailDialog = true
        } catch (_: Exception) {
            showGoogleEmailDialog = true
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

                        // Divider for Quick Social / Phone Sign-In
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.weight(1f),
                                color = Color(0xFFE2E8F0)
                            )
                            Text(
                                text = "أو التسجيل السريع عبر",
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

                        // Google Account Sign-In Button
                        OutlinedButton(
                            onClick = { if (!isLoading) launchGoogleSignIn() },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(24.dp),
                            border = BorderStroke(1.2.dp, Color(0xFFCBD5E1)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFFF8FAFC),
                                contentColor = Color(0xFF0F172A)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                // Google Colored "G" Emblem
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                        .border(1.dp, Color(0xFFE2E8F0), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "G",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFEA4335)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "الدخول أو التسجيل بحساب جوجل (Google)",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = TajawalFontFamily,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // WhatsApp 3-Minute Verification Button
                        Button(
                            onClick = {
                                if (!isLoading) {
                                    waStep = 1
                                    waOtpInput = ""
                                    waDialogError = null
                                    showWhatsAppDialog = true
                                }
                            },
                            enabled = !isLoading,
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF16A34A),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MarkChatRead,
                                    contentDescription = "واتساب",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "التسجيل عبر رسالة واتساب (صلاحية 3 دقائق)",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = TajawalFontFamily,
                                    color = Color.White
                                )
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

        // Google Email Confirmation / Fallback Dialog (when device has no signed-in Google account)
        if (showGoogleEmailDialog) {
            AlertDialog(
                onDismissRequest = { showGoogleEmailDialog = false },
                containerColor = Color.White,
                shape = RoundedCornerShape(22.dp),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEE2E2)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "G",
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFEA4335),
                                fontSize = 16.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "التسجيل بحساب جوجل",
                            fontFamily = TajawalFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color(0xFF0F172A)
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "أدخل بريدك الإلكتروني في Google لإتمام التسجيل أو الدخول مباشرة:",
                            fontFamily = TajawalFontFamily,
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                        OutlinedTextField(
                            value = manualGoogleEmail,
                            onValueChange = {
                                manualGoogleEmail = it
                                googleDialogError = null
                            },
                            label = { Text("البريد الإلكتروني (Gmail)", fontFamily = TajawalFontFamily) },
                            placeholder = { Text("example@gmail.com") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = manualGoogleName,
                            onValueChange = { manualGoogleName = it },
                            label = { Text("الاسم الظاهر (اختياري)", fontFamily = TajawalFontFamily) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
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
                                googleDialogError = "يرجى إدخال بريد جوجل صحيح"
                            } else {
                                showGoogleEmailDialog = false
                                handleGoogleSignInEmail(cleanEmail, manualGoogleName.trim().ifEmpty { null })
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("متابعة والدخول", fontFamily = TajawalFontFamily, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGoogleEmailDialog = false }) {
                        Text("إلغاء", fontFamily = TajawalFontFamily, color = Color(0xFF64748B))
                    }
                }
            )
        }

        // WhatsApp 2-Step Verification Dialog (Strict 3-Minute Expiry)
        if (showWhatsAppDialog) {
            AlertDialog(
                onDismissRequest = {
                    if (!isLoading) showWhatsAppDialog = false
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
                            text = if (waStep == 1) "التسجيل عبر واتساب" else "تأكيد رمز واتساب",
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
                                text = "أدخل رقم هاتفك المرتبط بواتساب. سيتم إرسال رسالة تحتوي على رمز تحقق من 6 أرقام صالح لمدة 3 دقائق فقط.",
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
                                            waCountryCode = code
                                            waDialogError = null
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

                            Text(
                                text = "تم تجهيز رسالة واتساب إلى الرقم: $waSentPhoneDisplay",
                                fontFamily = TajawalFontFamily,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0F172A)
                            )

                            if (!waOpenedExternal) {
                                Text(
                                    text = "لم يُفتح تطبيق واتساب تلقائياً؛ تأكد من تثبيت واتساب على الجهاز.",
                                    fontFamily = TajawalFontFamily,
                                    fontSize = 12.sp,
                                    color = Color(0xFFD97706)
                                )
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
                                    val digitsOnly = input.filter { it.isDigit() }.take(6)
                                    waOtpInput = digitsOnly
                                    waDialogError = null
                                },
                                enabled = !isExpired,
                                label = { Text("رمز التحقق (6 أرقام)", fontFamily = TajawalFontFamily) },
                                placeholder = { Text("000000") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            TextButton(
                                onClick = {
                                    val dispatch = WhatsAppOtpManager.sendOtpViaWhatsApp(
                                        context,
                                        waSentPhoneDisplay,
                                        waDisplayName
                                    )
                                    if (dispatch.success) {
                                        waOtpInput = ""
                                        waDialogError = null
                                        waOpenedExternal = dispatch.openedWhatsApp
                                        waRemainingSeconds = WhatsAppOtpManager.getRemainingSeconds(context)
                                        waOtpNonce++
                                        Toast.makeText(context, "تم إنشاء رمز جديد صالح لـ 3 دقائق", Toast.LENGTH_SHORT).show()
                                    } else {
                                        waDialogError = dispatch.errorMessage
                                    }
                                },
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
                                    text = "إعادة إرسال رمز جديد عبر واتساب",
                                    fontFamily = TajawalFontFamily,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF16A34A)
                                )
                            }
                        }

                        if (waDialogError != null) {
                            Text(
                                text = waDialogError!!,
                                color = Color(0xFFDC2626),
                                fontSize = 12.5.sp,
                                fontFamily = TajawalFontFamily,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                confirmButton = {
                    if (waStep == 1) {
                        Button(
                            onClick = {
                                val rawLocal = waPhoneInput.trim()
                                val combined = if (waCountryCode.isNotEmpty() && !rawLocal.startsWith("+") && !rawLocal.startsWith("00")) {
                                    "${waCountryCode}${rawLocal.trimStart('0')}"
                                } else {
                                    rawLocal
                                }
                                val dispatch = WhatsAppOtpManager.sendOtpViaWhatsApp(
                                    context,
                                    combined,
                                    waDisplayName
                                )
                                if (dispatch.success) {
                                    waSentPhoneDisplay = dispatch.cleanPhone
                                    waOpenedExternal = dispatch.openedWhatsApp
                                    waRemainingSeconds = WhatsAppOtpManager.getRemainingSeconds(context)
                                    waOtpInput = ""
                                    waDialogError = null
                                    waStep = 2
                                    waOtpNonce++
                                } else {
                                    waDialogError = dispatch.errorMessage
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("إرسال عبر واتساب", fontFamily = TajawalFontFamily, color = Color.White)
                        }
                    } else {
                        val isExpired = waRemainingSeconds <= 0
                        Button(
                            onClick = {
                                when (val res = WhatsAppOtpManager.verifyOtp(context, waSentPhoneDisplay, waOtpInput)) {
                                    is WhatsAppOtpManager.OtpVerifyResult.Success -> {
                                        showWhatsAppDialog = false
                                        isLoading = true
                                        CloudflareClient.loginOrRegisterWithWhatsApp(
                                            context,
                                            res.cleanPhone,
                                            res.displayName
                                        ) { ok, errMsg, user ->
                                            isLoading = false
                                            if (ok && user != null) {
                                                Toast.makeText(
                                                    context,
                                                    "تم التحقق بنجاح، أهلاً ${user.fullName.ifEmpty { user.username }}!",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                onLoginSuccess(user)
                                            } else {
                                                errorMessage = errMsg ?: "تعذر الدخول برقم الواتساب"
                                                Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    is WhatsAppOtpManager.OtpVerifyResult.Expired -> {
                                        waRemainingSeconds = 0
                                        waDialogError = res.message
                                    }
                                    is WhatsAppOtpManager.OtpVerifyResult.Invalid -> {
                                        waDialogError = res.message
                                    }
                                }
                            },
                            enabled = !isExpired && waOtpInput.length == 6 && !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF16A34A),
                                disabledContainerColor = Color(0xFFCBD5E1)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("تأكيد الرمز والدخول", fontFamily = TajawalFontFamily, color = Color.White)
                        }
                    }
                },
                dismissButton = {
                    if (waStep == 2) {
                        TextButton(onClick = {
                            WhatsAppOtpManager.clearPendingOtp(context)
                            waStep = 1
                            waDialogError = null
                        }) {
                            Text("تغيير الرقم", fontFamily = TajawalFontFamily, color = Color(0xFF64748B))
                        }
                    } else {
                        TextButton(onClick = { showWhatsAppDialog = false }) {
                            Text("إلغاء", fontFamily = TajawalFontFamily, color = Color(0xFF64748B))
                        }
                    }
                }
            )
        }
    }
}

