package com.cine.player

import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.app.Dialog
import android.view.WindowManager
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TrackSelectionDialogBuilder
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var urlInput: EditText
    private lateinit var searchInput: AutoCompleteTextView
    private var searchResults: List<Pair<String, String>> = emptyList()
    private lateinit var statusView: TextView
    private lateinit var resultsBox: LinearLayout
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 16, 18, 12)
            setBackgroundColor(0xFF101318.toInt())
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brand = TextView(this).apply {
            text = "G"; textSize = 38f; setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFF65D6AD.toInt()); setPadding(0, 0, 12, 0)
        }
        header.addView(brand, LinearLayout.LayoutParams(52, ViewGroup.LayoutParams.WRAP_CONTENT))
        val title = TextView(this).apply {
            text = "G 在线影视"; textSize = 23f; setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
        }
        header.addView(title, matchWrap())
        root.addView(header, matchWrap())

        root.addView(sectionLabel("片名 / 关键词搜索"), matchWrap())
        searchInput = AutoCompleteTextView(this).apply {
            hint = "输入电影名称或关键词"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            threshold = 1
            textSize = 22f
            minHeight = dp(68)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(14))
            setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF9AA4B2.toInt())
            setBackgroundColor(0xFF252B34.toInt())
            setDropDownBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFF252B34.toInt()))
            dropDownVerticalOffset = 6
            dropDownHeight = ViewGroup.LayoutParams.WRAP_CONTENT
            setOnItemClickListener { _, _, position, _ ->
                val selected = searchResults.getOrNull(position)
                if (selected != null) {
                    setText(selected.first, false)
                    urlInput.setText(selected.second)
                    playUrl()
                }
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) { searchArchive(); true } else false
            }
        }
        root.addView(searchInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(70)).apply {
            topMargin = dp(4)
            bottomMargin = dp(8)
        })
        root.addView(Button(this).apply {
            text = "搜索可播放影片"
            setOnClickListener { searchArchive() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(8)
        })

        resultsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(resultsBox, matchWrap())

        root.addView(TextView(this).apply {
            text = "──────────  或输入播放链接  ──────────"; textSize = 12f
            gravity = Gravity.CENTER; setTextColor(0xFF9AA4B2.toInt()); setPadding(0, 10, 0, 6)
        }, matchWrap())
        urlInput = makeInput("粘贴 MP4 / M3U8 / MPD 网络视频链接")
        urlInput.imeOptions = EditorInfo.IME_ACTION_GO
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) { playUrl(); true } else false
        }
        urlInput.textSize = 20f
        urlInput.minHeight = dp(70)
        root.addView(urlInput, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)).apply {
            topMargin = dp(6)
            bottomMargin = dp(8)
        })
        root.addView(Button(this).apply {
            text = "播放链接"; setOnClickListener { playUrl() }
        }, matchWrap())

        statusView = TextView(this).apply {
            text = "可搜索 Internet Archive 公共影片，也可直接输入网络视频链接。"
            textSize = 13f; setTextColor(0xFFB8C0CC.toInt()); setPadding(0, 8, 0, 8)
        }
        root.addView(statusView, matchWrap())
        playerView = PlayerView(this).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        }
        root.addView(Button(this).apply {
            text = "选择播放分辨率"
            textSize = 16f
            setOnClickListener { showResolutionSelector() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
            topMargin = dp(6)
            bottomMargin = dp(4)
        })
        root.addView(Button(this).apply {
            text = "⛶  全屏播放"
            textSize = 16f
            setOnClickListener { showFullscreenPlayer() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
            topMargin = dp(6)
            bottomMargin = dp(8)
        })
        root.addView(playerView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(240)))
        setContentView(ScrollView(this).apply { isFillViewport = true; addView(root) })
    }

    private fun sectionLabel(value: String) = TextView(this).apply {
        text = value; textSize = 15f; setTypeface(null, android.graphics.Typeface.BOLD)
        setTextColor(0xFFFFFFFF.toInt()); setPadding(0, 14, 0, 6)
    }

    private fun makeInput(hintText: String) = EditText(this).apply {
        hint = hintText; setSingleLine(true)
        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
        setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF9AA4B2.toInt())
        setPadding(16, 12, 16, 12); textSize = 16f; minHeight = 58
        gravity = Gravity.CENTER_VERTICAL; setBackgroundColor(0xFF252B34.toInt())
    }

    private fun searchArchive() {
        val query = searchInput.text.toString().trim()
        if (query.isBlank()) { statusView.text = "请先输入片名或关键词"; return }
        resultsBox.removeAllViews()
        statusView.text = "正在搜索公开影片，并检查视频文件…"
        executor.execute {
            try {
                val playable = findPlayableArchiveItems(query)
                runOnUiThread {
                    searchResults = playable
                    if (playable.isEmpty()) {
                        searchInput.setAdapter(ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, emptyList<String>()))
                        statusView.text = "没有找到通过初步链接检测的影片。可换关键词，或直接输入视频链接。"
                    } else {
                        val titles = playable.map { it.first }
                        val dropdownAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, titles) {
                            override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                                val item = (convertView as? TextView) ?: TextView(this@MainActivity)
                                item.text = getItem(position)
                                item.textSize = 18f
                                item.setTextColor(0xFF17202A.toInt())
                                item.setPadding(dp(18), dp(14), dp(18), dp(14))
                                item.minHeight = dp(54)
                                item.gravity = Gravity.CENTER_VERTICAL
                                item.setBackgroundColor(0xFFF4F6F8.toInt())
                                return item
                            }
                        }
                        searchInput.setDropDownBackgroundDrawable(android.graphics.drawable.ColorDrawable(0xFFF4F6F8.toInt()))
                        searchInput.setAdapter(dropdownAdapter)
                        searchInput.setText(query, false)
                        statusView.text = "找到 " + playable.size + " 个可尝试播放的公开影片，点开输入框下方列表即可播放。"
                        searchInput.requestFocus()
                        searchInput.showDropDown()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { statusView.text = "搜索失败：网络连接异常或服务暂不可用。请稍后重试。" }
            }
        }
    }

    private fun findPlayableArchiveItems(query: String): List<Pair<String, String>> {
        val q = "title:($query) AND mediatype:movies"
        val encoded = URLEncoder.encode(q, "UTF-8")
        val searchUrl = "https://archive.org/advancedsearch.php?q=$encoded&fl%5B%5D=identifier&fl%5B%5D=title&rows=8&page=1&output=json"
        val docs = JSONObject(httpGet(searchUrl)).getJSONObject("response").getJSONArray("docs")
        val pool = Executors.newFixedThreadPool(5)
        try {
            val tasks = (0 until minOf(docs.length(), 8)).map { index ->
                java.util.concurrent.Callable {
                    val doc = docs.getJSONObject(index)
                    val identifier = doc.optString("identifier")
                    val title = doc.optString("title", identifier)
                    if (identifier.isBlank()) return@Callable null
                    try {
                        val metadata = JSONObject(httpGet("https://archive.org/metadata/$identifier"))
                        val files = metadata.optJSONArray("files") ?: return@Callable null
                        var playableUrl: String? = null
                        for (j in 0 until files.length()) {
                            val file = files.optJSONObject(j) ?: continue
                            val name = file.optString("name")
                            val format = file.optString("format")
                            if (name.isBlank() || name.contains("__ia_thumb")) continue
                            if (!name.lowercase().endsWith(".mp4") &&
                                !format.contains("MPEG4", true) && !format.contains("h.264", true)) continue
                            val encodedName = name.split("/").joinToString("/") {
                                URLEncoder.encode(it, "UTF-8").replace("+", "%20")
                            }
                            playableUrl = "https://archive.org/download/$identifier/$encodedName"
                            break
                        }
                        if (playableUrl != null) title to playableUrl!! else null
                    } catch (_: Exception) { null }
                }
            }
            // Fetch metadata concurrently; don't wait for slow per-file range probes.
            return pool.invokeAll(tasks, 12, TimeUnit.SECONDS)
                .mapNotNull { future -> if (future.isCancelled) null else runCatching { future.get() }.getOrNull() }
                .take(6)
        } finally {
            pool.shutdownNow()
        }
    }

    private fun httpGet(address: String): String {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 6000; connection.readTimeout = 7000
        connection.setRequestProperty("User-Agent", "GOnlinePlayer/1.0")
        return try { connection.inputStream.bufferedReader().use { it.readText() } }
        finally { connection.disconnect() }
    }

    private fun isUrlReachable(address: String): Boolean {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 7000; connection.readTimeout = 7000
        connection.instanceFollowRedirects = true
        connection.requestMethod = "GET"
        connection.setRequestProperty("Range", "bytes=0-0")
        connection.setRequestProperty("User-Agent", "GOnlinePlayer/1.0")
        return try { connection.responseCode in 200..299 } finally { connection.disconnect() }
    }

    private fun playUrl() {
        val raw = urlInput.text.toString().trim()
        val uri = runCatching { Uri.parse(raw) }.getOrNull()
        if (raw.isBlank() || uri == null || (uri.scheme != "https" && uri.scheme != "http") || uri.host.isNullOrBlank()) {
            statusView.text = "地址无效：请输入完整的 http/https 视频直链"; return
        }
        statusView.text = "正在连接视频源…"
        val exoPlayer = player ?: ExoPlayer.Builder(this).build().also { created ->
            created.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    statusView.text = when (playbackState) {
                        Player.STATE_BUFFERING -> "正在缓冲…"
                        Player.STATE_READY -> if (created.isPlaying) "正在播放" else "已就绪"
                        Player.STATE_ENDED -> "播放结束"
                        Player.STATE_IDLE -> "播放器待机"
                        else -> statusView.text
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    statusView.text = "播放失败：源可能失效、格式不支持或服务器有限制。可尝试其他结果。"
                }
            })
            player = created; playerView.player = created
        }
        exoPlayer.setMediaItem(MediaItem.fromUri(uri)); exoPlayer.prepare(); exoPlayer.playWhenReady = true
    }

    private fun showResolutionSelector() {
        val activePlayer = player
        if (activePlayer == null) {
            statusView.text = "请先播放视频，再选择分辨率。"
            return
        }
        TrackSelectionDialogBuilder(this, "选择视频分辨率", activePlayer, C.TRACK_TYPE_VIDEO)
            .build()
            .show()
    }

    private fun showFullscreenPlayer() {
        val activePlayer = player
        if (activePlayer == null) {
            statusView.text = "请先播放视频，再进入全屏。"
            return
        }
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val fullPlayerView = PlayerView(this).apply {
            useController = true
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            player = activePlayer
            setBackgroundColor(0xFF000000.toInt())
        }
        dialog.setContentView(fullPlayerView)
        dialog.setOnDismissListener {
            fullPlayerView.player = null
            playerView.player = activePlayer
        }
        dialog.show()
        dialog.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun matchWrap() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    override fun onStop() { super.onStop(); player?.pause() }

    override fun onDestroy() {
        playerView.player = null; player?.release(); player = null
        executor.shutdownNow(); super.onDestroy()
    }
}
