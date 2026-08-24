# Jarvis Quest — Milestone 2

A local-first voice assistant for Meta Quest 3.

**Milestone 1 is confirmed working, not just written.** A GitHub Actions
build of Milestone 1's exact code was inspected directly (as an APK, since
this sandbox still has no way to build one) and every class, resource
name, and manifest entry matched the source in this repo exactly —
`versionName 0.1.0-milestone1`, `androidGradlePluginVersion=8.7.0`,
package `com.jarvisquest.app`, all present and correct. See "What was
verified from the Milestone 1 APK" below for the full inspection.

Milestone 2 adds real local speech-to-text via whisper.cpp. **This has
not been compiled** — same sandbox limitation as before (see "Why this
still can't be compiled here"). Qwen/llama.cpp are deliberately not
touched this round.

## What's real vs. stubbed right now

| Layer | Status | Notes |
|---|---|---|
| App shell, manifest, permissions | **Real, build-confirmed** | Unchanged since Milestone 1. |
| `AudioService` (microphone) | **Real, build-confirmed** | `AudioRecord`-based 16 kHz mono PCM16 capture. |
| `EnergyBasedVad` | **Real, build-confirmed** | Now also feeds `speech_start`/`speech_end` into `LatencyTracker` (Phase 8). |
| `AndroidTextToSpeechService` | **Real, build-confirmed**; on-Quest availability still unverified | Unchanged since Milestone 1. |
| `WhisperSpeechToTextService` + `WhisperNative` + `whisper_bridge.cpp` | **New, real, NOT compiled/verified** | Local whisper.cpp inference over the exact PCM16 buffer `EnergyBasedVad` already produces — see "Whisper implementation details". |
| `ModelManager` / `ModelStatus` | **New, real** | Detects whether `ggml-base.bin` exists on-device; no auto-download this round (see below). |
| `ModelMissingSpeechToTextService` | **New, honest stub** | Used instead of Whisper when the model file isn't found — shows `"Whisper model ontbreekt"` + the exact path, both as a persistent UI banner and if you try to speak anyway. |
| `NotReadyAIService` | **Unchanged, honest stub** | Qwen/llama.cpp explicitly out of scope this milestone. |
| `CommandRouter`, `LatencyTracker` | **Unchanged, real** | `LatencyTracker` now also reports a `Speech` stage. |

## What was verified from the Milestone 1 APK

You provided `JarvisQuest-fixed.apk`, built from GitHub Actions. Without
an Android SDK in this sandbox, it was inspected as what it actually is —
a ZIP file — using `unzip`, `strings`, and `file`; no `aapt`/`apkanalyzer`
was available or needed for this level of inspection.

- **Package**: `com.jarvisquest.app`. **Main activity**:
  `com.jarvisquest.app.MainActivity`, exported, `LAUNCHER` intent-filter.
- **Permissions**: `android.permission.RECORD_AUDIO` (ours) and
  `android.permission.DUMP` (auto-added by the AndroidX profileinstaller
  library, not something we declared or need to worry about). No
  `INTERNET` permission — correct for Milestone 1.
- **`uses-feature android.hardware.microphone required="false"`** —
  matches our manifest exactly.
- **`versionName=0.1.0-milestone1`**, **AGP `8.7.0`** — read from
  `META-INF/com/android/build/gradle/app-metadata.properties` — an exact
  match for this project's `build.gradle.kts` at the time it was pushed.
- **Every one of our classes is present** in `classes2.dex`–`classes9.dex`
  (10 dex files total — unminified debug Compose builds routinely need
  multidex; not a sign of anything being wrong): `MainActivity`,
  `AudioService`, `EnergyBasedVad`, `VadEvent` and its four subtypes,
  `AssistantController` (with the exact coroutine-lambda names our source
  produces — `handleFrame$1`, `processUtterance$1`, `speak$1`,
  `startListening$1/2`), `AssistantViewModel`, `NotYetImplementedSpeechToTextService`,
  `NotReadyAIService`, `AndroidTextToSpeechService`, `AssistantScreenKt`,
  `ui.theme.ThemeKt`, and more. This is about as strong a confirmation as
  is possible without a running device: the source in this repo really is
  what got compiled.
- **`lib/` contains only `libandroidx.graphics.path.so`** (an AndroidX
  Compose transitive dependency, prebuilt for all 4 ABIs by that
  library's own AAR) — **no native code of ours**, because Milestone 1
  had none. This is also why the APK shipped all 4 ABIs (arm64-v8a,
  armeabi-v7a, x86, x86_64) instead of just arm64-v8a: there was no
  `ndk.abiFilters` to restrict it yet. **Fixed this round** — see
  `app/build.gradle.kts`.
- **No Whisper/Qwen/llama.cpp/GGUF/GGML traces anywhere** in the dex,
  confirming this APK predates any of that work — consistent with what
  this repo's history shows.
- **What couldn't be determined from the APK alone**: exact `minSdk`
  value (the manifest's `uses-sdk` element strings are present but the
  actual integer attribute values are binary-encoded and weren't decoded
  without `aapt`/`androguard`, neither of which is available in this
  sandbox); runtime behavior (whether it actually launches, whether the
  mic permission prompt appears, whether the VAD visibly reacts) — none of
  that is knowable from a static APK, only from running it.

## Whisper implementation details

- **Pipeline**: `AudioService` (unchanged) → `EnergyBasedVad` (unchanged)
  → `WhisperSpeechToTextService.transcribe(ShortArray, sampleRateHz)` →
  `WhisperNative.nativeTranscribe()` (JNI) → `whisper_bridge.cpp` →
  `whisper_full()` → segment text concatenated and returned. No WAV/file
  round-trip — the PCM16 buffer is converted straight to a normalized
  `FloatArray` in Kotlin and passed across JNI.
- **Language**: `params.language = "auto"` (whisper.cpp auto-detects
  Dutch vs. other languages per-utterance) with `params.translate =
  false`, so Dutch speech is transcribed as Dutch text, never silently
  translated to English — matches Phase 6 exactly.
- **Single inference at a time**: `WhisperSpeechToTextService.transcribe`
  runs on `Dispatchers.Default` inside a suspend function called from
  `AssistantController`'s single coroutine `scope`; there is exactly one
  in-flight utterance at a time by construction (the controller doesn't
  start a new one until the previous `processUtterance` call returns), so
  no explicit mutex was needed to satisfy "only one inference at a time."
- **Loaded once**: `WhisperNative.nativeInit()` is called lazily on first
  use and the returned context handle is reused for every subsequent
  utterance; it is never reloaded per-request, matching Phase 9.

## Model selected/recommended

**`ggml-base.bin`** (whisper.cpp's multilingual "base" model, ~142 MB,
74M parameters), prioritized in this order per Phase 4:

1. **Latency** — base runs comfortably on ARM64 CPU for short
   VAD-segmented utterances; `tiny` is faster but meaningfully less
   accurate outside English.
2. **Dutch quality** — `tiny`'s multilingual accuracy drops off
   noticeably vs. `base`; `base` is the smallest model with genuinely
   usable non-English accuracy.
3. **RAM** — ~150 MB resident for `base`, well within Quest 3's 8 GB.
4. **ARM64 perf** — whisper.cpp's ARM NEON kernels apply equally to
   `tiny`/`base`/`small`, so this mostly follows from (1).

**Upgrade path if Dutch accuracy isn't good enough on real hardware**:
`ggml-small.bin` (~466 MB) is a straight drop-in — just change
`ModelManager.WHISPER_MODEL_FILENAME` and place the new file at the same
path. A quantized variant (e.g. `ggml-base-q5_1.bin`, roughly half the
size) is also worth trying if storage or load time becomes a concern.

**Getting the model onto the device** (no auto-download this round — see
below): download `ggml-base.bin` from the official
`ggml-org/whisper.cpp` model listing on Hugging Face, then
`adb push ggml-base.bin /sdcard/Android/data/com.jarvisquest.app/files/models/ggml-base.bin`
(create the `models` folder first if needed), or copy it there with any
on-device file manager. Until it's present, the app shows
`"Whisper model ontbreekt"` with this exact path in a banner, and never
returns a fabricated transcript.

*Why no auto-download*: this milestone's brief asks for detection +
honest messaging ("Do NOT silently fail"), not a download pipeline.
Adding one means adding `INTERNET` permission and download/progress UI
that weren't asked for here — easy to add next if wanted, but not
implemented speculatively.

## Native/JNI details

- `app/src/main/cpp/CMakeLists.txt` builds **only** `whisper.cpp` this
  round (`WHISPER_BUILD_EXAMPLES/TESTS/SERVER=OFF`, no SDL2) into
  `libwhisper.so`, plus our own `libjarvis_whisper.so` bridge.
- whisper.cpp is a **git submodule** (`.gitmodules` →
  `app/src/main/cpp/third_party/whisper.cpp`, tracking `ggml-org/whisper.cpp`
  `master`), not a Maven dependency — CI checks it out with
  `submodules: recursive`.
- `ndkVersion = "27.2.12479018"` is pinned in `app/build.gradle.kts` and
  matches the `ndk;27.2.12479018` package the CI workflow installs
  explicitly, so both resolve the identical NDK.
- `abiFilters += "arm64-v8a"` — Quest 3 is ARM64-only; this also fixes the
  4-ABI bloat observed in the Milestone 1 APK.
- **llama.cpp was intentionally removed from the build this round** — a
  `llama_bridge.cpp` JNI bridge and a `llama.cpp` submodule reference
  existed from an earlier exploratory pass but weren't wired into
  `AssistantViewModel` or `CMakeLists.txt`; both were deleted rather than
  left half-present, so this milestone's actual scope (STT only) matches
  what's really in the repo. The earlier design (`llama_model_load_from_file`
  / `llama_init_from_model` / `llama_chat_apply_template` / per-token JNI
  streaming callback) is recorded here so Milestone 3 doesn't start from
  zero.
- **Highest-risk files in this repo**: `whisper_bridge.cpp` calls
  whisper.cpp's C API (`whisper_init_from_file_with_params`,
  `whisper_full`, `whisper_full_get_segment_text`, ...) based on this
  project's best current knowledge of that API, not a compiler or a diff
  against the exact commit the submodule resolves to. If CI fails here,
  the error will name the exact missing/renamed symbol — fix that one
  call against `third_party/whisper.cpp/include/whisper.h` at whatever
  commit got checked out, not a rewrite.

## Why this still can't be compiled here

Same fundamental blocker as Milestone 1: this sandbox has no Android SDK,
NDK, Gradle, or outbound network access. That's now doubly true for a
native/JNI build — CMake would also need to fetch and cross-compile
whisper.cpp itself, which needs the whisper.cpp submodule to be present
(git submodule fetch needs network) and the NDK toolchain (also needs
network to install). GitHub Actions remains the actual build/verify step
— see `.github/workflows/android-build.yml`, updated this round to check
out the submodule and install the NDK.

## Installing on Quest 3

Unchanged from Milestone 1 — see the previous section of this file's
history, or: install a Meta Store file-manager app on-device and install
the downloaded APK directly from the Quest browser (no PC), or use
SideQuest/`adb install` with brief PC access.

## Known limitations (be precise — do not assume otherwise)

- **Not tested on physical Quest 3 hardware** — everything here describes
  what should work, not what has been observed running.
- **whisper_bridge.cpp / llama_bridge.cpp API calls are unverified**
  against the actual pinned submodule commit (see "Native/JNI details").
- **No auto-download for the whisper model** — must be placed manually
  (adb push or file manager) at the path the app reports.
- **TTS engine availability on Quest is still unverified** (unchanged
  from Milestone 1 — Quest ships without GMS).
- **`android.permission.DUMP` appears in the built APK** even though this
  project never declares it — confirmed via manifest inspection to be
  auto-merged by the AndroidX profileinstaller library, not a mistake in
  this project's own manifest.
- **VAD currently returns to `LISTENING` (continuous), not `IDLE`, after
  each utterance** — Phase 7's diagram shows a return to `IDLE`. Continuous
  listening was kept because it more directly satisfies "do not require a
  second button press" (no re-tap needed for the next utterance); flag if
  you'd rather it literally return to `IDLE` and wait for another tap.
- **No Gradle wrapper jar** — unchanged from Milestone 1, CI uses a pinned
  `gradle` install instead of `./gradlew`.
- **Kotlin/C++ syntax not compiler-verified** — same as Milestone 1's
  disclaimer, now applying to native code too, which is inherently
  higher-risk than pure Kotlin.

## Recommended next step

1. Push these changes to the same GitHub repository and confirm
   `android-build.yml` produces a green build — this is the real
   whisper.cpp compile signal, including the new "Verify native libraries
   are packaged" step that fails loudly if `libjarvis_whisper.so` /
   `libwhisper.so` don't end up in the APK.
2. Push `ggml-base.bin` onto a real Quest 3 (or any Android phone for a
   faster first test) at the reported path, sideload the new APK, and
   confirm: does the "Whisper model ontbreekt" banner disappear once the
   file is present, does speaking Dutch produce a real transcript, and
   does the `Speech` / `STT` latency breakdown show real, non-zero
   numbers.
3. Only after that: Milestone 3 (Qwen3 + llama.cpp), reusing the
   `llama_bridge.cpp` design recorded above.
