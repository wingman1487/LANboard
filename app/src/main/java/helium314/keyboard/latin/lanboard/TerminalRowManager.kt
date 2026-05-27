package helium314.keyboard.latin.lanboard

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import helium314.keyboard.latin.R

class TerminalRowManager(private val rootView: View) {

    private val rowContainer: ViewGroup? =
        rootView.findViewById(R.id.lb_terminal_row_scroll)

    var isVisible: Boolean
        get() = rowContainer?.visibility == View.VISIBLE
        set(value) {
            rowContainer?.visibility = if (value) View.VISIBLE else View.GONE
            rootView.requestLayout()
        }

    private var ctrlActive = false
    private var altActive = false

    private var keyEventSender: ((Int, Int) -> Unit)? = null

    fun setKeyEventSender(sender: (keyCode: Int, metaState: Int) -> Unit) {
        keyEventSender = sender
    }

    fun init() {
        val keys = mapOf(
            R.id.lb_key_esc to KeyEvent.KEYCODE_ESCAPE,
            R.id.lb_key_tab to KeyEvent.KEYCODE_TAB,
            R.id.lb_key_left to KeyEvent.KEYCODE_DPAD_LEFT,
            R.id.lb_key_up to KeyEvent.KEYCODE_DPAD_UP,
            R.id.lb_key_down to KeyEvent.KEYCODE_DPAD_DOWN,
            R.id.lb_key_right to KeyEvent.KEYCODE_DPAD_RIGHT,
            R.id.lb_key_pgup to KeyEvent.KEYCODE_PAGE_UP,
            R.id.lb_key_pgdn to KeyEvent.KEYCODE_PAGE_DOWN,
            R.id.lb_key_slash to KeyEvent.KEYCODE_SLASH,
        )

        for ((viewId, keyCode) in keys) {
            rootView.findViewById<TextView>(viewId)?.setOnClickListener { v ->
                performHaptic(v)
                sendKey(keyCode)
            }
        }

        rootView.findViewById<TextView>(R.id.lb_key_ctrl)?.apply {
            setBackgroundResource(R.drawable.lb_terminal_modifier_indicator_inactive)
            setOnClickListener { v ->
                performHaptic(v)
                ctrlActive = !ctrlActive
                v.setBackgroundResource(
                    if (ctrlActive) R.drawable.lb_terminal_modifier_indicator_active
                    else R.drawable.lb_terminal_modifier_indicator_inactive
                )
            }
        }

        rootView.findViewById<TextView>(R.id.lb_key_alt)?.apply {
            setBackgroundResource(R.drawable.lb_terminal_modifier_indicator_inactive)
            setOnClickListener { v ->
                performHaptic(v)
                altActive = !altActive
                v.setBackgroundResource(
                    if (altActive) R.drawable.lb_terminal_modifier_indicator_active
                    else R.drawable.lb_terminal_modifier_indicator_inactive
                )
            }
        }
    }

    private fun performHaptic(view: View) {
        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun sendKey(keyCode: Int) {
        var metaState = 0
        if (ctrlActive) metaState = metaState or KeyEvent.META_CTRL_ON
        if (altActive) metaState = metaState or KeyEvent.META_ALT_ON
        keyEventSender?.invoke(keyCode, metaState)
        if (ctrlActive) {
            ctrlActive = false
            rootView.findViewById<TextView>(R.id.lb_key_ctrl)
                ?.setBackgroundResource(R.drawable.lb_terminal_modifier_indicator_inactive)
        }
        if (altActive) {
            altActive = false
            rootView.findViewById<TextView>(R.id.lb_key_alt)
                ?.setBackgroundResource(R.drawable.lb_terminal_modifier_indicator_inactive)
        }
    }
}
