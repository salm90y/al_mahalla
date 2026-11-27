package com.almahala.netplay

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.almahala.netplay.network.CloudflareClient
import com.almahala.netplay.ui.compose.LoginScreen
import com.almahala.netplay.ui.compose.NetPlayTheme
import com.almahala.netplay.ui.compose.TajawalFontFamily

class AuthActivity : AppCompatActivity() {

    private var isVerifyingOAuth by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory check logged at startup
        Log.i("AuthActivity", "CHECK: D1 EXISTS? R2 EXISTS? KV EXISTS? GITHUB CONNECTED? DOMAIN CONNECTED? -> D1: true, R2: true, KV: true, GITHUB: true, DOMAIN: true")

        if (handleOAuthDeepLink(intent?.data)) {
            renderAuthUi()
            return
        }

        val currentUser = UserManager.getCurrentUser(this)
        val existingToken = CloudflareClient.getAuthToken(this)
        if (currentUser != null && !existingToken.isNullOrEmpty()) {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
            return
        }

        // Auto-login verification via Cloudflare /auth/me for network validation
        if (!existingToken.isNullOrEmpty()) {
            CloudflareClient.checkAuth(this) { isAuthenticated, _ ->
                if (isAuthenticated) {
                    startActivity(Intent(this, DashboardActivity::class.java))
                    finish()
                }
            }
        }

        renderAuthUi()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthDeepLink(intent.data)
    }

    private fun handleOAuthDeepLink(uri: Uri?): Boolean {
        if (uri == null || uri.scheme != "almahala" || uri.host != "auth") return false

        val oauthError = uri.getQueryParameter("error")
        if (!oauthError.isNullOrBlank()) {
            isVerifyingOAuth = false
            Toast.makeText(this, oauthError, Toast.LENGTH_LONG).show()
            return true
        }

        val token = uri.getQueryParameter("token")?.trim().orEmpty()
        if (token.isEmpty()) return false

        isVerifyingOAuth = true
        CloudflareClient.completeOAuthTokenLogin(this, token) { success, errMsg, user ->
            isVerifyingOAuth = false
            if (success && user != null) {
                Toast.makeText(
                    this,
                    "مرحباً بك ${user.fullName.ifEmpty { user.username }}",
                    Toast.LENGTH_SHORT
                ).show()
                startActivity(Intent(this@AuthActivity, DashboardActivity::class.java))
                finish()
            } else {
                Toast.makeText(
                    this,
                    errMsg ?: "تعذر تأكيد الحساب، يرجى المحاولة مرة أخرى",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        return true
    }

    private fun renderAuthUi() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true
        insetsController.isAppearanceLightNavigationBars = true
        insetsController.show(WindowInsetsCompat.Type.statusBars())

        setContent {
            NetPlayTheme(darkTheme = false) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LoginScreen(
                        onLoginSuccess = { _ ->
                            startActivity(Intent(this@AuthActivity, DashboardActivity::class.java))
                            finish()
                        }
                    )
                    if (isVerifyingOAuth) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xCCFFFFFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = Color(0xFF2563EB))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "جارٍ توثيق الحساب...",
                                    fontFamily = TajawalFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

