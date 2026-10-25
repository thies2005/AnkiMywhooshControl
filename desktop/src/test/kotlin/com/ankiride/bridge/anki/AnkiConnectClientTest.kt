package com.ankiride.bridge.anki

import com.ankiride.bridge.core.AnkiAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors

class AnkiConnectClientTest {

    @Test
    fun testBlockedOnNonLanAddress() {
        val client = AnkiConnectClient()
        val result = client.dispatchAnkiConnectAction("8.8.8.8", 8765, AnkiAction.FLIP_CARD)
        assertFalse("Requests to non-LAN IP must be blocked", result)

        val result2 = client.dispatchAnkiConnectAction("1.1.1.1", 8765, AnkiAction.FLIP_OR_GOOD)
        assertFalse("Requests to 1.1.1.1 must be blocked", result2)

        val result3 = client.dispatchAnkiConnectAction("google.com", 8765, AnkiAction.UNDO)
        assertFalse("Requests to domain names must be blocked", result3)
    }

    @Test
    fun testBlockedOnInvalidPort() {
        val client = AnkiConnectClient()
        assertFalse(client.dispatchAnkiConnectAction("127.0.0.1", 0, AnkiAction.FLIP_CARD))
        assertFalse(client.dispatchAnkiConnectAction("127.0.0.1", 70000, AnkiAction.FLIP_CARD))
    }

    @Test
    fun testGuiShowAnswerOnFirstPress() {
        val client = AnkiConnectClient()
        val server = ServerSocket(0)
        val port = server.localPort
        val executor = Executors.newSingleThreadExecutor()

        try {
            executor.submit {
                val socket = server.accept()
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                var line = reader.readLine()
                var contentLength = 0
                while (line != null && line.isNotEmpty()) {
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(":").trim().toInt()
                    }
                    line = reader.readLine()
                }
                val body = CharArray(contentLength)
                reader.read(body, 0, contentLength)
                val bodyStr = String(body)
                assertTrue(bodyStr.contains("\"action\":\"guiShowAnswer\""))

                val responseBody = """{"result":null,"error":null}"""
                val response = "HTTP/1.1 200 OK\r\nContent-Length: ${responseBody.length}\r\nConnection: close\r\n\r\n$responseBody"
                socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
                socket.close()
            }

            val ok = client.dispatchAnkiConnectAction(
                host = "127.0.0.1",
                port = port,
                action = AnkiAction.FLIP_OR_GOOD,
                nowMs = 1000L
            )
            assertTrue("FLIP_OR_GOOD on first press should call guiShowAnswer and succeed", ok)
            assertTrue("isAnswerVisible should be true after flip", client.isAnswerVisible)
            assertEquals(1000L, client.lastFlipTimestampMs)
        } finally {
            server.close()
            executor.shutdown()
        }
    }

    @Test
    fun testFlipCardActionOnlyFlips() {
        val client = AnkiConnectClient()
        val server = ServerSocket(0)
        val port = server.localPort
        val executor = Executors.newSingleThreadExecutor()

        try {
            executor.submit {
                val socket = server.accept()
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                var line = reader.readLine()
                var contentLength = 0
                while (line != null && line.isNotEmpty()) {
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line.substringAfter(":").trim().toInt()
                    }
                    line = reader.readLine()
                }
                val body = CharArray(contentLength)
                reader.read(body, 0, contentLength)
                val bodyStr = String(body)
                assertTrue(bodyStr.contains("\"action\":\"guiShowAnswer\""))

                val responseBody = """{"result":null,"error":null}"""
                val response = "HTTP/1.1 200 OK\r\nContent-Length: ${responseBody.length}\r\nConnection: close\r\n\r\n$responseBody"
                socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
                socket.close()
            }

            val ok = client.dispatchAnkiConnectAction(
                host = "127.0.0.1",
                port = port,
                action = AnkiAction.FLIP_CARD,
                nowMs = 1000L
            )
            assertTrue(ok)
            assertTrue(client.isAnswerVisible)
        } finally {
            server.close()
            executor.shutdown()
        }
    }

    @Test
    fun testBounceGuardBlocksQuickSecondPress() {
        val client = AnkiConnectClient()
        client.isAnswerVisible = true
        client.lastFlipTimestampMs = 1000L

        // Press again at 1200L (< 350ms after flip)
        val ok = client.dispatchAnkiConnectAction(
            host = "127.0.0.1",
            port = 8765,
            action = AnkiAction.FLIP_OR_GOOD,
            nowMs = 1200L
        )
        assertFalse("350ms bounce guard should block quick second press", ok)
        assertTrue("isAnswerVisible should remain true", client.isAnswerVisible)
    }

    @Test
    fun testGuiAnswerCardOnSecondPressAllEases() {
        val eases = listOf(
            AnkiAction.FLIP_OR_AGAIN to 1,
            AnkiAction.FLIP_OR_HARD to 2,
            AnkiAction.FLIP_OR_GOOD to 3,
            AnkiAction.FLIP_OR_EASY to 4
        )

        for ((action, expectedEase) in eases) {
            val client = AnkiConnectClient()
            client.isAnswerVisible = true
            client.lastFlipTimestampMs = 1000L

            val server = ServerSocket(0)
            val port = server.localPort
            val executor = Executors.newSingleThreadExecutor()

            try {
                executor.submit {
                    val socket = server.accept()
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                    var line = reader.readLine()
                    var contentLength = 0
                    while (line != null && line.isNotEmpty()) {
                        if (line.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = line.substringAfter(":").trim().toInt()
                        }
                        line = reader.readLine()
                    }
                    val body = CharArray(contentLength)
                    reader.read(body, 0, contentLength)
                    val bodyStr = String(body)
                    assertTrue(bodyStr.contains("\"action\":\"guiAnswerCard\""))
                    assertTrue(bodyStr.contains("\"ease\":$expectedEase"))

                    val responseBody = """{"result":true,"error":null}"""
                    val response = "HTTP/1.1 200 OK\r\nContent-Length: ${responseBody.length}\r\nConnection: close\r\n\r\n$responseBody"
                    socket.getOutputStream().write(response.toByteArray(Charsets.UTF_8))
                    socket.getOutputStream().flush()
                    socket.close()
                }

                val ok = client.dispatchAnkiConnectAction(
                    host = "127.0.0.1",
                    port = port,
                    action = action,
                    nowMs = 1400L // 400ms > 350ms guard
                )
                assertTrue("2nd press after guard for $action should succeed", ok)
                assertFalse("isAnswerVisible should reset to false after grading", client.isAnswerVisible)
            } finally {
                server.close()
                executor.shutdown()
            }
        }
    }

    @Test
    fun testSelfHealingWhenCardWasOnQuestionSide() {
        val client = AnkiConnectClient()
        client.isAnswerVisible = true
        client.lastFlipTimestampMs = 1000L

        val server = ServerSocket(0)
        val port = server.localPort
        val executor = Executors.newFixedThreadPool(2)

        try {
            executor.submit {
                // First request: guiAnswerCard -> respond with "result": false
                val socket1 = server.accept()
                val reader1 = BufferedReader(InputStreamReader(socket1.getInputStream(), Charsets.UTF_8))
                var line = reader1.readLine()
                var len1 = 0
                while (line != null && line.isNotEmpty()) {
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        len1 = line.substringAfter(":").trim().toInt()
                    }
                    line = reader1.readLine()
                }
                val body1 = CharArray(len1)
                reader1.read(body1, 0, len1)
                val resp1 = """{"result":false,"error":null}"""
                socket1.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: ${resp1.length}\r\nConnection: close\r\n\r\n$resp1".toByteArray(Charsets.UTF_8))
                socket1.getOutputStream().flush()
                socket1.close()

                // Second request: self-healing guiShowAnswer
                val socket2 = server.accept()
                val reader2 = BufferedReader(InputStreamReader(socket2.getInputStream(), Charsets.UTF_8))
                line = reader2.readLine()
                var len2 = 0
                while (line != null && line.isNotEmpty()) {
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        len2 = line.substringAfter(":").trim().toInt()
                    }
                    line = reader2.readLine()
                }
                val body2 = CharArray(len2)
                reader2.read(body2, 0, len2)
                val bodyStr2 = String(body2)
                assertTrue(bodyStr2.contains("\"action\":\"guiShowAnswer\""))

                val resp2 = """{"result":null,"error":null}"""
                socket2.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: ${resp2.length}\r\nConnection: close\r\n\r\n$resp2".toByteArray(Charsets.UTF_8))
                socket2.getOutputStream().flush()
                socket2.close()
            }

            val ok = client.dispatchAnkiConnectAction(
                host = "127.0.0.1",
                port = port,
                action = AnkiAction.FLIP_OR_AGAIN,
                nowMs = 1400L
            )
            assertTrue("Self-healing guiShowAnswer should succeed", ok)
            assertTrue("isAnswerVisible should remain true after self-healing flip", client.isAnswerVisible)
            assertEquals(1400L, client.lastFlipTimestampMs)
        } finally {
            server.close()
            executor.shutdown()
        }
    }

    @Test
    fun testUndo() {
        val client = AnkiConnectClient()
        client.isAnswerVisible = true
        val server = ServerSocket(0)
        val port = server.localPort
        val executor = Executors.newSingleThreadExecutor()

        try {
            executor.submit {
                val socket = server.accept()
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                var line = reader.readLine()
                var len = 0
                while (line != null && line.isNotEmpty()) {
                    if (line.startsWith("Content-Length:", ignoreCase = true)) {
                        len = line.substringAfter(":").trim().toInt()
                    }
                    line = reader.readLine()
                }
                val body = CharArray(len)
                reader.read(body, 0, len)
                val bodyStr = String(body)
                assertTrue(bodyStr.contains("\"action\":\"guiUndo\""))

                val resp = """{"result":null,"error":null}"""
                socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: ${resp.length}\r\nConnection: close\r\n\r\n$resp".toByteArray(Charsets.UTF_8))
                socket.getOutputStream().flush()
                socket.close()
            }

            val ok = client.dispatchAnkiConnectAction(
                host = "127.0.0.1",
                port = port,
                action = AnkiAction.UNDO
            )
            assertTrue(ok)
            assertFalse(client.isAnswerVisible)
        } finally {
            server.close()
            executor.shutdown()
        }
    }

    @Test
    fun testRogueServerStreamingExceedingLimitCappedAtMaxCharsWithoutOOM() {
        val client = AnkiConnectClient()
        val server = ServerSocket(0)
        val port = server.localPort
        val executor = Executors.newSingleThreadExecutor()

        try {
            executor.submit {
                val socket = server.accept()
                val out = socket.getOutputStream()
                val header = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n"
                out.write(header.toByteArray(Charsets.UTF_8))
                out.flush()

                // Stream 128 KB of data in chunks without closing socket (> 64 KB)
                val chunk = "X".repeat(4096).toByteArray(Charsets.UTF_8)
                for (i in 0 until 32) { // 32 * 4096 = 131,072 bytes
                    try {
                        out.write(chunk)
                        out.flush()
                    } catch (_: Exception) {
                        break
                    }
                }
                try {
                    Thread.sleep(1000L)
                } catch (_: InterruptedException) {
                }
                runCatching { socket.close() }
            }

            val response = client.sendJsonRpcRaw("127.0.0.1", port, "{\"action\":\"version\"}")
            assertNotNull("Response should not be null", response)
            assertEquals(
                "Response length must be capped at MAX_RESPONSE_CHARS",
                AnkiConnectClient.MAX_RESPONSE_CHARS,
                response!!.length
            )
            assertEquals(8192, AnkiConnectClient.MAX_RESPONSE_CHARS)

            // Verify dispatchAnkiConnectAction safely handles it without OutOfMemoryError
            val ok = client.dispatchAnkiConnectAction("127.0.0.1", port, AnkiAction.FLIP_CARD)
            assertFalse(ok)
        } finally {
            server.close()
            executor.shutdown()
        }
    }
}
