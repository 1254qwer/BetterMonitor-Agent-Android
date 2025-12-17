package com.zxq.bmagent.android.network

import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class WebSocketClient {
    private val client =
            OkHttpClient.Builder()
                    .readTimeout(0, TimeUnit.MILLISECONDS) // WebSocket keeps connection open
                    .pingInterval(10, TimeUnit.SECONDS)
                    .build()

    private var webSocket: WebSocket? = null

    // Connection State
    private val _isConnected = MutableStateFlow(false)
    val isConnected = _isConnected.asStateFlow()

    private var messageListener: ((String) -> Unit)? = null

    fun setListener(listener: (String) -> Unit) {
        messageListener = listener
    }

    fun connect(url: String) {
        if (webSocket != null) {
            Log.d(TAG, "Already connected/connecting")
            return
        }

        Log.d(TAG, "Connecting to $url")
        val request = Request.Builder().url(url).build()

        webSocket =
                client.newWebSocket(
                        request,
                        object : WebSocketListener() {
                            override fun onOpen(webSocket: WebSocket, response: Response) {
                                Log.d(TAG, "onOpen")
                                _isConnected.value = true
                            }

                            override fun onMessage(webSocket: WebSocket, text: String) {
                                Log.d(TAG, "onMessage: $text")
                                messageListener?.invoke(text)
                            }

                            override fun onClosing(
                                    webSocket: WebSocket,
                                    code: Int,
                                    reason: String
                            ) {
                                Log.d(TAG, "onClosing: $code $reason")
                                webSocket.close(1000, null)
                                _isConnected.value = false
                            }

                            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                                Log.d(TAG, "onClosed: $code $reason")
                                _isConnected.value = false
                                this@WebSocketClient.webSocket = null
                            }

                            override fun onFailure(
                                    webSocket: WebSocket,
                                    t: Throwable,
                                    response: Response?
                            ) {
                                Log.e(TAG, "onFailure: ${t.message}")
                                _isConnected.value = false
                                this@WebSocketClient.webSocket = null
                                // TODO: Implement reconnection logic here or in service
                            }
                        }
                )
    }

    fun send(text: String): Boolean {
        return webSocket?.send(text) == true
    }

    fun disconnect() {
        webSocket?.close(1000, "User requested close")
        webSocket = null
        _isConnected.value = false
    }

    companion object {
        private const val TAG = "WebSocketClient"
    }
}
