// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.lanboard

import android.view.View
import android.widget.TextView
import helium314.keyboard.latin.R

/**
 * Host for the §7.4 collapsed badge — an amber dot drawn on the ▸ toolbar key. Implemented by
 * SuggestionStripView (which owns the key) so DelayedTranscriptionBanner can drive the collapsed
 * state and claim the first ▸ tap (§9.2 tap-priority) without reaching into upstream view internals.
 */
interface TranscriptionDotHost {
    /** Show the amber dot; [pulse] plays the one-shot arrival pulse (never loops, §13.2). */
    fun showTranscriptionDot(pulse: Boolean)
    fun hideTranscriptionDot()
    /** Register the §9.2 tap-priority callback; returns true if the ▸ tap was consumed (banner expanded). */
    fun setTranscriptionTapHandler(handler: () -> Boolean)
}

/**
 * §7.4 delayed-transcription banner — collapsed-first controller (Phase 2).
 *
 * When a queued recording transcribes after the network returns (VoiceInputController.retryPending →
 * LANboardBridge's onPendingTranscription), focus may be on a different field than when the audio was
 * captured, so the text must never auto-insert. Phase 1 replaced the v1 Toast with the expanded glass
 * banner; Phase 2 makes the arrival state the **collapsed amber dot** on the ▸ toolbar key
 * (visual contract delayed-transcription-banner.html): the dot pulses once on arrival, then rests, and
 * a ▸ tap expands it into the full banner (amber dot + "TRANSCRIPTION READY" + ~60-char ellipsized
 * preview + Insert / Copy / × controls).
 *
 * §9.2 tap-priority: while the dot is present, the first ▸ tap expands the banner (pending transcription
 * wins); the toolbar opens on the next tap or once the banner is resolved (see [TranscriptionDotHost]).
 *
 * Session-scoped (§7.4): persists for the current keyboard session until the user resolves it
 * (Insert / Copy / ×); [clearSession] drops it when the input view finishes. It does NOT auto-insert and
 * does NOT touch the Pending Queue — the transcription stays there regardless (§7.5), so nothing is lost
 * if the banner is dismissed. At most one expanded banner shows at a time; while collapsed the dot just
 * marks presence, and expanding cycles through any additional arrivals via the "+N more" count.
 */
class DelayedTranscriptionBanner(
    rootView: View,
    private val dotHost: TranscriptionDotHost?,
    private val onInsert: (id: String, text: String) -> Unit,
    private val onCopy: (id: String, text: String) -> Unit,
) {
    private data class Item(val id: String, val text: String)

    private val banner: View? = rootView.findViewById(R.id.lb_transcription_banner)
    private val previewView: TextView? = rootView.findViewById(R.id.lb_banner_preview)
    private val countView: TextView? = rootView.findViewById(R.id.lb_banner_count)

    /** FIFO of transcriptions awaiting the user's decision; the head is the one currently shown. */
    private val queue = ArrayDeque<Item>()

    /** Always-dot-first (§7.4 Phase 2): arrivals show the collapsed dot; the banner opens only on a ▸ tap. */
    private var expanded = false

    init {
        // Clip the amber left edge + glass body to the bg's rounded outline (layer-list item
        // gravity/width needs API 23; minSdk is 21, so we clip in code instead).
        banner?.clipToOutline = true

        banner?.findViewById<View>(R.id.lb_banner_insert)?.setOnClickListener {
            val current = queue.firstOrNull() ?: return@setOnClickListener
            onInsert(current.id, current.text)
            advance()
        }
        banner?.findViewById<View>(R.id.lb_banner_copy)?.setOnClickListener {
            val current = queue.firstOrNull() ?: return@setOnClickListener
            onCopy(current.id, current.text)
            advance()
        }
        banner?.findViewById<View>(R.id.lb_banner_discard)?.setOnClickListener {
            // × discards from the banner only; the recording stays in the Pending Queue (§7.4).
            advance()
        }

        // §9.2 tap-priority: claim the first ▸ tap to expand from the collapsed dot.
        dotHost?.setTranscriptionTapHandler { expandFromDot() }
    }

    /** A queued recording just transcribed. Arrives as the collapsed dot, or updates the open banner. */
    fun enqueue(id: String, text: String) {
        if (text.isBlank()) return
        if (queue.any { it.id == id }) return // idempotent: retryPending re-fire shouldn't double-add
        queue.addLast(Item(id, text))
        if (expanded) {
            renderExpanded() // already open — refresh preview/count, keep showing the head item
        } else {
            // Collapsed-first: show the dot and pulse once to flag the new arrival.
            hideBanner()
            dotHost?.showTranscriptionDot(pulse = true)
        }
    }

    /** Session ended (input view finished) — the banner is session-scoped, so drop it. */
    fun clearSession() {
        queue.clear()
        expanded = false
        hideBanner()
        dotHost?.hideTranscriptionDot()
    }

    /** §9.2 first-tap handler: expand the collapsed dot into the banner. Returns true if it consumed the tap. */
    private fun expandFromDot(): Boolean {
        if (expanded || queue.isEmpty()) return false // nothing pending, or already open → ▸ behaves normally
        expanded = true
        dotHost?.hideTranscriptionDot()
        renderExpanded()
        return true
    }

    private fun advance() {
        queue.removeFirstOrNull()
        if (queue.isEmpty()) {
            // All resolved: tear down to the resting state (no banner, no dot).
            expanded = false
            hideBanner()
            dotHost?.hideTranscriptionDot()
        } else {
            // More pending: stay expanded and cycle to the next item (§7.4 multiple arrivals).
            renderExpanded()
        }
    }

    private fun renderExpanded() {
        val current = queue.firstOrNull() ?: return
        previewView?.text = current.text

        val others = queue.size - 1
        countView?.let {
            if (others > 0) {
                it.text = it.context.getString(R.string.lb_transcription_banner_count, others)
                it.visibility = View.VISIBLE
            } else {
                it.visibility = View.GONE
            }
        }

        banner?.let {
            if (it.visibility != View.VISIBLE) {
                it.visibility = View.VISIBLE
                // One-shot fade-in on expand — never looping, to honor §13.2 (no ambient animation).
                it.alpha = 0f
                it.animate().alpha(1f).setDuration(140L).start()
            }
        }
    }

    private fun hideBanner() {
        banner?.animate()?.cancel()
        banner?.alpha = 1f
        banner?.visibility = View.GONE
    }
}
