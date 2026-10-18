package com.example.metflix.data.drm

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaDrm
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class WidevineLevel {
    L1,
    L3,
    UNSUPPORTED
}

data class DeviceHardwareProfile(
    val widevineLevel: WidevineLevel,
    val supportsHevc: Boolean,
    val supportsAv1: Boolean,
    val supportsEac3: Boolean,
    val supportsAc3: Boolean,
    val maxDecodableHeight: Int
)

@Singleton
class DeviceCapabilityManager @Inject constructor() {

    companion object {
        private const val TAG = "DeviceCapability"
        // Standard Widevine Modular DRM UUID: edef8ba9-79d6-4ace-a3c8-27dcd51d21ed
        private val WIDEVINE_UUID = UUID.fromString("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed")
    }

    val profile: DeviceHardwareProfile by lazy {
        detectHardwareCapabilities()
    }

    private fun detectHardwareCapabilities(): DeviceHardwareProfile {
        val widevine = detectWidevineLevel()
        val (hevc, av1) = detectVideoDecoders()
        val (ac3, eac3) = detectAudioDecoders()
        val maxHeight = if (widevine == WidevineLevel.L1) 2160 else if (widevine == WidevineLevel.L3) 720 else 1080

        Log.i(
            TAG,
            "Hardware Profile Detected -> Widevine: $widevine, HEVC: $hevc, AV1: $av1, AC3: $ac3, EAC3: $eac3, MaxHeight: $maxHeight"
        )

        return DeviceHardwareProfile(
            widevineLevel = widevine,
            supportsHevc = hevc,
            supportsAv1 = av1,
            supportsEac3 = eac3,
            supportsAc3 = ac3,
            maxDecodableHeight = maxHeight
        )
    }

    private fun detectWidevineLevel(): WidevineLevel {
        return try {
            if (!MediaDrm.isCryptoSchemeSupported(WIDEVINE_UUID)) {
                WidevineLevel.UNSUPPORTED
            } else {
                val mediaDrm = MediaDrm(WIDEVINE_UUID)
                val level = mediaDrm.getPropertyString("securityLevel")
                mediaDrm.close()

                when {
                    "L1".equals(level, ignoreCase = true) -> WidevineLevel.L1
                    "L3".equals(level, ignoreCase = true) -> WidevineLevel.L3
                    else -> WidevineLevel.UNSUPPORTED
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query Widevine DRM: ${e.message}")
            WidevineLevel.UNSUPPORTED
        }
    }

    private fun detectVideoDecoders(): Pair<Boolean, Boolean> {
        var hasHevc = false
        var hasAv1 = false

        try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (codecInfo in codecList.codecInfos) {
                if (codecInfo.isEncoder) continue
                for (type in codecInfo.supportedTypes) {
                    if (type.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)) {
                        hasHevc = true
                    }
                    if (type.equals("video/av01", ignoreCase = true)) {
                        hasAv1 = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking video decoders: ${e.message}")
        }

        return Pair(hasHevc, hasAv1)
    }

    private fun detectAudioDecoders(): Pair<Boolean, Boolean> {
        var hasAc3 = false
        var hasEac3 = false

        try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            for (codecInfo in codecList.codecInfos) {
                if (codecInfo.isEncoder) continue
                for (type in codecInfo.supportedTypes) {
                    if (type.equals(MediaFormat.MIMETYPE_AUDIO_AC3, ignoreCase = true)) {
                        hasAc3 = true
                    }
                    if (type.equals(MediaFormat.MIMETYPE_AUDIO_EAC3, ignoreCase = true)) {
                        hasEac3 = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error checking audio decoders: ${e.message}")
        }

        return Pair(hasAc3, hasEac3)
    }

    /**
     * Determines if a channel can be played smoothly based on device hardware and DRM restrictions.
     */
    fun isStreamPlayable(
        drmRequired: String?,
        videoCodec: String? = null,
        audioCodec: String? = null
    ): Boolean {
        // DRM check
        when (drmRequired?.uppercase()) {
            "WIDEVINE_L1", "L1" -> {
                if (profile.widevineLevel != WidevineLevel.L1) return false
            }
            "WIDEVINE_L3", "L3" -> {
                if (profile.widevineLevel == WidevineLevel.UNSUPPORTED) return false
            }
        }

        // Video codec check
        if ("HEVC".equals(videoCodec, ignoreCase = true) || "H265".equals(videoCodec, ignoreCase = true)) {
            if (!profile.supportsHevc) return false
        }
        if ("AV1".equals(videoCodec, ignoreCase = true)) {
            if (!profile.supportsAv1) return false
        }

        return true
    }
}
