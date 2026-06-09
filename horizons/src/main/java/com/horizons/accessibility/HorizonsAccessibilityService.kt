package com.horizons.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class HorizonsAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun injectText(text: String) {
        // Phase 7: locate the focused EditText node and ACTION_SET_TEXT / paste.
        // Used by floating tiles when the keyboard isn't active.
    }
}
