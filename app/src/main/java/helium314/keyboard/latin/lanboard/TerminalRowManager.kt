package helium314.keyboard.latin.lanboard

import android.view.KeyEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.TextView
import helium314.keyboard.latin.R

class TerminalRowManager(private val rootView: View) {

    private val scrollView: HorizontalScrollView? =
        rootView.findViewById(R.id.lb_terminal_row_scroll)

    var isVisible: Boolean
        get() = scrollView?.visibility == View.VISIBLE
        set(value) {
            scrollView?.visibility = if (value) View.VISIBLE else View.GONE
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
        )

        for ((viewId, keyCode) in keys) {
            rootView.findViewById<TextView>(viewId)?.setOnClickListener {
                sendKey(keyCode)
            }
        }

        rootView.findViewById<TextView>(R.id.lb_key_ctrl)?.apply {
            setOnClickListener {
                ctrlActive = !ctrlActive
                alpha = if (ctrlActive) 1.0f else 0.6f
            }
            alpha = 0.6f
        }

        rootView.findViewById<TextView>(R.id.lb_key_alt)?.apply {
            setOnClickListener {
                altActive = !altActive
                alpha = if (altActive) 1.0f else 0.6f
            }
            alpha = 0.6f
        }
    }

    private fun sendKey(keyCode: Int) {
        var metaState = 0
        if (ctrlActive) metaState = metaState or KeyEvent.META_CTRL_ON
        if (altActive) metaState = metaState or KeyEvent.META_ALT_ON
        keyEventSender?.invoke(keyCode, metaState)
        if (ctrlActive) {
            ctrlActive = false
            rootView.findViewById<TextView>(R.id.lb_key_ctrl)?.alpha = 0.6f
        }
        if (altActive) {
            altActive = false
            rootView.findViewById<TextView>(R.id.lb_key_alt)?.alpha = 0.6f
        }
    }
}
