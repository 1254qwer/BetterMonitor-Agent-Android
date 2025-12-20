package com.zxq.bmagent.android.service

import android.annotation.SuppressLint
import android.content.Context
import com.zxq.bmagent.android.data.AgentRepository
import com.zxq.bmagent.android.data.SystemMonitor
import com.zxq.bmagent.android.network.WebSocketClient

object ServiceLocator {
    @SuppressLint("StaticFieldLeak") @Volatile private var repository: AgentRepository? = null

    fun provideAgentRepository(context: Context): AgentRepository {
        return repository
                ?: synchronized(this) {
                    val instance = AgentRepository(context.applicationContext)
                    repository = instance
                    instance
                }
    }

    @SuppressLint("StaticFieldLeak") @Volatile private var systemMonitor: SystemMonitor? = null

    fun provideSystemMonitor(context: Context): SystemMonitor {
        return systemMonitor
                ?: synchronized(this) {
                    val instance = SystemMonitor(context.applicationContext)
                    systemMonitor = instance
                    instance
                }
    }

    @Volatile private var webSocketClient: WebSocketClient? = null

    fun provideWebSocketClient(): WebSocketClient {
        return webSocketClient
                ?: synchronized(this) {
                    val instance = WebSocketClient()
                    webSocketClient = instance
                    instance
                }
    }

    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var actionHandler: ActionHandler? = null

    fun provideActionHandler(context: Context): ActionHandler {
        return actionHandler
                ?: synchronized(this) {
                    val repo = provideAgentRepository(context)
                    val client = provideWebSocketClient()
                    val instance = ActionHandler(repo, client)
                    actionHandler = instance
                    instance
                }
    }
}
