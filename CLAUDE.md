# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

WeightWise is an Android weight-tracking app built with Jetpack Compose. All UI text and comments are in Chinese (Simplified). The app tracks weight records, calculates BMI, displays trend charts, and provides AI-generated daily exercise plans via the DeepSeek API.

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK (minification enabled)
./gradlew assembleRelease

# Run unit tests (currently placeholder only)
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Generate baseline profile
./gradlew :app:generateBaselineProfile
```

**Requirements:** JDK 21, Android SDK with compileSdk 36, NDK (arm64-v8a), Gradle 8.13.

## Architecture

MVVM without a separate domain layer. Business logic lives in ViewModels and data objects.

```
ui/{feature}/     → Screen composables + ViewModel (StateFlow)
data/              → Room entities/DAOs, network, repositories
util/              → TimeUtils (date formatting, Beijing timezone)
```

### Navigation

Uses **Navigation3** (`androidx.navigation3`), not traditional Navigation Compose. Four `@Serializable` destinations defined as objects in `MainActivity.kt`: `Main`, `Setting`, `Record`, `ExercisePlan`. Navigation is imperative via `backStack.add()` / `backStack.removeAt()`.

### Dependency Injection

**Koin 4.2 with Koin Annotations** (KSP code generation). `@Module @ComponentScan` on `KoinModule` auto-discovers `@Single` and `@KoinViewModel` annotated classes. ViewModels injected in composables via `koinViewModel()`.

### Data Layer

- **Room** (version 4 database): `Record`, `DailyPlan`, `ExerciseCompletion` entities. Auto-migrations enabled, destructive migration disabled.
- **MMKV**: User preferences (height, target weight, exercise preferences). All values exposed as `StateFlow` via MMKV-KTX.
- **Ktor + OkHttp**: Network calls to DeepSeek API. SSE streaming for AI responses. API key via `BuildConfig.DEEPSEEK_KEY` from `secrets.properties` (git-ignored).

### Global UI Communication

`MainActivity.kt` defines `CompositionLocal` providers for SnackBar, loading dialog, and message dialog. Access via `LocalSnackBarShow`, `LocalShowLoadingDialog`, `LocalHideLoadingDialog`, `LocalShowMessageDialog`.

## Key Conventions

- **Compose UI only** — no XML layouts, no View system.
- **Material3 with Material Expressive** theme. Custom color scheme in `ui/theme/Color.kt`.
- **Vico 3.x** for weight trend charts (CartesianChart with line + area fill).
- **Paging 3** for weight record list (page size 20).
- **kotlinx.serialization** for JSON — all serialized models use `@Serializable`.
- ViewModels use single `MutableStateFlow<State>` pattern with `data class` state.
- Time utilities use **Beijing timezone** (UTC+8) for daily weight aggregation SQL queries.

## Exercise Plan System

The exercise feature has a sophisticated fallback chain:
1. AI-generated plan via DeepSeek API (`ExercisePromptBuilder` builds prompts from user data)
2. Local fallback from `ExerciseCatalog` (23 exercises across 3 difficulty levels)
3. `ExercisePreferences` supports blacklist/whitelist tags and scene filtering (indoor/outdoor/office)
4. Difficulty adjustment based on user fitness level (stored in MMKV)

## Release Build Notes

- `isMinifyEnabled = true`, `isShrinkResources = true`
- ProGuard rules file is empty — if adding release build issues, check kotlinx.serialization and Koin keep rules first
- Debug build suffix: `.debug` with name "体重记录-Debug"
