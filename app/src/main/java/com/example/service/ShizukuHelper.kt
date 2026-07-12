package com.example.service

import android.util.Log
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object ShizukuHelper {
    fun isShizukuAvailable(): Boolean {
        return Shizuku.pingBinder()
    }

    fun executeShellCommand(command: String): String {
        if (!isShizukuAvailable()) {
            return "Shizuku is not running"
        }
        try {
            // Replace spaces with %s for adb input text
            val safeCommand = if (command.startsWith("input text")) {
                val prefix = command.substringBefore(" \"")
                val text = command.substringAfter(" \"").removeSuffix("\"").replace(" ", "%s")
                "$prefix \"$text\""
            } else {
                command
            }
            val newProcessMethod = Shizuku::class.java.getMethod("newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java)
            val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", safeCommand), null, null) as Process
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            return output.toString()
        } catch (e: Exception) {
            Log.e("ShizukuHelper", "Error executing command", e)
            return "Error: ${e.message}"
        }
    }
}
