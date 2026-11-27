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
 * Manages server-backed WhatsApp OTP verification sessions with a strict 3-minute (180 seconds)
 * expiration window and phone number normalization.
 */
object WhatsAppOtpManager {
    const val OTP_VALIDITY_MILLIS: Long = 3 * 60 * 1000L // Strictly 3 minutes
    private const val PREFS_NAME = "almahala_wa_otp_prefs"
    private const val KEY_PHONE = "pending_phone"
    private const val KEY_DISPLAY_NAME = "pending_display_name"
    private const val KEY_EXPIRES_AT = "expires_at"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun normalizeAsciiDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            when (ch) {
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun normalizePhoneDigits(rawPhone: String): String {
        var digits = normalizeAsciiDigits(rawPhone).trim().replace(Regex("[^0-9+]"), "")
        if (digits.startsWith("00")) {
            digits = digits.substring(2)
        }
        digits = digits.replace("+", "")
        return digits
    }

    fun clearPendingOtp(context: Context) {
        prefs(context).edit().clear().apply()
    }

    fun savePendingSession(
        context: Context,
        cleanPhone: String,
        displayName: String = "",
        expiresInSeconds: Int = 180
    ): Long {
        val digits = normalizePhoneDigits(cleanPhone)
        val ttlMillis = if (expiresInSeconds > 0) expiresInSeconds * 1000L else OTP_VALIDITY_MILLIS
        val expiresAt = System.currentTimeMillis() + ttlMillis
        prefs(context).edit()
            .putString(KEY_PHONE, digits)
            .putString(KEY_DISPLAY_NAME, displayName.trim())
            .putLong(KEY_EXPIRES_AT, expiresAt)
            .apply()
        return expiresAt
    }

    fun getPendingPhone(context: Context): String {
        val digits = prefs(context).getString(KEY_PHONE, "") ?: ""
        return if (digits.isNotEmpty()) "+$digits" else ""
    }

    fun getRemainingSeconds(context: Context): Int {
        val expiresAt = prefs(context).getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAt <= 0L) return 0
        val diff = expiresAt - System.currentTimeMillis()
        return if (diff <= 0L) 0 else ((diff + 999L) / 1000L).toInt()
    }
}
