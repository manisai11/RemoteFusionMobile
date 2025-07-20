package com.manisai.remotefusionmobile

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.view.KeyEvent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class TouchAccessibilityService : AccessibilityService() {

    companion object {
        private var INSTANCE: TouchAccessibilityService? = null
        fun getInstance(): TouchAccessibilityService? = INSTANCE
    }

    // Stores the path for the current gesture (e.g., a drag)
    private var currentGesturePath: Path? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        INSTANCE = this
        Log.d("AccessibilityService", "Service Connected")

        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        serviceInfo = info
    }

    override fun onDestroy() {
        super.onDestroy()
        INSTANCE = null
        Log.d("AccessibilityService", "Service Destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used for this functionality
    }



    override fun onInterrupt() {
        Log.d("AccessibilityService", "On Interrupt")
    }

    // --- Core Action Methods ---

    /**
     * Simulates a single tap at the given coordinates.
     */
    fun simulateTap(x: Int, y: Int) {
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        val gestureBuilder = GestureDescription.Builder()
        // A tap is a brief down-and-up gesture
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 50))
        dispatchGesture(gestureBuilder.build(), null, null)
    }

    /**
     * Simulates a continuous pointer gesture for down, move, and up actions.
     * This correctly handles dragging and swiping.
     */
    fun simulatePointerEvent(x: Int, y: Int, action: String) {
        when (action) {
            "down" -> {
                // When the pointer goes down, start a new path
                currentGesturePath = Path()
                currentGesturePath?.moveTo(x.toFloat(), y.toFloat())
            }
            "move" -> {
                // While moving, extend the existing path if it exists
                currentGesturePath?.lineTo(x.toFloat(), y.toFloat())
            }
            "up" -> {
                // When the pointer goes up, finish the path and dispatch the full gesture
                currentGesturePath?.lineTo(x.toFloat(), y.toFloat())
                currentGesturePath?.let { path ->
                    val gestureBuilder = GestureDescription.Builder()
                    // Duration can be adjusted for the stroke
                    gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 100))
                    dispatchGesture(gestureBuilder.build(), null, null)
                }
                // The gesture is complete, so clear the path
                currentGesturePath = null
            }
            else -> {
                Log.w("AccessibilityService", "Unsupported pointer action: $action")
            }
        }
    }

    /**
     * Sends text to the currently focused editable field.
     */
    fun sendText(text: String) {
        val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode != null && focusedNode.isEditable) {
            val arguments = Bundle().apply {
                putString(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
            }
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            focusedNode.recycle()
        } else {
            Log.w("AccessibilityService", "No editable node focused to send text to.")
        }
    }

    /**
     * Simulates a global key press.
     * This now correctly handles the "Back" button press.
     */
    fun pressKey(keyCode: Int) {
        when (keyCode) {
            KeyEvent.KEYCODE_BACK -> { // This is integer 4
                Log.d("AccessibilityService", "Executing global action: BACK")
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            // You can add more global actions here (e.g., HOME, RECENTS)
            // KeyEvent.KEYCODE_HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            else -> {
                Log.w("AccessibilityService", "Unsupported key code: $keyCode")
            }
        }
    }
    fun inputText(text: String) {
        val arguments = Bundle()
        arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)

        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focusedNode != null && focusedNode.isEditable) {
                focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            } else {
                Log.e("TouchService", "No editable field in focus.")
            }
        } else {
            Log.e("TouchService", "Root window is null.")
        }
    }
}