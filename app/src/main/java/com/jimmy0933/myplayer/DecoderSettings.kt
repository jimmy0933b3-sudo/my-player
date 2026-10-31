@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.jimmy0933.myplayer

import android.content.Context
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

enum class VideoDecoderPreference {
    SYSTEM, HARDWARE_FIRST, SOFTWARE_FIRST,
}

data class DecoderPreferences(
    val video: VideoDecoderPreference = VideoDecoderPreference.SYSTEM,
    val softwareAudioFirst: Boolean = false,
    val decoderFallback: Boolean = true,
    val recoverOnPlaybackError: Boolean = true,
)

/** Local settings only: no elevated storage permissions or native bundled codecs. */
object DecoderSettings {
    private const val FILE = "my_player_decoder_settings"

    fun load(context: Context): DecoderPreferences {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val video = runCatching {
            VideoDecoderPreference.valueOf(
                prefs.getString("video_mode", VideoDecoderPreference.SYSTEM.name).orEmpty(),
            )
        }.getOrDefault(VideoDecoderPreference.SYSTEM)
        return DecoderPreferences(
            video = video,
            softwareAudioFirst = prefs.getBoolean("software_audio", false),
            decoderFallback = prefs.getBoolean("decoder_fallback", true),
            recoverOnPlaybackError = prefs.getBoolean("recover_error", true),
        )
    }

    fun save(context: Context, settings: DecoderPreferences) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString("video_mode", settings.video.name)
            .putBoolean("software_audio", settings.softwareAudioFirst)
            .putBoolean("decoder_fallback", settings.decoderFallback)
            .putBoolean("recover_error", settings.recoverOnPlaybackError)
            .apply()
    }

    fun renderers(context: Context, settings: DecoderPreferences): DefaultRenderersFactory {
        val videoSelector = settings.video
        val selector = MediaCodecSelector { mimeType, secure, tunneling ->
            val codecs = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling)
            val preference = if (mimeType.startsWith("audio/")) {
                if (settings.softwareAudioFirst) VideoDecoderPreference.SOFTWARE_FIRST
                else VideoDecoderPreference.SYSTEM
            } else videoSelector
            // Reorder existing codecs, never claim an unavailable software codec
            // exists. Secure/tunneling requirements remain handled by Media3.
            if (preference == VideoDecoderPreference.SYSTEM) codecs else codecs.sortedWith(
                compareByDescending { codec ->
                    decoderPriority(
                        preference, codec.hardwareAccelerated, codec.softwareOnly,
                    )
                },
            )
        }
        return DefaultRenderersFactory(context)
            .setMediaCodecSelector(selector)
            .setEnableDecoderFallback(settings.decoderFallback)
    }
}

/**
 * Stable relative ordering; unavailable decoders are never synthesized.
 * Media3 may use a lower-priority decoder if fallback is enabled.
 */
internal fun decoderPriority(
    mode: VideoDecoderPreference,
    hardware: Boolean,
    software: Boolean,
): Int = when (mode) {
    VideoDecoderPreference.SYSTEM -> 0
    VideoDecoderPreference.HARDWARE_FIRST ->
        if (hardware) 2 else if (software) 0 else 1
    VideoDecoderPreference.SOFTWARE_FIRST ->
        if (software) 2 else if (hardware) 0 else 1
}
