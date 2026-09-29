package com.almahala.netplay.ui.compose

/**
 * RoomNavState: Safe in-memory navigation state holder for video and stream rooms.
 * Prevents Jetpack Compose Navigation crashes caused by URL encoding, slashes (%2F),
 * hashes (#), and empty path segments in deep link URIs.
 */
object RoomNavState {
    var activeRoomId: String = ""
    var activeStreamUrl: String = ""
    var activeVideoId: String = ""
    var activeRoomTitle: String = ""
    var activeRoomCode: String = ""
    var isStealthMode: Boolean = false

    fun setRoom(
        roomId: String,
        streamUrl: String = "",
        videoId: String = "",
        roomTitle: String = "",
        roomCode: String = "",
        isStealth: Boolean = false
    ) {
        activeRoomId = roomId.trim()
        activeStreamUrl = streamUrl.trim()
        activeVideoId = videoId.trim()
        activeRoomTitle = roomTitle.trim()
        activeRoomCode = roomCode.trim()
        isStealthMode = isStealth
    }

    fun clear() {
        activeRoomId = ""
        activeStreamUrl = ""
        activeVideoId = ""
        activeRoomTitle = ""
        activeRoomCode = ""
        isStealthMode = false
    }
}
