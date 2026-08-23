# Agent build contract

This repository must be built with JDK 17. This is the Gradle runtime requirement; do not change the existing Android bytecode targets just to match it.

## Required toolchain

- JDK: 17.x (`java -version` must report major version 17)
- Android SDK: `C:\Users\vince\AppData\Local\Android\Sdk` unless `ANDROID_SDK_ROOT` points to another verified SDK
- Gradle: use `gradlew.bat` only; never use a globally installed Gradle
- MiPushFramework wrapper: Gradle 8.5, AGP 8.2.2, Kotlin 2.0.21
- Java/Kotlin compile target: 11, as defined by `push/build.gradle`; this is intentionally different from the JDK runtime

The currently verified local JDK 17 is:

```powershell
C:\Users\vince\MiPushFramework\.tmp-temurin17\jdk-17.0.20+8
```

Prefer a stable installed JDK 17 path when available. Set `JAVA_HOME` explicitly for every build and verify it before invoking Gradle:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:ANDROID_SDK_ROOT = 'C:\Users\vince\AppData\Local\Android\Sdk'
& "$env:JAVA_HOME\bin\java.exe" -version
```

If the reported Java major version is not 17, stop; do not attempt a build with JDK 21 or another version.

## Stable build procedure

Run builds serially. Do not run Android Studio/Gradle builds concurrently with these commands, and do not switch repositories while a Gradle process is running.

```powershell
.\gradlew.bat --stop
.\gradlew.bat :push:testNormalDebugUnitTest :push:assembleNormalDebug `
  --no-daemon --max-workers=1 --console=plain `
  "-Dkotlin.compiler.execution.strategy=in-process"
```

Do not run `clean` routinely; it removes useful incremental outputs and makes the next build a full rebuild. If a Windows `AccessDeniedException` names a Gradle/Kotlin temporary file or build JAR, stop daemons first and retry the same command once. Only investigate/remove the specific locked build artifact after a repeated failure; never delete source, `.git`, or the whole Gradle cache.

## Dirty and reproducibility rules

- `dirty` in an APK name comes from `git describe --dirty` and means tracked source changes are not committed. It is a version label, not a build mode or performance setting.
- Before handing off an APK, run `git status --short --untracked-files=no`; commit tracked source changes so the artifact has a reproducible commit-based name.
- Do not stage `.tmp-*`, screenshots, device dumps, APKs, or other sampling artifacts.

## Verification

At minimum, report the exact JDK version, Gradle task, result, and APK path. A failed build caused by toolchain/ACL setup must be reported separately from a source compilation failure.
