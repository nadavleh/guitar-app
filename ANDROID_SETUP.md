# Android Development Environment — Setup Notes

What we installed, configured, and why. Written for someone with a strong systems/algorithms background but new to the Android/JS-mobile ecosystem.

---

## TL;DR — what's now on this machine

| Component | Where | Purpose |
|---|---|---|
| Android Studio | `C:\Program Files\Android\Android Studio\` | IDE + installer/manager for the rest of the Android toolchain |
| JDK (JBR 21) | `C:\Program Files\Android\Android Studio\jbr\` | Java runtime; required by Gradle (the Android build orchestrator) |
| Android SDK | `C:\Users\Nadav\AppData\Local\Android\Sdk\` | Libraries, headers, and CLI tools needed to compile against and talk to Android |
| AVD (Pixel 7, API 34) | `C:\Users\Nadav\.android\avd\` (default) | Virtual phone the emulator runs |
| Windows Hypervisor Platform | Windows feature | Lets the emulator use hardware virtualization (near-native speed) |

Env vars set (User scope):
- `JAVA_HOME = C:\Program Files\Android\Android Studio\jbr`
- `ANDROID_HOME = C:\Users\Nadav\AppData\Local\Android\Sdk`
- `ANDROID_SDK_ROOT = ...\Sdk` (same — legacy duplicate name some tools still read)
- `PATH` prepended with `...\Sdk\platform-tools`, `...\Sdk\emulator`, `...\Sdk\cmdline-tools\latest\bin`

PATH cleanup done: stray `C:\Android` folder (an old, unmanaged standalone copy of `adb.exe` / `fastboot.exe`) removed from both Machine and User `PATH` — it was shadowing the proper SDK's `adb` and would have caused version-mismatch errors. The folder itself was left on disk untouched.

---

## Step-by-step recap

### Step 1 — BIOS virtualization + Windows Hypervisor Platform

**What:** Enabled hardware virtualization (Intel VT-x / AMD-V) in BIOS and the `Windows Hypervisor Platform` Windows feature.

**Why:** The Android emulator is a virtual machine running a full Android OS image. Without a hypervisor, the host CPU has to software-emulate every guest instruction — orders of magnitude slower. With a hypervisor, the guest runs guest instructions directly on the host CPU with hardware-enforced isolation, giving near-native speed.

> **Hypervisor** = a thin layer between OS and CPU that arbitrates virtual-machine execution. Two flavors: type-1 (bare-metal, like Hyper-V) and type-2 (hosted, like VirtualBox). Windows Hypervisor Platform exposes hypervisor primitives to third-party tools like the Android emulator.

### Step 2 — Android Studio

**What:** Installed Android Studio. The first-run wizard pulled down the Android SDK skeleton (~600 MB; system images come separately).

**Why:** Android Studio bundles the IDE (IntelliJ-based) plus an SDK Manager UI that downloads/updates SDK components. We mostly care about the SDK and its tools, not the IDE itself — you can build and run Android apps entirely from the command line, but Android Studio is the lowest-friction way to install and update the SDK on Windows.

> **IDE** = Integrated Development Environment. For Android, IntelliJ IDEA (the JetBrains base) with Android-specific plugins.

### Step 3 — JDK / `JAVA_HOME`

**What:** Pointed `JAVA_HOME` at Android Studio's bundled JDK (`...\Android Studio\jbr`, version 21.0.10).

**Why:** Android's build system (Gradle) runs on the JVM regardless of whether your app's source language is Java, Kotlin, or JavaScript. Gradle needs to know where Java lives via `JAVA_HOME`. The IDE has its own knowledge of the JDK, but command-line `gradle` / `./gradlew` invocations read the env var.

> **JDK** = Java Development Kit. Includes the Java compiler (`javac`), the JVM (`java`), and the core class libraries. A JRE (Runtime Environment) is just the JVM + libraries, no compiler — Gradle needs the full JDK.
> **JVM** = Java Virtual Machine. Bytecode interpreter / JIT compiler. Kotlin and Java both compile to JVM bytecode and run on it.
> **JBR** = JetBrains Runtime. A JDK distribution patched by JetBrains for use with their IDEs. Functionally interchangeable with OpenJDK for our purposes.

### Step 4 — `ANDROID_HOME` + `PATH`

**What:** Set `ANDROID_HOME` (and the legacy alias `ANDROID_SDK_ROOT`) to the SDK path, and prepended three SDK tool directories to `PATH`.

**Why:** Many CLI tools (`adb`, `emulator`, `sdkmanager`, build scripts, IDE plugins) look up the SDK via `ANDROID_HOME`. Putting `platform-tools`, `emulator`, and `cmdline-tools\latest\bin` on `PATH` lets you invoke `adb`, `emulator`, `sdkmanager`, etc. directly from any shell without typing absolute paths.

### Step 5 — Android Virtual Device (AVD)

**What:** Created a Pixel 7 AVD running an x86_64 Android system image (API 34 / Android 14), and booted it under the emulator.

**Why:** We need *something* to run the app on while developing. Options are a physical Android phone (USB-debugging) or an emulator. The emulator is faster to iterate on and doesn't require a phone — but only if hardware virtualization is enabled (see Step 1) and the system image is x86_64 (matches the host CPU). An arm64 image would run via slow software translation.

> **AVD** = Android Virtual Device. A configured virtual phone: hardware profile (screen size, RAM, sensors) + a chosen system image. Multiple AVDs can coexist; each one is a folder under `~/.android/avd/`.
> **System image** = the Android OS binary that the emulator boots. Comes in (API level × variant × ABI) combinations: e.g. *API 34 / Google Play / x86_64*.
> **ABI** = Application Binary Interface. The CPU-architecture target the binary is compiled for. Common Android ABIs: `x86_64` (PCs and many emulators), `arm64-v8a` (modern phones), `armeabi-v7a` (older phones).

### Step 6 — PATH cleanup

**What:** Removed `C:\Android` (a stray, half-installed `adb.exe` + `fastboot.exe` from some prior download) from both Machine and User `PATH`.

**Why:** On Windows the effective `PATH` for a new process is Machine `PATH` first, then User `PATH` appended. The stray `adb.exe` was on Machine `PATH` and was an old version (1.0.32). When `adb` connects to a device, the host-side `adb` and the device-side `adbd` daemon must report the same protocol version — mismatch causes silent failures or "adb server is out of date" errors. Removing the stale folder from `PATH` ensures the SDK's modern `adb` (1.0.41) wins.

---

## SDK component reference

The Android SDK isn't one binary — it's a tree of components, each independently versioned and downloaded. What we have:

| Component | What it contains | Why we need it |
|---|---|---|
| `platform-tools/` | `adb.exe`, `fastboot.exe`, `AdbWinApi.dll` | Talking to Android devices and emulators |
| `emulator/` | `emulator.exe` and the QEMU-based virtualization engine | Running AVDs |
| `platforms/` | `android.jar` for each API level (32, 33, 34, ...) | Compile-time SDK — the class definitions your app links against |
| `build-tools/` | `aapt2`, `d8`, `zipalign`, `apksigner` | Packaging/processing tools: resource compiler, dex compiler, APK signer |
| `system-images/` | Bootable Android OS images per (API × variant × ABI) | What the emulator actually runs **(MISSING — we'll add API 34 x86_64 when creating the AVD in M0/M5)** |
| `cmdline-tools/latest/bin/` | `sdkmanager`, `avdmanager` | Headless SDK management — useful for CI but not strictly needed locally **(MISSING — Android Studio's SDK Manager UI is fine for now)** |

---

## Key tools

### `adb` — Android Debug Bridge
A client/server protocol for talking to Android devices. Three pieces:
1. **Client** — the `adb` binary you run on your dev machine.
2. **Server** — a background process the client auto-starts on your dev machine (TCP port 5037). Multiplexes commands from multiple clients to multiple devices.
3. **Daemon (`adbd`)** — runs on each Android device/emulator, exposes a shell and file/install operations.

Common commands you'll use:
```text
adb devices              # list attached devices/emulators
adb install app.apk      # sideload an APK
adb shell                # interactive shell on the device
adb logcat               # stream the device log
adb push local remote    # copy file to device
adb pull remote local    # copy file from device
```

The server is the part that has to be version-matched to `adbd` — which is why the stray old `adb.exe` was a problem.

### `emulator` — AVD runner
Launches an AVD: `emulator -avd Pixel_7_API_34`. Backed by a QEMU fork tuned for Android. With hardware virtualization, it runs at near-native speed.

### Gradle (will appear in M0)
Android's build orchestrator. Conceptually like `cmake`+`make`+`cargo`+`npm` rolled into one for JVM projects. Reads `build.gradle.kts` files, downloads dependencies (from Maven Central / Google's Maven repo), compiles Kotlin/Java to JVM bytecode, packages bytecode + resources + native libs into an `.apk` or `.aab`, signs it, and can push it to a device via `adb`.

> **Maven Central** = the default public repo for JVM library artifacts. Like npm registry for JS or crates.io for Rust.

---

## Acronym glossary

| Acronym | Expansion | Notes |
|---|---|---|
| **ABI** | Application Binary Interface | CPU architecture target (`x86_64`, `arm64-v8a`, ...) |
| **AAB** | Android App Bundle | New Google Play upload format; Play generates per-device `.apk`s from one `.aab` |
| **adb** | Android Debug Bridge | Host↔device talk protocol; CLI tool of the same name |
| **API level** | — | Integer (24, 26, 34, ...) identifying a specific Android version's SDK contract |
| **APK** | Android Package | Direct-install Android package format (still used; `.aab` is preferred for Play) |
| **AVD** | Android Virtual Device | A configured virtual phone for the emulator |
| **DEX** | Dalvik Executable | Android's bytecode format; `d8` converts JVM bytecode → DEX |
| **Gradle** | — | The build tool we'll use; runs on JVM; configured in `.gradle` / `.gradle.kts` files |
| **IDE** | Integrated Development Environment | Android Studio in our case |
| **JBR** | JetBrains Runtime | JetBrains' JDK distribution, bundled with Android Studio |
| **JDK** | Java Development Kit | Java compiler + JVM + core libs |
| **JIT** | Just-In-Time (compiler) | JVM compiles hot bytecode paths to native at runtime |
| **JVM** | Java Virtual Machine | Runs Kotlin & Java bytecode |
| **KMP** | Kotlin Multiplatform | Lets you share Kotlin code across JVM, native, JS; planned for iOS-theory reuse later |
| **NDK** | Native Development Kit | The C/C++ toolchain for Android (Oboe lives here); we'll touch it for low-latency audio |
| **Oboe** | — (not an acronym, just a name) | Google's C++ wrapper over AAudio + OpenSL ES for low-latency audio |
| **AAudio** | Android Audio (API) | Modern low-latency audio API in Android (API 26+) |
| **SDK** | Software Development Kit | Headers, libraries, tools you compile/link against |
| **VT-x / AMD-V** | — | Intel / AMD hardware virtualization extensions |

---

## Verification commands (one-shot health check)

Run any time you suspect the environment drifted:

```powershell
"git:      $((git --version) 2>&1)"
"java:     $((& "$env:JAVA_HOME\bin\java.exe" -version) 2>&1)"
"adb:      $((adb --version) 2>&1 | Select-Object -First 1)"
"emulator: $((emulator -version 2>&1 | Select-Object -First 1))"
"ANDROID_HOME=$env:ANDROID_HOME"
"JAVA_HOME=$env:JAVA_HOME"
"adb resolves to: $((Get-Command adb).Source)"
```

Expected (as of 2026-05-28):
- `git ≥ 2.54`
- `openjdk 21.x`
- `adb 1.0.41+`, resolving to `...\Sdk\platform-tools\adb.exe`
- `Android emulator version 36.x` or later
- `ANDROID_HOME = C:\Users\Nadav\AppData\Local\Android\Sdk`
- `JAVA_HOME  = C:\Program Files\Android\Android Studio\jbr`

If `adb resolves to` ever shows `C:\Android\adb.exe` again, the stray folder snuck back onto `PATH` — re-run the Step-6 cleanup.

---

## What's still missing (will be added during M0)

- **System image** for the AVD (~1.5 GB, API 34 x86_64) — required to actually boot the emulator. Downloaded from Android Studio's Virtual Device Manager.
- **`cmdline-tools`** — optional, only matters if we set up CI later or want to script SDK updates without the IDE.
- **Android NDK** — needed for the audio module (M7) so we can use Oboe. Will install via SDK Manager when we get there.
- **Gradle wrapper** (`gradlew` / `gradlew.bat`) — comes with the project skeleton when M0 scaffolds.
