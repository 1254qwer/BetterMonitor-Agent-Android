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

    @Volatile private var webSocketClient: com.zxq.bmagent.android.network.WebSocketClient? = null

    fun provideWebSocketClient(): com.zxq.bmagent.android.network.WebSocketClient {
        return webSocketClient
                ?: synchronized(this) {
                    val instance = com.zxq.bmagent.android.network.WebSocketClient()
                    webSocketClient = instance
                    instance
                }
    }

    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var actionHandler: com.zxq.bmagent.android.service.ActionHandler? = null

    fun provideActionHandler(context: Context): com.zxq.bmagent.android.service.ActionHandler {
        return actionHandler
                ?: synchronized(this) {
                    val repo = provideAgentRepository(context)
                    val client = provideWebSocketClient()
                    val instance = com.zxq.bmagent.android.service.ActionHandler(repo, client)
                    actionHandler = instance
                    instance
                }
    }
}
