# LANboard v2.4 — Updated Wireframes

Wireframes authored/updated during the Stage-2 build-review round. Both have standalone HTML visual contracts in this package; build to match those. The descriptions below capture the approved layout decisions.

---

## Manage Presets (list + edit + swatch grid) — NEW
**Visual contract:** `Q2-manage-presets-screen.html`
**Spec:** §6.2, §8.1, §9.3
**Status:** Approved (owner, this session)

This is the previously-missing render that was blocking `preset-management-ui`. Three panels: list, edit (non-General), edit (General).

### Manage Presets — list screen
**Layout:** App bar ("Manage presets", back arrow) over a single grouped frosted card of preset rows, then a separate card holding the "Add preset" action.
**Components present:**
- Each row, left→right: a **§6.2 identity dot** (the preset's color), a **name** + **subtitle** (e.g. Coding / "Termux · code editors"), and an **explicit drag handle** (grip glyph) on the right for reorder.
- The **General** row is last and shows a small **"PERMANENT"** marker in place of the drag handle's delete path — it is non-deletable (still reorderable; name/prompt still editable). Its dot is cyan with a soft glow.
- A one-line hint under the app bar: reorder sets the quick-picker fan order and per-app cold-start default.
- **"Add preset"** row in its own card below, cyan "+" icon and cyan label.
**Colors:** Frosted card `linear-gradient(180deg,#1a2029,#13181f)` + `--border`, inset top highlight; identity dots use each preset's hex; cyan accents on the add action and the General dot glow. Tokens match `Q6-settings-reskin-screen.html`.
**Interactions:** Tap a row → opens its edit screen. Drag the handle → reorder (writes the single global order). Tap "Add preset" → new-preset edit screen. **No "Auto-select preset per app" toggle on this screen** — it lives only under Settings → Voice accuracy (§8.1/§9.3).
**Approved:** Yes

### Preset edit screen — non-General (Coding shown)
**Layout:** App bar ("Edit preset", back, cyan "Done"). Then NAME field, PROMPT field with a right-aligned soft word-count, then the **Preset color** block (its own frosted card), then a centered **Delete preset** action at the bottom.
**Components present:**
- **NAME** — single-line input, inset dark fill.
- **PROMPT** — multi-line input; sample content reads as Whisper *register/vocabulary biasing* and deliberately contains **none** of the §8.2 global-substitution proper-nouns (see §8.1 guard note). Right-aligned counter `NN / ~150 words`, turning `--amber` at 150.
- **Preset color block** — header "Preset color" + caption "tints the mic icon + spike tips". Body is two columns: a **6×2 curated swatch grid** (left, flexes) and a **live mic preview** (right, ~96px). Selected swatch carries a cyan focus ring + check. The four locked-default swatches carry a small notch dot (bottom-right). A footnote spells out the reserved exclusions (cyan, green-pulse, amber, red, + purple) with sample reserved chips.
- **Live mic preview** — a cyan ring with three spike marks rendered as cyan-base → preset-tip gradients (mirrors §6.2 listening render), captioned "Live preview". Updates to the selected swatch.
- **Delete preset** — outlined `--red-dim` pill, low-emphasis (non-General only).
**Colors:** Swatch set = the 12 authored hexes (§6.2). Selected ring `--cyan`. Preview ring `--cyan` stroke, glow tinted to the selected color.
**Interactions:** Tap a swatch → selects it (preview + identity dot update live); duplicate selection allowed with a soft "also used by X" hint (G3). Edit name/prompt inline. Delete → removes the preset (cascade per §8.1/G1: active reverts to General, stale per-app map entries fall to cold-start).
**Approved:** Yes

### Preset edit screen — General (the exception)
**Layout:** Same app bar + NAME + PROMPT, but the **Preset color** block is replaced by a **fixed cyan identity chip** — a cyan disc + "Always cyan — the signature" + sub-caption "General owns LANboard's cyan; it can't be recolored." **No swatch grid. No Delete action.**
**Colors:** Cyan chip on a cyan-tinted frosted card.
**Interactions:** Name/prompt editable; color and existence are fixed (§6.2/§8.1).
**Approved:** Yes

---

## §7.4 Delayed-transcription banner — NEW
**Visual contract:** `delayed-transcription-banner.html`
**Spec:** §7.4 (full design), §9.2 (collapsed-dot host)
**Status:** Approved (owner, this session)

Replaces the v1 non-actionable Toast. Three states shown: expanded, collapsed, multi-arrival.

### Expanded banner
**Layout:** A floating glass surface docked directly above the full-width top strip, spanning the full strip width in every posture. Single row, left→right.
**Components present:** `--amber` status dot + label **"TRANSCRIPTION READY"**; a single-line **~60-char preview** (ellipsized); then right-aligned controls **Insert** (cyan primary), **Copy** (secondary outline), **×** (discard).
**Colors:** Dark glass body with an `--amber` left edge (3px) + amber dot; Insert is `--cyan` fill on dark text; Copy is `--border`-outlined secondary; × is muted. **Real backdrop blur** (floating surface, §6.7) — not the keys' faux-glass.
**Interactions:** Insert → drops text at the current cursor + dismiss. Copy → clipboard + dismiss. × → discard. Never auto-inserts. Session-scoped; the transcription also persists in the Pending Queue regardless (§7.5).
**Approved:** Yes

### Collapsed state
**Layout:** Banner gone; a small **amber dot badge on the far-left `▸` toolbar arrow**.
**Components present:** The `▸` arrow with an amber dot at its top-right.
**Interactions:** Dot pulses **once** on arrival then rests static (no looping — §13.2). **Tap priority:** while the dot is present, the first tap on the arrow expands the banner; the toolbar opens on the next tap or after the banner is resolved (G2/§9.2).
**Approved:** Yes

### Multiple arrivals
**Layout:** One expanded banner at a time; the label carries a **"+N more"** count chip (amber outline). Collapsed, the count rides on the dot.
**Interactions:** Resolving the current banner (Insert / Copy / ×) advances to the next queued transcription.
**Approved:** Yes
