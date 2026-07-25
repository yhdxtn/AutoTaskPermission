package com.autotask.permission

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * v1.0.0 仅用于让用户在系统设置中启用无障碍服务。
 * 本版本不读取界面、不查找控件、不执行点击或手势。
 */
class AutomationAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // v1.0.0：不处理任何事件。
    }

    override fun onInterrupt() {
        // v1.0.0：无正在执行的任务。
    }
}
