package com.zxq.bmagent.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import com.zxq.bmagent.android.MainActivity
import com.zxq.bmagent.android.R
import com.zxq.bmagent.android.data.AgentRepository
import com.zxq.bmagent.android.data.SystemMonitor
import com.zxq.bmagent.android.network.HeartbeatMessage
import com.zxq.bmagent.android.network.MonitorMessage
import com.zxq.bmagent.android.network.SystemInfoMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AgentService : LifecycleService() {

    private lateinit var repository: AgentRepository
    private var heartbeatJob: Job? = null
    private var monitorJob: Job? = null
    private val gson = Gson()

    companion object {
        const val CHANNEL_ID = "AgentServiceChannel"
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onCreate() {
        super.onCreate()
        repository = ServiceLocator.provideAgentRepository(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        createNotificationChannel()
        createNotificationChannel()
        val notification = createNotification("Agent Running", "Starting...")
        startForeground(1, notification)

        if (intent?.action == ACTION_START) {
            startNativeAgent()
        } else if (intent?.action == ACTION_STOP) {
            stopNativeAgent()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun startNativeAgent() {
        repository.setRunning(true)
        val serverId = repository.serverId
        val serverKey = repository.serverKey
        val serverUrl = repository.serverUrl

        if (serverId.isBlank() || serverUrl.isBlank()) {
            repository.appendLog("Error: Missing Server ID or URL")
            repository.setRunning(false)
            return
        }

        // Construct URL
        var cleanUrl = serverUrl.removePrefix("http://").removePrefix("https://")
        if (cleanUrl.endsWith("/")) cleanUrl = cleanUrl.dropLast(1)
        val scheme = if (serverUrl.startsWith("https")) "wss" else "ws"
        val fullUrl = "$scheme://$cleanUrl/api/servers/$serverId/ws?token=$serverKey"

        val wsClient = ServiceLocator.provideWebSocketClient()
        val actionHandler = ServiceLocator.provideActionHandler(applicationContext)
        val systemMonitor = ServiceLocator.provideSystemMonitor(applicationContext)

        wsClient.setListener { message -> actionHandler.handleMessage(message) }

        // Monitoring Loop
        lifecycleScope.launch {
            wsClient.isConnected.collect { connected ->
                repository.setConnected(connected)
                if (connected) {
                    repository.appendLog("Connected to server.")
                    startBackgroundTasks(wsClient, systemMonitor)
                } else {
                    repository.appendLog("Disconnected from server.")
                    stopBackgroundTasks()
                    // Auto-reconnect if supposed to be running
                    // isRunning is a StateFlow, so check value
                    if (repository.isRunning.value) {
                        repository.appendLog("Waiting 5s to reconnect...")
                        delay(5000)
                        if (repository.isRunning.value) {
                            repository.appendLog("Reconnecting...")
                            wsClient.connect(fullUrl)
                        }
                    }
                }
            }
        }

        // Initial connect is handled by the loop if we trigger it, or explicit first call
        // But since we rely on the loop for re-connect, let's just do the first one here.
        repository.appendLog("Connecting to server...")
        wsClient.connect(fullUrl)
    }

    private fun stopNativeAgent() {
        val wsClient = ServiceLocator.provideWebSocketClient()
        wsClient.disconnect()
        repository.setRunning(false)
        repository.setConnected(false)
        repository.appendLog("Agent stopped.")
    }

    private fun startBackgroundTasks(
            wsClient: com.zxq.bmagent.android.network.WebSocketClient,
            systemMonitor: SystemMonitor
    ) {
        if (heartbeatJob?.isActive == true) return
        repository.appendLog("Starting background tasks...")

        // 1. Send System Info immediately
        lifecycleScope.launch {
            try {
                val sysInfo = systemMonitor.getSystemInfo()
                val msg = SystemInfoMessage(payload = sysInfo)
                wsClient.send(gson.toJson(msg))
                repository.appendLog("Sent System Info")
            } catch (e: Exception) {
                repository.appendLog("Error sending system info: ${e.message}")
            }
        }

        // 2. Heartbeat Loop (10s)
        heartbeatJob =
                lifecycleScope.launch {
                    // Send initial heartbeat immediately
                    try {
                        val initialMsg =
                                HeartbeatMessage(timestamp = System.currentTimeMillis() / 1000)
                        wsClient.send(gson.toJson(initialMsg))
                    } catch (_: Exception) {}

                    while (true) {
                        delay(10000)
                        try {
                            val msg =
                                    HeartbeatMessage(timestamp = System.currentTimeMillis() / 1000)
                            wsClient.send(gson.toJson(msg))
                            updateNotification("Last report: ${getCurrentTime()}")
                        } catch (e: Exception) {
                            repository.appendLog("Error sending heartbeat: ${e.message}")
                        }
                    }
                }

        // 3. Monitor Loop (30s)
        monitorJob =
                lifecycleScope.launch {
                    while (true) {
                        try {
                            val data = systemMonitor.getMonitorData()
                            val msg = MonitorMessage(payload = data)
                            wsClient.send(gson.toJson(msg))
                            updateNotification("Last report: ${getCurrentTime()}")
                        } catch (e: Exception) {
                            repository.appendLog("Error sending monitor data: ${e.message}")
                        }
                        delay(30000)
                    }
                }
    }

    private fun stopBackgroundTasks() {
        heartbeatJob?.cancel()
        monitorJob?.cancel()
        heartbeatJob = null
        monitorJob = null
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun createNotificationChannel() {
        val serviceChannel =
                NotificationChannel(
                        CHANNEL_ID,
                        "Agent Service Channel",
                        NotificationManager.IMPORTANCE_DEFAULT
                )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(serviceChannel)
    }

    private fun createNotification(title: String, content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(true) // Don't alert on every update
                .build()
    }

    private fun updateNotification(content: String) {
        val notification = createNotification("Agent Running", content)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(1, notification)
    }

    private fun getCurrentTime(): String {
        return java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(java.util.Date())
    }
}
