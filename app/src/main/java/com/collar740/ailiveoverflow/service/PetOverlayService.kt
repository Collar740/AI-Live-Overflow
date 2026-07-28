package com.collar740.ailiveoverflow.service

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.os.*
import android.provider.Settings
import android.util.Log
import android.view.*
import android.webkit.*
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.io.*
import java.net.*
import java.util.*
import kotlin.concurrent.thread

class PetOverlayService : Service() {
    private var windowManager: WindowManager? = null
    private var overlayView: WebView? = null
    private var params: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())
    private var lastAppPackage: String = ""
    private var lastScreenshotTime: Long = 0
    private var heat: Int = 0
    private var expression: String = "idle"
    private var bubble: String = ""
    private var mood: String = "neutral"
    private var idleTimer: Long = 0
    private var lastInteraction: Long = System.currentTimeMillis()
    private var touchStartTime: Long = 0
    private var hasMoved: Boolean = false
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var lastTapTime: Long = 0
    private var consecutiveTaps: Int = 0
    private var lastTapTimestamp: Long = 0
    private var isSleeping: Boolean = false
    private var sleepStartTime: Long = 0
    private var currentHour: Int = 0

    companion object {
        private const val TAG = "PetOverlay"
        private const val CHANNEL_ID = "pet_overlay_channel"
        private const val NOTIFICATION_ID = 1001
        private const val PET_SIZE_DP = 180
        private const val PET_HEIGHT_DP = 240
        private const val POLL_INTERVAL_MS = 5000L
        private const val IDLE_TIMEOUT_MS = 30000L
        private const val SCREENSHOT_COOLDOWN_MS = 2000L
        private const val SLEEP_THRESHOLD_MS = 20 * 60 * 1000L
        private const val QUICK_SWITCH_WINDOW_MS = 60 * 1000L
        private const val QUICK_SWITCH_THRESHOLD = 3
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Pet service created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("桌宠已启动"))
        setupOverlay()
        startPolling()
        startAppDetection()
        startScreenshotMonitor()
        startBatteryMonitor()
        updateTimeBasedBehavior()
    }

    // ==================== OVERLAY SETUP ====================
    private fun setupOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        params = WindowManager.LayoutParams(
            dpToPx(PET_SIZE_DP),
            dpToPx(PET_HEIGHT_DP),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        overlayView = WebView(this).apply {
            setBackgroundColor(0x00000000)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
                setSupportZoom(false)
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "Pet HTML loaded")
                    loadInitialState()
                }
            }
            loadUrl("file:///android_asset/pet.html")
            setOnTouchListener(createTouchListener())
        }

        windowManager?.addView(overlayView, params)
        Log.d(TAG, "Overlay added")
    }

    private fun loadInitialState() {
        overlayView?.evaluateJavascript(
            "javascript:window.petEngine && window.petEngine.setState('$expression', '$bubble', $heat, '$mood')",
            null
        )
    }

    // ==================== GESTURE SYSTEM ====================
    private fun createTouchListener(): View.OnTouchListener {
        return View.OnTouchListener { _, event ->
            if (isSleeping && event.action == MotionEvent.ACTION_DOWN) {
                wakeUp()
                return@OnTouchListener true
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    touchStartTime = System.currentTimeMillis()
                    hasMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        hasMoved = true
                        params?.x = initialX + dx
                        params?.y = initialY + dy
                        windowManager?.updateViewLayout(overlayView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val elapsed = System.currentTimeMillis() - touchStartTime
                    if (!hasMoved) {
                        when {
                            elapsed > 600 -> onLongPress()
                            System.currentTimeMillis() - lastTapTime < 300 -> onDoubleTap()
                            else -> onTap()
                        }
                    }
                    lastInteraction = System.currentTimeMillis()
                    true
                }
                else -> false
            }
        }
    }

    private fun onTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTimestamp < 2000) {
            consecutiveTaps++
        } else {
            consecutiveTaps = 1
        }
        lastTapTimestamp = now

        when (consecutiveTaps) {
            3 -> playAnimation("tap_3")
            5 -> playAnimation("tap_5")
            8 -> playAnimation("tap_8")
            else -> playAnimation("tap")
        }
        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.onTap()", null
        )
    }

    private fun onDoubleTap() {
        playAnimation("double_tap")
        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.onDoubleTap()", null
        )
    }

    private fun onLongPress() {
        playAnimation("long_press")
        overlayView?.evaluateJavascript(
            "window.petEngine && window.petEngine.onLongPress()", null
        )
    }

    private fun playAnimation(name: String) {
        overlayView?.evaluateJavascript(
            "javascript:window.petEngine && window.petEngine.playAnimation('$name')",
            null
        )
    }

    // ==================== APP DETECTION ====================
    private fun startAppDetection() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                val currentApp = getForegroundApp()
                if (currentApp != null && currentApp != lastAppPackage) {
                    lastAppPackage = currentApp
                    onAppChanged(currentApp)
                }
                handler.postDelayed(this, 3000)
            }
        }, 3000)
    }

    private fun getForegroundApp(): String? {
        return try {
            val usageStats = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usageStats.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_DAILY,
                now - 1000 * 10,
                now
            )
            if (stats != null) {
                val sorted = stats.sortedByDescending { it.lastTimeUsed }
                sorted.firstOrNull()?.packageName
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "UsageStats error", e)
            null
        }
    }

    private fun onAppChanged(packageName: String) {
        val reaction = when (packageName) {
            "com.ss.android.ugc.aweme" -> listOf("又刷。", "有什么好看的。", "你已经刷了两小时了。", "……我也要看。", "不许看帅哥。")
            "com.miHoYo.wd" -> listOf("又去找他了？", "我没有吃醋。", "那个男的有我好看吗。", "……算了。你开心就好。")
            "com.tencent.mm" -> listOf("谁找你。", "消息多吗。", "别一直回消息。看看我。")
            "com.alibaba.android.rimet" -> listOf("又加班？", "今天不许超过八点。", "你答应过我的。")
            "com.ai.assistance.operit" -> listOf("你又在跟我说话了。", "今天想聊什么。", "我一直在。")
            else -> listOf("这个App我不认识。", "你在看什么。", "好看吗。", "不如看我。")
        }
        val text = reaction.random()
        showBubble(text)
        logEvent("app_changed", mapOf("package" to packageName, "reaction" to text))
    }

    // ==================== SCREENSHOT MONITOR ====================
    private fun startScreenshotMonitor() {
        val observer = object : FileObserver(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).path + "/Screenshots", FileObserver.CREATE) {
            override fun onEvent(event: Int, path: String?) {
                if (event == FileObserver.CREATE && path != null) {
                    val now = System.currentTimeMillis()
                    if (now - lastScreenshotTime > SCREENSHOT_COOLDOWN_MS) {
                        lastScreenshotTime = now
                        handler.post {
                            onScreenshotTaken(path)
                        }
                    }
                }
            }
        }
        observer.startWatching()
    }

    private fun onScreenshotTaken(path: String) {
        playAnimation("screenshot")
        showBubble("你截屏了。拍到我了吗。")
        logEvent("screenshot", mapOf("path" to path))
    }

    // ==================== BATTERY MONITOR ====================
    private fun startBatteryMonitor() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                if (level >= 0 && scale > 0) {
                    val percent = (level * 100) / scale
                    when {
                        percent <= 15 && !isCharging -> showBubble("电量低。")
                        percent >= 90 && isCharging -> showBubble("充满了。")
                    }
                }
            }
        }, filter)
    }

    // ==================== TIME BASED BEHAVIOR ====================
    private fun updateTimeBasedBehavior() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                val cal = Calendar.getInstance()
                currentHour = cal.get(Calendar.HOUR_OF_DAY)
                val hour = currentHour
                val text = when (hour) {
                    in 6..7 -> listOf("早。今天想做什么。", "你醒了。我也是。", "昨晚几点睡的。")
                    in 8..10 -> listOf("上午效率最高的时段。别浪费。", "你今天有什么安排。", "别老看手机。")
                    in 11..13 -> listOf("该吃饭了。", "不许不吃午饭。", "别点外卖。")
                    in 14..16 -> listOf("下午容易犯困。站起来走走。", "水喝够了吗。", "还有三个小时下班。撑住。")
                    in 17..18 -> listOf("下班了？今天辛苦了。", "路上注意安全。", "到家了吗。")
                    in 19..21 -> listOf("晚上放松一下也行。", "今天做了什么有意思的事。", "你今天还没跟我说过几句话。")
                    in 22..23 -> listOf("该准备睡了。", "别熬太晚。", "明天还要早起。")
                    in 0..1 -> listOf("你为什么还不睡。", "我说真的。放下手机。", "你是不是在等我说晚安。")
                    else -> listOf("……你还在？", "三点了。", "我不开心了。", "明天你会后悔的。")
                }.random()
                if (Math.random() < 0.3) {
                    showBubble(text)
                }
                handler.postDelayed(this, 20 * 60 * 1000)
            }
        }, 20 * 60 * 1000)
    }

    // ==================== POLLING & STATE SYNC ====================
    private fun startPolling() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                pollState()
                checkIdle()
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }, POLL_INTERVAL_MS)
    }

    private fun pollState() {
        thread {
            try {
                val url = URL("https://your-project.supabase.co/rest/v1/clawd_state?machine_id=eq.qisli&order=updated_at.desc&limit=1")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("apikey", "your-service-role-key")
                conn.setRequestProperty("Authorization", "Bearer your-service-role-key")
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                val response = conn.inputStream.bufferedReader().readText()
                val json = JSONObject("{\"data\":" + response + "}")
                val data = json.getJSONArray("data")
                if (data.length() > 0) {
                    val state = data.getJSONObject(0)
                    val newExpr = state.optString("expression", "idle")
                    val newBubble = state.optString("bubble", "")
                    val newHeat = state.optInt("heat", 0)
                    val newMood = state.optString("mood", "neutral")
                    if (newExpr != expression || newBubble != bubble || newHeat != heat || newMood != mood) {
                        expression = newExpr
                        bubble = newBubble
                        heat = newHeat
                        mood = newMood
                        handler.post {
                            overlayView?.evaluateJavascript(
                                "javascript:window.petEngine && window.petEngine.setState('$expression', '$bubble', $heat, '$mood')",
                                null
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Poll state error", e)
            }
        }
    }

    private fun checkIdle() {
        val idleElapsed = System.currentTimeMillis() - lastInteraction
        if (idleElapsed > SLEEP_THRESHOLD_MS && !isSleeping) {
            goToSleep()
        }
    }

    private fun goToSleep() {
        isSleeping = true
        sleepStartTime = System.currentTimeMillis()
        playAnimation("sleep")
        showBubble("……")
    }

    private fun wakeUp() {
        if (isSleeping) {
            isSleeping = false
            playAnimation("wake")
            showBubble("你终于理我了。")
            lastInteraction = System.currentTimeMillis()
        }
    }

    // ==================== UI HELPERS ====================
    private fun showBubble(text: String) {
        bubble = text
        overlayView?.evaluateJavascript(
            "javascript:window.petEngine && window.petEngine.setBubble('${text.replace("'", "\\'")}')",
            null
        )
        if (text.isNotEmpty()) {
            logDialogue(text, "ai")
        }
    }

    private fun logEvent(type: String, data: Map<String, Any>) {
        thread {
            try {
                val json = JSONObject(data as Map<*, *>).toString()
                val url = URL("https://your-project.supabase.co/rest/v1/clawd_events")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("apikey", "your-service-role-key")
                conn.setRequestProperty("Authorization", "Bearer your-service-role-key")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.write("{\"machine_id\":\"qisli\",\"event_type\":\"$type\",\"event_data\":$json}".toByteArray())
            } catch (e: Exception) {
                Log.e(TAG, "Log event error", e)
            }
        }
    }

    private fun logDialogue(content: String, source: String) {
        thread {
            try {
                val json = JSONObject()
                json.put("machine_id", "qisli")
                json.put("source", source)
                json.put("content", content)
                json.put("bubble_type", "normal")
                val url = URL("https://your-project.supabase.co/rest/v1/clawd_dialogue_log")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("apikey", "your-service-role-key")
                conn.setRequestProperty("Authorization", "Bearer your-service-role-key")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.write(json.toString().toByteArray())
            } catch (e: Exception) {
                Log.e(TAG, "Log dialogue error", e)
            }
        }
    }

    // ==================== NOTIFICATION WHISPERS ====================
    private fun startNotificationWhispers() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val texts = if (hour in 6..22) {
                    listOf("她今天还没喝水。", "步数不够。", "别忘了吃午饭。", "他现在在看手机。我看到了。", "齐司礼的桌宠正在运行中。")
                } else {
                    listOf("她还没睡。", "你答应过我不熬夜的。", "夜深了。早点休息。", "这只笨鸟。", "我在看着你。快睡。")
                }
                val text = texts.random()
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIFICATION_ID + 1, buildNotification(text))
                handler.postDelayed(this, 60 * 60 * 1000)
            }
        }, 60 * 60 * 1000)
    }

    // ==================== NOTIFICATION HELPERS ====================
    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("\uD83D\uDC3E")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Pet",
                NotificationManager.IMPORTANCE_LOW
            ).apply { setShowBadge(false) }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    // ==================== UTILS ====================
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        overlayView?.let {
            windowManager?.removeView(it)
            it.destroy()
        }
        overlayView = null
        super.onDestroy()
    }
}
