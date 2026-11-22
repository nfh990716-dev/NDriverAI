package com.nooralsalhi.rideanalyzer

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nooralsalhi.rideanalyzer.data.RideEntity
import com.nooralsalhi.rideanalyzer.data.RideSettings
import com.nooralsalhi.rideanalyzer.data.hasFuelProfile
import com.nooralsalhi.rideanalyzer.domain.ArabicNumberNormalizer
import com.nooralsalhi.rideanalyzer.domain.DistanceMode
import com.nooralsalhi.rideanalyzer.domain.FuelMode
import com.nooralsalhi.rideanalyzer.service.ScreenCaptureService
import com.nooralsalhi.rideanalyzer.overlay.FloatingOverlayManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale

private val Bg = Color(0xFF0B120F)
private val CardBg = Color(0xFF14211A)
private val Green = Color(0xFF35D18B)
private val Muted = Color(0xFF9DB2A5)

class MainActivity : ComponentActivity() {
    private val captureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) ScreenCaptureService.start(this, result.resultCode, result.data!!)
    }
    private val notificationLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { NoorApp(this, onStartCapture = { requestCapture() }, onStopCapture = { ScreenCaptureService.stop(this) }) }
    }

    private fun requestCapture() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        captureLauncher.launch(manager.createScreenCaptureIntent())
    }
}

@Composable
private fun NoorApp(activity: MainActivity, onStartCapture: () -> Unit, onStopCapture: () -> Unit) {
    val app = activity.application as NoorApplication
    val settings by app.settings.settings.collectAsStateWithLifecycle(initialValue = RideSettings())
    val rides by app.database.rideDao().latest().collectAsStateWithLifecycle(initialValue = emptyList())
    var tab by remember { mutableStateOf(0) }
    val runtime = remember { activity.getSharedPreferences("runtime", Activity.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1500); refresh++ } }
    val access = remember(refresh) { runtime.getBoolean("accessibility_connected", false) }
    val targetVisible = remember(refresh) { runtime.getBoolean("target_app_visible", false) }
    val overlayAllowed = remember(refresh) { Settings.canDrawOverlays(activity) }
    MaterialTheme(colorScheme = darkColorScheme(primary = Green, background = Bg, surface = CardBg, onSurface = Color.White)) {
        Scaffold(containerColor = Bg, bottomBar = {
            NavigationBar(containerColor = CardBg, contentColor = Green) {
                listOf("الرئيسية", "الإعدادات", "السجل", "الإحصائيات", "اختبار القراءة").forEachIndexed { index, title ->
                    NavigationBarItem(selected = tab == index, onClick = { tab = index }, icon = {
                        Icon(when (index) { 0 -> Icons.Default.Dashboard; 1 -> Icons.Default.Settings; 2 -> Icons.Default.History; 3 -> Icons.Default.BarChart; else -> Icons.Default.Visibility }, null)
                    }, label = { Text(title) })
                }
            }
        }) { pad ->
            when (tab) {
                0 -> HomeScreen(activity, settings, rides.firstOrNull(), access, targetVisible, overlayAllowed, pad,
                    onStartCapture, onStopCapture, onOpenSettings = { tab = 1 })
                1 -> SettingsScreen(settings, onSave = { next ->
                    // validation is performed before persisting
                    scope.launch { app.settings.save(next) }
                }, modifier = Modifier.padding(pad))
                2 -> HistoryScreen(rides, onDelete = { id -> scope.launch { app.database.rideDao().delete(id) } },
                    onClear = { scope.launch { app.database.rideDao().clear() } }, modifier = Modifier.padding(pad))
                3 -> StatsScreen(rides, Modifier.padding(pad))
                else -> ReadTestScreen(activity, rides.firstOrNull(), Modifier.padding(pad))
            }
        }
    }
}

@Composable
private fun HomeScreen(activity: MainActivity, s: RideSettings, last: RideEntity?, access: Boolean, targetVisible: Boolean,
                       overlayAllowed: Boolean, pad: PaddingValues, onStartCapture: () -> Unit, onStopCapture: () -> Unit, onOpenSettings: () -> Unit) {
    val scope = rememberCoroutineScope()
    val app = activity.application as NoorApplication
    var serviceEnabled by remember(s.serviceEnabled) { mutableStateOf(s.serviceEnabled) }
    LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(top = 22.dp, bottom = 20.dp)) {
        item {
            Text("NOOR AL-SALHI", color = Green, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text("مساعد تقييم رحلات التكسي", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("قراءة تلقائية • حساب صافي الربح • دون قبول الرحلة نيابةً عنك", color = Muted, fontSize = 12.sp)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatusRow("خدمة إمكانية الوصول", if (access) "متصلة" else "غير مفعّلة", access)
                    StatusRow("تطبيق Baly Driver", if (targetVisible) "ظاهر الآن" else "بانتظار ظهوره", targetVisible)
                    Text(runtimeReadStatus(activity), color = Muted, fontSize = 11.sp)
                    val readTime = activity.getSharedPreferences("runtime", Activity.MODE_PRIVATE).getLong("last_read_at", 0L)
                    if (readTime > 0) Text("آخر فحص: ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(readTime))}", color = Muted, fontSize = 10.sp)
                    StatusRow("النافذة العائمة", if (overlayAllowed && s.overlayEnabled) "جاهزة" else "تحتاج صلاحية", overlayAllowed && s.overlayEnabled)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }, modifier = Modifier.weight(1f)) { Text("تفعيل القراءة") }
                        OutlinedButton(onClick = { activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}"))) }, modifier = Modifier.weight(1f)) { Text("صلاحية النافذة") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onStartCapture, modifier = Modifier.weight(1f)) { Text("تشغيل OCR الاحتياطي") }
                        OutlinedButton(onClick = onStopCapture, modifier = Modifier.weight(1f)) { Text("إيقاف OCR") }
                    }
                    OutlinedButton(onClick = { FloatingOverlayManager(activity).showLast() }, modifier = Modifier.fillMaxWidth()) {
                        Text("إظهار آخر نتيجة في النافذة العائمة")
                    }
                    Text("OCR يحتاج موافقة تسجيل الشاشة في كل جلسة. ابدأ به فقط إذا لم تستطع خدمة القراءة استخراج النص من Baly Driver.", color = Muted, fontSize = 11.sp)
                }
            }
        }
        if (!s.hasFuelProfile()) item {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2B16))) {
                Text("أكمل إعدادات الوقود أولاً حتى لا يظهر صافي ربح مضلل. أدخل سعر اللتر والاستهلاك أو تكلفة 100 كم.", color = Color(0xFFFFD28A), modifier = Modifier.padding(12.dp), fontSize = 12.sp)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("آخر رحلة محللة", color = Muted, fontSize = 13.sp)
                    if (last == null) Text("لم يتم تحليل أي عرض بعد", color = Color.White, fontSize = 18.sp)
                    else {
                        Text("${money(last.netProfitIqd)} د.ع", color = ratingColor(last.rating), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("التقييم: ${ratingAr(last.rating)}", color = ratingColor(last.rating), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("الأجرة ${money(last.fareIqd)} د.ع  •  ${"%.1f".format(last.distanceKm)} كم", color = Color.White)
                        Text(SimpleDateFormat("yyyy/MM/dd  HH:mm:ss", Locale.getDefault()).format(Date(last.timestamp)), color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    serviceEnabled = !serviceEnabled
                    scope.launch { app.settings.save(s.copy(serviceEnabled = serviceEnabled)) }
                    if (!serviceEnabled) FloatingOverlayManager(activity).remove()
                }, modifier = Modifier.weight(1f)) {
                    Text(if (serviceEnabled) "إيقاف التحليل" else "تشغيل التحليل")
                }
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f)) { Text("إعدادات التكاليف") }
            }
        }
        item { Text("ملاحظة: قراءة Baly Driver لا تعتبر ناجحة إلا بعد اختبار ظهور عرض حقيقي واستخراج الأجرة والمسافة دون تدخل يدوي.", color = Color(0xFFFFC66D), fontSize = 12.sp) }
    }
}

@Composable private fun StatusRow(label: String, value: String, ok: Boolean) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, modifier = Modifier.weight(1f))
        Text("● $value", color = if (ok) Green else Color(0xFFFFB64D), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsScreen(current: RideSettings, onSave: (RideSettings) -> Unit, modifier: Modifier = Modifier) {
    var s by remember(current) { mutableStateOf(current) }
    var message by remember { mutableStateOf("") }
    LazyColumn(modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(top = 22.dp, bottom = 30.dp)) {
        item { Text("الإعدادات", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold) }
        item { SectionTitle("العمولة والوقود") }
        item { NumberField("عمولة التطبيق (%)", s.commissionPercent.toString()) { v -> s = s.copy(commissionPercent = parseDouble(v)) } }
        item { Text("طريقة حساب الوقود", color = Muted) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = s.fuelMode == FuelMode.LITRE_PRICE, onClick = { s = s.copy(fuelMode = FuelMode.LITRE_PRICE) }, label = { Text("سعر اللتر") })
            FilterChip(selected = s.fuelMode == FuelMode.COST_PER_100_KM, onClick = { s = s.copy(fuelMode = FuelMode.COST_PER_100_KM) }, label = { Text("تكلفة 100 كم") })
        } }
        if (s.fuelMode == FuelMode.LITRE_PRICE) {
            item { NumberField("سعر لتر الوقود (د.ع)", s.fuelPricePerLitre.toString()) { s = s.copy(fuelPricePerLitre = parseDouble(it)) } }
            item { NumberField("استهلاك السيارة (لتر / 100 كم)", s.consumptionPer100Km.toString()) { s = s.copy(consumptionPer100Km = parseDouble(it)) } }
        } else item { NumberField("تكلفة الوقود لكل 100 كم (د.ع)", s.fuelCostPer100Km.toString()) { s = s.copy(fuelCostPer100Km = parseDouble(it)) } }
        item { SectionTitle("مصاريف كل كيلومتر – بالدينار") }
        item { NumberField("الصيانة / كم", s.maintenancePerKm.toString()) { s = s.copy(maintenancePerKm = parseDouble(it)) } }
        item { NumberField("الإطارات / كم", s.tyresPerKm.toString()) { s = s.copy(tyresPerKm = parseDouble(it)) } }
        item { NumberField("استهلاك السيارة / كم", s.depreciationPerKm.toString()) { s = s.copy(depreciationPerKm = parseDouble(it)) } }
        item { NumberField("الاحتياطي / كم", s.reservePerKm.toString()) { s = s.copy(reservePerKm = parseDouble(it)) } }
        item { NumberField("تكاليف إضافية / كم", s.extraPerKm.toString()) { s = s.copy(extraPerKm = parseDouble(it)) } }
        item { NumberField("تكاليف إضافية ثابتة لكل رحلة", s.extraPerRide.toString()) { s = s.copy(extraPerRide = parseDouble(it)) } }
        item { SectionTitle("المسافة المحتسبة") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = s.distanceMode == DistanceMode.TRIP_ONLY, onClick = { s = s.copy(distanceMode = DistanceMode.TRIP_ONLY) }, label = { Text("مسافة الرحلة فقط") })
            FilterChip(selected = s.distanceMode == DistanceMode.PICKUP_PLUS_TRIP, onClick = { s = s.copy(distanceMode = DistanceMode.PICKUP_PLUS_TRIP) }, label = { Text("الوصول + الرحلة") })
        } }
        if (s.distanceMode == DistanceMode.PICKUP_PLUS_TRIP) item { NumberField("مسافة الوصول الافتراضية (كم)", s.defaultPickupKm.toString()) { s = s.copy(defaultPickupKm = parseDouble(it)) } }
        item { NumberField("مسافة إضافية ثابتة (كم)", s.extraDistanceKm.toString()) { s = s.copy(extraDistanceKm = parseDouble(it)) } }
        item { NumberField("احتياط المسافة (%)", s.distanceBufferPercent.toString()) { s = s.copy(distanceBufferPercent = parseDouble(it)) } }
        item { SectionTitle("حدود التقييم – صافي ربح الرحلة") }
        item { NumberField("حد سيء / بداية المتوسط (د.ع)", s.averageFrom.toString()) { value ->
            val n = parseLong(value); s = s.copy(badBelow = n, averageFrom = n)
        } }
        item { NumberField("بداية الجيد (د.ع)", s.goodFrom.toString()) { s = s.copy(goodFrom = parseLong(it)) } }
        item { NumberField("بداية الممتاز (د.ع)", s.excellentFrom.toString()) { s = s.copy(excellentFrom = parseLong(it)) } }
        item { SectionTitle("القراءة والنافذة") }
        item { NumberField("اسم حزمة التطبيق المستهدف (Package)", s.targetPackage) { s = s.copy(targetPackage = it) } }
        item { Text("منطقة OCR كنسبة من الشاشة (0–100). اتركها 0 / 0 / 100 / 100 حتى تضبطها بعد اختبار Baly Driver.", color = Muted, fontSize = 11.sp) }
        item { NumberField("قص OCR – اليسار (%)", s.ocrCropLeftPercent.toString()) { s = s.copy(ocrCropLeftPercent = parseInt(it, 0)) } }
        item { NumberField("قص OCR – الأعلى (%)", s.ocrCropTopPercent.toString()) { s = s.copy(ocrCropTopPercent = parseInt(it, 0)) } }
        item { NumberField("قص OCR – العرض (%)", s.ocrCropWidthPercent.toString()) { s = s.copy(ocrCropWidthPercent = parseInt(it, 100)) } }
        item { NumberField("قص OCR – الارتفاع (%)", s.ocrCropHeightPercent.toString()) { s = s.copy(ocrCropHeightPercent = parseInt(it, 100)) } }
        item { SwitchRow("إظهار النافذة العائمة", s.overlayEnabled) { s = s.copy(overlayEnabled = it) } }
        item { SwitchRow("إظهار تفاصيل التكاليف في النافذة", s.showCostDetails) { s = s.copy(showCostDetails = it) } }
        item { SwitchRow("وضع النافذة المصغّر", s.overlayCompact) { s = s.copy(overlayCompact = it) } }
        item { Text("شفافية النافذة", color = Muted) }
        item { Slider(value = s.overlayAlpha, onValueChange = { s = s.copy(overlayAlpha = it) }, valueRange = 0.70f..1.0f) }
        item { Text("${(s.overlayAlpha * 100).toInt()}%", color = Green, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth()) }
        item { Text("اسم الحزمة الافتراضي هو app.baly.driver. استخدم اسم الحزمة الكامل، وليس جزءاً منه، إذا أردت تغيير التطبيق المستهدف.", color = Muted, fontSize = 11.sp) }
        item {
            Button(onClick = {
                val valid = s.commissionPercent in 0.0..100.0 && listOf(s.fuelPricePerLitre, s.consumptionPer100Km, s.fuelCostPer100Km,
                    s.maintenancePerKm, s.tyresPerKm, s.depreciationPerKm, s.reservePerKm, s.extraPerKm, s.extraPerRide,
                    s.defaultPickupKm, s.extraDistanceKm, s.distanceBufferPercent).all { it.isFinite() && it >= 0.0 } &&
                    s.ocrCropLeftPercent in 0..99 && s.ocrCropTopPercent in 0..99 && s.ocrCropWidthPercent in 1..100 && s.ocrCropHeightPercent in 1..100 &&
                    s.averageFrom >= 0 && s.badBelow == s.averageFrom && s.averageFrom < s.goodFrom && s.goodFrom < s.excellentFrom && s.targetPackage.isNotBlank()
                if (valid && s.hasFuelProfile()) { onSave(s); message = "تم حفظ الإعدادات" } else message = "أدخل بيانات الوقود، وتأكد من العمولة والحدود والتكاليف ونسب قص OCR."
            }, modifier = Modifier.fillMaxWidth()) { Text("حفظ الإعدادات") }
            if (message.isNotBlank()) Text(message, color = if (message.startsWith("تم")) Green else Color(0xFFFFC66D), fontSize = 12.sp)
        }
    }
}

@Composable private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = { next ->
        val filtered = if (label.contains("Package")) next.filter { it.isLetterOrDigit() || it == '.' || it == '_' }
            else next.filter { it.isDigit() || it == '.' || it == ',' || it == '٫' || it == '٬' || it == '-' }
        onChange(filtered)
    }, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (label.contains("Package")) KeyboardType.Text else KeyboardType.Decimal),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Green, unfocusedBorderColor = Color(0xFF405348), focusedLabelColor = Green))
}

@Composable private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(label, color = Color.White, modifier = Modifier.weight(1f)); Switch(checked, onChange) }
}

@Composable private fun ReadTestScreen(activity: MainActivity, last: RideEntity?, modifier: Modifier = Modifier) {
    val prefs = remember { activity.getSharedPreferences("runtime", Activity.MODE_PRIVATE) }
    var refresh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1000); refresh++ } }
    val status = remember(refresh) { prefs.getString("read_status", "لم يتم استقبال حدث قراءة بعد") ?: "لم يتم استقبال حدث قراءة بعد" }
    val packageName = remember(refresh) { prefs.getString("last_seen_package", "غير معروف") ?: "غير معروف" }
    val lastRead = remember(refresh) { prefs.getLong("last_read_at", 0L) }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("اختبار قراءة الشاشة", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("هذه الشاشة تعرض حالة الاستخراج فقط. الاختبار الحقيقي يكون بظهور عرض رحلة جديد داخل Baly Driver.", color = Muted, fontSize = 12.sp)
        Card(colors = CardDefaults.cardColors(containerColor = CardBg)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("نتيجة القراءة", color = Green, fontWeight = FontWeight.Bold)
                Text(status, color = Color.White, fontSize = 16.sp)
                Text("آخر حزمة مرصودة: $packageName", color = Muted, fontSize = 11.sp)
                Text(if (lastRead > 0) "آخر قراءة: ${SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(lastRead))}" else "لا توجد قراءة مسجلة", color = Muted, fontSize = 11.sp)
            }
        }
        if (last != null) {
            Card(colors = CardDefaults.cardColors(containerColor = CardBg)) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("آخر طلب تم تحليله", color = Green, fontWeight = FontWeight.Bold)
                    Text("الأجرة: ${money(last.fareIqd)} د.ع", color = Color.White)
                    Text("المسافة: ${"%.1f".format(last.distanceKm)} كم", color = Color.White)
                    Text("صافي الربح: ${money(last.netProfitIqd)} د.ع", color = ratingColor(last.rating), fontWeight = FontWeight.Bold)
                    Text("درجة الثقة: ${(last.confidence * 100).toInt()}% • ${ratingAr(last.rating)}", color = Muted, fontSize = 11.sp)
                }
            }
        }
        Text("إذا ظهر أن النص موجود لكن الأجرة أو المسافة غير مكتملة، فهذا يعني أن كلمات أو عناصر بطاقة Baly Driver لا تزال تحتاج إلى ضبط محلّلها بناءً على عرض حقيقي.", color = Color(0xFFFFC66D), fontSize = 12.sp)
    }
}

@Composable private fun HistoryScreen(rides: List<RideEntity>, onDelete: (Long) -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    var confirmClear by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("الكل") }
    var search by remember { mutableStateOf("") }
    val filtered = rides.filter { ride ->
        (filter == "الكل" || ratingAr(ride.rating) == filter) &&
            (search.isBlank() || ride.fareIqd.toString().contains(search) || ride.netProfitIqd.toString().contains(search) || ride.timestamp.toString().contains(search))
    }
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(22.dp)); Text("سجل الرحلات", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = search, onValueChange = { search = it.filter(Char::isDigit) }, label = { Text("بحث بالأجرة أو صافي الربح") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("الكل", "سيء", "متوسط", "جيد", "ممتاز").forEach { FilterChip(filter == it, { filter = it }, label = { Text(it) }) } }
        TextButton(onClick = { confirmClear = true }) { Text("مسح السجل بالكامل", color = Color(0xFFFF7777)) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(filtered, key = { it.id }) { ride ->
                var expanded by remember(ride.id) { mutableStateOf(false) }
                Card(colors = CardDefaults.cardColors(containerColor = CardBg)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${money(ride.netProfitIqd)} د.ع", color = ratingColor(ride.rating), fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                            Text(ratingAr(ride.rating), color = ratingColor(ride.rating), fontWeight = FontWeight.Bold)
                        }
                        Text("أجرة ${money(ride.fareIqd)} • ${"%.1f".format(ride.distanceKm)} كم • تكاليف ${money(ride.totalCostIqd)}", color = Color.White, fontSize = 12.sp)
                        Text(SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(ride.timestamp)) + if (ride.isEstimated) " • تقديري" else "", color = Muted, fontSize = 10.sp)
                        Row {
                            TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "إخفاء التفاصيل" else "تفاصيل التكاليف") }
                            TextButton(onClick = { onDelete(ride.id) }) { Text("حذف", color = Color(0xFFFF7777)) }
                        }
                        if (expanded) {
                            Divider(color = Color(0xFF405348))
                            Text("العمولة: ${money(ride.commissionIqd)} د.ع", color = Muted, fontSize = 12.sp)
                            Text("الوقود: ${money(ride.fuelIqd)} د.ع", color = Muted, fontSize = 12.sp)
                            Text("الصيانة: ${money(ride.maintenanceIqd)} د.ع", color = Muted, fontSize = 12.sp)
                            Text("الإطارات: ${money(ride.tyresIqd)} د.ع", color = Muted, fontSize = 12.sp)
                            Text("استهلاك السيارة: ${money(ride.depreciationIqd)} د.ع", color = Muted, fontSize = 12.sp)
                            Text("الاحتياطي: ${money(ride.reserveIqd)} د.ع", color = Muted, fontSize = 12.sp)
                            Text("مصاريف إضافية: ${money(ride.extraIqd)} د.ع", color = Muted, fontSize = 12.sp)
                            Text("ثقة الاستخراج: ${(ride.confidence * 100).toInt()}% • المصدر: ${ride.source}", color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("مسح سجل الرحلات؟") },
        text = { Text("سيتم حذف جميع الرحلات المحفوظة ولا يمكن التراجع.") }, confirmButton = { TextButton(onClick = { onClear(); confirmClear = false }) { Text("مسح") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("إلغاء") } })
}

@Composable private fun StatsScreen(rides: List<RideEntity>, modifier: Modifier = Modifier) {
    val now = Calendar.getInstance()
    val dayStart = (now.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val weekStart = (now.clone() as Calendar).apply {
        set(Calendar.DAY_OF_WEEK, firstDayOfWeek); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val monthStart = (now.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    val day = rides.filter { it.timestamp >= dayStart }
    val week = rides.filter { it.timestamp >= weekStart }
    val month = rides.filter { it.timestamp >= monthStart }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(8.dp)); Text("إحصائيات الأرباح", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        StatCard("اليوم", day.sumOf { it.netProfitIqd }, day.size)
        StatCard("هذا الأسبوع", week.sumOf { it.netProfitIqd }, week.size)
        StatCard("هذا الشهر", month.sumOf { it.netProfitIqd }, month.size)
        val average = if (rides.isEmpty()) 0 else rides.sumOf { it.netProfitIqd } / rides.size
        StatCard("متوسط ربح الرحلة (كل السجل)", average.toLong(), rides.size)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("سيء", "متوسط", "جيد", "ممتاز").forEach { rating ->
            Card(Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = CardBg)) { Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(ratingAr(rating), color = ratingColor(rating), fontSize = 11.sp); Text(rides.count { ratingAr(it.rating) == rating }.toString(), color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            } }
        } }
        Text("الإحصائيات تُحتسب من الرحلات المحفوظة فقط، وفق اليوم والأسبوع والشهر الحالي.", color = Muted, fontSize = 11.sp)
    }
}

@Composable private fun StatCard(title: String, total: Long, count: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = CardBg), shape = RoundedCornerShape(16.dp)) { Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, color = Muted, fontSize = 12.sp); Text("${money(total)} د.ع", color = Green, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
        Text("$count رحلة", color = Color.White)
    } }
}

@Composable private fun SectionTitle(text: String) { Text(text, color = Green, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp)) }
private fun runtimeReadStatus(activity: MainActivity): String = activity.getSharedPreferences("runtime", Activity.MODE_PRIVATE)
    .getString("read_status", "لم تصل قراءة اختبارية بعد – افتح Baly Driver لبدء المراقبة") ?: "لم تصل قراءة اختبارية بعد"
private fun parseDouble(value: String): Double = ArabicNumberNormalizer.parseNumber(value) ?: 0.0
private fun parseLong(value: String): Long = ArabicNumberNormalizer.parseNumber(value)?.toLong() ?: 0L
private fun parseInt(value: String, fallback: Int): Int = ArabicNumberNormalizer.parseNumber(value)?.toInt() ?: fallback
private fun money(value: Long) = String.format(Locale.US, "%,d", value)
private fun ratingAr(code: String): String = when (code) { "BAD" -> "سيء"; "AVERAGE" -> "متوسط"; "GOOD" -> "جيد"; "EXCELLENT" -> "ممتاز"; else -> code }
private fun ratingColor(code: String): Color = when (code) { "BAD" -> Color(0xFFFF6262); "AVERAGE" -> Color(0xFFFFB64D); "GOOD" -> Color(0xFF62D995); "EXCELLENT" -> Color(0xFF31E7A1); else -> Color.White }
