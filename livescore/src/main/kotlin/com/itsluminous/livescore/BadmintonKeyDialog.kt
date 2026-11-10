package com.itsluminous.livescore

import android.app.Activity
import android.app.AlertDialog
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText

/**
 * The one dialog for the user's RapidAPI key (ADR-044), from Settings and from the follow picker the moment badminton
 * is enabled with no key stored ([BadmintonGate]): a text field for the TV's keyboard, a message that says whose key
 * it is and what it may cost, OK to save (trimmed, in the app's preferences and nowhere else), Cancel to leave
 * things as they are; the TV keyboard's Done key is OK too, since the keyboard covers the buttons while it is up.
 * Settings also offers Remove. The key itself is never shown back except as its last four
 * characters in the field's hint.
 */
object BadmintonKeyDialog {
    /**
     * Shows the dialog. [onSaved] runs after OK with a non-blank key, [onRemoved] after Remove (offered only when
     * [allowRemove]), [onCancelled] after Cancel or Back; OK with an empty field counts as Cancel.
     */
    fun show(
        activity: Activity,
        prefs: Prefs,
        allowRemove: Boolean,
        onSaved: () -> Unit,
        onRemoved: () -> Unit = {},
        onCancelled: () -> Unit = {},
    ): AlertDialog {
        val field = EditText(activity)
        field.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_PASSWORD
        val tail = BadmintonGate.tail(prefs.badmintonKey)
        field.hint = if (tail.isEmpty()) activity.getString(R.string.badminton_key_hint) else activity.getString(R.string.badminton_key_hint_set, tail)
        field.setSingleLine()
        field.imeOptions = EditorInfo.IME_ACTION_DONE
        field.id = R.id.badmintonKeyField
        val builder = AlertDialog.Builder(activity)
            .setTitle(R.string.badminton_key)
            .setMessage(R.string.badminton_key_explain)
            .setView(field)
            .setPositiveButton(R.string.ok) { _, _ ->
                val key = field.text.toString().trim()
                if (key.isEmpty()) {
                    onCancelled()
                } else {
                    prefs.badmintonKey = key
                    onSaved()
                }
            }
            .setNegativeButton(R.string.cancel) { _, _ -> onCancelled() }
            .setOnCancelListener { onCancelled() }
        if (allowRemove) {
            builder.setNeutralButton(R.string.badminton_key_remove) { _, _ ->
                prefs.badmintonKey = ""
                onRemoved()
            }
        }
        val dialog = builder.show()
        // The TV keyboard covers the dialog's buttons while it is up: its Done key is the OK button.
        field.setOnEditorActionListener { _, action, _ ->
            if (action != EditorInfo.IME_ACTION_DONE) return@setOnEditorActionListener false
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            true
        }
        field.requestFocus()
        return dialog
    }
}
