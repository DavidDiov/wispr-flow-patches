# Option A — unmodified Wispr Flow + Morphe-managed Gboard — feasibility review

Reviewed 2026-11-17. This document is an engineering decision record, not a claim that a finished integration exists.

## Non-negotiable requirements

1. Keep the official Google Play Wispr Flow app unmodified and eligible for ordinary updates.
2. Keep PixelBoard/Gboard managed by Morphe, with a **native** button in Gboard's toolbar to switch Rambler/Wispr.
3. No root. No separate floating trigger, touch-overlay button, or visible Wispr bubble for triggering.
4. Later: Samsung Side button opens Gemini, focuses an editable prompt field, and uses the selected Wispr engine.
5. Do not confuse Wispr Flow (proprietary product) with the OpenAI Whisper ASR model.

## Direct inspection of the supplied official 2.8.7 XAPK

Input: user-supplied `Wispr Flow_2.8.7.xapk`, package `com.wispr.flowapp`. Unpacked base APK and examined binary `AndroidManifest.xml` plus DEX method tables and call sites.

- `com.wispr.flowapp.service.FlowService`: `android:exported="false"`. This service owns `handleRecordingToggled(boolean, boolean)`, `startDictationStream(...)`, `stopDictationStream(...)`, but Android forbids another ordinary app from invoking these private methods or starting that service directly.
- `FlowAccessibilityService` and `AccessibilityBridgeService`: also not exported.
- Public VIEW deep link `wispr-flow://open` is exposed through MainActivity. No public recording-start action was identified. Opening an app is not starting dictation.
- `FlowService.onStartCommand` handles internal actions like `com.wispr.flowapp.ACTION_SHOW`, `ACTION_HIDE`, `ACTION_REPAIR_ACCESSIBILITY`; it does **not** expose a third-party recording command. The internal commands cannot be sent directly to a nonexported service from Gboard.
- The normal ongoing status notification is built by `createForegroundNotification` with `PendingIntent.getActivity` for launching the application, and title `Voice dictation is ready`; no documented start-dictation action was found in this notification.
- Public exported boot/unlock receivers have lifecycle purposes, not a recording toggle.
- These are static observations. We did not have live-device control or device-side runtime inspection.

## External evidence

- Wispr documents the Android **Flow Bubble** as the start/stop trigger and directs users to keep the Android accessibility **shortcut OFF**: https://docs.wisprflow.ai/articles/5906198129-starting-your-first-dictation-on-android
- Android documents that other apps cannot start nonexported services: https://developer.android.com/develop/background-work/services
- Third-party speech keyboard projects document that standard Gboard's microphone does not delegate to arbitrary `RecognitionService`: https://github.com/georgernstgraf/polished-recognition and https://github.com/futo-org/voice-input
- PixelBoard provides native Morphe patches for Rambler: https://github.com/Akshayykadam/PixelBoard
- Wispr publishes an official **Voice Interface API** (WebSocket/REST): https://api-docs.wisprflow.ai/introduction
- Official API access is **approval-only** and requires a developer-platform API key and separate billing; it is not part of normal consumer Wispr sign-in: https://api-docs.wisprflow.ai/quickstart and https://api-docs.wisprflow.ai/usage_billing
- An unofficial SDK exists but depends on patched desktop app configuration/session tokens and frequently breaks across app updates; it is not a stable Android companion route: https://github.com/ThisisShashwat/wisprflow-sdk

## Feasible architectures

| Route | Official Wispr unmodified? | No bubble? | Native Gboard button? | Reliability |
|---|---|---|---|---|
| Accessibility gesture taps visible Flow Bubble | Yes | **No** | Possible via custom Morphe patch | Requires live testing; fragile UI and positioning |
| Directly send intent to FlowService | Yes | Yes | Possible | **Blocked** by `exported=false`; no trigger found |
| Change default Android speech recognizer | Yes | Yes | Not enough | Gboard owns its voice-typing pipeline; does not call arbitrary recognizer |
| Official Wispr Voice Interface API in a custom Gboard+companion client | Yes (official app untouched) | **Yes** | Possible | Requires Wispr **approval**, an API key, a second audio capture/text insertion client, Android permissions, and device tests |
| Unofficial desktop SDK/session-token reuse | Desktop must be patched | Yes | Possible in theory | Unsupported, unstable, not an acceptable production path |

## Decision

**Do not promise, publish, or automatically install a complete solution under the current hard constraints.** The official Wispr Android client does not expose a third-party recording trigger that we could verify. There is no viable guarantee that a Morphe toolbar button can start official Wispr recording without the bubble. The existing `feature/wispr-flow-287-bridge` experimental patch applies only to a **modified** Wispr APK, and the user's installed modified version was blocked by Google Play app protection.

### What would unblock the exact design

Ask Wispr to add a public, permission-protected Android `PendingIntent`/deep-link/start/stop dictation API that works from a trusted keyboard, **or** obtain Wispr Voice Interface API access and build a new independent on-device speech client (not a trigger of the installed Wispr app). In either case, Android installation, permission grants, and a device test are required, so no remote-only 100% guarantee is possible.

### Do not do

- Do not tell the user to disable Play Protect or remove the official Wispr app again.
- Do not imply that `wispr-flow://open` starts dictation.
- Do not claim a generic companion service can access Wispr's nonexported `FlowService`.
- Do not silently switch to OpenAI Whisper or another model and call it Wispr Flow.
- Do not build an overlay-only solution when the user explicitly rejects overlays.
