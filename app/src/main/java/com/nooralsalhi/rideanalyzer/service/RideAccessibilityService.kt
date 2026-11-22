package com.nooralsalhi.rideanalyzer.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.nooralsalhi.rideanalyzer.domain.RidePipeline
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import com.nooralsalhi.rideanalyzer.NoorApplication
import com.nooralsalhi.rideanalyzer.data.DEFAULT_TARGET_PACKAGE

class RideAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastSubmittedTextHash = 0
    private var lastSubmittedAt = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        getSharedPreferences("runtime", MODE_PRIVATE).edit().putBoolean("accessibility_connected", true).apply()
        serviceInfo = serviceInfo.apply { flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString()?.lowercase() ?: return
        val runtime = getSharedPreferences("runtime", MODE_PRIVATE)
        val cachedTarget = runtime.getString("target_package_cache", null).orEmpty().lowercase().trim()
        // Migrate the previous project's placeholder filter to the confirmed Baly Driver package.
        val target = if (cachedTarget.isBlank() || cachedTarget == "super") DEFAULT_TARGET_PACKAGE else cachedTarget
        if (target != cachedTarget) runtime.edit().putString("target_package_cache", target).apply()
        val enabled = runtime.getBoolean("service_enabled_cache", true)
        // Exact match prevents accidentally monitoring a different app with a similar package name.
        val allowedPackages = setOf("app.baly.driver", "online.toprank.sardeliveryboy")
        val appIsTarget = enabled && packageName in allowedPackages
        runtime.edit().putBoolean("target_app_visible", appIsTarget).putString("last_seen_package", packageName).apply()
        if (!appIsTarget) return

        // Read the node tree synchronously during the event callback; AccessibilityEvent
        // objects must not be retained and accessed later from a coroutine.
        val root = rootInActiveWindow ?: event.source ?: return
        val text = StringBuilder()
        try { collectVisibleText(root, text, 0) } finally { runCatching { root.recycle() } }
        val candidate = text.toString().trim()
        if (candidate.length < 5) return
        val now = System.currentTimeMillis()
        val hash = candidate.hashCode()
        if (hash == lastSubmittedTextHash && now - lastSubmittedAt < 1800L) return
        lastSubmittedTextHash = hash; lastSubmittedAt = now
        runtime.edit()
            .putLong("last_read_at", now)
            .putString("last_read_preview", candidate.take(180))
            .putString("read_status", if (com.nooralsalhi.rideanalyzer.domain.RideTextParser().parse(candidate) != null) "تم استخراج الأجرة والمسافة" else "النص ظاهر لكن بيانات الأجرة/المسافة غير مكتملة")
            .apply()

        scope.launch {
            val settings = (application as NoorApplication).settings.settings.first()
            if (!settings.serviceEnabled) return@launch
            RidePipeline(this@RideAccessibilityService).analyze(candidate, "ACCESSIBILITY")
        }
    }

    private fun collectVisibleText(node: AccessibilityNodeInfo, out: StringBuilder, depth: Int) {
        if (depth > 28 || out.length > 6000) return
        if (node.isPassword) return
        if (node.isVisibleToUser) {
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.append(it).append('\n') }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.append(it).append('\n') }
        }
        for (i in 0 until node.childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            collectVisibleText(child, out, depth + 1)
            child.recycle()
        }
    }

    override fun onInterrupt() = Unit
    override fun onDestroy() {
        getSharedPreferences("runtime", MODE_PRIVATE).edit().putBoolean("accessibility_connected", false).putBoolean("target_app_visible", false).apply()
        scope.cancel()
        super.onDestroy()
    }
}
