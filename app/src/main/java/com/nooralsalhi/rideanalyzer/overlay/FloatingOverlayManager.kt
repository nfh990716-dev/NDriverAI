package com.nooralsalhi.rideanalyzer.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.nooralsalhi.rideanalyzer.data.RideSettings
import com.nooralsalhi.rideanalyzer.domain.RideCalculation
import com.nooralsalhi.rideanalyzer.domain.RideRating

class FloatingOverlayManager(private val context: Context) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = context.getSharedPreferences("overlay_position", Context.MODE_PRIVATE)

    fun show(calc: RideCalculation, settings: RideSettings) {
        lastCalculation = calc
        lastSettings = settings
        if (!android.provider.Settings.canDrawOverlays(context)) return
        val existing = currentView
        if (existing != null) {
            update(existing, calc, settings)
            return
        }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb((settings.overlayAlpha.coerceIn(0.70f, 1f) * 255).toInt(), 13, 25, 20))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.rgb(48, 170, 117))
            }
        }
        currentView = panel
        update(panel, calc, settings)
        val params = WindowManager.LayoutParams(
            dp(266), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", dp(12)); y = prefs.getInt("y", dp(120))
        }
        panel.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> { downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y; return true }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = startX + (event.rawX - downX).toInt()
                        params.y = startY + (event.rawY - downY).toInt()
                        runCatching { wm.updateViewLayout(panel, params) }
                        return true
                    }
                    MotionEvent.ACTION_UP -> { prefs.edit().putInt("x", params.x).putInt("y", params.y).apply(); return true }
                }
                return false
            }
        })
        runCatching { wm.addView(panel, params) }.onFailure { currentView = null }
    }

    private fun update(panel: LinearLayout, c: RideCalculation, s: RideSettings) {
        panel.removeAllViews()
        val color = when (c.rating) {
            RideRating.BAD -> Color.rgb(255, 83, 83)
            RideRating.AVERAGE -> Color.rgb(255, 185, 65)
            RideRating.GOOD -> Color.rgb(71, 205, 132)
            RideRating.EXCELLENT -> Color.rgb(42, 226, 157)
        }
        panel.addView(text("Noor Al-Salhi  •  تحليل تلقائي", 12, Color.LTGRAY, true))
        val netText = if (c.netProfitIqd >= 0) "+${fmt(c.netProfitIqd)}" else "−${fmt(kotlin.math.abs(c.netProfitIqd))}"
        panel.addView(text("صافي الربح  $netText د.ع", 20, color, true))
        panel.addView(text("التقييم: ${c.rating.titleAr}", 17, color, true))
        panel.addView(text("الأجرة: ${fmt(c.fareIqd)} د.ع", 14, Color.WHITE))
        panel.addView(text("المسافة المحتسبة: ${"%.1f".format(c.distanceKm)} كم", 14, Color.WHITE))
        panel.addView(text("إجمالي التكاليف: ${fmt(c.totalCostIqd)} د.ع", 14, Color.WHITE))
        if (s.showCostDetails && !s.overlayCompact) {
            panel.addView(text("عمولة ${fmt(c.commissionIqd)}  •  وقود ${fmt(c.fuelIqd)}", 11, Color.LTGRAY))
            panel.addView(text("صيانة ${fmt(c.maintenanceIqd)}  •  إطارات ${fmt(c.tyresIqd)}", 11, Color.LTGRAY))
            panel.addView(text("استهلاك ${fmt(c.depreciationIqd)}  •  احتياطي ${fmt(c.reserveIqd)}", 11, Color.LTGRAY))
            panel.addView(text("إضافي ${fmt(c.extraIqd)} د.ع", 11, Color.LTGRAY))
        }
        if (c.isEstimated) panel.addView(text("الحساب تقديري – تحقق من مسافة الوصول", 10, Color.rgb(255, 205, 110)))
        val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END }
        val hide = text("إخفاء", 12, Color.WHITE, true).apply { setPadding(dp(12), dp(8), dp(12), dp(4)); setOnClickListener { remove() } }
        val mini = text("تصغير", 12, Color.WHITE, true).apply { setPadding(dp(12), dp(8), dp(12), dp(4)); setOnClickListener {
            val collapsed = context.getSharedPreferences("overlay_position", Context.MODE_PRIVATE).getBoolean("collapsed", false)
            context.getSharedPreferences("overlay_position", Context.MODE_PRIVATE).edit().putBoolean("collapsed", !collapsed).apply()
            if (!collapsed) { panel.removeAllViews(); panel.addView(text("Noor  •  ${fmt(c.netProfitIqd)} د.ع  •  ${c.rating.titleAr}", 15, color, true)) }
            else update(panel, c, s)
        } }
        actions.addView(mini); actions.addView(hide); panel.addView(actions)
    }

    private fun text(value: String, size: Int, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); gravity = Gravity.END
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        setPadding(0, dp(2), 0, dp(2)); textDirection = View.TEXT_DIRECTION_RTL
    }

    fun showLast() {
        val calc = lastCalculation ?: return
        val settings = lastSettings ?: return
        show(calc, settings)
    }

    fun remove() { currentView?.let { runCatching { wm.removeView(it) } }; currentView = null }
    private fun fmt(v: Long) = String.format(java.util.Locale.US, "%,d", v)
    private fun dp(v: Int) = (v * context.resources.displayMetrics.density).toInt()
    companion object {
        @Volatile private var currentView: LinearLayout? = null
        @Volatile private var lastCalculation: RideCalculation? = null
        @Volatile private var lastSettings: RideSettings? = null
    }
}
