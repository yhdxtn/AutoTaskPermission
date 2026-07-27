package com.autotask.permission

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.TargetApi
import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.accessibility.AccessibilityManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var rootContainer: LinearLayout
    private lateinit var overlaySwitch: Switch
    private lateinit var accessibilitySwitch: Switch
    private lateinit var homePage: ScrollView
    private lateinit var discoverPage: ScrollView
    private lateinit var profilePage: ScrollView
    private lateinit var navHome: TextView
    private lateinit var navDiscover: TextView
    private lateinit var navProfile: TextView
    private lateinit var taskStatusText: TextView

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activationClient = ActivationClient(BuildConfig.ACTIVATION_API_BASE_URL)
    private val activationPrefs by lazy { getSharedPreferences("activation", MODE_PRIVATE) }
    private var updatingSwitches = false
    private var activationReady = false
    private var activationDialog: AlertDialog? = null

    private val promotionItems = listOf(
        FeatureItem("基础观看", "B"), FeatureItem("垂直观看", "V"),
        FeatureItem("点赞浏览", "L"), FeatureItem("普通浏览", "P"),
        FeatureItem("智能浏览", "AI"), FeatureItem("智能宣传", "S"),
        FeatureItem("评论浏览", "C"), FeatureItem("喜欢浏览", "H"),
        FeatureItem("直播浏览", "LIVE"), FeatureItem("行业搜索", "Q"),
        FeatureItem("同城宣传", "T"), FeatureItem("万能推广", "M"),
        FeatureItem("粉丝浏览", "F"), FeatureItem("智能推广", "AI"),
        FeatureItem("直播宣传", "Z")
    )
    private val volcanoItems = listOf(
        FeatureItem("基础观看", "B"), FeatureItem("粉丝观看", "F"),
        FeatureItem("智能浏览", "AI")
    )
    private val toolItems = listOf(
        FeatureItem("直播氛围", "L"), FeatureItem("清理关注", "X"),
        FeatureItem("AI录制", "AI"), FeatureItem("取消喜欢", "U"),
        FeatureItem("用户回关", "R"), FeatureItem("用户回访", "V")
    )
    private val ttkItems = listOf(
        FeatureItem("普通观看", "P"), FeatureItem("垂直观看", "V"),
        FeatureItem("精准宣传", "J"), FeatureItem("榜单私信", "M")
    )
    private val redBookItems = listOf(FeatureItem("万能观看", "B"))
    private val wxItems = listOf(
        FeatureItem("视频浏览", "V"), FeatureItem("同城宣传", "T"),
        FeatureItem("基础观看", "B")
    )
    private val kuaishouItems = listOf(
        FeatureItem("行业推广", "P"), FeatureItem("评论浏览", "C"),
        FeatureItem("粉丝浏览", "F")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        rootContainer.visibility = View.INVISIBLE
        applySystemBarInsets()
        buildHomeSections()
        buildProfileActions()
        setupPermissions()
        setupNavigation()

        findViewById<TextView>(R.id.deviceInfo).text =
            "设备：${Build.MANUFACTURER}\n型号：${Build.MODEL}"
        checkActivation()
    }

    override fun onResume() {
        super.onResume()
        if (activationReady) {
            updatePermissionStates()
            updateTaskStatus()
            updateFloatingLogService()
        }
        notifyFloatingLogAppVisibility(true)
    }

    override fun onPause() {
        notifyFloatingLogAppVisibility(false)
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdownNow()
    }

    private fun bindViews() {
        rootContainer = findViewById(R.id.rootContainer)
        overlaySwitch = findViewById(R.id.overlaySwitch)
        accessibilitySwitch = findViewById(R.id.accessibilitySwitch)
        homePage = findViewById(R.id.homePage)
        discoverPage = findViewById(R.id.discoverPage)
        profilePage = findViewById(R.id.profilePage)
        navHome = findViewById(R.id.navHome)
        navDiscover = findViewById(R.id.navDiscover)
        navProfile = findViewById(R.id.navProfile)
        taskStatusText = findViewById(R.id.taskStatusText)
    }

    private fun checkActivation() {
        val savedCode = activationPrefs.getString(KEY_ACTIVATION_CODE, null)
        if (savedCode.isNullOrBlank()) {
            showActivationDialog()
            return
        }
        verifyActivation(savedCode, bindIfNeeded = false)
    }

    private fun showActivationDialog(message: String? = null) {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), 0)
        }
        val messageView = TextView(this).apply {
            text = message ?: "请输入后台生成的激活码"
            setTextColor(if (message == null) getColor(R.color.text_secondary) else Color.rgb(190, 45, 45))
            textSize = 14f
            setPadding(0, 0, 0, dp(10))
        }
        val codeInput = EditText(this).apply {
            hint = "激活码"
            setSingleLine(true)
            setText(activationPrefs.getString(KEY_ACTIVATION_CODE, null).orEmpty())
        }
        val progress = ProgressBar(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(14)
            }
        }
        content.addView(messageView)
        content.addView(codeInput)
        content.addView(progress)

        activationDialog?.dismiss()
        val dialog = AlertDialog.Builder(this)
            .setTitle("软件激活")
            .setView(content)
            .setCancelable(false)
            .setPositiveButton("激活", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = codeInput.text.toString().trim()
                if (code.isBlank()) {
                    messageView.text = "请输入激活码"
                    messageView.setTextColor(Color.rgb(190, 45, 45))
                    return@setOnClickListener
                }
                progress.visibility = View.VISIBLE
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                messageView.text = "正在连接后台验证..."
                messageView.setTextColor(getColor(R.color.text_secondary))
                verifyActivation(code, bindIfNeeded = true) { success, error ->
                    progress.visibility = View.GONE
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    if (success) {
                        dialog.dismiss()
                    } else {
                        messageView.text = error ?: "激活失败"
                        messageView.setTextColor(Color.rgb(190, 45, 45))
                    }
                }
            }
        }
        activationDialog = dialog
        dialog.show()
    }

    private fun verifyActivation(
        code: String,
        bindIfNeeded: Boolean,
        callback: ((Boolean, String?) -> Unit)? = null
    ) {
        executor.execute {
            val deviceId = DeviceIdProvider.deviceId(this)
            val deviceName = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
            val result = runCatching {
                if (bindIfNeeded) {
                    activationClient.activate(code, deviceId, deviceName)
                } else {
                    activationClient.verify(code, deviceId, deviceName)
                }
            }
            mainHandler.post {
                val response = result.getOrNull()
                if (response?.success == true) {
                    activationPrefs.edit().putString(KEY_ACTIVATION_CODE, response.code ?: code).apply()
                    enterApplication()
                    callback?.invoke(true, null)
                } else {
                    if (!bindIfNeeded) {
                        activationPrefs.edit().remove(KEY_ACTIVATION_CODE).apply()
                    }
                    val error = response?.message
                        ?: result.exceptionOrNull()?.message
                        ?: "无法连接后台，请检查网络或后台地址"
                    if (callback == null) {
                        showActivationDialog(error)
                    } else {
                        callback.invoke(false, error)
                    }
                }
            }
        }
    }

    private fun enterApplication() {
        activationReady = true
        rootContainer.visibility = View.VISIBLE
        updatePermissionStates()
        updateTaskStatus()
        updateFloatingLogService()
    }

    private fun buildHomeSections() {
        val container = findViewById<LinearLayout>(R.id.homeSections)
        addSection(container, getString(R.string.promotion_section), promotionItems)
        addSection(container, getString(R.string.volcano_section), volcanoItems)
        addSection(container, getString(R.string.tools_section), toolItems)
        addSection(container, getString(R.string.ttk_section), ttkItems)
        addSection(container, getString(R.string.redbook_section), redBookItems)
        addSection(container, getString(R.string.wx_section), wxItems)
        addSection(container, getString(R.string.kuaishou_section), kuaishouItems)
    }

    private fun addSection(parent: LinearLayout, title: String, items: List<FeatureItem>) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = getDrawable(R.drawable.bg_section_card)
            setPadding(dp(12), dp(12), dp(12), dp(10))
        }
        card.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        header.addView(TextView(this).apply {
            text = title
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(getColor(R.color.section_title))
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(this).apply {
            text = "${items.size}项"
            textSize = 12f
            gravity = Gravity.CENTER
            includeFontPadding = false
            setTextColor(getColor(R.color.brand))
            background = getDrawable(R.drawable.bg_icon_soft)
            setPadding(dp(8), dp(4), dp(8), dp(4))
        })
        card.addView(header)
        card.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            )
            setBackgroundColor(getColor(R.color.card_border))
        })
        val grid = GridLayout(this).apply {
            columnCount = 3
            alignmentMode = GridLayout.ALIGN_MARGINS
            clipChildren = false
            clipToPadding = false
            setPadding(0, dp(6), 0, 0)
        }
        populateGrid(grid, items)
        card.addView(grid)
        parent.addView(card)
    }

    private fun populateGrid(grid: GridLayout, items: List<FeatureItem>) {
        val cellWidth = featureCellWidth()
        items.forEach { item ->
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                background = getDrawable(R.drawable.bg_feature_ripple)
                setPadding(dp(4), dp(8), dp(4), dp(10))
                contentDescription = "功能入口：${item.label}"
                setOnClickListener {
                    showFeatureDialog(item)
                }
            }
            cell.layoutParams = GridLayout.LayoutParams().apply {
                width = cellWidth
                height = dp(96)
                setMargins(0, dp(4), 0, dp(4))
            }
            cell.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(52), dp(52))
                background = getDrawable(R.drawable.bg_feature_icon)
                gravity = Gravity.CENTER
                text = item.symbol
                setTextColor(Color.WHITE)
                textSize = if (item.symbol.length > 2) 13f else 22f
                typeface = Typeface.DEFAULT_BOLD
            })
            cell.addView(TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(7) }
                text = item.label
                setTextColor(getColor(R.color.text_primary))
                textSize = 13.5f
                gravity = Gravity.CENTER
                includeFontPadding = false
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
            grid.addView(cell)
        }
    }

    private fun showFeatureDialog(item: FeatureItem) {
        activationPrefs.edit()
            .putString(KEY_SELECTED_FEATURE, item.label)
            .apply()
        updateTaskStatus()
        sendFloatingLog("已选择功能：${item.label}")

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(8), dp(4), 0)
        }
        content.addView(TextView(this).apply {
            text = "${item.label} 的动作流程在后台录入和编排，手机端这里只负责选择模式、开始和暂停。"
            setTextColor(getColor(R.color.text_secondary))
            textSize = 14f
            setLineSpacing(dp(3).toFloat(), 1f)
        })

        val dialog = AlertDialog.Builder(this)
            .setTitle(item.label)
            .setView(content)
            .setPositiveButton("开始") { _, _ -> startFeature(item.label) }
            .setNegativeButton("暂停") { _, _ -> pauseFeature(item.label) }
            .setNeutralButton("介绍") { _, _ ->
                Toast.makeText(this, "先在后台流程编排里录动作，再回到这里点开始。", Toast.LENGTH_LONG).show()
            }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).contentDescription = "开始${item.label}"
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).contentDescription = "暂停${item.label}"
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).contentDescription = "介绍${item.label}"
        }
        dialog.show()
    }

    private fun startFeature(featureName: String) {
        activationPrefs.edit()
            .putString(KEY_SELECTED_FEATURE, featureName)
            .putBoolean(KEY_FEATURE_RUNNING, true)
            .apply()
        updateTaskStatus()
        sendFloatingLog("开始执行：$featureName")
        sendRunnerStatus("运行中", featureName)
        sendRunnerCommand(ACTION_START_FEATURE, featureName)
        Toast.makeText(this, "已开始：$featureName", Toast.LENGTH_SHORT).show()
    }

    private fun pauseFeature(featureName: String) {
        activationPrefs.edit()
            .putString(KEY_SELECTED_FEATURE, featureName)
            .putBoolean(KEY_FEATURE_RUNNING, false)
            .apply()
        updateTaskStatus()
        sendFloatingLog("已暂停：$featureName")
        sendRunnerStatus("已暂停", featureName)
        sendRunnerCommand(ACTION_PAUSE_FEATURE, featureName)
        Toast.makeText(this, "已暂停：$featureName", Toast.LENGTH_SHORT).show()
    }

    private fun updateTaskStatus() {
        if (!::taskStatusText.isInitialized) return
        val featureName = activationPrefs.getString(KEY_SELECTED_FEATURE, null)
            ?.takeIf { it.isNotBlank() }
            ?: "未选择功能"
        val running = activationPrefs.getBoolean(KEY_FEATURE_RUNNING, false)
        val status = if (running) "运行中" else "已暂停"
        taskStatusText.text = "任务状态：$status · 当前功能：$featureName"
        taskStatusText.setTextColor(getColor(if (running) R.color.brand else R.color.text_secondary))
    }

    private fun featureCellWidth(): Int {
        val screenWidth = resources.displayMetrics.widthPixels
        val pagePadding = dp(24)
        val cardPadding = dp(24)
        return (screenWidth - pagePadding - cardPadding) / 3
    }

    private fun buildProfileActions() {
        val actions = listOf(
            FeatureItem("下载管理", "D"),
            FeatureItem("更新日志", "U"),
            FeatureItem("退出系统", "E")
        )
        populateGrid(findViewById(R.id.profileActions), actions)
    }

    private fun setupPermissions() {
        findViewById<View>(R.id.overlayRow).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                openOverlayPermissionSettings()
            } else {
                setFloatingWindowEnabled(!isFloatingWindowEnabled())
            }
        }
        findViewById<View>(R.id.accessibilityRow).setOnClickListener {
            openAccessibilitySettings()
        }
        overlaySwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingSwitches) {
                setFloatingWindowEnabled(checked)
            }
        }
        accessibilitySwitch.setOnCheckedChangeListener { _, checked ->
            if (!updatingSwitches && checked != isAccessibilityServiceEnabled()) {
                openAccessibilitySettings()
            }
        }
    }

    private fun setupNavigation() {
        navHome.setOnClickListener { showPage(Page.HOME) }
        navDiscover.setOnClickListener { showPage(Page.DISCOVER) }
        navProfile.setOnClickListener { showPage(Page.PROFILE) }
    }

    private fun showPage(page: Page) {
        homePage.visibility = if (page == Page.HOME) View.VISIBLE else View.GONE
        discoverPage.visibility = if (page == Page.DISCOVER) View.VISIBLE else View.GONE
        profilePage.visibility = if (page == Page.PROFILE) View.VISIBLE else View.GONE
        navHome.setTextColor(getColor(if (page == Page.HOME) R.color.brand else R.color.text_secondary))
        navDiscover.setTextColor(getColor(if (page == Page.DISCOVER) R.color.brand else R.color.text_secondary))
        navProfile.setTextColor(getColor(if (page == Page.PROFILE) R.color.brand else R.color.text_secondary))
    }

    private fun updatePermissionStates() {
        updatingSwitches = true
        overlaySwitch.isChecked = Settings.canDrawOverlays(this) && isFloatingWindowEnabled()
        accessibilitySwitch.isChecked = isAccessibilityServiceEnabled()
        updatingSwitches = false
    }

    private fun updateFloatingLogService() {
        val serviceIntent = Intent(this, FloatingLogService::class.java)
            .putExtra(FloatingLogService.EXTRA_APP_VISIBLE, true)
            .putExtra(FloatingLogService.EXTRA_FLOATING_ENABLED, isFloatingWindowEnabled())
        if (Settings.canDrawOverlays(this) && isFloatingWindowEnabled()) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            }.onFailure {
                Toast.makeText(this, "悬浮日志启动失败，请检查悬浮窗和通知权限", Toast.LENGTH_SHORT).show()
            }
        } else {
            runCatching { stopService(serviceIntent) }
        }
    }

    private fun setFloatingWindowEnabled(enabled: Boolean) {
        if (enabled && !Settings.canDrawOverlays(this)) {
            activationPrefs.edit().putBoolean(KEY_FLOATING_WINDOW_ENABLED, true).apply()
            updatePermissionStates()
            openOverlayPermissionSettings()
            return
        }
        activationPrefs.edit().putBoolean(KEY_FLOATING_WINDOW_ENABLED, enabled).apply()
        updatePermissionStates()
        updateFloatingLogService()
        Toast.makeText(this, if (enabled) "悬浮窗已开启" else "悬浮窗已关闭", Toast.LENGTH_SHORT).show()
    }

    private fun isFloatingWindowEnabled(): Boolean =
        activationPrefs.getBoolean(KEY_FLOATING_WINDOW_ENABLED, true)

    private fun notifyFloatingLogAppVisibility(visible: Boolean) {
        if (activationReady && Settings.canDrawOverlays(this) && isFloatingWindowEnabled()) {
            runCatching {
                startService(
                    Intent(this, FloatingLogService::class.java)
                        .putExtra(FloatingLogService.EXTRA_APP_VISIBLE, visible)
                        .putExtra(FloatingLogService.EXTRA_FLOATING_ENABLED, true)
                )
            }
        }
        sendBroadcast(
            Intent(FloatingLogService.ACTION_APP_VISIBILITY)
                .setPackage(packageName)
                .putExtra(FloatingLogService.EXTRA_APP_VISIBLE, visible)
        )
    }

    private fun sendFloatingLog(message: String) {
        sendBroadcast(
            Intent(FloatingLogService.ACTION_ACTIVITY_LOG)
                .setPackage(packageName)
                .putExtra(FloatingLogService.EXTRA_MESSAGE, message)
        )
    }

    private fun sendRunnerCommand(command: String, featureName: String) {
        sendBroadcast(
            Intent(AutomationAccessibilityService.ACTION_RUNNER_COMMAND)
                .setPackage(packageName)
                .putExtra(AutomationAccessibilityService.EXTRA_RUNNER_COMMAND, command)
                .putExtra(AutomationAccessibilityService.EXTRA_FEATURE_NAME, featureName)
        )
    }

    private fun sendRunnerStatus(status: String, step: String) {
        sendBroadcast(
            Intent(FloatingLogService.ACTION_RUNNER_STATUS)
                .setPackage(packageName)
                .putExtra(FloatingLogService.EXTRA_STATUS, status)
                .putExtra(FloatingLogService.EXTRA_STEP, step)
        )
    }

    private fun applySystemBarInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            applySystemBarInsetsApi30()
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    private fun applySystemBarInsetsApi30() {
        val padding = intArrayOf(
            rootContainer.paddingLeft, rootContainer.paddingTop,
            rootContainer.paddingRight, rootContainer.paddingBottom
        )
        rootContainer.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
            view.setPadding(
                padding[0] + bars.left, padding[1] + bars.top,
                padding[2] + bars.right, padding[3] + bars.bottom
            )
            insets
        }
    }

    private fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startSettings(intent, Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
    }

    private fun openAccessibilitySettings() {
        startSettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), null)
    }

    private fun startSettings(primary: Intent, fallbackAction: String?) {
        try {
            startActivity(primary)
        } catch (_: ActivityNotFoundException) {
            if (fallbackAction == null) {
                showSettingsUnavailableMessage()
                return
            }
            try {
                startActivity(Intent(fallbackAction))
            } catch (_: ActivityNotFoundException) {
                showSettingsUnavailableMessage()
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val expected = ComponentName(this, AutomationAccessibilityService::class.java)
        return manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any {
                val service = it.resolveInfo.serviceInfo
                ComponentName(service.packageName, service.name) == expected
            }
    }

    private fun showSettingsUnavailableMessage() {
        Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density + 0.5f).toInt()

    private data class FeatureItem(val label: String, val symbol: String)
    private enum class Page { HOME, DISCOVER, PROFILE }

    companion object {
        private const val KEY_ACTIVATION_CODE = "activation_code"
        private const val KEY_FLOATING_WINDOW_ENABLED = "floating_window_enabled"
        private const val KEY_SELECTED_FEATURE = "selected_feature"
        private const val KEY_FEATURE_RUNNING = "feature_running"
        private const val ACTION_START_FEATURE = "start"
        private const val ACTION_PAUSE_FEATURE = "pause"
    }
}
