package com.zxq.bmagent.android.network

import com.google.gson.annotations.SerializedName

data class BaseMessage(
        @SerializedName("type") val type: String,
        @SerializedName("timestamp") val timestamp: Long? = null,
        @SerializedName("request_id") val requestId: String? = null
)

data class HeartbeatMessage(
        @SerializedName("type") val type: String = "heartbeat",
        @SerializedName("timestamp") val timestamp: Long,
        @SerializedName("status") val status: String = "online",
        @SerializedName("version")
        val version: String = com.zxq.bmagent.android.BuildConfig.VERSION_NAME,
        @SerializedName("is_reply") val isReply: Boolean = false
)

data class MonitorMessage(
        @SerializedName("type") val type: String = "monitor",
        @SerializedName("payload") val payload: MonitorPayload
)

data class MonitorPayload(
        @SerializedName("cpu_usage") val cpuUsage: Double,
        @SerializedName("memory_used") val memoryUsed: Long,
        @SerializedName("memory_total") val memoryTotal: Long,
        @SerializedName("disk_used") val diskUsed: Long,
        @SerializedName("disk_total") val diskTotal: Long,
        @SerializedName("network_in") val networkIn: Double,
        @SerializedName("network_out") val networkOut: Double,
        @SerializedName("boot_time") val bootTime: Long,
        @SerializedName("latency") val latency: Double,
        @SerializedName("packet_loss") val packetLoss: Double,
        // Optional
        @SerializedName("load_avg_1") val loadAvg1: Double = 0.0,
        @SerializedName("load_avg_5") val loadAvg5: Double = 0.0,
        @SerializedName("load_avg_15") val loadAvg15: Double = 0.0
)

data class SystemInfoMessage(
        @SerializedName("type") val type: String = "system_info",
        @SerializedName("payload") val payload: SystemInfoPayload
)

data class SystemInfoPayload(
        val hostname: String,
        val os: String,
        val platform: String,
        val platform_version: String,
        val kernel_version: String,
        val kernel_arch: String,
        val cpu_model: String,
        val cpu_cores: Int,
        val memory_total: Long,
        val disk_total: Long,
        val boot_time: Long,
        val public_ip: String
)

// File Management Models
// File Management Models
data class FileListMessage(
        @SerializedName("type") val type: String = "file_list",
        @SerializedName("request_id") val requestId: String,
        @SerializedName("payload") val payload: FileListPayload
)

data class FileListPayload(val path: String)

data class FileListResponse(
        @SerializedName("type") val type: String = "file_list_response",
        @SerializedName("request_id") val requestId: String,
        @SerializedName("data") val data: FileListData
)

data class FileListData(val path: String, val files: List<FileItem>)

data class FileItem(
        val name: String,
        val size: Long,
        val is_dir: Boolean,
        val mod_time: String,
        val mode: String,
        val children: List<FileItem>? = null
)

data class FileContentMessage(
        @SerializedName("type") val type: String = "file_content",
        @SerializedName("request_id") val requestId: String,
        @SerializedName("payload") val payload: FileContentPayload
)

data class FileContentPayload(
        val path: String,
        val action: String, // get, save, create, mkdir
        val content: String? = null
)

data class FileContentResponse(
        @SerializedName("type") val type: String = "file_content_response",
        @SerializedName("request_id") val requestId: String,
        @SerializedName("data") val data: FileContentResponseData
)

data class FileContentResponseData(
        val path: String,
        val content: String? = null,
        val success: Boolean? = null,
        val message: String? = null
)

data class FileTreeMessage(
        @SerializedName("type") val type: String = "tree",
        @SerializedName("request_id") val requestId: String,
        @SerializedName("payload") val payload: FileTreePayload
)

data class FileTreePayload(
        val path: String,
        val content: String // Depth
)

data class FileTreeResponse(
        @SerializedName("type") val type: String = "file_tree_response",
        @SerializedName("request_id") val requestId: String,
        @SerializedName("data") val data: FileTreeData
)

data class FileTreeData(val path: String, val tree: List<FileItem>)

// Shell & Process Models
data class ShellCommandMessage(
        @SerializedName("type") val type: String = "shell_command",
        @SerializedName("payload") val payload: ShellPayload
)

data class ShellPayload(
        val type: String, // create, input, resize, close
        val session: String,
        val data: String? = null
)

data class ShellResponseMessage(
        @SerializedName("type") val type: String = "shell_response",
        @SerializedName("session") val session: String,
        @SerializedName("data") val data: String
)

data class ShellCloseMessage(
        @SerializedName("type") val type: String = "shell_close",
        @SerializedName("session") val session: String,
        @SerializedName("message") val message: String
)

data class ProcessListMessage(
        @SerializedName("type") val type: String = "process_list",
        @SerializedName("request_id") val requestId: String
)

data class ProcessListResponse(
        @SerializedName("type") val type: String = "process_list_response",
        @SerializedName("request_id") val requestId: String,
        @SerializedName("data") val data: ProcessListData
)

data class ProcessListData(val timestamp: Long, val count: Int, val processes: List<ProcessItem>)

data class ProcessItem(
        val pid: Int,
        val ppid: Int,
        val name: String,
        val username: String,
        val memory: Long, // RSS bytes usually
        val cpu: Double,
        val status: String,
        val cmdline: String
)

data class ProcessKillMessage(
        @SerializedName("type") val type: String = "process_kill",
        @SerializedName("request_id") val requestId: String,
        @SerializedName("payload") val payload: ProcessKillPayload
)

data class ProcessKillPayload(val pid: Int)

data class ProcessKillResponse(
        @SerializedName("type") val type: String = "process_kill_response",
        @SerializedName("request_id") val requestId: String,
        @SerializedName("data") val data: ProcessKillData
)

data class ProcessKillData(
        val pid: Int,
        val name: String = "",
        val success: Boolean,
        val message: String,
        val timestamp: Long
)
