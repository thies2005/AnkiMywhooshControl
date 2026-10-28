package com.ankiride.bridge.mywhoosh

import com.ankiride.bridge.core.AppConfiguration
import com.ankiride.bridge.core.DiagnosticLogger
import com.ankiride.bridge.core.LogCategory
import com.ankiride.bridge.core.MyWhooshAction
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Robot
import java.awt.Toolkit
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

object DesktopMyWhooshRobot {

    private val robot: Robot? = try {
        if (GraphicsEnvironment.isHeadless()) null else Robot()
    } catch (_: Exception) {
        null
    }

    fun isAvailable(): Boolean = robot != null

    fun performKeyboardShiftOrAction(action: MyWhooshAction): Boolean {
        val r = robot ?: return false
        val keyCode = when (action) {
            MyWhooshAction.SHIFT_UP -> KeyEvent.VK_I
            MyWhooshAction.SHIFT_DOWN -> KeyEvent.VK_K
            MyWhooshAction.STEER_LEFT -> KeyEvent.VK_A
            MyWhooshAction.STEER_RIGHT -> KeyEvent.VK_D
            MyWhooshAction.HUD_TOGGLE -> KeyEvent.VK_H
            MyWhooshAction.TOGGLE_CAMERA -> KeyEvent.VK_1
            MyWhooshAction.EMOTE -> KeyEvent.VK_E
            MyWhooshAction.UI_CONFIRM -> KeyEvent.VK_ENTER
            MyWhooshAction.UI_BACK -> KeyEvent.VK_ESCAPE
            else -> return false
        }
        return try {
            r.keyPress(keyCode)
            r.delay(45)
            r.keyRelease(keyCode)
            DiagnosticLogger.info(
                LogCategory.MYWHOOSH,
                "Desktop Robot sent key '${KeyEvent.getKeyText(keyCode)}' for $action"
            )
            true
        } catch (e: Exception) {
            DiagnosticLogger.error(LogCategory.MYWHOOSH, "Desktop Robot key injection failed: ${e.message}")
            false
        }
    }

    fun performScreenClickFallback(action: MyWhooshAction, config: AppConfiguration): Boolean {
        if (action != MyWhooshAction.SHIFT_UP && action != MyWhooshAction.SHIFT_DOWN) return false
        val r = robot ?: return false
        return try {
            val screenSize = Toolkit.getDefaultToolkit().screenSize
            val (normX, normY) = if (action == MyWhooshAction.SHIFT_UP) {
                config.myShiftUpNormalizedX to config.myShiftUpNormalizedY
            } else {
                config.myShiftDownNormalizedX to config.myShiftDownNormalizedY
            }

            val targetX = (screenSize.width * normX).toInt()
            val targetY = (screenSize.height * normY).toInt()
            val previousMouse = MouseInfo.getPointerInfo()?.location

            r.mouseMove(targetX, targetY)
            r.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            r.delay(50)
            r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            // Immediately restore user's cursor position
            if (previousMouse != null) {
                r.mouseMove(previousMouse.x, previousMouse.y)
            }
            DiagnosticLogger.info(LogCategory.MYWHOOSH, "Desktop Robot clicked ($targetX, $targetY) for $action")
            true
        } catch (e: Exception) {
            DiagnosticLogger.error(LogCategory.MYWHOOSH, "Desktop Robot click injection failed: ${e.message}")
            false
        }
    }

    fun inspectFocusedWindow(): String {
        return try {
            val os = System.getProperty("os.name", "").lowercase()
            if (os.contains("linux")) {
                val proc = ProcessBuilder("xdotool", "getactivewindow", "getwindowname").start()
                val title = proc.inputStream.bufferedReader().readText().trim()
                if (proc.waitFor() == 0 && title.isNotEmpty()) {
                    title
                } else {
                    "Unknown Linux Window"
                }
            } else {
                "Platform window inspection not available on $os"
            }
        } catch (_: Exception) {
            "Unable to inspect focused window"
        }
    }
}
