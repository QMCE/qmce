# Repository Guidelines

## Project Structure & Module Organization

QMCE is a Kotlin Android Wear OS application. The active Gradle module is
`app-new`; `settings.gradle.kts` includes it as `:app-new`. Application code is
under `app-new/src/main/java`, Compose UI is primarily in
`rj.qmce.lite.ui`, view models in `rj.qmce.lite.viewmodel`, data and kernel
bridges in `rj.qmce.lite.data` and `rj.qmce.lite.kernel`. Android resources are
in `app-new/src/main/res`, bundled assets in `app-new/src/main/assets`, and
native libraries in `app-new/src/main/jniLibs`. Binary compatibility artifacts
such as `qq-sdk.jar` are kept in `app-new/libs`. Research notes and analysis
belong in `work/`.

## Build, Test, and Development Commands

- `./gradlew :app-new:assembleDebug` builds a debuggable, signed APK.
- `./gradlew :app-new:assembleRelease` builds the minified release APK.
- `./gradlew :app-new:compileDebugKotlin --no-daemon -Pkotlin.incremental=false`
  performs a clean-style Kotlin compilation useful for diagnosing cache noise.
- `./gradlew assemble` builds the repository’s default artifacts.
- `./gradlew :app-new:testDebugUnitTest` runs unit tests when available.

Use Java/Kotlin 17 and the repository-provided `gradlew`. Do not commit
machine-specific `local.properties` changes or generated files.

## Coding Style & Naming Conventions

Use four-space indentation, Kotlin expression-oriented code, PascalCase for
classes/composables, camelCase for functions and properties, and descriptive
names for message/kernel state. Follow existing Wear Material 3 patterns:
prefer `ScreenScaffold`, `TransformingLazyColumn`, `EdgeButton`, and default
`MaterialTheme` colors over custom phone-style layouts or hard-coded colors.
Keep UI, state, and kernel/data responsibilities separated. Run
`git diff --check` before submitting; no repository-wide formatter is configured.

Do not casually modify `moye.*` stubs or `rj.qmce.lite.fix`; these are runtime
compatibility and anti-detection code. Preserve required ProGuard rules because
the bundled SDK jars reference QMCE classes.

## Testing Guidelines

There is no established instrumentation-test suite. Compile both Debug and
Release variants after changes, then manually verify affected flows on a Wear OS
device or emulator. For UI changes, check round-screen scrolling, back
navigation, loading/error states, and minified Release behavior.

## Commit & Pull Request Guidelines

History uses short imperative messages, sometimes with a scope (for example,
`fix: ...` or `表情 & 群管理`). Keep commits focused and explain root causes.
Pull requests should include a behavior summary, validation commands, affected
device/API levels, and screenshots or a short recording for visible Wear UI
changes. Call out new SDK, resource, native library, or ProGuard requirements.

## Security & Configuration

Never commit API keys, signing credentials, private dumps, or device paths.
Treat QQ/kernel interfaces and third-party requests as unstable: add graceful
fallbacks, diagnostic logging, and avoid exposing message or account data.
