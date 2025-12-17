package com.zxq.bmagent.android.data

import com.zxq.bmagent.android.network.ProcessItem
import java.io.BufferedReader
import java.io.InputStreamReader

object ProcessManager {

    fun listProcesses(): List<ProcessItem> {
        val processes = mutableListOf<ProcessItem>()
        try {
            val process = Runtime.getRuntime().exec("ps -A")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String? = reader.readLine() // Header

            // Format varies, but usually: USER PID PPID VSIZE RSS WCHAN PC S NAME
            // Simple space splitting
            while (reader.readLine().also { line = it } != null) {
                line?.let { l ->
                    val parts = l.trim().split("\\s+".toRegex())
                    if (parts.size >= 8) {
                        // Very rough parsing, might need adjustment for specific Android versions
                        // Assuming: USER(0) PID(1) PPID(2) ... NAME(last)
                        try {
                            // Try to find PID index if possible, or assume columns
                            // On some androids: USER PID PPID VSZ RSS WCHAN ADDR S NAME
                            val pid = parts[1].toIntOrNull() ?: return@let
                            val ppid = parts[2].toIntOrNull() ?: 0
                            val user = parts[0]
                            val name = parts.last()

                            // Try to parse RSS if available (often 5th or similar)
                            // We'll just put 0 for now or try to heuristic
                            val rss = 0L

                            processes.add(
                                    ProcessItem(
                                            pid = pid,
                                            ppid = ppid,
                                            name = name,
                                            username = user,
                                            memory = rss,
                                            cpu = 0.0, // Hard to get without top -n 1
                                            status = "Running",
                                            cmdline = name
                                    )
                            )
                        } catch (e: Exception) {
                            // Ignore parse errors for individual lines
                        }
                    }
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return processes
    }

    fun killProcess(pid: Int): Boolean {
        return try {
            Runtime.getRuntime().exec("kill $pid").waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
}
