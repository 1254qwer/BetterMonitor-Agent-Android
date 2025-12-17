package com.zxq.bmagent.android.data

import android.os.Environment
import com.zxq.bmagent.android.network.FileItem
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object FileManager {

    // Check for root once
    val isRootAvailable: Boolean by lazy { checkRoot() }

    private fun checkRoot(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun resolvePath(path: String): String {
        val root = isRootAvailable
        if (root) return if (path.isEmpty()) "/" else path

        val externalStorage = Environment.getExternalStorageDirectory().absolutePath
        val resolved =
                if (path.startsWith(externalStorage)) {
                    path
                } else if (path == "/" || path.isEmpty()) {
                    externalStorage
                } else if (path.startsWith("/")) {
                    externalStorage + path
                } else {
                    externalStorage + "/" + path
                }
        android.util.Log.d("FileManager", "Resolving path: '$path' -> '$resolved' (Root: $root)")
        return resolved
    }

    fun listFiles(path: String): List<FileItem> {
        val useRoot = isRootAvailable
        val effectivePath = resolvePath(path)

        // 1. Try Standard Java File API first (for non-root accessible paths)
        val directory = File(effectivePath)
        if (directory.canRead() && directory.exists()) {
            val files = directory.listFiles()
            if (files != null) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                return files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                        .map { file ->
                            FileItem(
                                    name = file.name,
                                    size = if (file.isDirectory) 4096 else file.length(),
                                    is_dir = file.isDirectory,
                                    mod_time = dateFormat.format(Date(file.lastModified())),
                                    mode = getPermissions(file)
                            )
                        }
            }
        }

        // 2. If Standard API failed (e.g. permission denied) AND we have root, use Root
        if (useRoot) {
            return listFilesRoot(effectivePath)
        }

        return emptyList()
    }

    private fun listFilesRoot(path: String): List<FileItem> {
        try {
            // Use ls -l to get details. Format varies but usually:
            // drwxr-xr-x 1 root root 1234 2023-01-01 12:00 name
            val cmd = arrayOf("su", "-c", "ls -l \"$path\"")
            val process = Runtime.getRuntime().exec(cmd)
            val reader = process.inputStream.bufferedReader()
            val lines = reader.readLines()
            process.waitFor()

            val items = mutableListOf<FileItem>()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            dateFormat.timeZone = TimeZone.getTimeZone("UTC")

            for (line in lines) {
                if (line.startsWith("total")) continue
                try {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size < 6) continue

                    val perms = parts[0]
                    val isDir = perms.startsWith("d")

                    var dateIndex = -1
                    for (i in 3 until parts.size - 1) {
                        if (parts[i].contains("-") &&
                                        parts[i].length >= 5 &&
                                        parts[i + 1].contains(":")
                        ) {
                            dateIndex = i
                            break
                        }
                    }

                    if (dateIndex != -1) {
                        val size = parts[dateIndex - 1].toLongOrNull() ?: 0L
                        val name = parts.subList(dateIndex + 2, parts.size).joinToString(" ")

                        items.add(
                                FileItem(
                                        name = name,
                                        size = size,
                                        is_dir = isDir,
                                        mod_time = dateFormat.format(Date()),
                                        mode = perms
                                )
                        )
                    }
                } catch (e: Exception) {
                    continue
                }
            }
            return items.sortedWith(compareBy({ !it.is_dir }, { it.name.lowercase() }))
        } catch (e: Exception) {
            return emptyList()
        }
    }

    private fun getPermissions(file: File): String {
        val sb = StringBuilder()
        sb.append(if (file.isDirectory) "d" else "-")
        sb.append(if (file.canRead()) "r" else "-")
        sb.append(if (file.canWrite()) "w" else "-")
        sb.append(if (file.canExecute()) "x" else "-")
        sb.append("r-x") // Group
        sb.append("r-x") // Other
        return sb.toString()
    }

    fun getFileContent(path: String): String {
        val useRoot = isRootAvailable
        val effectivePath = resolvePath(path)
        val file = File(effectivePath)
        if (file.canRead()) {
            return file.readText()
        }
        if (useRoot) {
            val p = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat \"$effectivePath\""))
            val text = p.inputStream.bufferedReader().use { it.readText() }
            if (p.waitFor() == 0) return text
        }
        throw Exception("File not found or unreadable")
    }

    fun saveFileContent(path: String, content: String) {
        val file = File(resolvePath(path))
        file.writeText(content)
    }

    fun createFile(path: String) {
        val file = File(resolvePath(path))
        if (file.exists()) throw Exception("File already exists")
        file.createNewFile()
    }

    fun mkdir(path: String) {
        val file = File(resolvePath(path))
        if (file.exists()) throw Exception("Directory already exists")
        if (!file.mkdirs()) throw Exception("Failed to create directory")
    }

    fun getDirectoryTree(path: String, depth: Int): List<FileItem> {
        android.util.Log.d("FileManager", "getDirectoryTree: path='$path', depth=$depth")
        // listFiles returns the children of 'path'
        val items = listFiles(path)
        if (depth <= 1) return items

        return items.map { item ->
            if (item.is_dir) {
                val childPath = if (path.endsWith("/")) path + item.name else "$path/${item.name}"
                // Recursively fetch children, decreasing depth
                item.copy(children = getDirectoryTree(childPath, depth - 1))
            } else {
                item
            }
        }
    }
}
