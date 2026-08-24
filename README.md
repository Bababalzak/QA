# Jarvis Quest — Milestone 1

A local-first voice assistant for Meta Quest 3. This is the foundation
milestone: a real, native Android app with working microphone capture and
voice-activity detection, a Compose UI built for a VR panel, and the full
set of module interfaces (`AIService`, `SpeechToTextService`,
`TextToSpeechService`, `CommandRouter`) the rest of the assistant will be
built on. It has **not** been compiled in this sandbox — see "Known
limitations" before you do anything else with it.

## What's real vs. stubbed right now

| Layer | Status | Notes |
|---|---|---|
| App shell, manifest, permissions | **Real** | Standard 2D panel app; Quest runs these natively in Horizon Home, no VR SDK needed. |
| `AudioService` (microphone) | **Real** | `AudioRecord`-based 16 kHz mono PCM16 capture. |
| `EnergyBasedVad` | **Real** (intentionally simple) | Adaptive-noise-floor short-term energy VAD with debounce + hangover. Meets the brief's "temporary but not throwaway" bar — swap it for Silero VAD later behind the same `VoiceActivityDetector` interface. |
| `AndroidTextToSpeechService` | **Real**, availability unverified on Quest | Wraps `android.speech.tts.TextToSpeech`. The API shape has no conflict with this app's pipeline, so this is a genuine implementation — but see limitations below. |
| `NotYetImplementedSpeechToTextService` | **Honest stub** | Returns `Result.failure` with a clear message. Never fabricates a transcript. See the class doc for exactly why a "quick" implementation was rejected. |
| `NotReadyAIService` | **Honest stub** | Returns `Result.failure`. Never fabricates a reply. |
| `CommandRouter` | **Real**, minimal | Two direct actions (`stop`, `clear`) per the brief's "keep it minimal" instruction; everything else routes to `AIService`. |
| `LatencyTracker` | **Real** | Measures actual `System.nanoTime()` deltas — nothing here is invented. |

## Why this couldn't be compiled here

This project was built inside a cloud sandbox with **no Android SDK, no
NDK, no Gradle, no Kotlin compiler, and no outbound network access**
(verified directly — see the chat transcript for the actual commands and
output). A real Gradle Android build needs to download the Android Gradle
Plugin, the compileSdk platform, build-tools, and every AndroidX/Compose
dependency from Google's Maven and Maven Central; none of that is
reachable from this environment. This is a hard blocker, not a guess.

**The fix is `.github/workflows/android-build.yml`.** Push this project to
a GitHub repository (you can do this entirely from a browser — no PC
required, see below) and GitHub's own `ubuntu-latest` runners — which do
have full network access and Android tooling — will compile a real debug
APK and attach it to the workflow run as a downloadable artifact.

## Getting a real compiled APK (no PC required)

1. Create a new GitHub repository from **github.com** in any browser
   (works from the Quest browser or a phone).
2. Use GitHub's web-based file upload (or GitHub Codespaces / the web
   editor) to add every file in this project, preserving the folder
   structure.
3. The push automatically triggers `.github/workflows/android-build.yml`.
   Open the **Actions** tab to watch it build (~2–4 minutes).
4. When it finishes, open the run and download the `jarvis-quest-debug-apk`
   artifact — that's your real, compiled, installable APK.

## Installing on Quest 3

Two realistic paths, in order of how "PC-free" they are:

- **Fully PC-free:** Install a Meta Store app such as *VR Android File
  Manager* or *Quest APK Installer* directly on the headset from the
  official Meta Horizon Store (no developer mode needed for this step).
  Then, in the Quest browser, download the APK from your GitHub Actions
  artifact page and use that file manager to install it directly on-device.
- **With brief PC/phone access:** Enable Developer Mode via the Meta
  Horizon mobile app, then use [SideQuest](https://sidequestvr.com) or
  `adb install path/to/app-debug.apk` from any computer.

## Known limitations (be precise about these — do not assume otherwise)

- **Not tested on physical Quest 3 hardware.** Everything above describes
  what should work based on Quest 3's confirmed specs (Horizon OS on
  Android 14, Snapdragon XR2 Gen 2, 8 GB RAM, one built-in mic, no Google
  Mobile Services) — not what has been observed running on-device.
- **TTS engine availability on Quest is unverified.** Quest ships without
  GMS, so there is no confirmed built-in TTS engine to bind to. If
  `AndroidTextToSpeechService.isAvailable()` returns `false` on-device,
  that's the real, measured answer — the UI will show an honest
  "engine unavailable" state rather than fail silently.
- **STT is not implemented**, on purpose — see
  `NotYetImplementedSpeechToTextService`'s doc comment for the exact
  platform reason. Milestone 2 is expected to be a whisper.cpp JNI binding
  (an official Android example exists in `ggerganov/whisper.cpp`), fed
  directly from `AudioService`'s PCM16 output, with a small multilingual
  model (e.g. `ggml-base`) for Dutch + English.
- **AI inference is not implemented.** Milestone 3 is expected to wrap
  llama.cpp's Android JNI bindings (`examples/llama.android` upstream)
  loading a Qwen3-1.7B GGUF file. `ggml-org/Qwen3-1.7B-GGUF` on Hugging
  Face puts the Q4_K_M quantization at **1.28 GB** — too large to bundle
  in the APK, confirming the brief's Section 13 (`ModelManager` downloading
  it separately, likely over Wi-Fi from within the app on first run).
- **No Gradle wrapper jar is checked in.** `gradle wrapper` needs a local
  Gradle install to generate the binary `gradle-wrapper.jar`, which this
  sandbox doesn't have either. The CI workflow installs Gradle 8.9 directly
  instead of using `./gradlew`. Opening this project in Android Studio will
  prompt you to regenerate the wrapper automatically — let it.
- **Kotlin syntax has not been compiler-verified.** Every file was written
  carefully against known-current AndroidX/Compose/Kotlin APIs, but with
  no `kotlinc` or Android SDK available locally, the very first real
  compile signal will be the GitHub Actions run above. If it fails, the
  error log will point at the exact line — that's the fastest path to a
  fix, faster than guessing further in a sandbox that can't verify anything.

## Recommended next step

1. Push this to GitHub and confirm `android-build.yml` produces a real
   `app-debug.apk` (fix any compile errors the log surfaces — they'll be
   small, e.g. a version mismatch, not structural).
2. Sideload it and confirm on real Quest 3 hardware: does the app launch,
   does the mic permission prompt appear, does tapping "TAP TO LISTEN"
   visibly react to your voice (the VAD should flip IDLE → LISTENING →
   THINKING within well under a second of you finishing a sentence), and
   does `AndroidTextToSpeechService.isAvailable()` come back true or false?
   That last answer determines whether Milestone 2's TTS work is "wire up
   streaming" or "bundle an embedded engine from scratch."
3. Start Milestone 2 (VAD is already done; add whisper.cpp for local STT).
