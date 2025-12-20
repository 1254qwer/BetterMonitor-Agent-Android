package com.zxq.bmagent.android.data

import com.zxq.bmagent.android.network.ProcessItem
import java.io.BufferedReader
import java.io.InputStreamReader

object ProcessManager {

    fun listProcesses(): List<ProcessItem> {
        return if (FileManager.isRootAvailable) {
            listProcessesRoot()
        } else {
            listProcessesFallback()
        }
    }

    private fun listProcessesRoot(): List<ProcessItem> {
        val processes = mutableListOf<ProcessItem>()
        try {
            // Using -A to list all processes, -o to specify output columns for easier parsing
            // Columns: USER, PID, PPID, VSIZE(vsz), RSS, STATUS(s), NAME(comm/args)
            // Note: ps arguments vary by Android version / busybox / toybox.
            // "ps -A -o USER,PID,PPID,VSZ,RSS,S,NAME" is a common standard toybox syntax.
            val cmd = arrayOf("su", "-c", "ps -A -o USER,PID,PPID,VSZ,RSS,S,NAME")
            val process = Runtime.getRuntime().exec(cmd)
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            // Consume header
            reader.readLine() // e.g., "USER PID PPID VSZ RSS S NAME"

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { l -> parsePsLine(l)?.let { processes.add(it) } }
            }
            process.waitFor()
        } catch (e: Exception) {
            android.util.Log.e("ProcessManager", "Root process list failed", e)
            // Fallback if root command fails weirdly
            return listProcessesFallback()
        }
        return processes
    }

    private fun listProcessesFallback(): List<ProcessItem> {
        val processes = mutableListOf<ProcessItem>()
        try {
            // Standard user ps, restricted view on Android 7+
            val process = Runtime.getRuntime().exec("ps -A")
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            reader.readLine() // Header

            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { l ->
                    // Fallback parsing might need to be more flexible as we don't control columns
                    // with -o usually
                    parsePsLineGeneric(l)?.let { processes.add(it) }
                }
            }
            process.waitFor()
        } catch (e: Exception) {
            android.util.Log.e("ProcessManager", "Fallback process list failed", e)
        }
        return processes
    }

    private fun parsePsLine(line: String): ProcessItem? {
        try {
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size < 7) return null

            // Expected: USER(0) PID(1) PPID(2) VSZ(3) RSS(4) S(5) NAME(6...)
            val user = parts[0]
            val pid = parts[1].toIntOrNull() ?: return null
            val ppid = parts[2].toIntOrNull() ?: 0
            parts[3].toLongOrNull() ?: 0L // VSZ is usually in K
            val rss = parts[4].toLongOrNull() ?: 0L // RSS is usually in K
            val status = parts[5]
            val name = parts.subList(6, parts.size).joinToString(" ")

            return ProcessItem(
                    pid = pid,
                    ppid = ppid,
                    name = name,
                    username = user,
                    memory = rss * 1024, // Convert K to Bytes
                    cpu = 0.0, // Calculated separately if needed, 0.0 for now
                    status = mapStatus(status),
                    cmdline = name
            )
        } catch (_: Exception) {
            return null
        }
    }

    private fun parsePsLineGeneric(line: String): ProcessItem? {
        try {
            // Generic PS output: USER PID PPID VSZ RSS WCHAN ADDR S NAME
            // This is variable. match heuristic.
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size < 8) return null

            // Heuristic: USER is 0, PID is 1, PPID is 2. Name is last.
            val user = parts[0]
            val pid = parts[1].toIntOrNull() ?: return null
            val ppid = parts[2].toIntOrNull() ?: 0
            val name = parts.last()

            // Try to find RSS/VSZ if possible. Usually indices 3 and 4 or 4 and 5 depending on if
            // WCHAN etc are present.
            // Let's assume standard toybox output which is common on modern Android:
            // USER PID PPID VSZ RSS WCHAN ADDR S NAME

            var rss = 0L
            // If we have VSZ/RSS columns
            if (parts.size >= 9) {
                rss = parts[4].toLongOrNull() ?: 0L
            }

            return ProcessItem(
                    pid = pid,
                    ppid = ppid,
                    name = name,
                    username = user,
                    memory = rss * 1024,
                    cpu = 0.0,
                    status = "Running",
                    cmdline = name
            )
        } catch (_: Exception) {
            return null
        }
    }

    private fun mapStatus(s: String): String {
        return when (s.uppercase()) {
            "R" -> "Running"
            "S" -> "Sleeping"
            "D" -> "Disk Sleep"
            "Z" -> "Zombie"
            "T" -> "Stopped"
            else -> s
        }
    }

    fun killProcess(pid: Int): Boolean {
        if (FileManager.isRootAvailable) {
            try {
                // Try SIGTERM first, then SIGKILL? Standard "kill" is SIGTERM (15) default.
                // Using -9 (SIGKILL) to ensure it dies.
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "kill -9 $pid"))
                return process.waitFor() == 0
            } catch (e: Exception) {
                android.util.Log.e("ProcessManager", "Root kill failed", e)
            }
        }

        // Fallback: Can only kill own background processes really
        return try {
            android.os.Process.killProcess(pid)
            true
        } catch (_: Exception) {
            // Try shell kill just in case it is own user
            try {
                Runtime.getRuntime().exec("kill -9 $pid").waitFor() == 0
            } catch (_: Exception) {
                false
            }
        }
    }
}
