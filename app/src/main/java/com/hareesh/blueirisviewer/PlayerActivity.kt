package com.hareesh.blueirisviewer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {

    companion object {
        private const val CONNECT_WATCHDOG_MS = 15_000L
        private const val FROZEN_STREAM_WATCHDOG_MS = 10_000L
        private const val FRAME_CHECK_INTERVAL_MS = 2_000L
        private const val MAX_BACKOFF_SECONDS = 30
        private const val SETTINGS_HINT_DURATION_MS = 4_000L
    }

    private lateinit var playerView: PlayerView
    private lateinit var statusText: TextView
    private lateinit var hintText: TextView
    private lateinit var rootView: FrameLayout
    private var player: ExoPlayer? = null

    private val handler = Handler(Looper.getMainLooper())
    private var reconnectAttempt = 0
    private var pendingReconnect: Runnable? = null
    private var connectWatchdog: Runnable? = null
    private var countdownTicker: Runnable? = null
    private var frameWatchdog: Runnable? = null
    private var hintHide: Runnable? = null

    private var lastKnownPositionMs = 0L
    private var lastPositionAdvanceAt = 0L

    private lateinit var gestures: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)
        statusText = findViewById(R.id.status_text)
        hintText = findViewById(R.id.hint_text)
        rootView = findViewById(R.id.root)

        gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                openSettings()
            }
        })
        rootView.setOnTouchListener { _, ev ->
            gestures.onTouchEvent(ev)
            true
        }
    }

    override fun onStart() {
        super.onStart()
        hideSystemBars()
        startPlayback()
        showSettingsHint()
    }

    override fun onStop() {
        super.onStop()
        cancelAllTimers()
        releasePlayer()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // TV remote: MENU or long-press OK → Settings. Any D-pad press reveals the hint.
        when (keyCode) {
            KeyEvent.KEYCODE_MENU,
            KeyEvent.KEYCODE_BUTTON_MODE,
            KeyEvent.KEYCODE_SETTINGS -> {
                openSettings()
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                if (event.isLongPress) {
                    openSettings()
                    return true
                }
                showSettingsHint()
            }
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                showSettingsHint()
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun startPlayback() {
        val cfg = Prefs.load(this)
        if (cfg.url.isBlank()) {
            showStatus(getString(R.string.status_no_url))
            return
        }
        reconnectAttempt = 0
        connect(cfg)
    }

    private fun connect(cfg: StreamConfig) {
        cancelAllTimers()
        releasePlayer()
        showStatus(connectingMessage())

        val p = ExoPlayer.Builder(this).build()
        val source = RtspMediaSource.Factory()
            .setForceUseRtpTcp(cfg.preferTcp)
            .setTimeoutMs(10_000)
            .setDebugLoggingEnabled(BuildConfig.DEBUG)
            .createMediaSource(MediaItem.fromUri(cfg.url))

        p.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        reconnectAttempt = 0
                        cancelConnectWatchdog()
                        hideStatus()
                        startFrameWatchdog()
                    }
                    Player.STATE_ENDED -> scheduleReconnect("stream ended")
                    Player.STATE_BUFFERING, Player.STATE_IDLE -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                scheduleReconnect(error.errorCodeName)
            }
        })

        p.setMediaSource(source)
        p.playWhenReady = true
        p.prepare()
        playerView.player = p
        player = p

        startConnectWatchdog()
    }

    private fun startConnectWatchdog() {
        val r = Runnable { scheduleReconnect("watchdog: no STATE_READY") }
        connectWatchdog = r
        handler.postDelayed(r, CONNECT_WATCHDOG_MS)
    }

    private fun cancelConnectWatchdog() {
        connectWatchdog?.let { handler.removeCallbacks(it) }
        connectWatchdog = null
    }

    /**
     * Watches for "ExoPlayer in STATE_READY but the stream isn't advancing" —
     * i.e. server stopped sending RTP while the TCP socket stays open, which
     * ExoPlayer doesn't detect as an error. Polls the player's current
     * position and schedules a reconnect if it hasn't advanced in
     * FROZEN_STREAM_WATCHDOG_MS.
     */
    private fun startFrameWatchdog() {
        cancelFrameWatchdog()
        lastKnownPositionMs = player?.currentPosition ?: 0L
        lastPositionAdvanceAt = System.currentTimeMillis()

        val tick = object : Runnable {
            override fun run() {
                val p = player ?: return
                if (p.playbackState != Player.STATE_READY || !p.isPlaying) {
                    handler.postDelayed(this, FRAME_CHECK_INTERVAL_MS)
                    return
                }
                val now = System.currentTimeMillis()
                val pos = p.currentPosition
                if (pos != lastKnownPositionMs) {
                    lastKnownPositionMs = pos
                    lastPositionAdvanceAt = now
                } else if (now - lastPositionAdvanceAt >= FROZEN_STREAM_WATCHDOG_MS) {
                    scheduleReconnect("watchdog: stream frozen")
                    return
                }
                handler.postDelayed(this, FRAME_CHECK_INTERVAL_MS)
            }
        }
        frameWatchdog = tick
        handler.postDelayed(tick, FRAME_CHECK_INTERVAL_MS)
    }

    private fun cancelFrameWatchdog() {
        frameWatchdog?.let { handler.removeCallbacks(it) }
        frameWatchdog = null
    }

    private fun scheduleReconnect(reason: String) {
        android.util.Log.i("BlueIrisViewer", "reconnect scheduled: $reason")
        val cfg = Prefs.load(this)
        reconnectAttempt++
        val base = cfg.reconnectSeconds.coerceAtLeast(1)
        val waitSeconds = (base * reconnectAttempt).coerceAtMost(MAX_BACKOFF_SECONDS)

        cancelAllTimers()
        startCountdown(waitSeconds)

        val r = Runnable {
            val latest = Prefs.load(this)
            if (latest.url.isNotBlank()) connect(latest) else showStatus(getString(R.string.status_no_url))
        }
        pendingReconnect = r
        handler.postDelayed(r, waitSeconds * 1000L)
    }

    private fun startCountdown(seconds: Int) {
        var remaining = seconds
        val tick = object : Runnable {
            override fun run() {
                if (remaining <= 0) {
                    showStatus(connectingMessage())
                } else {
                    showStatus(getString(R.string.status_reconnecting, remaining, reconnectAttempt))
                    remaining--
                    handler.postDelayed(this, 1000L)
                }
            }
        }
        countdownTicker = tick
        handler.post(tick)
    }

    private fun cancelAllTimers() {
        pendingReconnect?.let { handler.removeCallbacks(it) }
        pendingReconnect = null
        countdownTicker?.let { handler.removeCallbacks(it) }
        countdownTicker = null
        cancelConnectWatchdog()
        cancelFrameWatchdog()
    }

    private fun releasePlayer() {
        player?.release()
        player = null
        playerView.player = null
    }

    private fun connectingMessage(): String =
        if (reconnectAttempt > 0)
            getString(R.string.status_connecting_attempt, reconnectAttempt)
        else
            getString(R.string.status_connecting)

    private fun showStatus(msg: String) {
        statusText.text = msg
        statusText.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        statusText.visibility = View.GONE
    }

    private fun showSettingsHint() {
        hintText.visibility = View.VISIBLE
        hintHide?.let { handler.removeCallbacks(it) }
        val r = Runnable { hintText.visibility = View.GONE }
        hintHide = r
        handler.postDelayed(r, SETTINGS_HINT_DURATION_MS)
    }

    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                )
        }
    }
}
