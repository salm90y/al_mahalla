package com.almahala.netplay.network

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.almahala.netplay.UserManager
import im.zego.zegoexpress.ZegoExpressEngine
import im.zego.zegoexpress.callback.IZegoEventHandler
import im.zego.zegoexpress.constants.ZegoRoomStateChangedReason
import im.zego.zegoexpress.constants.ZegoScenario
import im.zego.zegoexpress.constants.ZegoUpdateType
import im.zego.zegoexpress.entity.ZegoEngineProfile
import im.zego.zegoexpress.entity.ZegoRoomConfig
import im.zego.zegoexpress.entity.ZegoStream
import im.zego.zegoexpress.entity.ZegoUser
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * ZegoCallManager: Official Voice & Video Calling Engine for Al-Mahalla.
 * Handles Zego Cloud initialization, room login, audio/video streaming,
 * microphone mute/unmute, speaker switching, camera controls, and clean call lifecycle.
 */
object ZegoCallManager {
    private const val TAG = "ZegoCallManager"
    private const val DEFAULT_APP_ID = 1477087305L
    private const val DEFAULT_APP_SIGN = "29c005b621138958b88eea14c91bd62b2189095171ce962ffe3680974493b41d"

    private var zegoEngine: ZegoExpressEngine? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isInitialized = AtomicBoolean(false)
    private val isInitializing = AtomicBoolean(false)
    private val initLock = Any()
    private val pendingInitCallbacks = mutableListOf<(Boolean) -> Unit>()
    private var currentRoomId: String = ""
    private var loggedInRoomId: String = ""
    private var pendingPublishRoomId: String? = null
    private var pendingPublishStreamId: String? = null
    private var pendingMicUnmute: Boolean = false
    private val isPublishing = AtomicBoolean(false)
    private val isMicMuted = AtomicBoolean(false)
    private val isSpeakerOn = AtomicBoolean(false)
    private val isCameraOn = AtomicBoolean(false)
    private val isFrontCamera = AtomicBoolean(true)
    private var appContextRef: Context? = null

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    var onCallConnected: (() -> Unit)? = null
    var onCallDisconnected: ((String) -> Unit)? = null
    var onRemoteUserJoined: ((String) -> Unit)? = null
    var onRemoteUserLeft: ((String) -> Unit)? = null

    private fun hasRecordAudioPermission(context: Context?): Boolean {
        val ctx = context ?: appContextRef ?: return false
        return ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    private fun dispatchInitCallbacks(success: Boolean) {
        val callbacks = synchronized(initLock) {
            isInitializing.set(false)
            val copy = pendingInitCallbacks.toList()
            pendingInitCallbacks.clear()
            copy
        }
        callbacks.forEach { cb ->
            try { cb(success) } catch (_: Throwable) {}
        }
    }

    /**
     * Initialize Zego Express Engine (Thread-safe single-flight)
     */
    fun initEngine(context: Context, callback: ((Boolean) -> Unit)? = null) {
        val appCtx = context.applicationContext ?: context
        appContextRef = appCtx

        if (isInitialized.get() && zegoEngine != null) {
            callback?.invoke(true)
            return
        }

        val shouldStartInit = synchronized(initLock) {
            if (isInitialized.get() && zegoEngine != null) {
                false
            } else {
                if (callback != null) pendingInitCallbacks.add(callback)
                if (isInitializing.compareAndSet(false, true)) {
                    true
                } else {
                    false
                }
            }
        }

        if (isInitialized.get() && zegoEngine != null && !shouldStartInit) {
            synchronized(initLock) { pendingInitCallbacks.clear() }
            callback?.invoke(true)
            return
        }

        if (!shouldStartInit) return

        CloudflareClient.getZegoConfig(appCtx) { _, appId, appSign ->
            val finalAppId = if (appId > 0L) appId else DEFAULT_APP_ID
            val finalAppSign = if (appSign.isNotEmpty()) appSign else DEFAULT_APP_SIGN

            runOnMain {
                try {
                    if (isInitialized.get() && zegoEngine != null) {
                        dispatchInitCallbacks(true)
                        return@runOnMain
                    }
                    val app = (appCtx as? Application) ?: (context as? Application)
                    if (app == null) {
                        Log.w(TAG, "Application context unavailable for Zego init")
                        isInitialized.set(false)
                        dispatchInitCallbacks(false)
                        return@runOnMain
                    }
                    val profile = ZegoEngineProfile().apply {
                        this.appID = finalAppId
                        this.appSign = finalAppSign
                        this.scenario = ZegoScenario.DEFAULT
                        this.application = app
                    }

                    zegoEngine = ZegoExpressEngine.createEngine(profile, object : IZegoEventHandler() {
                        override fun onRoomStateChanged(
                            roomID: String?,
                            reason: ZegoRoomStateChangedReason?,
                            errorCode: Int,
                            extendedData: JSONObject?
                        ) {
                            Log.d(TAG, "Zego room state changed: room=$roomID, reason=$reason, code=$errorCode")
                            runOnMain {
                                try {
                                    if (errorCode == 0 && (reason == ZegoRoomStateChangedReason.LOGINED || reason == ZegoRoomStateChangedReason.RECONNECTED)) {
                                        val joinedRoom = roomID ?: currentRoomId
                                        loggedInRoomId = joinedRoom
                                        val streamToPublish = pendingPublishStreamId
                                        val targetPublishRoom = pendingPublishRoomId
                                        val engine = zegoEngine
                                        if (engine != null &&
                                            streamToPublish != null &&
                                            targetPublishRoom == joinedRoom &&
                                            pendingMicUnmute &&
                                            hasRecordAudioPermission(appContextRef)
                                        ) {
                                            try {
                                                engine.enableAudioCaptureDevice(true)
                                                engine.muteMicrophone(false)
                                                isMicMuted.set(false)
                                                engine.setAudioRouteToSpeaker(isSpeakerOn.get())
                                                if (!isPublishing.get()) {
                                                    engine.startPublishingStream(streamToPublish)
                                                    isPublishing.set(true)
                                                }
                                            } catch (pe: Throwable) {
                                                Log.w(TAG, "Deferred stream publish warning: ${pe.message}")
                                            }
                                        }
                                        onCallConnected?.invoke()
                                    } else if (reason == ZegoRoomStateChangedReason.LOGOUT) {
                                        if (roomID == null || roomID == loggedInRoomId) {
                                            loggedInRoomId = ""
                                        }
                                        onCallDisconnected?.invoke("تم إنهاء جلسة Zego")
                                    } else if (errorCode != 0) {
                                        Log.w(TAG, "Zego room error: $errorCode")
                                    }
                                } catch (_: Throwable) {}
                            }
                        }

                        override fun onRoomStreamUpdate(
                            roomID: String?,
                            updateType: ZegoUpdateType?,
                            streamList: ArrayList<ZegoStream>?,
                            extendedData: JSONObject?
                        ) {
                            if (streamList == null) return
                            val snapshot = streamList.toList()
                            runOnMain {
                                for (stream in snapshot) {
                                    val streamId = stream?.streamID ?: continue
                                    val remoteUserId = stream.user?.userID ?: continue
                                    if (updateType == ZegoUpdateType.ADD) {
                                        Log.d(TAG, "Remote Zego stream added: $streamId")
                                        try {
                                            zegoEngine?.startPlayingStream(streamId)
                                        } catch (_: Throwable) {}
                                        try { onRemoteUserJoined?.invoke(remoteUserId) } catch (_: Throwable) {}
                                    } else if (updateType == ZegoUpdateType.DELETE) {
                                        Log.d(TAG, "Remote Zego stream removed: $streamId")
                                        try {
                                            zegoEngine?.stopPlayingStream(streamId)
                                        } catch (_: Throwable) {}
                                        try { onRemoteUserLeft?.invoke(remoteUserId) } catch (_: Throwable) {}
                                    }
                                }
                            }
                        }
                    })

                    isInitialized.set(zegoEngine != null)
                    Log.d(TAG, "ZegoExpressEngine initialized=${isInitialized.get()} appId=$finalAppId")
                    dispatchInitCallbacks(isInitialized.get())
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to initialize Zego engine: ${e.message}", e)
                    isInitialized.set(false)
                    dispatchInitCallbacks(false)
                }
            }
        }
    }

    private fun sanitizeId(raw: String, prefix: String): String {
        val cleaned = raw.replace(Regex("[^a-zA-Z0-9_]"), "_").trim('_')
        return if (cleaned.isNotEmpty()) cleaned.take(64) else "${prefix}_${abs(raw.hashCode().toLong())}".take(64)
    }

    /**
     * Join Watch Party room in safe Audience Mode (Listen-only by default, no mic capturing, no crash)
     */
    fun joinWatchParty(
        context: Context,
        roomId: String,
        userId: String,
        userName: String,
        onConnected: (() -> Unit)? = null
    ) {
        val appCtx = context.applicationContext ?: context
        appContextRef = appCtx
        val safeRoomId = sanitizeId(roomId, "room")
        onCallConnected = onConnected

        initEngine(appCtx) { success ->
            if (!success || zegoEngine == null) return@initEngine
            runOnMain {
                try {
                    val engine = zegoEngine ?: return@runOnMain
                    val safeUserId = sanitizeId(userId, "u")
                    val safeUserName = userName.ifBlank { safeUserId }

                    if (currentRoomId.isNotEmpty() && currentRoomId != safeRoomId) {
                        try {
                            if (isPublishing.get()) {
                                engine.stopPublishingStream()
                                isPublishing.set(false)
                            }
                            engine.logoutRoom(currentRoomId)
                        } catch (_: Throwable) {}
                        loggedInRoomId = ""
                    }

                    val alreadyInRoom = currentRoomId == safeRoomId
                    currentRoomId = safeRoomId

                    val user = ZegoUser(safeUserId, safeUserName)
                    val roomConfig = ZegoRoomConfig().apply {
                        isUserStatusNotify = true
                    }

                    // Audience Mode: mute mic, do not record or publish, keep video/audio playing cleanly
                    try {
                        engine.muteMicrophone(true)
                        isMicMuted.set(true)
                        engine.enableCamera(false)
                        isCameraOn.set(false)
                        engine.setAudioRouteToSpeaker(true)
                        isSpeakerOn.set(true)
                    } catch (_: Throwable) {}

                    if (!alreadyInRoom) {
                        engine.loginRoom(safeRoomId, user, roomConfig)
                    }
                    Log.d(TAG, "Zego watch party room joined in audience mode: safeRoomId=$safeRoomId")
                } catch (e: Throwable) {
                    Log.w(TAG, "joinWatchParty non-fatal warning: ${e.message}")
                }
            }
        }
    }

    /**
     * Start publishing audio when user activates Walkie-Talkie Intercom (On-demand with explicit permission)
     */
    fun startPublishingAudio(context: Context? = null, roomId: String, userId: String) {
        val appCtx = context?.applicationContext ?: context ?: appContextRef
        if (appCtx != null) appContextRef = appCtx

        if (!hasRecordAudioPermission(appCtx)) {
            Log.w(TAG, "startPublishingAudio skipped: RECORD_AUDIO not granted")
            return
        }

        val safeRoomId = sanitizeId(roomId, "room")
        val safeUserId = sanitizeId(userId, "u")
        val streamId = "s_${safeRoomId}_${safeUserId}"

        pendingPublishRoomId = safeRoomId
        pendingPublishStreamId = streamId
        pendingMicUnmute = true

        val engine = zegoEngine
        if (engine == null && appCtx != null) {
            initEngine(appCtx) { success ->
                if (success && zegoEngine != null && pendingMicUnmute) {
                    runOnMain {
                        try {
                            val eg = zegoEngine ?: return@runOnMain
                            if (!hasRecordAudioPermission(appContextRef)) return@runOnMain
                            if (currentRoomId != safeRoomId) {
                                if (currentRoomId.isNotEmpty()) {
                                    try {
                                        if (isPublishing.get()) {
                                            eg.stopPublishingStream()
                                            isPublishing.set(false)
                                        }
                                        eg.logoutRoom(currentRoomId)
                                    } catch (_: Throwable) {}
                                    loggedInRoomId = ""
                                }
                                val user = ZegoUser(safeUserId, safeUserId)
                                val roomConfig = ZegoRoomConfig().apply { isUserStatusNotify = true }
                                currentRoomId = safeRoomId
                                eg.loginRoom(safeRoomId, user, roomConfig)
                            } else if (loggedInRoomId == safeRoomId) {
                                eg.enableAudioCaptureDevice(true)
                                eg.muteMicrophone(false)
                                isMicMuted.set(false)
                                eg.setAudioRouteToSpeaker(isSpeakerOn.get())
                                if (!isPublishing.get()) {
                                    eg.startPublishingStream(streamId)
                                    isPublishing.set(true)
                                }
                            }
                        } catch (e: Throwable) {
                            Log.w(TAG, "startPublishingAudio deferred error: ${e.message}")
                        }
                    }
                }
            }
            return
        }

        runOnMain {
            try {
                val eg = zegoEngine ?: return@runOnMain
                if (!hasRecordAudioPermission(appContextRef)) return@runOnMain
                if (currentRoomId != safeRoomId) {
                    if (currentRoomId.isNotEmpty()) {
                        try {
                            if (isPublishing.get()) {
                                eg.stopPublishingStream()
                                isPublishing.set(false)
                            }
                            eg.logoutRoom(currentRoomId)
                        } catch (_: Throwable) {}
                        loggedInRoomId = ""
                    }
                    val user = ZegoUser(safeUserId, safeUserId)
                    val roomConfig = ZegoRoomConfig().apply { isUserStatusNotify = true }
                    currentRoomId = safeRoomId
                    eg.loginRoom(safeRoomId, user, roomConfig)
                } else if (loggedInRoomId == safeRoomId) {
                    eg.enableAudioCaptureDevice(true)
                    eg.muteMicrophone(false)
                    isMicMuted.set(false)
                    eg.setAudioRouteToSpeaker(isSpeakerOn.get())
                    if (!isPublishing.get()) {
                        eg.startPublishingStream(streamId)
                        isPublishing.set(true)
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "startPublishingAudio error: ${e.message}")
            }
        }
    }

    fun startPublishingAudio(roomId: String, userId: String) {
        startPublishingAudio(null, roomId, userId)
    }

    /**
     * Ensure publishing starts/resumes cleanly after runtime RECORD_AUDIO permission is granted
     */
    fun ensureAudioPublishingAfterPermission(context: Context, roomId: String, userId: String, muted: Boolean) {
        val appCtx = context.applicationContext ?: context
        appContextRef = appCtx
        if (!hasRecordAudioPermission(appCtx)) {
            Log.w(TAG, "ensureAudioPublishingAfterPermission skipped: RECORD_AUDIO not granted")
            return
        }
        val safeRoomId = sanitizeId(roomId, "room")
        val safeUserId = sanitizeId(userId, "u")
        val streamId = "s_${safeRoomId}_${safeUserId}"
        pendingPublishRoomId = safeRoomId
        pendingPublishStreamId = streamId
        pendingMicUnmute = !muted

        try {
            val engine = zegoEngine
            if (engine == null) {
                initEngine(appCtx) { ok ->
                    if (ok && zegoEngine != null) {
                        runOnMain {
                            try {
                                val eg = zegoEngine ?: return@runOnMain
                                if (!hasRecordAudioPermission(appContextRef)) return@runOnMain
                                if (currentRoomId != safeRoomId) {
                                    val user = ZegoUser(safeUserId, safeUserId)
                                    currentRoomId = safeRoomId
                                    loggedInRoomId = ""
                                    eg.loginRoom(safeRoomId, user, ZegoRoomConfig().apply { isUserStatusNotify = true })
                                } else if (loggedInRoomId == safeRoomId) {
                                    eg.enableAudioCaptureDevice(true)
                                    eg.muteMicrophone(muted)
                                    isMicMuted.set(muted)
                                    if (!isPublishing.get()) {
                                        eg.startPublishingStream(streamId)
                                        isPublishing.set(true)
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                }
            } else {
                runOnMain {
                    try {
                        engine.enableAudioCaptureDevice(true)
                        engine.muteMicrophone(muted)
                        isMicMuted.set(muted)
                        if (loggedInRoomId == safeRoomId && !isPublishing.get()) {
                            engine.startPublishingStream(streamId)
                            isPublishing.set(true)
                        }
                    } catch (_: Throwable) {}
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "ensureAudioPublishingAfterPermission error: ${e.message}")
        }
    }

    /**
     * Stop publishing audio when user releases Walkie-Talkie Intercom
     */
    fun stopPublishingAudio() {
        pendingMicUnmute = false
        pendingPublishStreamId = null
        runOnMain {
            try {
                val engine = zegoEngine ?: return@runOnMain
                engine.muteMicrophone(true)
                isMicMuted.set(true)
                if (isPublishing.get()) {
                    engine.stopPublishingStream()
                    isPublishing.set(false)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "stopPublishingAudio error: ${e.message}")
            }
        }
    }

    /**
     * Start or join a voice or video call session via Zego
     */
    fun startCall(
        context: Context,
        roomId: String,
        userId: String,
        userName: String,
        isVideo: Boolean,
        isOutgoing: Boolean,
        onConnected: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val appCtx = context.applicationContext ?: context
        appContextRef = appCtx
        val safeRoomId = sanitizeId(roomId, "room")
        onCallConnected = onConnected

        initEngine(appCtx) { success ->
            if (!success || zegoEngine == null) {
                onError?.invoke("تعذر الاتصال بخوادم Zego للمكالمات")
                return@initEngine
            }

            runOnMain {
                try {
                    val engine = zegoEngine ?: return@runOnMain
                    val safeUserId = sanitizeId(userId, "u")
                    val safeUserName = userName.ifBlank { safeUserId }

                    if (currentRoomId.isNotEmpty() && currentRoomId != safeRoomId) {
                        try {
                            if (isPublishing.get()) {
                                engine.stopPublishingStream()
                                isPublishing.set(false)
                            }
                            engine.logoutRoom(currentRoomId)
                        } catch (_: Throwable) {}
                        loggedInRoomId = ""
                    }
                    val alreadyInRoom = currentRoomId == safeRoomId
                    currentRoomId = safeRoomId

                    val user = ZegoUser(safeUserId, safeUserName)
                    val roomConfig = ZegoRoomConfig().apply {
                        isUserStatusNotify = true
                    }

                    val hasAudioPerm = ContextCompat.checkSelfPermission(
                        appCtx, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    val hasCameraPerm = ContextCompat.checkSelfPermission(
                        appCtx, Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED

                    // 1. Audio routing
                    try {
                        engine.setAudioRouteToSpeaker(isVideo)
                        isSpeakerOn.set(isVideo)
                    } catch (_: Throwable) {}

                    // 2. Microphone & Camera — ONLY activate hardware capture when permission is granted!
                    try {
                        engine.muteMicrophone(!hasAudioPerm)
                        isMicMuted.set(!hasAudioPerm)
                        val enableCam = isVideo && hasCameraPerm
                        engine.enableCamera(enableCam)
                        isCameraOn.set(enableCam)
                    } catch (_: Throwable) {}

                    // 3. Log in to Zego Room safely
                    val streamId = "s_${safeRoomId}_${safeUserId}"
                    if (hasAudioPerm) {
                        pendingPublishRoomId = safeRoomId
                        pendingPublishStreamId = streamId
                        pendingMicUnmute = true
                    }

                    if (!alreadyInRoom) {
                        engine.loginRoom(safeRoomId, user, roomConfig)
                    } else if (hasAudioPerm && loggedInRoomId == safeRoomId && !isPublishing.get()) {
                        try {
                            engine.startPublishingStream(streamId)
                            isPublishing.set(true)
                        } catch (pe: Throwable) {
                            Log.w(TAG, "startPublishingStream non-fatal warning: ${pe.message}")
                        }
                    }

                    Log.d(TAG, "Zego call initiated: safeRoomId=$safeRoomId, hasAudio=$hasAudioPerm, isVideo=$isVideo, isOutgoing=$isOutgoing")
                } catch (e: Throwable) {
                    Log.e(TAG, "Failed to start Zego room session: ${e.message}", e)
                    onError?.invoke(e.message ?: "خطأ في بدء جلسة Zego")
                }
            }
        }
    }

    /**
     * Mute / Unmute microphone
     */
    fun setMicrophoneMute(muted: Boolean) {
        isMicMuted.set(muted)
        try {
            zegoEngine?.muteMicrophone(muted)
        } catch (e: Throwable) {
            Log.w(TAG, "setMicrophoneMute error: ${e.message}")
        }
    }

    /**
     * Enable / Disable speakerphone
     */
    fun setSpeakerEnabled(context: Context, enabled: Boolean) {
        isSpeakerOn.set(enabled)
        try {
            zegoEngine?.setAudioRouteToSpeaker(enabled)
            if (zegoEngine != null) {
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                audioManager?.mode = android.media.AudioManager.MODE_IN_COMMUNICATION
                @Suppress("DEPRECATION")
                audioManager?.isSpeakerphoneOn = enabled
            }
        } catch (e: Throwable) {
            Log.w(TAG, "setSpeakerEnabled error: ${e.message}")
        }
    }

    /**
     * Enable / Disable camera
     */
    fun setCameraEnabled(enabled: Boolean) {
        isCameraOn.set(enabled)
        try {
            zegoEngine?.enableCamera(enabled)
        } catch (e: Throwable) {
            Log.w(TAG, "setCameraEnabled error: ${e.message}")
        }
    }

    /**
     * Switch front / back camera
     */
    fun switchCamera() {
        val next = !isFrontCamera.get()
        isFrontCamera.set(next)
        try {
            zegoEngine?.useFrontCamera(next)
        } catch (e: Throwable) {
            Log.w(TAG, "switchCamera error: ${e.message}")
        }
    }

    /**
     * Leave call and clean up all room session resources
     */
    fun endCall(context: Context? = null, roomId: String = currentRoomId) {
        pendingMicUnmute = false
        pendingPublishStreamId = null
        pendingPublishRoomId = null
        try {
            val engine = zegoEngine
            if (engine != null) {
                if (isPublishing.get()) {
                    engine.stopPublishingStream()
                    isPublishing.set(false)
                }
                engine.enableCamera(false)
                engine.muteMicrophone(true)
                val targetRoom = if (roomId.isNotEmpty()) sanitizeId(roomId, "room") else currentRoomId
                if (targetRoom.isNotEmpty()) {
                    engine.logoutRoom(targetRoom)
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Error leaving Zego room: ${e.message}")
        } finally {
            isPublishing.set(false)
            currentRoomId = ""
            loggedInRoomId = ""
            onCallConnected = null
            onCallDisconnected = null
            onRemoteUserJoined = null
            onRemoteUserLeft = null
        }
    }
}
