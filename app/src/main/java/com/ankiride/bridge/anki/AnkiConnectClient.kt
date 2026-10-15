package com.ankiride.bridge.anki

import com.ankiride.bridge.core.AnkiAction
import com.ankiride.bridge.core.DiagnosticLogger
import com.ankiride.bridge.core.LogCategory
import com.ankiride.bridge.mywhoosh.LanAddressValidator
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class AnkiConnectClient {

    companion object {
        const val DEFAULT_PORT = 8765
        const val SOCKET_TIMEOUT_MS = 1200
        const val FLIP_TO_GRADE_GUARD_MS = 350L
    }

    @Volatile
    var isAnswerVisible: Boolean = false

    @Volatile
    var lastFlipTimestampMs: Long = 0L

    fun dispatchAnkiConnectAction(
        host: String,
        port: Int = DEFAULT_PORT,
        action: AnkiAction,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val cleanHost = host.trim()
        if (!LanAddressValidator.isLocalOrLanAddress(cleanHost)) {
            DiagnosticLogger.error(
                LogCategory.ANKI,
                "Blocked AnkiConnect request to non-LAN address '$cleanHost' (must be 192.168.x.x, 10.x.x.x, 172.16-31.x.x, or 127.0.0.1)"
            )
            return false
        }
        if (port !in 1..65535) return false

        return when (action) {
            AnkiAction.FLIP_CARD -> {
                val ok = sendJsonRpc(cleanHost, port, """{"action":"guiShowAnswer","version":6}""")
                if (ok) {
                    isAnswerVisible = true
                    lastFlipTimestampMs = nowMs
                    DiagnosticLogger.info(LogCategory.ANKI, "Ubuntu AnkiConnect ($cleanHost:$port): Flipped card (guiShowAnswer)")
                }
                ok
            }
            AnkiAction.UNDO -> {
                val ok = sendJsonRpc(cleanHost, port, """{"action":"guiUndo","version":6}""")
                if (ok) {
                    isAnswerVisible = false
                    DiagnosticLogger.info(LogCategory.ANKI, "Ubuntu AnkiConnect ($cleanHost:$port): Undid last card (guiUndo)")
                }
                ok
            }
            AnkiAction.FLIP_OR_AGAIN -> flipOrAnswerEase(cleanHost, port, ease = 1, label = "Again(1)", nowMs = nowMs)
            AnkiAction.FLIP_OR_HARD -> flipOrAnswerEase(cleanHost, port, ease = 2, label = "Hard(2)", nowMs = nowMs)
            AnkiAction.FLIP_OR_GOOD -> flipOrAnswerEase(cleanHost, port, ease = 3, label = "Good(3)", nowMs = nowMs)
            AnkiAction.FLIP_OR_EASY -> flipOrAnswerEase(cleanHost, port, ease = 4, label = "Easy(4)", nowMs = nowMs)
        }
    }

    private fun flipOrAnswerEase(
        host: String,
        port: Int,
        ease: Int,
        label: String,
        nowMs: Long
    ): Boolean {
        if (!isAnswerVisible) {
            val flipped = sendJsonRpc(host, port, """{"action":"guiShowAnswer","version":6}""")
            if (flipped) {
                isAnswerVisible = true
                lastFlipTimestampMs = nowMs
                DiagnosticLogger.info(LogCategory.ANKI, "Ubuntu AnkiConnect ($host:$port): 1st press -> guiShowAnswer (armed 350ms guard)")
            }
            return flipped
        }

        if ((nowMs - lastFlipTimestampMs) < FLIP_TO_GRADE_GUARD_MS) {
            DiagnosticLogger.warn(LogCategory.ANKI, "Ubuntu AnkiConnect: Bounce Guard (350ms) blocked double-press for $label")
            return false
        }

        val response = sendJsonRpcRaw(
            host,
            port,
            """{"action":"guiAnswerCard","version":6,"params":{"ease":$ease}}"""
        ) ?: return false

        // Self-healing check: if Desktop Anki was still on Question side, guiAnswerCard returns "result": false
        if (response.contains("\"result\":false") || response.contains("\"result\": false")) {
            val flipped = sendJsonRpc(host, port, """{"action":"guiShowAnswer","version":6}""")
            isAnswerVisible = true
            lastFlipTimestampMs = nowMs
            DiagnosticLogger.info(LogCategory.ANKI, "Ubuntu AnkiConnect ($host:$port): Card was on Question side -> auto-called guiShowAnswer")
            return flipped
        }

        val ok = response.contains("\"error\":null") || response.contains("\"error\": null")
        if (ok) {
            isAnswerVisible = false
            DiagnosticLogger.info(LogCategory.ANKI, "Ubuntu AnkiConnect ($host:$port): Graded card -> $label (guiAnswerCard ease=$ease)")
        } else {
            DiagnosticLogger.warn(LogCategory.ANKI, "Ubuntu AnkiConnect error response: $response")
        }
        return ok
    }

    fun sendJsonRpc(host: String, port: Int, jsonBody: String): Boolean {
        val resp = sendJsonRpcRaw(host, port, jsonBody) ?: return false
        return resp.contains("\"error\":null") || resp.contains("\"error\": null")
    }

    fun sendJsonRpcRaw(host: String, port: Int, jsonBody: String): String? {
        if (!LanAddressValidator.isLocalOrLanAddress(host)) return null
        val bodyBytes = jsonBody.toByteArray(Charsets.UTF_8)
        val requestHeader = buildString {
            append("POST / HTTP/1.1\r\n")
            append("Host: $host:$port\r\n")
            append("Content-Type: application/json\r\n")
            append("Content-Length: ${bodyBytes.size}\r\n")
            append("Connection: close\r\n")
            append("\r\n")
        }.toByteArray(Charsets.UTF_8)

        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), SOCKET_TIMEOUT_MS)
                socket.soTimeout = SOCKET_TIMEOUT_MS
                val out: OutputStream = socket.getOutputStream()
                out.write(requestHeader)
                out.write(bodyBytes)
                out.flush()

                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                val sb = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.appendLine(line)
                }
                sb.toString()
            }
        } catch (e: Exception) {
            DiagnosticLogger.error(LogCategory.ANKI, "Ubuntu AnkiConnect ($host:$port) connection failed: ${e.message}")
            null
        }
    }
}
