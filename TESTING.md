# Testing Guide — LANboard v1

## Automated Tests

### Unit tests

```bash
./gradlew testDebugUnitTest
```

Expected: 160 passing, 3 pre-existing HeliBoard failures:
- `SuggestTest > autocorrect capitalization` (upstream)
- `InputLogicTest > insertLetterIntoWordHangulFails` (upstream)
- `StringUtilsTest > detectEmojisAtEndFails` (upstream)

These are inherited from HeliBoard and are not LANboard regressions.

### Build verification

```bash
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/LANboard_3.9-debug.apk
```

---

## Device Testing

### Prerequisites

- Android device (tested on Pixel 10 Fold, Android 16)
- `faster-whisper-server` or any OpenAI-compatible Whisper endpoint running and reachable from the device
- Device and server on the same network (LAN, Tailscale, or WireGuard)

### Install

```bash
adb install app/build/outputs/apk/debug/LANboard_3.9-debug.apk
```

Or transfer the APK to the device and install via file manager.

---

## Manual Test Checklist

### First-Run Wizard

- [ ] Wizard launches automatically on first keyboard use
- [ ] Step 1 (Welcome): displays correctly, "I don't have a server" link visible
- [ ] Step 2 (Mic Permission): requests RECORD_AUDIO, "Skip — typing only" option works
- [ ] Step 3 (Server Setup): URL field, test connection button, auth section collapsed by default
- [ ] Step 4 (Enable IME): opens system settings, detects when LANboard is enabled
- [ ] Step 5 (Try It Out): live text field, keyboard auto-pops, mic tap works
- [ ] Wizard can be re-triggered from Settings → "Run setup again"
- [ ] Wizard skippable via "I know what I'm doing"

### Keyboard Layout

- [ ] Top strip visible: toolbar button (left), suggestions (center), mic ring (right)
- [ ] Toolbar button opens tray overlay with: Clipboard, Terminal, Preset, Emoji
- [ ] QWERTY rows render correctly (inherited HeliBoard layout)
- [ ] Landscape mode: top strip scales, mic ring stays fixed size
- [ ] Foldable inner display: keyboard spans full width, elements proportional

### Mic Ring States

- [ ] **Idle, server OK**: thin grey circle, slow green breathing pulse (3.4s cycle)
- [ ] **Idle, server unreachable**: dim red circle, fully static, tap shows toast
- [ ] **Idle, server checking**: amber circle, slow pulse
- [ ] **Listening**: cyan-to-blue gradient spikes driven by voice amplitude
- [ ] **Transcribing**: dim circle with sweeping bright arc

### Voice Transcription

- [ ] Tap mic → starts listening (ring transitions to listening state)
- [ ] Speak → spikes respond to voice amplitude in real-time
- [ ] Tap mic again → commits, audio sent to server, text inserted at cursor
- [ ] Long-press mic during listening → discards, nothing typed
- [ ] Tap mic during transcribing → no-op (button locked)
- [ ] Near-silence → "No speech detected" toast (RMS gating)
- [ ] Hallucination blocklist filters known false outputs ("Okay.", "Thank you.", etc.)

### Audio Meter

- [ ] Replaces suggestion strip during listening
- [ ] 14-18 cyan bars bouncing with audio amplitude
- [ ] Edge bars render at lower alpha
- [ ] Fast attack, slow release animation
- [ ] Shares same audio source as mic ring (no visual lag between them)

### Dual-Path Commit

- [ ] Audio is cached locally AND sent to server on commit
- [ ] Kill network mid-recording → local file preserved, marked pending
- [ ] Pending recordings visible in Settings → Storage → Pending recordings
- [ ] Pending retries on next keyboard session when server healthy
- [ ] Successful retry shows amber banner with Insert/Copy/Dismiss options
- [ ] Recordings auto-expire after 7 days

### Terminal Row

- [ ] Toggle on from toolbar tray → terminal row appears above QWERTY
- [ ] Toggle off → row disappears, QWERTY reclaims space
- [ ] Top row: Esc, Ctrl, Alt, PgUp, PgDn
- [ ] Bottom row: Tab, left/up/down/right arrows, /
- [ ] Ctrl and Alt show yellow underline when active (sticky modifier)
- [ ] Keys produce correct key events in Termux/SSH apps
- [ ] Terminal row default toggle in Settings → Keyboard

### Voice Presets

- [ ] 5 default presets: Coding, Email, Personal, Terminal, General
- [ ] Switch preset from toolbar tray → pill animates with cyan scale-pulse
- [ ] Active preset shown in tray and in Settings → Voice accuracy
- [ ] Manage Presets screen: add, edit, delete presets
- [ ] Word counter warns at 150 words (amber)
- [ ] Active preset text sent as `prompt` parameter to Whisper

### Substitutions

- [ ] Default rules present (TrueNAS, kubectl, PyQt, etc.)
- [ ] Substitutions applied after transcription, before insertion
- [ ] Longest-match-first ordering
- [ ] Case-insensitive matching
- [ ] Add/edit/delete rules in Settings → Voice accuracy → Substitutions

### Authentication

- [ ] Auth mode: None (default) — no header sent
- [ ] Auth mode: Bearer — `Authorization: Bearer <token>` header
- [ ] Auth mode: Basic — `Authorization: Basic <base64>` header
- [ ] Credentials stored in Android Keystore (encrypted)
- [ ] Token masked in settings UI with show/hide toggle
- [ ] 401 response shows "Authentication failed — check your token in settings"
- [ ] Auth value never appears in logs or toasts

### Settings

- [ ] Server section: URL, Connection status, Model dropdown (from `/v1/models`), Language, Auth, Test connection
- [ ] Voice accuracy section: Active preset, Manage presets, Substitutions
- [ ] Keyboard section: Terminal row default, Sensitive-field behavior, HeliBoard settings
- [ ] Storage section: Pending recordings count and drill-down
- [ ] About section: Credits & licenses, Version

### Sensitive Fields

- [ ] Mic tap in password field → warning dialog appears
- [ ] Cancel → no recording
- [ ] "Use voice this time" → records but bypasses local cache (no file written)
- [ ] Override is session-scoped: leave and return to field → warning shows again

### Credits & Licenses

- [ ] Settings → About → Credits & Licenses
- [ ] HeliBoard attribution with tappable link
- [ ] OpenBoard attribution with tappable link
- [ ] GPL-3.0, Apache 2.0, CC-BY-SA 4.0 licenses referenced

### App Icon

- [ ] Distinctive ring-with-spikes icon visible in launcher
- [ ] Icon renders clearly at all device DPIs
- [ ] Adaptive icon shape adapts to device theme (round, squircle, etc.)

---

## Server Setup for Testing

### faster-whisper-server (Docker)

```bash
docker run -d \
  --name faster-whisper \
  --gpus all \
  -p 8000:8000 \
  fedirz/faster-whisper-server:latest
```

Verify: `curl http://<server-ip>:8000/health` should return 200.

### Endpoints used by LANboard

| Endpoint | Purpose |
|---|---|
| `GET /health` | Server health check |
| `POST /v1/audio/transcriptions` | Transcribe audio (multipart WAV) |
| `GET /v1/models` | List available models (populates settings dropdown) |

---

## Troubleshooting

| Symptom | Check |
|---|---|
| Mic ring stays red | Server URL correct? Device on same network? Cleartext HTTP allowed? |
| "No speech detected" on every attempt | Speak louder or closer to mic; RMS threshold is 0.010 |
| Transcription returns hallucinations | Ensure blocklist is active; try a different voice context preset |
| Terminal keys not working | Verify the target app accepts key events (works in Termux, may not in all apps) |
| Wizard doesn't auto-launch | Clear app data and reopen keyboard |
| APK won't install | Check for existing HeliBoard installation with conflicting package name |
