# NGDroid

NGDroid is an Android circuit simulation application built around a native SPICE engine. It provides a netlist editor, an interactive waveform viewer with measurement cursors, and an AI assistant that can help with simulation tasks. The project is developed and maintained by Janada Sroor.

## Features

### Netlist editor

- Syntax-aware SPICE netlist editing with run, stop, and error reporting.
- Simulation progress, cancellation, and recovery from stalled or failed runs.
- Built-in example circuits to get started quickly.

### Simulation engine

- VioMATRIXC integration through JNI (`libngspice`), with automatic fallback to a built-in engine when the native library is unavailable for the device ABI. VioMATRIXC is an ngspice-based simulation engine maintained alongside this project at [Janadasroor/VioMATRIXC](https://github.com/Janadasroor/VioMATRIXC).
- The engine is not built from source here: the Gradle `fetchViomatrixc` task downloads pinned, SHA-256-verified release binaries (version in `gradle/viomatrixc.properties`) at build time. No engine binaries are committed to this repository and no local paths are required. Engine developers can point `-PviomatrixcLocalDir` (or `VIOMATRIXC_PREBUILT_DIR`) at a local build instead.
- Pre-decimated waveform data for smooth rendering of large transient analyses.
- Supported ABIs: `armeabi-v7a` (built-in engine), `arm64-v8a`, `x86_64` (VioMATRIXC).

### Waveform plot

- Interactive pan, zoom, and signal visibility controls.
- Dual measurement cursors with per-signal readout, range statistics between cursors, and a signal picker.
- Legend with long-press statistics and an exportable netlist/measurement summary.
- Plot export and sharing (PNG, PDF, text) through the system share sheet.

### AI assistant

- Multi-provider chat with streaming responses, vision (circuit image) support, and tool use for simulation and plotting tasks.
- Supported providers include OpenCode Zen, Google Gemini, and OpenRouter.
- On-device model catalogs with search, per-provider API key management, and custom skill definitions.
- Note: OpenCode Zen requires a personal API key (available free at opencode.ai). Anonymous keyless access to the Zen free tier has been discontinued by the provider.

### Application

- Four-tab navigation: Netlist, Plot, Assistant, Settings.
- Material 3 interface with adaptive phone and tablet layouts.
- Runtime permission handling for camera, notifications, and legacy storage where applicable.

## Requirements

- Android 8.0 (API 26) or later.
- Android Studio with Android SDK Platform 37, Build-Tools 37, NDK, and CMake for local builds.
- JDK 17.

## Getting started

Clone the repository and open it in Android Studio, or build from the command line:

```bash
./gradlew :app:assembleDebug
```

Run the unit test suite:

```bash
./gradlew :app:testDebugUnitTest
```

Install on a connected device or emulator:

```bash
./gradlew :app:installDebug
```

Continuous integration (`.github/workflows/ci.yml`) runs the unit tests and assembles the debug build, including the native library, on every push to `master` and on pull requests.

## AI assistant setup

1. Open the app and go to Settings, then AI Assistant.
2. Select a provider and paste the corresponding API key:
   - OpenCode Zen: key from opencode.ai (required for all Zen models, including `-free` models, which then run on the account's free quota).
   - Google Gemini or OpenRouter: key from the respective provider console.
3. Choose a model from the catalog and start chatting from the Assistant tab.

API keys are stored locally on the device using encrypted preferences and are never transmitted anywhere except to the selected provider's API.

## Architecture

```text
app/src/main/java/com/jnd/ngdroid/
  agent/     LLM providers, streaming clients, agent orchestration, skills
  data/      Persistent settings, model catalogs, file store
  domain/    Measurement and analysis use cases
  engine/    Simulation bridge, waveform analysis, native interface
  ui/        Compose screens (editor, plot, assistant, settings), navigation
app/src/main/cpp/
  ngspice_jni.cpp   JNI bridge to the native ngspice library
```

Key design points:

- Single Gradle module (`:app`); dependencies are version-catalogued in `gradle/libs.versions.toml`.
- ViewModels use injectable constructors with framework-compatible secondary constructors.
- Provider-specific behavior is isolated behind `HostDefaults`, a skill registry, and per-provider configuration objects.
- The unit suite (349 tests) covers error mapping, retry policy, cursor math, and measurement use cases.

## Contributing

Issues and pull requests are welcome. Please run the unit tests and lint before submitting, and keep changes scoped to a single concern per pull request.

## License

This project is licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for the full text. Third-party components bundled with the application are listed in the [NOTICE](NOTICE) file.

Copyright 2026 Janada Sroor.

## Author

Janada Sroor
