package com.v2ray.ang.ui.base

import android.os.Bundle
import androidx.lifecycle.SavedStateHandle

/**
 * Process-death persistence for editor forms.
 *
 * The snapshot is written **only after the user actually edited something**, and the flag travels
 * with it. Without that flag a plain "app went to background right after opening the editor"
 * would restore an empty pre-load snapshot, block [markDirty]-guarded reloading, and let the
 * user overwrite a stored profile with blanks.
 *
 * Usage order matters: [restore] first (it may set the flag), then [register].
 */
class EditFormSaver(
    private val handle: SavedStateHandle,
    private val key: String,
) {

    var dirty: Boolean = false
        private set

    fun markDirty() {
        dirty = true
    }

    /** Returns the restored snapshot, or null when nothing user-edited was saved. */
    fun restore(): Bundle? = handle.get<Bundle>(key)?.takeIf { it.getBoolean(KEY_DIRTY, false) }

    /** [write] is invoked only while [dirty]; an untouched form persists nothing. */
    fun register(write: (Bundle) -> Unit) {
        handle.setSavedStateProvider(key) {
            Bundle(2).also { bundle ->
                if (dirty) {
                    bundle.putBoolean(KEY_DIRTY, true)
                    write(bundle)
                }
            }
        }
    }

    private companion object {
        const val KEY_DIRTY = "user_edited"
    }
}
