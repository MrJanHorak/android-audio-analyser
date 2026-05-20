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
| JDK 17+ | Gradle, Android builds, and XCFramework preparation |
| Android Studio + Android SDK | Android app development and emulator/device testing |
| Xcode | iOS build, simulator/device launch, signing |
| XcodeGen | Generates `ios/SoundRunner.xcodeproj` from `ios/project.yml` |

## Platform build targets

### Android

- `compileSdk = 36`
- `targetSdk = 36`
- `minSdk = 24`
- Java target = 11

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
