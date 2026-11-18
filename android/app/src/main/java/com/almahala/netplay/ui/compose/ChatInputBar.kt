package com.almahala.netplay.ui.compose

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInputBar(
    modifier: Modifier = Modifier,
    onSendText: (String) -> Unit = {},
    onTyping: () -> Unit = {},
    onSendFile: (Uri, String) -> Unit = { _, _ -> },
    onSendEditedPhoto: (Uri, String, Boolean) -> Unit = { _, _, _ -> },
    onSendLocation: (Double, Double, String, String, Int) -> Unit = { _, _, _, _, _ -> },
    onSendVoiceNote: (Uri, Int) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var showAttachments by remember { mutableStateOf(false) }

    // Voice Note Recording State
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableIntStateOf(0) }
    var mediaRecorderRef by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordedFileRef by remember { mutableStateOf<File?>(null) }

    fun stopAndReleaseRecorder(saveAndSend: Boolean) {
        val rec = mediaRecorderRef
        val outFile = recordedFileRef
        val dur = recordingSeconds.coerceAtLeast(1)
        mediaRecorderRef = null
        isRecording = false
        recordingSeconds = 0
        try {
            rec?.stop()
        } catch (_: Exception) {}
        try {
            rec?.reset()
            rec?.release()
        } catch (_: Exception) {}

        if (saveAndSend && outFile != null && outFile.exists() && outFile.length() > 512L) {
            onSendVoiceNote(Uri.fromFile(outFile), dur)
        } else {
            try { outFile?.delete() } catch (_: Exception) {}
            if (saveAndSend) {
                Toast.makeText(context, "التسجيل الصوتي قصير جداً", Toast.LENGTH_SHORT).show()
            }
        }
        recordedFileRef = null
    }

    fun startVoiceRecording() {
        if (isRecording) return
        try {
            val dir = File(context.cacheDir, "voice_notes").apply { mkdirs() }
            val outFile = File(dir, "voice_${System.currentTimeMillis()}.m4a")
            @Suppress("DEPRECATION")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(96000)
                setAudioSamplingRate(44100)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorderRef = recorder
            recordedFileRef = outFile
            recordingSeconds = 0
            isRecording = true
        } catch (e: Exception) {
            try { mediaRecorderRef?.release() } catch (_: Exception) {}
            mediaRecorderRef = null
            recordedFileRef = null
            isRecording = false
            Toast.makeText(context, "تعذر تشغيل الميكروفون للتسجيل", Toast.LENGTH_SHORT).show()
        }
    }

    val recordAudioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecording()
        } else {
            Toast.makeText(context, "يرجى منح إذن الميكروفون للتسجيل الصوتي", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                delay(1000L)
                if (isRecording) recordingSeconds++
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaRecorderRef?.stop()
            } catch (_: Exception) {}
            try {
                mediaRecorderRef?.release()
            } catch (_: Exception) {}
            mediaRecorderRef = null
        }
    }

    // Image editing dialog state
    var selectedImageForEditing by remember { mutableStateOf<Uri?>(null) }
    // Location sharing dialog state
    var showLocationDialog by remember { mutableStateOf(false) }

    // Launcher for general files & media
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: "*/*"
            if (mimeType.startsWith("image/")) {
                selectedImageForEditing = uri
            } else {
                onSendFile(uri, mimeType)
            }
            showAttachments = false
        }
    }

    // Direct Image Picker with Image Editor
    val photoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            selectedImageForEditing = uri
            showAttachments = false
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val rotation by animateFloatAsState(
        targetValue = if (showAttachments) 45f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "plus_rotation"
    )

    // 1. Image Editor Modal if user picked a photo
    if (selectedImageForEditing != null) {
        ImageEditorDialog(
            imageUri = selectedImageForEditing!!,
            onDismiss = { selectedImageForEditing = null },
            onSendEditedImage = { editedUri, caption, isViewTwice ->
                selectedImageForEditing = null
                onSendEditedPhoto(editedUri, caption, isViewTwice)
            }
        )
    }

    // 2. Real Location Dialog
    if (showLocationDialog) {
        RealLocationShareDialog(
            onDismiss = { showLocationDialog = false },
            onSendLocation = { lat, lng, addr, dist, dur ->
                showLocationDialog = false
                onSendLocation(lat, lng, addr, dist, dur)
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFC))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (isRecording) {
            val pulseTransition = rememberInfiniteTransition(label = "rec_pulse")
            val dotAlpha by pulseTransition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "rec_alpha"
            )
            val mins = recordingSeconds / 60
            val secs = recordingSeconds % 60
            val timeStr = String.format("%02d:%02d", mins, secs)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFFEF2F2), RoundedCornerShape(24.dp))
                    .border(1.dp, Color(0xFFFECACA), RoundedCornerShape(24.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Cancel button
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color.White, CircleShape)
                        .border(1.dp, Color(0xFFFECACA), CircleShape)
                        .clickable { stopAndReleaseRecorder(saveAndSend = false) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "إلغاء التسجيل",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(19.dp)
                    )
                }

                // Pulsing red indicator & duration
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .alpha(dotAlpha)
                            .background(Color(0xFFEF4444), CircleShape)
                    )
                    Text(
                        text = "جاري التسجيل الصوتي • $timeStr",
                        fontFamily = TajawalFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF991B1B)
                    )
                }

                // Send Voice Note button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2563EB), CircleShape)
                        .clickable { stopAndReleaseRecorder(saveAndSend = true) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "إرسال التسجيل",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. Attachment (+) Button
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(Color.White, CircleShape)
                        .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                        .clickable {
                            coroutineScope.launch {
                                if (showAttachments) {
                                    sheetState.hide()
                                    showAttachments = false
                                } else {
                                    showAttachments = true
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "مرفقات",
                        tint = Color(0xFF2563EB),
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(rotation)
                    )
                }

                // 2. Center Text Field & Actions
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White, RoundedCornerShape(24.dp))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(24.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            onTyping()
                        },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(
                            fontSize = 15.sp,
                            fontFamily = TajawalFontFamily,
                            color = Color(0xFF0F172A)
                        ),
                        cursorBrush = SolidColor(Color(0xFF2563EB)),
                        decorationBox = { innerTextField ->
                            if (text.isEmpty()) {
                                Text(
                                    text = "اكتب رسالة...",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 14.sp,
                                    fontFamily = TajawalFontFamily
                                )
                            }
                            innerTextField()
                        }
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Quick photo / camera action with built-in Editor
                    IconButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = "التقاط صورة",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                // 3. Send or Mic Button
                var lastSendTimestamp by remember { mutableLongStateOf(0L) }
                if (text.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2563EB), CircleShape)
                            .clickable {
                                val now = System.currentTimeMillis()
                                if (now - lastSendTimestamp > 350L) {
                                    lastSendTimestamp = now
                                    val trimmed = text.trim()
                                    if (trimmed.isNotEmpty()) {
                                        onSendText(trimmed)
                                        text = ""
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "إرسال",
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White, CircleShape)
                            .border(1.dp, Color(0xFFE2E8F0), CircleShape)
                            .clickable {
                                val hasPerm = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (hasPerm) {
                                    startVoiceRecording()
                                } else {
                                    recordAudioPermLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "تسجيل صوتي",
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    if (showAttachments) {
        ModalBottomSheet(
            onDismissRequest = { showAttachments = false },
            sheetState = sheetState,
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .background(Color(0xFFCBD5E1), CircleShape)
                )
            }
        ) {
            AttachmentGrid(
                onPickPhoto = {
                    showAttachments = false
                    photoPickerLauncher.launch("image/*")
                },
                onPickDocument = {
                    showAttachments = false
                    fileLauncher.launch("*/*")
                },
                onPickAudio = {
                    showAttachments = false
                    fileLauncher.launch("audio/*")
                },
                onOpenLocation = {
                    showAttachments = false
                    showLocationDialog = true
                },
                onDismiss = { showAttachments = false }
            )
        }
    }
}

@Composable
fun AttachmentGrid(
    onPickPhoto: () -> Unit = {},
    onPickDocument: () -> Unit = {},
    onPickAudio: () -> Unit = {},
    onOpenLocation: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Text(
            text = "إرفاق ومشاركة",
            fontFamily = TajawalFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = Color(0xFF0F172A),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            AttachmentIcon("معرض الصور", Color(0xFFEEF2FF), Color(0xFF4F46E5), Icons.Default.InsertPhoto) {
                onPickPhoto()
            }
            AttachmentIcon("الكاميرا", Color(0xFFFEF3C7), Color(0xFFD97706), Icons.Default.CameraAlt) {
                onPickPhoto()
            }
            AttachmentIcon("مستند وملف", Color(0xFFECFDF5), Color(0xFF059669), Icons.Default.InsertDriveFile) {
                onPickDocument()
            }
            AttachmentIcon("تسجيل صوتي", Color(0xFFEFF6FF), Color(0xFF2563EB), Icons.Default.Mic) {
                onPickAudio()
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            AttachmentIcon("الموقع الجغرافي", Color(0xFFFDF2F8), Color(0xFFDB2777), Icons.Default.LocationOn) {
                onOpenLocation()
            }
            AttachmentIcon("جهة اتصال", Color(0xFFF0FDF4), Color(0xFF16A34A), Icons.Default.Person) {
                Toast.makeText(context, "مشاركة جهة اتصال", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
            AttachmentIcon("تصويت سريع", Color(0xFFF5F3FF), Color(0xFF7C3AED), Icons.Default.Poll) {
                Toast.makeText(context, "إنشاء استطلاع رأي", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
            AttachmentIcon("غرفة لعب", Color(0xFFFFF1F2), Color(0xFFE11D48), Icons.Default.SportsEsports) {
                Toast.makeText(context, "دعوة لغرفة اللعب", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
fun AttachmentIcon(
    label: String,
    bgTint: Color,
    iconTint: Color,
    icon: ImageVector,
    onClick: () -> Unit = {}
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(bgTint, CircleShape)
                .border(1.dp, iconTint.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = Color(0xFF334155),
            fontFamily = TajawalFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
