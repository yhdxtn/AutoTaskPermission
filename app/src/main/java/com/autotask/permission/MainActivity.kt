package com.autotask.permission

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.annotation.TargetApi
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowInsets
import android.view.accessibility.AccessibilityManager
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var rootContainer: LinearLayout
    private lateinit var overlayRow: View
    private lateinit var accessibilityRow: View
    private lateinit var overlaySwitch: Switch
    private lateinit var accessibilitySwitch: Switch
    private lateinit var overlayStatus: TextView
    private lateinit var accessibilityStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        rootContainer = findViewById(R.id.rootContainer)
        overlayRow = findViewById(R.id.overlayRow)
        accessibilityRow = findViewById(R.id.accessibilityRow)
        overlaySwitch = findViewById(R.id.overlaySwitch)
        accessibilitySwitch = findViewById(R.id.accessibilitySwitch)
        overlayStatus = findViewById(R.id.overlayStatus)
        accessibilityStatus = findViewById(R.id.accessibilityStatus)

        applySystemBarInsets()

        // 开关仅展示当前权限状态；点击整行进入对应系统设置页面。
        overlaySwitch.isClickable = false
        overlaySwitch.isFocusable = false
        accessibilitySwitch.isClickable = false
        accessibilitySwitch.isFocusable = false

        overlayRow.setOnClickListener { openOverlayPermissionSettings() }
        accessibilityRow.setOnClickListener { openAccessibilitySettings() }

        updatePermissionStates()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
    }

    private fun applySystemBarInsets() {
        // Android 11+ 使用新版 Insets API；旧系统保持 XML 中的安全边距。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            applySystemBarInsetsApi30()
        }
    }

    @TargetApi(Build.VERSION_CODES.R)
    private fun applySystemBarInsetsApi30() {
        val initialLeft = rootContainer.paddingLeft
        val initialTop = rootContainer.paddingTop
        val initialRight = rootContainer.paddingRight
        val initialBottom = rootContainer.paddingBottom

        rootContainer.setOnApplyWindowInsetsListener { view, insets ->
            val systemBars = insets.getInsets(
                WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars()
            )
            view.setPadding(
                initialLeft + systemBars.left,
                initialTop + systemBars.top,
                initialRight + systemBars.right,
                initialBottom + systemBars.bottom
            )
            insets
        }
    }

    private fun updatePermissionStates() {
        val overlayGranted = Settings.canDrawOverlays(this)
        val accessibilityGranted = isAccessibilityServiceEnabled()

        renderState(overlaySwitch, overlayStatus, overlayGranted)
        renderState(accessibilitySwitch, accessibilityStatus, accessibilityGranted)
    }

    private fun renderState(permissionSwitch: Switch, statusView: TextView, granted: Boolean) {
        permissionSwitch.isChecked = granted
        statusView.setText(if (granted) R.string.permission_granted else R.string.permission_not_granted)
        statusView.setTextColor(
            if (granted) Color.rgb(25, 135, 84) else Color.rgb(112, 117, 122)
        )
    }

    private fun openOverlayPermissionSettings() {
        val appSpecificIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )

        try {
            startActivity(appSpecificIntent)
        } catch (_: ActivityNotFoundException) {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            } catch (_: ActivityNotFoundException) {
                showSettingsUnavailableMessage()
            }
        }
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            showSettingsUnavailableMessage()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager =
            getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val expectedComponent = ComponentName(this, AutomationAccessibilityService::class.java)

        return accessibilityManager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { service ->
                val serviceInfo = service.resolveInfo.serviceInfo
                ComponentName(serviceInfo.packageName, serviceInfo.name) == expectedComponent
            }
    }

    private fun showSettingsUnavailableMessage() {
        Toast.makeText(this, R.string.settings_unavailable, Toast.LENGTH_SHORT).show()
    }
}
