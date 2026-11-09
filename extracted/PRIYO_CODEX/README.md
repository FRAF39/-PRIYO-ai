# PRIYO_CODEX

Offline-first Android AI chat starter for Priyo. Kotlin, Jetpack Compose, Room, CameraX dependencies, Android SpeechRecognizer/TTS, and llama.cpp JNI CPU inference.

## Important delivery note
The GGUF model is about 491 MB. The source ZIP deliberately does **not** contain the model because the download could not be verified during packaging. The app expects it at `app/src/main/assets/models/qwen2.5-coder-0.5b-instruct-q4_k_m.gguf`. After adding it, Gradle bundles it in the APK and model inference is offline. No model download is performed by the app.

## Requirements
- Android Studio with JDK 17
- Android SDK 36 and NDK installed
- CMake 3.22.1
- Git + network once, to fetch llama.cpp source
- arm64-v8a recommended; 32-bit ABI may be removed if the native dependency does not support it on your toolchain

## Setup / build
1. Extract ZIP.
2. Run `bash scripts/prepare-native.sh` to fetch upstream llama.cpp source into `app/src/main/cpp/llama.cpp`.
3. Download the exact model using `bash scripts/download-model.sh` (or copy it manually into `app/src/main/assets/models/`).
4. Open the root folder in Android Studio and sync Gradle.
5. Build > Build APK(s).

On Windows, run equivalent `git clone` and download commands in PowerShell.

## Low-memory profile
The JNI engine uses CPU-only inference, 2 threads, 1024-token context and batch size 128. This is a conservative starting point for 4 GB RAM; actual speed and stability depend on device free RAM, thermal state, ABI, and Android version. Avoid increasing context or thread count without profiling.

## App behavior
- Animated PRIYO splash
- PIN gate: default `7777` (demo-grade, not cryptographic authentication)
- Compose chat UI and quick chips
- Room local history
- Native Android speech recognition and TTS controls
- User identity response hardcoded before model invocation
- CameraX dependencies included; camera preview/capture flow is not yet exposed in the chat UI
- Network permission intentionally omitted; app runtime has no network feature

## Security caveats
- A hardcoded PIN can be extracted from an APK and must not be treated as secure protection for sensitive data.
- Room database is local but not encrypted. Do not store secrets in chat history.
- Android SpeechRecognizer may use the system speech service; depending on the installed provider and language pack, recognition may require a network connection. TTS voices may also require installed voice data.
- The 0.5B coder model is primarily optimized for coding; multilingual conversation quality is not guaranteed.
- The JNI layer uses the upstream llama.cpp C API. Upstream API changes can require updating `native-lib.cpp`; pin/commit the fetched source before production release.

## Official references
- llama.cpp Android docs: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- Model: https://huggingface.co/Qwen/Qwen2.5-Coder-0.5B-Instruct-GGUF
