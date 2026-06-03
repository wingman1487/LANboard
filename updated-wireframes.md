# LANboard v2.5 — Updated Wireframes

> One new wireframe this round. It was rendered and approved during the design review conversation; the file itself ships in this package and is the **authoritative render contract** — build to match it (§13.3). The description below captures the approved decisions in the standard format.

## Quick Preset Picker Overlay (`quick-picker-overlay.html`) — NEW (§5.6)

**Layout**: A single full-strip floating scrim plate over the 44dp top strip's center+right zone (the plate ends before the far-left `▸` arrow column, which stays visible beside it). Contents right-to-left: the inert active-preset float as the in-strip head immediately left of the ring → alternative pills filling nearest-the-ring-first in §8.1 order (up to the ~4 cap) → the two-state "…"/gear utility tile at the row's far-left end. Resting layout is a straight horizontal row on the strip baseline; "fan" is the staggered open motion only. The ring stays top-right in every posture; the plate spans the full strip width in all postures (never halved).

**Components present**:
- **Scrim plate**: `backdrop-filter:blur(14px)`, radius 12, shadow `0 10px 24px rgba(0,0,0,0.6)` + inset top lip `rgba(255,255,255,0.08)` (the §7.4 banner's values), cyan-glass fill `#10242c→#0b181d` at ~92% alpha, border `rgba(0,212,255,0.18)`. One plate — never per-pill blur surfaces.
- **Inert float**: 30dp tall, radius 8, padding 0 12dp, weight 700 — **solid cyan `#00d4ff` fill always** (regardless of active preset; identity rides on the mic tint), ink `#04222b`, soft glow `0 0 10px rgba(0,212,255,0.6)`, **no top-lip inset, no lift shadow** (marker, not target).
- **Alternative pills**: 30dp tall, radius 8, system sans 12.5sp/600, neutral glass `#2b333d→#181e25`, white-lip inset + small lift shadow, max-width 118dp with tail ellipsis, leading 13dp identity dot (solid preset hex + `0 0 0 3px rgba(255,255,255,0.04)` ring; General's dot keeps its cyan glow). Gap 7dp, plate insets 8dp.
- **Utility tile**: 30dp, radius 8, dimmer glass `#222a33→#161c23`, muted ink, no dot. Gear when nothing hidden; "…" + cyan "+N" count chip (10.5sp, outlined `rgba(0,212,255,0.35)`, radius 6) on overflow.

**Colors used**: cyan signature on float + armed states + count chip; per-preset hexes on dots only (Coding `#3b82f6`, Email `#14b8a6`, Personal `#e0569f`, Terminal `#8090a8`, General `#00d4ff`); scrim/pills in the neutral/cyan dark-glass family — never the banner's amber.

**Interactions** (all within the one continuous gesture, §5.6):
- Slide over a pill → **armed**: cyan border + 1dp lift + 1.03 scale. Hit region per pill = pill width + half-gap each side at the **full 44dp band height**.
- Lift on a pill → float label updates + pinned **cyan** scale-pulse (~150ms, 1→1.14→1) → retract (~200ms) → mic icon crossfades to the landed preset's color (~200ms, one-shot).
- Lift on "…"/gear → Manage Presets (active preset pre-selected). During an open picker, release precedence at the `▸` end belongs to the picker; the §7.4 badge's first-tap priority applies only outside a gesture.
- Lift anywhere else / instant release → honest unfurl-then-snap-back retract, no preset committed.
- Open/retract are one-shot on gesture, settle static — §13.2-compliant via the press-glint carve-out (demonstrated in the file's motion demos).

**Frames in the file**: (1) portrait open with gear; (2) armed state + hit-rule visualization; (3) portrait overflow "… +2" with a truncated long label; (4) single-alternative edge state; (5) wide unfolded/split frame filling to the cap; (6) folded-landscape worst case (two-row terminal + live amber badge + open picker); (7) red `IDLE_UNREACHABLE` ring beneath an unchanged overlay; (8) §7.4 banner expanded + picker open (float in-strip below the banner-aware `visibleTopY`); (9) interactive one-shot open / switch / instant-release motion demos.

**Approved**: Yes (owner-approved in the v2.5 design review; float-stays-cyan and nearest-ring-first fill were explicit owner calls).

## Screens checked, unchanged

- **§7.4 banner (`delayed-transcription-banner.html`)** — no visual change; the picker borrows its blur/shadow/lip values and adds a coexistence/z-order rule only (spec §5.6/§9.1/§9.2).
- **Manage Presets (`Q2-manage-presets-screen.html`)** — no visual change; it is the "…"/gear destination and its reorder already drives the (now nearest-ring-first) fill order.
- **Split layout (`Q5-split-keyboard-layout.html`)** — no visual change; the wide-strip count rule is documented in §10.4 and rendered in the new contract's wide frames.
