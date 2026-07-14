# CLAUDE.md — SDG Claw Developer Notes

## Build

```bash
./gradlew assembleDebug
```

Requires Android SDK 34, Java 8, Gradle 8.13.

---

## Stability Dashboard (ChatActivity)

### What it does

After every tool-result in an agent turn, `AgentLoop` converts the
`conversationHistory` into numeric feature vectors and calls
`StabilityDiagnostic.analyze()`.  The resulting `Report` is delivered to
`ChatActivity` via the `onDiagnosticReport` callback and rendered in a
**persistent BottomSheet** that slides up from the bottom of the chat screen.

### Feature vector (`AgentLoop.extractFeatureVector`)

Each `ChatMessage` is mapped to a 10-dimensional `DoubleArray` (all values
normalised to `[0, 1]`):

| Dim | Feature | Normalisation |
|-----|---------|---------------|
| 0 | Character count | log-scaled, cap 4 000 |
| 1 | Word count | log-scaled, cap 500 |
| 2 | Sentence count | log-scaled, cap 50 |
| 3 | Average word length | capped at 20 |
| 4 | Punctuation ratio | fraction of all chars |
| 5 | Digit ratio | fraction of all chars |
| 6 | Uppercase ratio | fraction of alpha chars |
| 7 | Unique-word ratio | unique / total words |
| 8 | Role bit-0 (LSB) | ordinal of role |
| 9 | Role bit-1 (MSB) | ordinal of role |

System messages are excluded so the static prompt doesn't skew drift.

### Dashboard UI elements

| View ID | Purpose |
|---------|---------|
| `stabilityDashboard` | The BottomSheet container |
| `tvDashboardHandle` | Tappable title row — toggles collapsed ↔ expanded |
| `chipRegime` | Colour-coded regime label (✅/🔴/🔁/⚡) |
| `chipSScore` | Stability score `S` |
| `chipDrift` | Final step drift magnitude |
| `chipEntropy` | Mean normalised Shannon entropy |
| `tvSparkline` | Monospace ASCII block-element drift sparkline |
| `tvRegimeDetail` | Human-readable regime detail string from `StabilityDiagnostic` |
| `btnForceStop` | Visible **only** for `diverging` / `chaotic-or-unsettled` regimes |

### Force Stop

`btnForceStop` calls `AgentLoop.forceStop()` which cancels the running
coroutine (`Job.cancel()`), resets state to `IDLE`, and hides the sheet.
It is only shown when `StabilityDiagnostic.Report.regime` is `"diverging"`
or `"chaotic-or-unsettled"`.

### Bottom sheet states

| State | When |
|-------|------|
| `HIDDEN` | Before first diagnostic report arrives |
| `COLLAPSED` | Peek height 112 dp — chips + sparkline visible — while agent is idle |
| `EXPANDED` | User taps handle, or agent is active with a dangerous regime |

---

## Key files changed in this feature

| File | Change |
|------|--------|
| `AgentLoop.kt` | Added `forceStop()`, `setOnDiagnosticReport()`, `extractFeatureVector()`, `emitDiagnosticIfPossible()` |
| `ChatActivity.kt` | Added dashboard init/render, `buildDriftSparkline()`, Force Stop wiring |
| `activity_chat.xml` | Root changed to `CoordinatorLayout`; added `stabilityDashboard` BottomSheet |
| `drawable/bg_dashboard_sheet.xml` | Rounded-top sheet background |
| `values/colors.xml` | Added 5 chip colours |
| `values/dimens.xml` | Added `dashboard_peek_height = 112dp` |
| `app/build.gradle` | Confirmed Material 1.11.0 + CoordinatorLayout 1.2.0 dependencies |
