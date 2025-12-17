package com.zxq.bmagent.android.data

import android.os.Environment
import com.google.gson.Gson
import com.zxq.bmagent.android.network.ShellCloseMessage
import com.zxq.bmagent.android.network.ShellResponseMessage
import com.zxq.bmagent.android.network.WebSocketClient
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

object ShellManager {

    private val sessions = ConcurrentHashMap<String, ShellSession>()
    private val gson = Gson()

    data class ShellSession(
            val process: Process,
            val outputStream: OutputStream, // stdin of the process
            val inputBuffer: StringBuilder = StringBuilder()
    )

    fun createSession(sessionId: String, webSocketClient: WebSocketClient) {
        if (sessions.containsKey(sessionId)) return

        try {
            android.util.Log.d("ShellManager", "Creating session: $sessionId")

            // On Android without PTY, we need to force interactive mode carefully.
            // If root: su -c "sh -i" (invokes interactive sh)
            // If non-root: sh -i
            val command =
                    if (FileManager.isRootAvailable) {
                        arrayOf("su", "-c", "/system/bin/sh -i")
                    } else {
                        arrayOf("/system/bin/sh", "-i")
                    }

            val pb = ProcessBuilder(*command)
            pb.directory(Environment.getExternalStorageDirectory())
            pb.redirectErrorStream(true)
            val process = pb.start()
            val outputStream = process.outputStream

            sessions[sessionId] = ShellSession(process, outputStream)

            // Read Thread (stdout + stderr merged)
            thread(start = true) {
                try {
                    val inputStream = process.inputStream
                    val buffer = ByteArray(4096)
                    var read: Int
                    android.util.Log.d("ShellManager", "Session $sessionId: Reader thread started")

                    while (inputStream.read(buffer).also { read = it } != -1) {
                        val data = String(buffer, 0, read)
                        android.util.Log.d(
                                "ShellManager",
                                "Session $sessionId output: ${data.trim()}"
                        )

                        val msg = ShellResponseMessage(session = sessionId, data = data)
                        webSocketClient.send(gson.toJson(msg))
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ShellManager", "Session $sessionId error", e)
                } finally {
                    android.util.Log.d("ShellManager", "Session $sessionId: Reader thread ended")
                    closeSession(sessionId, webSocketClient)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ShellManager", "Failed to create session $sessionId", e)
            val msg =
                    ShellCloseMessage(
                            session = sessionId,
                            message = "Failed to create shell: ${e.message}"
                    )
            webSocketClient.send(gson.toJson(msg))
        }
    }

    fun input(sessionId: String, data: String, webSocketClient: WebSocketClient) {
        if (!sessions.containsKey(sessionId)) {
            android.util.Log.d(
                    "ShellManager",
                    "Session $sessionId not found on input, auto-creating..."
            )
            createSession(sessionId, webSocketClient)
        }

        val session = sessions[sessionId]
        if (session == null) {
            android.util.Log.e(
                    "ShellManager",
                    "Session $sessionId still missing after creation attempt"
            )
            return
        }

        try {
            // Line buffering logic
            for (char in data) {
                if (char == '\r' || char == '\n') {
                    // Enter pressed: Send buffer + newline to process
                    session.inputBuffer.append('\n')
                    val command = session.inputBuffer.toString()
                    android.util.Log.d(
                            "ShellManager",
                            "Sending command: ${command.replace("\n", "\\n")}"
                    )
                    session.outputStream.write(command.toByteArray())
                    session.outputStream.flush()
                    session.inputBuffer.setLength(0) // Clear buffer

                    // Echo newline to client
                    sendEcho(sessionId, "\r\n", webSocketClient)
                } else if (char == '\u007f' || char == '\b') {
                    // Backspace pressed
                    if (session.inputBuffer.isNotEmpty()) {
                        session.inputBuffer.deleteCharAt(session.inputBuffer.length - 1)
                        // Echo backspace sequence to erase char on client
                        // \b (move left) + space (overwrite) + \b (move left)
                        sendEcho(sessionId, "\b \b", webSocketClient)
                    }
                } else {
                    // Normal character
                    session.inputBuffer.append(char)
                    sendEcho(sessionId, char.toString(), webSocketClient)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ShellManager", "Session $sessionId input error", e)
        }
    }

    private fun sendEcho(sessionId: String, data: String, webSocketClient: WebSocketClient) {
        val echoMsg = ShellResponseMessage(session = sessionId, data = data)
        webSocketClient.send(gson.toJson(echoMsg))
    }

    fun resize(sessionId: String, cols: Int, rows: Int) {
        // Not supported without PTY
    }

    fun closeSession(sessionId: String, webSocketClient: WebSocketClient) {
        val session = sessions.remove(sessionId)
        if (session != null) {
            try {
                session.process.destroy()
            } catch (e: Exception) {}

            val msg = ShellCloseMessage(session = sessionId, message = "Session closed")
            webSocketClient.send(gson.toJson(msg))
        }
    }
}
