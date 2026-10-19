# Ventricle Assessment

Android app scaffold: Kotlin, Jetpack Compose (Material 3), MVVM, Hilt DI, Room DB.

## Stack

- Kotlin 2.0.21, Gradle 8.9, AGP 8.7.3 (Java 17)
- Jetpack Compose (BOM 2024.12.01) + Material 3
- Hilt 2.56.2 for dependency injection
- Room 2.6.1 for local persistence
- Coroutines 1.9.0 + Flow

## Project layout (MVVM)

- `app/src/main/java/com/ventricle/assessment/ui/` — Compose screens (`screens/`), theme, `ViewModel`s
- `app/src/main/java/com/ventricle/assessment/data/` — Room entities / DAO / database (`local/`), repositories
- `app/src/main/java/com/ventricle/assessment/di/` — Hilt modules

## Build

1. Open the project in Android Studio (it will prompt a Gradle sync).
2. First time only: run `gradle wrapper` once in the project root to generate
   `gradle-wrapper.jar` (intentionally not committed to git).
3. Run the `app` configuration on a device or emulator (minSdk 26).

## Notes

- Package / applicationId: `com.ventricle.assessment`
- Assessments are stored locally in Room (`assessment.db`); no network calls.
- Launcher icon and theme resources are included under `app/src/main/res/`.
