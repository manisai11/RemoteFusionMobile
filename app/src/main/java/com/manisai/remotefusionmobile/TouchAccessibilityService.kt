// In file: TouchAccessibilityService.kt

package com.manisai.remotefusionmobile

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class TouchAccessibilityService : AccessibilityService() {

    // V V V  ADD THIS COMPANION OBJECT V V V
    companion object {
        var instance: TouchAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this // <-- ADD THIS LINE
        Log.d("TouchService", "Accessibility Service is active.")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null // <-- ADD THIS LINE
        Log.d("TouchService", "Accessibility Service has been destroyed.")
    }

    fun simulateTap(x: Int, y: Int) {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for this functionality
    }

    override fun onInterrupt() {
        // Not needed for this functionality
    }
}