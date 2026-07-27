package com.autotask.permission

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import kotlin.math.max

class AutomationAccessibilityService : AccessibilityService() {

    private val executor = Executors.newSingleThreadExecutor()
    private var lastSnapshotKey = ""
    private var lastReportTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (packageName != DOUYIN_PACKAGE && packageName != this.packageName) return

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
        sendActivityLog("无障碍服务已暂停")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        sendActivityLog("无障碍服务已连接，开始采集当前 App 控件")
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
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
            }.onSuccess { statusCode ->
                if (statusCode in 200..299) {
                    sendActivityLog("已采集 ${appLabel(packageName)}：${controls.size} 个控件")
                }
            }.onFailure { error ->
                sendActivityLog("控件上传失败：${error.message ?: "网络异常"}")
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

    private fun sendActivityLog(message: String) {
        sendBroadcast(
            Intent(FloatingLogService.ACTION_ACTIVITY_LOG)
                .setPackage(packageName)
                .putExtra(FloatingLogService.EXTRA_MESSAGE, message)
        )
    }

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

    companion object {
        private const val DOUYIN_PACKAGE = "com.ss.android.ugc.aweme"
        private const val MAX_CONTROLS = 160
        private const val MAX_LABEL_LENGTH = 32
        private const val REPORT_INTERVAL_MS = 1800L
    }
}
