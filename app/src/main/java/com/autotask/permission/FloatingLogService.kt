package com.autotask.permission

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class FloatingLogService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private lateinit var bodyContainer: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var foldButton: Button
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView
    private val logLines = ArrayDeque<String>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.CHINA)
    private var expanded = true
    private var appVisible = false
    private var expandedWidth = 0
    private var expandedHeight = 0
    private var collapsedWidth = 0
    private var collapsedHeight = 0
    private val activationPrefs by lazy { getSharedPreferences("activation", MODE_PRIVATE) }

    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_ACTIVITY_LOG -> {
                    val message = intent.getStringExtra(EXTRA_MESSAGE) ?: return
                    appendLog(message)
                }
                ACTION_APP_VISIBILITY -> {
                    appVisible = intent.getBooleanExtra(EXTRA_APP_VISIBLE, false)
                    updateOverlayVisibility()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        runCatching {
            createNotificationChannel()
            startForeground(NOTIFICATION_ID, buildNotification())
            registerLogReceiver()
        }.onFailure {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_FLOATING_ENABLED, true) == false) {
            removeOverlay()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.hasExtra(EXTRA_APP_VISIBLE) == true) {
            appVisible = intent.getBooleanExtra(EXTRA_APP_VISIBLE, false)
        }
        if (overlayView == null && Settings.canDrawOverlays(this)) {
            runCatching { showOverlay() }.onFailure { stopSelf() }
        }
        updateOverlayVisibility()
        return START_STICKY
    }

    override fun onDestroy() {
        removeOverlay()
        runCatching { unregisterReceiver(logReceiver) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerLogReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_ACTIVITY_LOG)
            addAction(ACTION_APP_VISIBILITY)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(logReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(logReceiver, filter)
        }
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        expandedWidth = min(dp(270), (screenWidth * 0.68f).toInt())
        expandedHeight = min(dp(260), (screenHeight * 0.30f).toInt())
        collapsedWidth = min(dp(104), (screenWidth * 0.28f).toInt())
        collapsedHeight = dp(36)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = roundedBackground(Color.argb(238, 18, 27, 48), dp(14).toFloat())
            elevation = dp(8).toFloat()
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.rgb(39, 135, 245))
            setPadding(dp(6), 0, dp(4), 0)
        }
        titleView = TextView(this).apply {
            text = "自动任务助手 · 活动日志"
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER_VERTICAL
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        header.addView(titleView, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
        ))
        foldButton = Button(this).apply {
            text = "折叠"
            textSize = 11f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = roundedBackground(Color.argb(70, 255, 255, 255), dp(8).toFloat())
            setPadding(dp(4), 0, dp(4), 0)
            setOnClickListener { toggleFold() }
        }
        header.addView(foldButton, LinearLayout.LayoutParams(dp(48), dp(26)))
        root.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, collapsedHeight
        ))

        bodyContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        root.addView(bodyContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
        }
        bodyContainer.addView(controls, LinearLayout.LayoutParams(dp(82), LinearLayout.LayoutParams.MATCH_PARENT))
        controls.addView(actionButton("介绍") {
            val mode = selectedFeature()
            appendLog("$mode：流程在后台录入，点开始后按后台配置执行")
            Toast.makeText(this, "$mode 的动作在后台流程编排里配置", Toast.LENGTH_LONG).show()
        })
        controls.addView(actionButton("开始") {
            val mode = selectedFeature()
            activationPrefs.edit().putBoolean(KEY_FEATURE_RUNNING, true).apply()
            appendLog("开始执行：$mode")
        })
        controls.addView(actionButton("暂停") {
            val mode = selectedFeature()
            activationPrefs.edit().putBoolean(KEY_FEATURE_RUNNING, false).apply()
            appendLog("已暂停：$mode")
        })
        controls.addView(actionButton("主页") {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            appendLog("已打开助手主页")
        })
        controls.addView(actionButton("清空") {
            logLines.clear()
            refreshLogs()
            appendLog("日志已清空")
        })
        controls.addView(actionButton("关闭") {
            activationPrefs.edit()
                .putBoolean(KEY_FLOATING_WINDOW_ENABLED, false)
                .apply()
            removeOverlay()
            stopSelf()
        })

        logView = TextView(this).apply {
            setTextColor(Color.rgb(225, 235, 255))
            textSize = 12f
            setLineSpacing(0f, 1.15f)
            setPadding(dp(10), dp(6), dp(8), dp(6))
        }
        logScroll = ScrollView(this).apply {
            background = roundedBackground(Color.argb(180, 7, 13, 27), dp(10).toFloat())
            addView(logView)
        }
        bodyContainer.addView(logScroll, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
        ).apply { marginStart = dp(8) })

        val params = WindowManager.LayoutParams(
            expandedWidth,
            expandedHeight,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - expandedWidth) / 2
            y = (screenHeight * 0.16f).toInt()
        }

        attachDragHandler(header, params)
        runCatching {
            windowManager.addView(root, params)
            overlayView = root
            overlayParams = params
            updateOverlayVisibility()
            appendLog("悬浮窗已启动")
        }.onFailure {
            overlayView = null
            overlayParams = null
            stopSelf()
        }
    }

    private fun toggleFold() {
        setFolded(expanded)
    }

    private fun setFolded(folded: Boolean) {
        expanded = !folded
        bodyContainer.visibility = if (expanded) View.VISIBLE else View.GONE
        titleView.text = if (expanded) "自动任务助手 · 活动日志" else "日志"
        foldButton.text = if (expanded) "折叠" else "展开"
        overlayParams?.let { params ->
            params.width = if (expanded) expandedWidth else collapsedWidth
            params.height = if (expanded) expandedHeight else collapsedHeight
            val metrics = resources.displayMetrics
            params.x = params.x.coerceIn(0, max(0, metrics.widthPixels - params.width))
            params.y = params.y.coerceIn(0, max(0, metrics.heightPixels - params.height))
            overlayView?.let { view ->
                if (::windowManager.isInitialized) {
                    runCatching { windowManager.updateViewLayout(view, params) }
                }
            }
        }
    }

    private fun updateOverlayVisibility() {
        overlayView?.visibility = if (appVisible) View.GONE else View.VISIBLE
    }

    private fun removeOverlay() {
        overlayView?.let { view ->
            if (::windowManager.isInitialized) {
                runCatching { windowManager.removeView(view) }
            }
        }
        overlayView = null
        overlayParams = null
    }

    private fun actionButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            textSize = 11f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = roundedBackground(Color.rgb(39, 135, 245), dp(9).toFloat())
            setPadding(dp(4), 0, dp(4), 0)
            setOnClickListener { action() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(32)
            ).apply { bottomMargin = dp(5) }
        }

    private fun selectedFeature(): String =
        activationPrefs.getString(KEY_SELECTED_FEATURE, null)?.takeIf { it.isNotBlank() } ?: "当前功能"

    private fun attachDragHandler(header: View, params: WindowManager.LayoutParams) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        header.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    overlayView?.let {
                        if (::windowManager.isInitialized) {
                            runCatching { windowManager.updateViewLayout(it, params) }
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun appendLog(message: String) {
        if (isCaptureNoise(message)) return
        val line = "[${timeFormat.format(Date())}] $message"
        if (logLines.lastOrNull() == line) return
        logLines.addLast(line)
        while (logLines.size > 80) logLines.removeFirst()
        refreshLogs()
    }

    private fun isCaptureNoise(message: String): Boolean =
        message.contains("控件上传失败") ||
            message.contains("已采集") ||
            message.contains("开始采集") ||
            message.contains("无障碍服务")

    private fun refreshLogs() {
        if (!::logView.isInitialized) return
        logView.text = logLines.joinToString("\n")
        logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun roundedBackground(color: Int, radius: Float) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
        }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "自动任务助手悬浮窗",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("自动任务助手正在运行")
            .setContentText("正在显示悬浮操作与中文活动日志")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        const val ACTION_ACTIVITY_LOG = "com.autotask.permission.ACTIVITY_LOG"
        const val ACTION_APP_VISIBILITY = "com.autotask.permission.APP_VISIBILITY"
        const val EXTRA_MESSAGE = "message"
        const val EXTRA_APP_VISIBLE = "app_visible"
        const val EXTRA_FLOATING_ENABLED = "floating_enabled"
        private const val CHANNEL_ID = "floating_log"
        private const val NOTIFICATION_ID = 1001
        private const val KEY_FLOATING_WINDOW_ENABLED = "floating_window_enabled"
        private const val KEY_SELECTED_FEATURE = "selected_feature"
        private const val KEY_FEATURE_RUNNING = "feature_running"
    }
}
