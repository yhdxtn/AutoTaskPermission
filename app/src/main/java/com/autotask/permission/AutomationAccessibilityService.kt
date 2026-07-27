package com.autotask.permission

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

class AutomationAccessibilityService : AccessibilityService() {

    private val executor = Executors.newSingleThreadExecutor()
    private var lastSnapshotKey = ""
    private var lastReportTime = 0L
    @Volatile private var runnerActive = false
    @Volatile private var currentFeature = ""
    @Volatile private var appLoopRepeat = 3
    private val activationPrefs by lazy { getSharedPreferences("activation", MODE_PRIVATE) }

    private val runnerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != ACTION_RUNNER_COMMAND) return
            val command = intent.getStringExtra(EXTRA_RUNNER_COMMAND).orEmpty()
            val featureName = intent.getStringExtra(EXTRA_FEATURE_NAME).orEmpty()
            val repeat = intent.getIntExtra(EXTRA_APP_LOOP_REPEAT, activationPrefs.getInt(KEY_APP_LOOP_REPEAT, 3))
                .coerceIn(1, 999)
            when (command) {
                COMMAND_START -> startFeature(featureName, repeat)
                COMMAND_PAUSE -> pauseFeature(featureName)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        maybeStartFromPrefs()
        if (!AUTO_CAPTURE_ENABLED) return
        val packageName = event?.packageName?.toString() ?: return
        if (packageName != DOUYIN_PACKAGE) return

        val root = rootInActiveWindow ?: return
        val now = System.currentTimeMillis()
        val controls = collectControls(root)
        if (controls.isEmpty()) return

        val snapshotKey = buildSnapshotKey(packageName, event.className?.toString(), controls)
        if (snapshotKey == lastSnapshotKey && now - lastReportTime < REPORT_INTERVAL_MS) return

        lastSnapshotKey = snapshotKey
        lastReportTime = now
        uploadSnapshot(packageName, event.className?.toString(), controls)
    }

    override fun onInterrupt() {
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerRunnerReceiver()
        maybeStartFromPrefs()
    }

    override fun onDestroy() {
        runnerActive = false
        runCatching { unregisterReceiver(runnerReceiver) }
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun registerRunnerReceiver() {
        val filter = IntentFilter(ACTION_RUNNER_COMMAND)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(runnerReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(runnerReceiver, filter)
        }
    }

    private fun startFeature(featureName: String, repeatFromApp: Int = activationPrefs.getInt(KEY_APP_LOOP_REPEAT, 3)) {
        val name = featureName.trim().ifBlank { "当前功能" }
        if (runnerActive && currentFeature == name) return
        currentFeature = name
        appLoopRepeat = repeatFromApp.coerceIn(1, 999)
        activationPrefs.edit().putInt(KEY_APP_LOOP_REPEAT, appLoopRepeat).apply()
        runnerActive = false
        executor.execute {
            runnerActive = true
            sendFloatingLog("执行器已启动：$name，本次循环 $appLoopRepeat 次")
            runCatching {
                val flow = loadFlowForFeature(name)
                if (flow == null) {
                    sendFloatingLog("没有找到已启用流程：$name")
                    return@execute
                }
                runFlow(flow)
            }.onFailure {
                sendFloatingLog("执行失败：${it.message ?: "未知错误"}")
            }
            runnerActive = false
            activationPrefs.edit().putBoolean(KEY_FEATURE_RUNNING, false).apply()
        }
    }

    private fun pauseFeature(featureName: String) {
        runnerActive = false
        activationPrefs.edit().putBoolean(KEY_FEATURE_RUNNING, false).apply()
        sendFloatingLog("执行器已暂停：${featureName.ifBlank { currentFeature }}")
    }

    private fun maybeStartFromPrefs() {
        if (runnerActive) return
        if (!activationPrefs.getBoolean(KEY_FEATURE_RUNNING, false)) return
        val feature = activationPrefs.getString(KEY_SELECTED_FEATURE, null).orEmpty()
        if (feature.isBlank()) return
        startFeature(feature, activationPrefs.getInt(KEY_APP_LOOP_REPEAT, 3))
    }

    private fun loadFlowForFeature(featureName: String): FlowDefinition? {
        val featureKey = featureKeyForName(featureName)
        val url = URL(
            BuildConfig.ACTIVATION_API_BASE_URL.trimEnd('/') +
                "/api/device/automation/flows?packageName=" +
                URLEncoder.encode(DOUYIN_PACKAGE, "UTF-8")
        )
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Accept", "application/json")
        }
        val statusCode = connection.responseCode
        val body = connection.inputStreamOrError()
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
        connection.disconnect()
        if (statusCode !in 200..299) error("后台流程读取失败：$statusCode")
        val flows = JSONArray(body)
        val candidates = (0 until flows.length()).map { flows.getJSONObject(it) }
        val picked = candidates.firstOrNull { it.optString("name") == featureName }
            ?: candidates.firstOrNull { featureKey.isNotBlank() && it.optString("description").contains("feature:$featureKey") }
            ?: candidates.firstOrNull { it.optString("description").contains(featureName) }
        return picked?.let { FlowDefinition.fromJson(it) }
    }

    private fun featureKeyForName(featureName: String): String =
        when (featureName.trim()) {
            "基础观看", "basic_watch" -> "basic_watch"
            "垂直观看", "vertical_watch" -> "vertical_watch"
            "点赞浏览", "like_browse" -> "like_browse"
            "普通浏览", "normal_browse" -> "normal_browse"
            "智能浏览", "smart_browse" -> "smart_browse"
            "智能宣传", "smart_promo" -> "smart_promo"
            "评论浏览", "comment_browse" -> "comment_browse"
            "喜欢浏览", "favorite_browse" -> "favorite_browse"
            "直播浏览", "live_browse" -> "live_browse"
            "行业搜索", "industry_search" -> "industry_search"
            "同城宣传", "local_promo" -> "local_promo"
            "万能推广", "universal_promo" -> "universal_promo"
            "粉丝浏览", "fans_browse" -> "fans_browse"
            "智能推广", "smart_growth" -> "smart_growth"
            "直播宣传", "live_promo" -> "live_promo"
            else -> ""
        }

    private fun runFlow(flow: FlowDefinition) {
        if (flow.nodes.isEmpty()) {
            sendFloatingLog("流程没有节点：${flow.name}")
            return
        }
        RuntimeScreen.width = resources.displayMetrics.widthPixels
        RuntimeScreen.height = resources.displayMetrics.heightPixels
        sendFloatingLog("加载流程：${flow.name}，${flow.nodes.size} 个节点")
        sendRunnerStatus("运行中", "加载流程：${flow.name}")
        val inbound = flow.edges.map { it.to }.toSet()
        var node = flow.nodes.firstOrNull { it.id !in inbound && it.type == "launch" }
            ?: flow.nodes.firstOrNull { it.id !in inbound }
            ?: flow.nodes.first()
        var guard = 0
        val loopRounds = mutableMapOf<String, Int>()
        while (runnerActive && guard < MAX_FLOW_STEPS) {
            guard++
            val outcome = if (node.type == "loop") {
                loopOutcome(node, loopRounds)
            } else {
                executeNodeWithRepeat(node)
            }
            val next = flow.nextNode(node, outcome)
            if (next == null) {
                sendFloatingLog("流程结束：${node.label}")
                sendRunnerStatus("已完成", "流程结束：${node.label}")
                break
            }
            if (next.delaySeconds > 0) {
                sendFloatingLog("连线等待：${formatSeconds(next.delaySeconds)} 秒")
                sendRunnerStatus("等待中", "连线等待 ${formatSeconds(next.delaySeconds)} 秒")
                sleepSeconds(next.delaySeconds)
            }
            node = next.node
        }
        if (guard >= MAX_FLOW_STEPS) sendFloatingLog("流程已停止：超过最大步数")
    }

    private fun executeNodeWithRepeat(node: FlowNode): String {
        val repeat = node.int("repeatCount", 1).coerceAtLeast(1)
        val probability = node.int("loopProbability", 100).coerceIn(0, 100)
        val interval = node.double("intervalSeconds", 0.0).coerceAtLeast(0.0)
        var outcome = "next"
        for (index in 0 until repeat) {
            if (!runnerActive) break
            if (index > 0 && probability < 100 && Random.nextInt(100) >= probability) break
            outcome = executeNodeOnce(node)
            if (index < repeat - 1 && interval > 0) sleepSeconds(interval)
        }
        val wait = node.waitDurationSeconds()
        if (wait > 0) {
            sendFloatingLog("执行后等待：${formatSeconds(wait)} 秒")
            sendRunnerStatus("等待中", "${node.label} 后等待 ${formatSeconds(wait)} 秒")
            sleepSeconds(wait)
        }
        return outcome
    }

    private fun executeNodeOnce(node: FlowNode): String {
        sendFloatingLog("执行：${node.label}")
        sendRunnerStatus("运行中", node.label)
        return when (node.type) {
            "launch" -> {
                launchTargetApp(node.string("packageName").ifBlank { DOUYIN_PACKAGE })
                "next"
            }
            "click" -> {
                if (clickTarget(node)) "found" else "missing"
            }
            "tap" -> {
                tap(pointX(node, "xPoint"), pointY(node, "yPoint"))
                "next"
            }
            "swipe" -> {
                swipe(
                    pointX(node, "startX"),
                    pointY(node, "startY"),
                    pointX(node, "endX"),
                    pointY(node, "endY"),
                    node.long("durationMs", 450L)
                )
                "next"
            }
            "input" -> {
                if (inputText(node)) "found" else "missing"
            }
            "condition" -> {
                if (waitForControl(node)) "found" else "missing"
            }
            "recognizePage" -> {
                if (waitForPage(node)) "found" else "missing"
            }
            "loop", "wait" -> "next"
            else -> "next"
        }
    }

    private fun loopOutcome(node: FlowNode, loopRounds: MutableMap<String, Int>): String {
        val used = loopRounds.getOrDefault(node.id, 0)
        val repeat = loopRepeatCount(node).coerceAtLeast(1)
        val probability = node.int("loopProbability", 100).coerceIn(0, 100)
        val shouldLoop = used < repeat - 1 && (probability >= 100 || Random.nextInt(100) < probability)
        return if (shouldLoop) {
            loopRounds[node.id] = used + 1
            sendFloatingLog("循环第 ${used + 2}/$repeat 轮：${node.label}")
            sendRunnerStatus("循环中", "${node.label} ${used + 2}/$repeat")
            val interval = node.double("intervalSeconds", 0.0).coerceAtLeast(0.0)
            if (interval > 0) sleepSeconds(interval)
            "loop"
        } else {
            loopRounds.remove(node.id)
            sendFloatingLog("循环结束：${node.label}")
            sendRunnerStatus("运行中", "循环结束，继续后续步骤")
            "done"
        }
    }

    private fun loopRepeatCount(node: FlowNode): Int {
        return if (node.string("repeatSource") == "app") {
            appLoopRepeat
        } else {
            node.int("repeatCount", 1)
        }.coerceIn(1, 999)
    }

    private fun launchTargetApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: fallbackLaunchIntent(packageName)
            ?: error("找不到可启动 App：$packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        sendFloatingLog("已启动 App：$packageName")
    }

    private fun fallbackLaunchIntent(packageName: String): Intent? {
        if (packageName == DOUYIN_PACKAGE) {
            return Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setClassName(packageName, "com.ss.android.ugc.aweme.splash.SplashActivity")
        }
        val query = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(packageName)
        val activity = packageManager.queryIntentActivities(query, 0).firstOrNull() ?: return null
        return Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(activity.activityInfo.packageName, activity.activityInfo.name)
    }

    private fun clickTarget(node: FlowNode): Boolean {
        val root = rootInActiveWindow
        val target = findMatchingNode(root, node)
        if (target != null) {
            val clickable = target.clickableParent()
            if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        val point = node.boundsCenter()
        if (point != null) {
            tap(point.first, point.second)
            return true
        }
        return false
    }

    private fun inputText(node: FlowNode): Boolean {
        val text = node.inputValue(this)
        val root = rootInActiveWindow
        val target = findMatchingNode(root, node)
        if (target != null) {
            target.clickableParent().performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Thread.sleep(250)
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true
        }
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("automation-input", text))
        return target?.performAction(AccessibilityNodeInfo.ACTION_PASTE) == true
    }

    private fun waitForControl(node: FlowNode): Boolean {
        val deadline = System.currentTimeMillis() + (node.double("detectTimeoutSeconds", 3.0) * 1000).toLong()
        val pollMs = (node.double("pollSeconds", 0.5) * 1000).toLong().coerceAtLeast(120L)
        while (runnerActive && System.currentTimeMillis() <= deadline) {
            if (findMatchingNode(rootInActiveWindow, node) != null) return true
            Thread.sleep(pollMs)
        }
        return false
    }

    private fun waitForPage(node: FlowNode): Boolean {
        val deadline = System.currentTimeMillis() + (node.double("detectTimeoutSeconds", 3.0) * 1000).toLong()
        val pollMs = (node.double("pollSeconds", 0.5) * 1000).toLong().coerceAtLeast(120L)
        while (runnerActive && System.currentTimeMillis() <= deadline) {
            if (matchesPage(node)) {
                val probability = node.int("triggerProbability", 100).coerceIn(0, 100)
                return probability >= 100 || Random.nextInt(100) < probability
            }
            Thread.sleep(pollMs)
        }
        return false
    }

    private fun matchesPage(node: FlowNode): Boolean {
        val root = rootInActiveWindow ?: return false
        val pageText = collectPageText(root).lowercase()
        val keywords = node.array("ocrKeywords")
            .mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
        val keywordHits = keywords.count { pageText.contains(it.lowercase()) }
        val minHits = node.int("minKeywordHits", 1).coerceAtLeast(1)
        val controls = node.array("requiredControls").mapNotNull { it as? JSONObject }
        val controlHits = controls.count { findMatchingNode(root, FlowNode.fromJson(it)) != null }
        val keywordOk = keywords.isEmpty() || keywordHits >= minHits
        val controlOk = controls.isEmpty() || controlHits > 0
        return keywordOk && controlOk
    }

    private fun collectPageText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val values = mutableListOf<String>()
        fun walk(current: AccessibilityNodeInfo?) {
            if (current == null) return
            current.text?.toString()?.takeIf { it.isNotBlank() }?.let(values::add)
            current.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let(values::add)
            for (index in 0 until current.childCount) walk(current.getChild(index))
        }
        walk(node)
        return values.joinToString(" ")
    }

    private fun findMatchingNode(root: AccessibilityNodeInfo?, node: FlowNode): AccessibilityNodeInfo? {
        if (root == null) return null
        val viewId = node.string("viewId").ifBlank { node.string("viewIdResourceName") }
        if (viewId.isNotBlank()) {
            val byId = runCatching { root.findAccessibilityNodeInfosByViewId(viewId) }.getOrNull()
            byId?.firstOrNull { it.isVisibleToUser }?.let { return it }
        }
        val labels = listOf(
            node.string("controlText"),
            node.string("label"),
            node.string("text"),
            node.string("contentDescription")
        ).map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("android.") && !it.startsWith("com.") }
        if (labels.isEmpty()) return null
        return findNodeByText(root, labels)
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, labels: List<String>): AccessibilityNodeInfo? {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val node = stack.removeFirst()
            val text = node.text?.toString().orEmpty()
            val desc = node.contentDescription?.toString().orEmpty()
            if (node.isVisibleToUser && labels.any { text.contains(it) || desc.contains(it) || it.contains(text) && text.isNotBlank() }) {
                return node
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let(stack::add)
            }
        }
        return null
    }

    private fun AccessibilityNodeInfo.clickableParent(): AccessibilityNodeInfo {
        var current: AccessibilityNodeInfo = this
        var parent = current.parent
        while (!current.isClickable && parent != null) {
            current = parent
            parent = current.parent
        }
        return current
    }

    private fun pointX(node: FlowNode, key: String): Int =
        pointValue(node, key, resources.displayMetrics.widthPixels)

    private fun pointY(node: FlowNode, key: String): Int =
        pointValue(node, key, resources.displayMetrics.heightPixels)

    private fun pointValue(node: FlowNode, key: String, base: Int): Int {
        val value = node.double(key, 0.0)
        return if (node.string("coordinateMode") == "percent") {
            (value / 100.0 * base).roundToInt()
        } else {
            value.roundToInt()
        }
    }

    private fun tap(x: Int, y: Int) {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 80))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun swipe(startX: Int, startY: Int, endX: Int, endY: Int, durationMs: Long) {
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs.coerceAtLeast(120L)))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun sendFloatingLog(message: String) {
        sendBroadcast(
            Intent(FloatingLogService.ACTION_ACTIVITY_LOG)
                .setPackage(packageName)
                .putExtra(FloatingLogService.EXTRA_MESSAGE, message)
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

    private fun sleepSeconds(seconds: Double) {
        Thread.sleep((seconds * 1000).toLong().coerceAtLeast(0L))
    }

    private fun collectControls(root: AccessibilityNodeInfo): List<ControlCapture> {
        val controls = mutableListOf<ControlCapture>()
        traverse(root, 0, controls)
        return controls
    }

    private fun traverse(
        node: AccessibilityNodeInfo?,
        depth: Int,
        controls: MutableList<ControlCapture>
    ) {
        if (node == null || controls.size >= MAX_CONTROLS) return

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val text = node.text?.toString()?.trimToNull()
        val desc = node.contentDescription?.toString()?.trimToNull()
        val viewId = node.viewIdResourceName?.trimToNull()
        val className = node.className?.toString()?.trimToNull()
        val editable = className?.contains("EditText", ignoreCase = true) == true
        val shortLabel = listOfNotNull(text, desc).any { it.length <= MAX_LABEL_LENGTH }
        val useful = node.isVisibleToUser &&
            bounds.width() > 0 &&
            bounds.height() > 0 &&
            (node.isClickable || node.isFocusable || editable || viewId != null || shortLabel)

        if (useful) {
            controls.add(
                ControlCapture(
                    key = stableControlKey(controls.size, viewId, text, desc, bounds),
                    text = text?.limit(160),
                    contentDescription = desc?.limit(160),
                    viewId = viewId?.limit(220),
                    className = className?.limit(180),
                    left = bounds.left,
                    top = bounds.top,
                    right = bounds.right,
                    bottom = bounds.bottom,
                    depth = depth,
                    clickable = node.isClickable,
                    enabled = node.isEnabled,
                    focusable = node.isFocusable,
                    visibleToUser = node.isVisibleToUser
                )
            )
        }

        for (index in 0 until node.childCount) {
            traverse(node.getChild(index), depth + 1, controls)
        }
    }

    private fun uploadSnapshot(
        packageName: String,
        activityName: String?,
        controls: List<ControlCapture>
    ) {
        val payload = buildPayload(packageName, activityName, controls)
        executor.execute {
            runCatching {
                val url = URL(BuildConfig.ACTIVATION_API_BASE_URL.trimEnd('/') + "/api/device/ui-snapshots")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                }
                connection.outputStream.use { output ->
                    output.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
                val statusCode = connection.responseCode
                connection.inputStreamOrError().close()
                connection.disconnect()
                statusCode
            }
        }
    }

    private fun buildPayload(
        packageName: String,
        activityName: String?,
        controls: List<ControlCapture>
    ): JSONObject {
        val metrics = resources.displayMetrics
        val screenWidth = max(metrics.widthPixels, controls.maxOfOrNull { it.right } ?: 0)
        val screenHeight = max(metrics.heightPixels, controls.maxOfOrNull { it.bottom } ?: 0)
        return JSONObject()
            .put("packageName", packageName)
            .put("appName", appLabel(packageName))
            .put("activityName", activityName)
            .put("deviceId", DeviceIdProvider.deviceId(this))
            .put("deviceName", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
            .put("screenWidth", screenWidth)
            .put("screenHeight", screenHeight)
            .put("controls", JSONArray().apply {
                controls.forEach { control ->
                    put(JSONObject()
                        .put("key", control.key)
                        .put("text", control.text)
                        .put("contentDescription", control.contentDescription)
                        .put("viewId", control.viewId)
                        .put("className", control.className)
                        .put("left", control.left)
                        .put("top", control.top)
                        .put("right", control.right)
                        .put("bottom", control.bottom)
                        .put("depth", control.depth)
                        .put("clickable", control.clickable)
                        .put("enabled", control.enabled)
                        .put("focusable", control.focusable)
                        .put("visibleToUser", control.visibleToUser)
                    )
                }
            })
    }

    private fun buildSnapshotKey(
        packageName: String,
        activityName: String?,
        controls: List<ControlCapture>
    ): String {
        val firstControls = controls
            .take(12)
            .joinToString("|") { "${it.viewId}:${it.text}:${it.left},${it.top},${it.right},${it.bottom}" }
        return "$packageName#$activityName#${controls.size}#$firstControls"
    }

    private fun stableControlKey(
        index: Int,
        viewId: String?,
        text: String?,
        desc: String?,
        bounds: Rect
    ): String {
        val raw = listOf(
            viewId.orEmpty(),
            text.orEmpty(),
            desc.orEmpty(),
            bounds.left,
            bounds.top,
            bounds.right,
            bounds.bottom
        ).joinToString("|")
        return "c$index-" + raw.hashCode().toUInt().toString(16)
    }

    private fun appLabel(packageName: String): String {
        return runCatching {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)
    }

    private fun HttpURLConnection.inputStreamOrError() =
        if (responseCode in 200..299) inputStream else errorStream ?: inputStream

    private fun String.trimToNull(): String? =
        trim().takeIf { it.isNotEmpty() }

    private fun String.limit(maxLength: Int): String =
        if (length <= maxLength) this else take(maxLength)

    private data class ControlCapture(
        val key: String,
        val text: String?,
        val contentDescription: String?,
        val viewId: String?,
        val className: String?,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val depth: Int,
        val clickable: Boolean,
        val enabled: Boolean,
        val focusable: Boolean,
        val visibleToUser: Boolean
    )

    private data class FlowDefinition(
        val name: String,
        val description: String,
        val nodes: List<FlowNode>,
        val edges: List<FlowEdge>
    ) {
        fun nextNode(currentNode: FlowNode, outcome: String): NextNode? {
            val fromId = currentNode.id
            val exactModes = when (outcome) {
                "found" -> listOf("found", "true")
                "missing" -> listOf("missing", "false")
                else -> listOf(outcome)
            }
            val outgoing = edges.filter { it.from == fromId }
            val candidates = outgoing.filter { it.mode in exactModes }
                .ifEmpty { outgoing.filter { it.mode == "next" || it.mode.isBlank() } }
            val edge = candidates.firstOrNull { it.shouldTrigger() }
                ?: fallbackLoopEdge(currentNode, outcome)
                ?: return null
            val next = nodes.firstOrNull { it.id == edge.to } ?: return null
            return NextNode(next, edge.delayDurationSeconds())
        }

        private fun fallbackLoopEdge(currentNode: FlowNode, outcome: String): FlowEdge? {
            if (currentNode.type != "loop") return null
            val currentIndex = nodes.indexOfFirst { it.id == currentNode.id }
            if (currentIndex < 0) return null
            return if (outcome == "loop") {
                val backCount = currentNode.int("loopBackCount", 1).coerceAtLeast(1)
                val targetIndex = (currentIndex - backCount).coerceAtLeast(0)
                FlowEdge.synthetic(
                    from = currentNode.id,
                    to = nodes[targetIndex].id,
                    mode = "loop",
                    delaySeconds = currentNode.double("intervalSeconds", 0.0)
                )
            } else if (outcome == "done" && currentIndex + 1 < nodes.size) {
                FlowEdge.synthetic(
                    from = currentNode.id,
                    to = nodes[currentIndex + 1].id,
                    mode = "done",
                    delaySeconds = currentNode.double("waitSeconds", 0.0)
                )
            } else {
                null
            }
        }

        companion object {
            fun fromJson(json: JSONObject): FlowDefinition {
                val nodesJson = json.optJSONArray("nodes") ?: JSONArray()
                val edgesJson = json.optJSONArray("edges") ?: JSONArray()
                return FlowDefinition(
                    name = json.optString("name", "未命名流程"),
                    description = json.optString("description"),
                    nodes = (0 until nodesJson.length())
                        .mapNotNull { nodesJson.optJSONObject(it) }
                        .map { FlowNode.fromJson(it) },
                    edges = (0 until edgesJson.length())
                        .mapNotNull { edgesJson.optJSONObject(it) }
                        .map { FlowEdge.fromJson(it) }
                )
            }
        }
    }

    private data class NextNode(val node: FlowNode, val delaySeconds: Double)

    private data class FlowEdge(
        val from: String,
        val to: String,
        val mode: String,
        val probability: Int,
        val delaySeconds: Double,
        val delayMode: String,
        val delayMinSeconds: Double,
        val delayMaxSeconds: Double
    ) {
        fun shouldTrigger(): Boolean =
            probability >= 100 || Random.nextInt(100) < probability.coerceIn(0, 100)

        fun delayDurationSeconds(): Double {
            return if (delayMode == "random") {
                randomSeconds(delayMinSeconds, delayMaxSeconds)
            } else {
                delaySeconds.coerceAtLeast(0.0)
            }
        }

        companion object {
            fun synthetic(from: String, to: String, mode: String, delaySeconds: Double): FlowEdge =
                FlowEdge(
                    from = from,
                    to = to,
                    mode = mode,
                    probability = 100,
                    delaySeconds = delaySeconds,
                    delayMode = "fixed",
                    delayMinSeconds = delaySeconds,
                    delayMaxSeconds = delaySeconds
                )

            fun fromJson(json: JSONObject): FlowEdge =
                FlowEdge(
                    from = json.optString("from"),
                    to = json.optString("to"),
                    mode = json.optString("mode", "next"),
                    probability = json.optInt("probability", 100),
                    delaySeconds = json.optDouble("delaySeconds", 0.0),
                    delayMode = json.optString("delayMode", "fixed"),
                    delayMinSeconds = json.optDouble("delayMinSeconds", json.optDouble("delaySeconds", 0.0)),
                    delayMaxSeconds = json.optDouble("delayMaxSeconds", json.optDouble("delaySeconds", 0.0))
                )
        }
    }

    private data class FlowNode(
        val id: String,
        val type: String,
        val label: String,
        val json: JSONObject
    ) {
        fun string(key: String): String = json.optString(key, "")
        fun int(key: String, fallback: Int): Int =
            if (json.has(key) && !json.isNull(key)) json.optInt(key, fallback) else fallback

        fun long(key: String, fallback: Long): Long =
            if (json.has(key) && !json.isNull(key)) json.optLong(key, fallback) else fallback

        fun double(key: String, fallback: Double): Double =
            if (json.has(key) && !json.isNull(key)) json.optDouble(key, fallback) else fallback

        fun waitDurationSeconds(): Double {
            return if (string("waitMode") == "random") {
                randomSeconds(
                    double("waitMinSeconds", double("waitSeconds", 0.0)),
                    double("waitMaxSeconds", double("waitSeconds", 0.0))
                )
            } else {
                double("waitSeconds", 0.0).coerceAtLeast(0.0)
            }
        }

        fun array(key: String): List<Any?> {
            val array = json.optJSONArray(key) ?: return emptyList()
            return (0 until array.length()).map { array.opt(it) }
        }

        fun boundsCenter(): Pair<Int, Int>? {
            val bounds = json.optJSONObject("bounds") ?: return null
            val metricsWidth = 1080.coerceAtLeast(bounds.optInt("screenWidth", 0))
            val metricsHeight = 2400.coerceAtLeast(bounds.optInt("screenHeight", 0))
            val screenWidth = metricsWidth.takeIf { it > 0 } ?: return null
            val screenHeight = metricsHeight.takeIf { it > 0 } ?: return null
            val displayWidth = RuntimeScreen.width
            val displayHeight = RuntimeScreen.height
            val cxRatio = if (bounds.has("centerXRatio")) bounds.optDouble("centerXRatio") else {
                val left = bounds.optDouble("left")
                val right = bounds.optDouble("right")
                ((left + right) / 2.0) / screenWidth
            }
            val cyRatio = if (bounds.has("centerYRatio")) bounds.optDouble("centerYRatio") else {
                val top = bounds.optDouble("top")
                val bottom = bounds.optDouble("bottom")
                ((top + bottom) / 2.0) / screenHeight
            }
            return Pair(
                (cxRatio * displayWidth).roundToInt(),
                (cyRatio * displayHeight).roundToInt()
            )
        }

        fun inputValue(context: Context): String {
            return when (string("textSource")) {
                "clipboard" -> {
                    val clipboard = context.getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                }
                else -> string("inputText")
            }
        }

        companion object {
            fun fromJson(json: JSONObject): FlowNode =
                FlowNode(
                    id = json.optString("id", json.optString("key")),
                    type = json.optString("type", "wait"),
                    label = json.optString("label", json.optString("controlText", "节点")),
                    json = json
                )
        }
    }

    private object RuntimeScreen {
        var width: Int = 1080
        var height: Int = 2400
    }

    companion object {
        private const val DOUYIN_PACKAGE = "com.ss.android.ugc.aweme"
        private const val MAX_CONTROLS = 160
        private const val MAX_LABEL_LENGTH = 32
        private const val REPORT_INTERVAL_MS = 1800L
        private const val AUTO_CAPTURE_ENABLED = false
        private const val KEY_SELECTED_FEATURE = "selected_feature"
        private const val KEY_FEATURE_RUNNING = "feature_running"
        private const val KEY_APP_LOOP_REPEAT = "app_loop_repeat"
        private const val MAX_FLOW_STEPS = 220
        const val ACTION_RUNNER_COMMAND = "com.autotask.permission.RUNNER_COMMAND"
        const val EXTRA_RUNNER_COMMAND = "runner_command"
        const val EXTRA_FEATURE_NAME = "feature_name"
        const val EXTRA_APP_LOOP_REPEAT = "app_loop_repeat"
        private const val COMMAND_START = "start"
        private const val COMMAND_PAUSE = "pause"

        private fun randomSeconds(minSeconds: Double, maxSeconds: Double): Double {
            val min = minSeconds.coerceAtLeast(0.0)
            val max = maxSeconds.coerceAtLeast(min)
            if (max <= min) return min
            return min + Random.nextDouble() * (max - min)
        }

        private fun formatSeconds(seconds: Double): String {
            val rounded = kotlin.math.round(seconds * 10.0) / 10.0
            return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
        }
    }
}
