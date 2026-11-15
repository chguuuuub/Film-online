package com.cine.player

import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var urlInput: EditText
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
            setBackgroundColor(0xFF101318.toInt())
        }

        val title = TextView(this).apply {
            text = "Cine4K 在线播放器"
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
        }
        root.addView(title, matchWrap())

        val subtitle = TextView(this).apply {
            text = "播放你有权访问的 MP4、HLS (.m3u8) 或 DASH (.mpd) 视频地址"
            textSize = 13f
            setTextColor(0xFFB8C0CC.toInt())
            setPadding(0, 8, 0, 14)
        }
        root.addView(subtitle, matchWrap())

        urlInput = EditText(this).apply {
            hint = "粘贴视频直链（https://…）"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF9AA4B2.toInt())
            setPadding(16, 14, 16, 14)
            textSize = 16f
            minHeight = 64
            gravity = android.view.Gravity.CENTER_VERTICAL
            setBackgroundColor(0xFF252B34.toInt())
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_GO) {
                    playUrl()
                    true
                } else false
            }
        }
        root.addView(urlInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 68))

        val playButton = Button(this).apply {
            text = "播放"
            setOnClickListener { playUrl() }
        }
        root.addView(playButton, matchWrap())

        statusView = TextView(this).apply {
            text = "等待输入视频地址"
            textSize = 13f
            setTextColor(0xFFB8C0CC.toInt())
            setPadding(0, 8, 0, 8)
        }
        root.addView(statusView, matchWrap())

        playerView = PlayerView(this).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        }
        root.addView(
            playerView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        setContentView(root)
    }

    private fun playUrl() {
        val raw = urlInput.text.toString().trim()
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
        if (raw.isBlank() || uri == null || (uri.scheme != "https" && uri.scheme != "http") || uri.host.isNullOrBlank()) {
            statusView.text = "地址无效：请输入完整的 http/https 视频直链"
            return
        }

        statusView.text = "正在连接视频源…"
        val exoPlayer = player ?: ExoPlayer.Builder(this).build().also { created ->
            created.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    statusView.text = when (playbackState) {
                        Player.STATE_BUFFERING -> "正在缓冲…"
                        Player.STATE_READY -> if (created.isPlaying) "正在播放" else "已就绪，按播放键开始"
                        Player.STATE_ENDED -> "播放结束"
                        Player.STATE_IDLE -> "播放器待机"
                        else -> statusView.text
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    statusView.text = "播放失败：视频地址不可用、格式不支持或服务器限制了访问"
                }
            })
            player = created
            playerView.player = created
        }

        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    )

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
