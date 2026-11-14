# Dependencies and Toolchains

This project uses both Gradle-managed dependencies and native platform toolchains.

## Gradle-managed versions

Core versions are defined in `gradle/libs.versions.toml`.

```toml
[versions]
agp = "9.2.1"
kotlin = "2.2.10"
composeBom = "2026.02.01"
```

Important modules:

- `app/` uses `com.android.application`
- `shared/` uses Kotlin Multiplatform plus `com.android.kotlin.multiplatform.library`

## Local toolchain requirements

| Tool | Why it is needed |
| --- | --- |
| JDK 21 | Gradle daemon toolchain selected by `gradle/gradle-daemon-jvm.properties` |
| Android Studio + Android SDK | Android app development and emulator/device testing |
| Xcode | iOS build, simulator/device launch, signing |
| XcodeGen | Generates `ios/SoundRunner.xcodeproj` from `ios/project.yml` |

## Platform build targets

### Android

- Compile SDK: Android 36, minor API level 1
- Target SDK: Android 36
- `minSdk = 24`
- Java target = 11

The Android app also uses CMake and the Android NDK to build its native FFT library. With Android Studio, install the SDK platform, build tools, NDK (Side by side), and CMake through SDK Manager. Android Gradle Plugin 9.2.1 selects NDK `28.2.13676358` by default unless the project configures another version. The SDK directory can be supplied with `ANDROID_HOME` or a local `sdk.dir` in `local.properties` (keep this machine-specific file out of version control). Gradle or SDK Manager may need to download missing components and ask you to accept Android SDK licenses.

### Shared module

- Android target
- JVM target
- `iosX64`
- `iosArm64`
- `iosSimulatorArm64`

The shared iOS binary is produced as `Shared.xcframework`.

### iOS shell

- deployment target = iOS 16.0
- Xcode scheme = `SoundRunner`
- default bundle identifier in the project spec = `com.example.audioanalyser.ios`

## Helpful commands

### Android + shared validation

```bash
bash ./gradlew :shared:jvmTest :app:testDebugUnitTest :app:assembleDebug
```

### iOS framework preparation

```bash
./scripts/prepare-ios-framework.sh
```

### iOS shell build

```bash
./scripts/build-ios-shell.sh
```

## Notes

- `./scripts/prepare-ios-framework.sh` builds `Shared.xcframework` and copies it into `ios/Frameworks/`.
- `./scripts/build-ios-shell.sh` also regenerates the Xcode project before running `xcodebuild`.
- On macOS, the iOS prep script will try to use Android Studio's bundled JDK automatically if `JAVA_HOME` is unset.
