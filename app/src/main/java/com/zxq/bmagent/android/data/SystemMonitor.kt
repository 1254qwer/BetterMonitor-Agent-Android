package com.zxq.bmagent.android.data

import android.app.ActivityManager
import android.content.Context
import android.net.TrafficStats
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import com.zxq.bmagent.android.network.MonitorPayload
import com.zxq.bmagent.android.network.SystemInfoPayload
import java.io.RandomAccessFile
import java.net.NetworkInterface

class SystemMonitor(private val context: Context) {

    private val client =
            okhttp3.OkHttpClient.Builder()
                    .callTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

    suspend fun getSystemInfo(): SystemInfoPayload {
        val memoryInfo = getMemoryInfo()
        val diskInfo = getDiskInfo()
        val publicIp = getPublicIp()

        return SystemInfoPayload(
                hostname = Build.MODEL,
                os = "android",
                platform = "android",
                platform_version = Build.VERSION.RELEASE,
                kernel_version = System.getProperty("os.version") ?: "unknown",
                kernel_arch = System.getProperty("os.arch") ?: "unknown",
                cpu_model = Build.HARDWARE,
                cpu_cores = Runtime.getRuntime().availableProcessors(),
                memory_total = memoryInfo.totalMem,
                disk_total = diskInfo.totalBytes,
                boot_time = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 1000,
                public_ip = publicIp
        )
    }

    private suspend fun getPublicIp(): String {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val ips = mutableListOf<String>()

            // Try IPv4
            try {
                val request4 = okhttp3.Request.Builder().url("https://4.ipw.cn").build()
                val response4 = client.newCall(request4).execute()
                if (response4.isSuccessful) {
                    response4.body?.string()?.trim()?.let { if (it.isNotEmpty()) ips.add(it) }
                }
            } catch (e: Exception) {
                // Ignore
            }

            // Try IPv6
            try {
                val request6 = okhttp3.Request.Builder().url("https://6.ipw.cn").build()
                val response6 = client.newCall(request6).execute()
                if (response6.isSuccessful) {
                    response6.body?.string()?.trim()?.let { if (it.isNotEmpty()) ips.add(it) }
                }
            } catch (e: Exception) {
                // Ignore
            }

            if (ips.isNotEmpty()) {
                ips.joinToString(",")
            } else {
                // Fallback to local IP
                getIPAddress()
            }
        }
    }

    fun getMonitorData(): MonitorPayload {
        val memoryInfo = getMemoryInfo()
        val diskInfo = getDiskInfo()
        val networkStats = getNetworkStats()

        return MonitorPayload(
                cpuUsage = getCpuUsage(),
                memoryUsed = memoryInfo.totalMem - memoryInfo.availMem,
                memoryTotal = memoryInfo.totalMem,
                diskUsed = diskInfo.totalBytes - diskInfo.freeBytes,
                diskTotal = diskInfo.totalBytes,
                networkIn = networkStats.rxRate,
                networkOut = networkStats.txRate,
                bootTime = (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 1000,
                latency = 0.0, // Ping not implemented yet
                packetLoss = 0.0
        )
    }

    private fun getMemoryInfo(): ActivityManager.MemoryInfo {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return memoryInfo
    }

    private data class DiskInfo(val totalBytes: Long, val freeBytes: Long)

    private fun getDiskInfo(): DiskInfo {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        return DiskInfo(totalBytes = stat.totalBytes, freeBytes = stat.availableBytes)
    }

    private fun getIPAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val enumIpAddr = intf.inetAddresses
                while (enumIpAddr.hasMoreElements()) {
                    val inetAddress = enumIpAddr.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress.hostAddress.indexOf(':') < 0
                    ) {
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (ex: Exception) {}
        return "127.0.0.1"
    }

    // CPU Usage Calculation
    private var lastCpuTotal: Long = 0
    private var lastCpuIdle: Long = 0

    private fun getCpuUsage(): Double {
        try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val load = reader.readLine()
            reader.close()

            val toks = load.split(" +".toRegex())
            // /proc/stat: cpu user nice system idle iowait irq softirq ...
            // toks[0] is "cpu"
            val idle = toks[4].toLong()
            val total = toks.drop(1).take(7).sumOf { it.toLong() }

            val diffIdle = idle - lastCpuIdle
            val diffTotal = total - lastCpuTotal

            lastCpuIdle = idle
            lastCpuTotal = total

            if (diffTotal == 0L) return 0.0
            return ((diffTotal - diffIdle) * 100.0) / diffTotal
        } catch (e: Exception) {
            return 0.0
        }
    }

    // Network Usage Logic
    private var lastRxBytes: Long = 0
    private var lastTxBytes: Long = 0
    private var lastNetworkCheckTime: Long = 0

    private data class NetworkStats(val rxRate: Double, val txRate: Double)

    private fun getNetworkStats(): NetworkStats {
        val currentRx = TrafficStats.getTotalRxBytes()
        val currentTx = TrafficStats.getTotalTxBytes()
        val now = System.currentTimeMillis()

        if (lastNetworkCheckTime == 0L) {
            lastRxBytes = currentRx
            lastTxBytes = currentTx
            lastNetworkCheckTime = now
            return NetworkStats(0.0, 0.0)
        }

        val timeDiff = (now - lastNetworkCheckTime) / 1000.0 // Seconds
        if (timeDiff <= 0) return NetworkStats(0.0, 0.0)

        val rxRate = (currentRx - lastRxBytes) / timeDiff
        val txRate = (currentTx - lastTxBytes) / timeDiff

        lastRxBytes = currentRx
        lastTxBytes = currentTx
        lastNetworkCheckTime = now

        return NetworkStats(rxRate, txRate)
    }
}
