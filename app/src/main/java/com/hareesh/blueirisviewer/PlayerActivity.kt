package com.hareesh.blueirisviewer

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
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
        private const val CONNECT_WATCHDOG_MS = 15_000L  // reach STATE_READY within this, else retry
        private const val MAX_BACKOFF_SECONDS = 30
    }

    private lateinit var playerView: PlayerView
    private lateinit var statusText: TextView
    private lateinit var rootView: FrameLayout
    private var player: ExoPlayer? = null

    private val handler = Handler(Looper.getMainLooper())
    private var reconnectAttempt = 0
    private var pendingReconnect: Runnable? = null
    private var connectWatchdog: Runnable? = null
    private var countdownTicker: Runnable? = null

    private lateinit var gestures: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.player_view)
        statusText = findViewById(R.id.status_text)
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
    }

    override fun onStop() {
        super.onStop()
        cancelAllTimers()
        releasePlayer()
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
