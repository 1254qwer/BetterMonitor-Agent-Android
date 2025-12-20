package com.zxq.bmagent.android.service

import com.google.gson.Gson
import com.zxq.bmagent.android.data.AgentRepository
import com.zxq.bmagent.android.data.FileManager
import com.zxq.bmagent.android.data.ProcessManager
import com.zxq.bmagent.android.data.ShellManager
import com.zxq.bmagent.android.network.BaseMessage
import com.zxq.bmagent.android.network.FileContentMessage
import com.zxq.bmagent.android.network.FileContentResponse
import com.zxq.bmagent.android.network.FileContentResponseData
import com.zxq.bmagent.android.network.FileListData
import com.zxq.bmagent.android.network.FileListMessage
import com.zxq.bmagent.android.network.FileListResponse
import com.zxq.bmagent.android.network.FileTreeData
import com.zxq.bmagent.android.network.FileTreeMessage
import com.zxq.bmagent.android.network.FileTreeResponse
import com.zxq.bmagent.android.network.HeartbeatMessage
import com.zxq.bmagent.android.network.ProcessKillData
import com.zxq.bmagent.android.network.ProcessKillMessage
import com.zxq.bmagent.android.network.ProcessKillResponse
import com.zxq.bmagent.android.network.ProcessListData
import com.zxq.bmagent.android.network.ProcessListMessage
import com.zxq.bmagent.android.network.ProcessListResponse
import com.zxq.bmagent.android.network.ShellCommandMessage
import com.zxq.bmagent.android.network.WebSocketClient

class ActionHandler(
        private val repository: AgentRepository,
        private val webSocketClient: WebSocketClient
) {
    private val gson = Gson()

    fun handleMessage(message: String) {
        try {
            val base = gson.fromJson(message, BaseMessage::class.java)
            when (base.type) {
                "heartbeat" -> handleHeartbeat(message)
                "file_list" -> handleFileList(message)
                "file_content" -> handleFileContent(message)
                "process_list" -> handleProcessList(message)
                "process_kill" -> handleProcessKill(message)
                "shell_command" -> handleShellCommand(message)
                "tree" -> handleFileTree(message)
                // Legacy terminal support if needed
                "terminal_create" ->
                        handleShellCommand(
                                message.replace("terminal_create", "shell_command")
                        ) // Hacky or ignore
                else -> repository.appendLog("Unknown message type: ${base.type}")
            }
        } catch (e: Exception) {
            repository.appendLog("Error parsing message: ${e.message}")
        }
    }

    private fun handleHeartbeat(json: String) {
        try {
            val msg = gson.fromJson(json, HeartbeatMessage::class.java)
            if (!msg.isReply) {
                val reply =
                        HeartbeatMessage(
                                timestamp = System.currentTimeMillis() / 1000,
                                isReply = true
                        )
                webSocketClient.send(gson.toJson(reply))
            }
        } catch (e: Exception) {
            repository.appendLog("Error handling heartbeat: ${e.message}")
        }
    }

    private fun handleFileList(json: String) {
        try {
            val msg = gson.fromJson(json, FileListMessage::class.java)
            val files = FileManager.listFiles(msg.payload.path)
            val response =
                    FileListResponse(
                            requestId = msg.requestId,
                            data = FileListData(path = msg.payload.path, files = files)
                    )
            val jsonResponse = gson.toJson(response)

            android.util.Log.d("ActionHandler", "Sending File List: $jsonResponse")
            webSocketClient.send(jsonResponse)
        } catch (e: Exception) {
            android.util.Log.e("ActionHandler", "Error listing files: ${e.message}")
        }
    }

    private fun handleFileContent(json: String) {
        try {
            val msg = gson.fromJson(json, FileContentMessage::class.java)
            val path = msg.payload.path
            val action = msg.payload.action

            var content: String? = null
            var message: String? = "Success"
            var success = true

            try {
                when (action) {
                    "get" -> content = FileManager.getFileContent(path)
                    "save" -> FileManager.saveFileContent(path, msg.payload.content ?: "")
                    "create" -> {
                        FileManager.createFile(path)
                        if (!msg.payload.content.isNullOrEmpty()) {
                            FileManager.saveFileContent(path, msg.payload.content)
                        }
                    }
                    "mkdir" -> FileManager.mkdir(path)
                    "tree" -> {
                        val depth = msg.payload.content?.toIntOrNull() ?: 1
                        val files = FileManager.getDirectoryTree(path, depth)
                        val treeResponse =
                                FileTreeResponse(
                                        requestId = msg.requestId,
                                        data = FileTreeData(path = path, tree = files)
                                )
                        val jsonResponse = gson.toJson(treeResponse)

                        android.util.Log.d(
                                "ActionHandler",
                                "Sending Tree Response (Legacy): $jsonResponse"
                        )
                        webSocketClient.send(jsonResponse)
                        return // Exit function to avoid sending the second default response
                    }
                }
            } catch (e: Exception) {
                success = false
                message = e.message
            }

            val response =
                    FileContentResponse(
                            requestId = msg.requestId,
                            data =
                                    FileContentResponseData(
                                            path = path,
                                            content = content,
                                            success = success,
                                            message = message
                                    )
                    )
            val jsonResponse = gson.toJson(response)

            android.util.Log.d("ActionHandler", "Sending File Content: $jsonResponse")
            webSocketClient.send(jsonResponse)
        } catch (e: Exception) {
            android.util.Log.e("ActionHandler", "Error handling file content: ${e.message}")
        }
    }

    private fun handleProcessList(json: String) {
        try {
            val msg = gson.fromJson(json, ProcessListMessage::class.java)
            val processes = ProcessManager.listProcesses()
            val response =
                    ProcessListResponse(
                            requestId = msg.requestId,
                            data =
                                    ProcessListData(
                                            timestamp = System.currentTimeMillis() / 1000,
                                            count = processes.size,
                                            processes = processes
                                    )
                    )
            webSocketClient.send(gson.toJson(response))
        } catch (e: Exception) {
            repository.appendLog("Error listing processes: ${e.message}")
        }
    }

    private fun handleProcessKill(json: String) {
        try {
            val msg = gson.fromJson(json, ProcessKillMessage::class.java)
            val pid = msg.payload.pid
            val success = ProcessManager.killProcess(pid)
            val response =
                    ProcessKillResponse(
                            requestId = msg.requestId,
                            data =
                                    ProcessKillData(
                                            pid = pid,
                                            success = success,
                                            message =
                                                    if (success) "Process killed"
                                                    else "Failed to kill process",
                                            timestamp = System.currentTimeMillis() / 1000
                                    )
                    )
            webSocketClient.send(gson.toJson(response))
        } catch (e: Exception) {
            repository.appendLog("Error killing process: ${e.message}")
        }
    }

    private fun handleShellCommand(json: String) {
        try {
            val msg = gson.fromJson(json, ShellCommandMessage::class.java)
            val payload = msg.payload
            when (payload.type) {
                "create" -> ShellManager.createSession(payload.session, webSocketClient)
                "input" -> ShellManager.input(payload.session, payload.data ?: "", webSocketClient)
                "close" -> ShellManager.closeSession(payload.session, webSocketClient)
            }
        } catch (e: Exception) {
            repository.appendLog("Error handling shell command: ${e.message}")
        }
    }

    private fun handleFileTree(json: String) {
        try {
            val msg = gson.fromJson(json, FileTreeMessage::class.java)
            val path = msg.payload.path
            val depthStr = msg.payload.content
            val depth = depthStr.toIntOrNull() ?: 1

            val files = FileManager.getDirectoryTree(path, depth)

            val response =
                    FileTreeResponse(
                            requestId = msg.requestId,
                            data = FileTreeData(path = path, tree = files)
                    )
            val jsonResponse = gson.toJson(response)

            android.util.Log.d("ActionHandler", "Sending Tree Response: $jsonResponse")
            webSocketClient.send(jsonResponse)
        } catch (e: Exception) {
            android.util.Log.e("ActionHandler", "Error handling file tree: ${e.message}")
        }
    }
}
