package com.hareesh.blueirisviewer

import android.content.Context
import android.content.SharedPreferences

data class StreamConfig(
    val url: String,
    val preferTcp: Boolean,
    val reconnectSeconds: Int,
    val autoStart: Boolean,
)

object Prefs {
    private const val FILE = "blueiris-viewer"
    private const val KEY_URL = "url"
    private const val KEY_TRANSPORT_TCP = "transport_tcp"
    private const val KEY_RECONNECT = "reconnect_seconds"
    private const val KEY_AUTOSTART = "autostart"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(ctx: Context): StreamConfig {
        val s = sp(ctx)
        return StreamConfig(
            url = s.getString(KEY_URL, "").orEmpty(),
            preferTcp = s.getBoolean(KEY_TRANSPORT_TCP, true),
            reconnectSeconds = s.getInt(KEY_RECONNECT, 3),
            autoStart = s.getBoolean(KEY_AUTOSTART, true),
        )
    }

    fun save(ctx: Context, cfg: StreamConfig) {
        sp(ctx).edit()
            .putString(KEY_URL, cfg.url.trim())
            .putBoolean(KEY_TRANSPORT_TCP, cfg.preferTcp)
            .putInt(KEY_RECONNECT, cfg.reconnectSeconds.coerceIn(1, 300))
            .putBoolean(KEY_AUTOSTART, cfg.autoStart)
            .apply()
    }
}
