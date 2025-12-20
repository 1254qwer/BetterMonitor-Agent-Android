package com.zxq.bmagent.android.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.util.LinkedList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.core.content.edit

class AgentRepository(context: Context) {

    private val prefs: SharedPreferences =
            context.getSharedPreferences("agent_prefs", Context.MODE_PRIVATE)

    // Configuration
    var serverId: String
        get() = prefs.getString("server_id", "") ?: ""
        set(value) = prefs.edit { putString("server_id", value) }

    var serverKey: String
        get() = prefs.getString("server_key", "") ?: ""
        set(value) = prefs.edit { putString("server_key", value)}

    var serverUrl: String
        get() = prefs.getString("server_url", "") ?: ""
        set(value) = prefs.edit { putString("server_url", value)}

    // Logs
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()
    private val logBuffer = LinkedList<String>()
    private val MAX_LOGS = 1000

    // Status
    private val _isRunning = MutableStateFlow(false)
    val isRunning = _isRunning.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    fun setRunning(running: Boolean) {
        _isRunning.value = running
    }

    fun setConnected(connected: Boolean) {
        _isConnected.value = connected
    }

    // Deprecated: startAgent is now managed by AgentService directly calling WebSocketClient
    // We can keep these empty or remove them. Ideally remove to avoid confusion.
    // The Service logs "Agent started." so we can keep log functionality here.

    fun appendLog(msg: String) {
        synchronized(logBuffer) {
            if (logBuffer.size >= MAX_LOGS) {
                logBuffer.removeFirst()
            }
            logBuffer.add(msg)
            _logs.value = logBuffer.toList()
        }
        Log.d("AgentRepository", msg)
    }
}
