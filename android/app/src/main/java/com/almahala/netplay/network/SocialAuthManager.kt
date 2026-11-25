package com.almahala.netplay.network

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Provides runtime application metadata (App Name, Version Name, Version Code, Package ID)
 * directly from Android PackageManager and manifest resources.
 */
object AppInfoHelper {
    fun getAppName(context: Context): String {
        return try {
            val labelRes = context.applicationInfo.labelRes
            if (labelRes != 0) context.getString(labelRes) else "المحلة"
        } catch (_: Exception) {
            "المحلة"
        }
    }

    fun getVersionName(context: Context): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "1.4.0"
        } catch (_: Exception) {
            "1.4.0"
        }
    }

    fun getVersionCode(context: Context): Long {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            PackageInfoCompat.getLongVersionCode(pInfo)
        } catch (_: Exception) {
            14L
        }
    }
}

/**
 * Manages WhatsApp OTP verification codes with a strict 3-minute (180 seconds) expiration window,
 * constant-time hash comparison, attempt limiting, and WhatsApp intent dispatch.
 */
object WhatsAppOtpManager {
    const val OTP_VALIDITY_MILLIS: Long = 3 * 60 * 1000L // Strictly 3 minutes
    private const val MAX_ATTEMPTS = 5
    private const val PREFS_NAME = "almahala_wa_otp_prefs"
    private const val KEY_PHONE = "pending_phone"
    private const val KEY_DISPLAY_NAME = "pending_display_name"
    private const val KEY_OTP_HASH = "otp_hash"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_ATTEMPTS_LEFT = "attempts_left"

    sealed class OtpVerifyResult {
        data class Success(val cleanPhone: String, val displayName: String) : OtpVerifyResult()
        data class Expired(val message: String) : OtpVerifyResult()
        data class Invalid(val message: String, val attemptsLeft: Int) : OtpVerifyResult()
    }

    data class OtpDispatchResult(
        val success: Boolean,
        val cleanPhone: String = "",
        val expiresAtMillis: Long = 0L,
        val openedWhatsApp: Boolean = false,
        val errorMessage: String? = null
    )

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun normalizePhoneDigits(rawPhone: String): String {
        var digits = rawPhone.trim().replace(Regex("[^0-9+]"), "")
        if (digits.startsWith("00")) {
            digits = digits.substring(2)
        }
        digits = digits.replace("+", "")
        return digits
    }

    private fun hashOtp(phoneDigits: String, code: String): String {
        val raw = "AlMahalla_WA_OTP_v1:${phoneDigits}:${code.trim()}"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun clearPendingOtp(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun getRemainingSeconds(context: Context): Int {
        val expiresAt = prefs(context).getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAt <= 0L) return 0
        val diff = expiresAt - System.currentTimeMillis()
        return if (diff <= 0L) 0 else ((diff + 999L) / 1000L).toInt()
    }

    fun sendOtpViaWhatsApp(
        context: Context,
        rawPhone: String,
        displayName: String = ""
    ): OtpDispatchResult {
        val cleanDigits = normalizePhoneDigits(rawPhone)
        if (cleanDigits.length < 8 || cleanDigits.length > 15) {
            return OtpDispatchResult(
                success = false,
                errorMessage = "يرجى إدخال رقم واتساب صحيح مع رمز الدولة (مثال: +9647701234567)"
            )
        }

        val otpCode = (100000 + SecureRandom().nextInt(900000)).toString()
        val now = System.currentTimeMillis()
        val expiresAt = now + OTP_VALIDITY_MILLIS
        val otpHash = hashOtp(cleanDigits, otpCode)

        prefs(context).edit()
            .putString(KEY_PHONE, cleanDigits)
            .putString(KEY_DISPLAY_NAME, displayName.trim())
            .putString(KEY_OTP_HASH, otpHash)
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .putInt(KEY_ATTEMPTS_LEFT, MAX_ATTEMPTS)
            .apply()

        val message = buildString {
            append("🔐 رمز التحقق لتسجيل الدخول في تطبيق المحلة هو: *")
            append(otpCode)
            append("*\n⏳ تنتهي صلاحية هذا الرمز تلقائياً بعد 3 دقائق.")
        }
        val waUri = Uri.parse("https://wa.me/$cleanDigits?text=${Uri.encode(message)}")

        val opened = try {
            val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
            var launched = false
            for (pkg in packages) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, waUri).apply {
                        setPackage(pkg)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    launched = true
                    break
                } catch (_: Exception) {}
            }
            if (!launched) {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, waUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                launched = true
            }
            launched
        } catch (_: Exception) {
            false
        }

        return OtpDispatchResult(
            success = true,
            cleanPhone = "+$cleanDigits",
            expiresAtMillis = expiresAt,
            openedWhatsApp = opened
        )
    }

    fun verifyOtp(
        context: Context,
        rawPhone: String,
        enteredCode: String
    ): OtpVerifyResult {
        val sp = prefs(context)
        val savedPhone = sp.getString(KEY_PHONE, null)
        val savedHash = sp.getString(KEY_OTP_HASH, null)
        val expiresAt = sp.getLong(KEY_EXPIRES_AT, 0L)
        val savedName = sp.getString(KEY_DISPLAY_NAME, "") ?: ""
        val attemptsLeft = sp.getInt(KEY_ATTEMPTS_LEFT, 0)

        if (savedPhone.isNullOrBlank() || savedHash.isNullOrBlank() || expiresAt <= 0L) {
            return OtpVerifyResult.Expired("لا يوجد رمز تحقق نشط. يرجى طلب رمز جديد عبر واتساب.")
        }

        val now = System.currentTimeMillis()
        if (now > expiresAt) {
            clearPendingOtp(context)
            return OtpVerifyResult.Expired("انتهت صلاحية رمز التحقق (تجاوزت 3 دقائق). يرجى إرسال رمز جديد عبر واتساب.")
        }

        val cleanDigits = normalizePhoneDigits(rawPhone)
        if (cleanDigits != savedPhone) {
            return OtpVerifyResult.Invalid("رقم الهاتف لا يطابق الرقم الذي طُلب له الرمز.", attemptsLeft)
        }

        val cleanCode = enteredCode.trim().replace(Regex("[^0-9]"), "")
        if (cleanCode.length != 6) {
            return OtpVerifyResult.Invalid("يرجى إدخال رمز التحقق المكون من 6 أرقام.", attemptsLeft)
        }

        val candidateHash = hashOtp(cleanDigits, cleanCode)
        val matches = MessageDigest.isEqual(
            candidateHash.toByteArray(Charsets.UTF_8),
            savedHash.toByteArray(Charsets.UTF_8)
        )

        if (matches) {
            clearPendingOtp(context)
            return OtpVerifyResult.Success(cleanPhone = "+$cleanDigits", displayName = savedName)
        }

        val remainingAttempts = (attemptsLeft - 1).coerceAtLeast(0)
        if (remainingAttempts <= 0) {
            clearPendingOtp(context)
            return OtpVerifyResult.Expired("تم استنفاد المحاولات المسموحة. يرجى طلب رمز جديد عبر واتساب.")
        } else {
            sp.edit().putInt(KEY_ATTEMPTS_LEFT, remainingAttempts).apply()
            return OtpVerifyResult.Invalid(
                "رمز التحقق غير صحيح (المتبقي $remainingAttempts محاولات).",
                remainingAttempts
            )
        }
    }
}
