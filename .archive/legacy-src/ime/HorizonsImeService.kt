package com.horizons.ime

import android.inputmethodservice.InputMethodService
import android.view.View

class HorizonsImeService : InputMethodService() {
    override fun onCreateInputView(): View {
        // Phase 7: keyboard with 3-position STT mode toggle (Dictation / MetaPrompt / BashCommand).
        // Smart default per EditorInfo (terminal flags -> Bash hint), operator can override.
        // commitText(...) for cleaned STT output.
        TODO("Phase 7")
    }
}
