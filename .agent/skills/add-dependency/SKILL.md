---
name: add-dependency
description: Use this skill when adding a new library or dependency to the project. It ensures the dependency is added to the correct build.gradle file and the project is synced.
---

# Add Gradle Dependency

## When to use this skill
- When the user asks to install a library (e.g., Gson, Retrofit).
- When you need to add a new `implementation` or `api` line to `build.gradle`.

## Instructions

### 1. Identify the correct build.gradle
- Usually `app/build.gradle` for app-level dependencies.
- `build.gradle` (root) for project-wide configurations (less common for dependencies).

### 2. Check for conflicts
- Search existing dependencies to avoid duplicates or version conflicts.
- Use `gradle dependencies` command if unsure about transitive dependencies (optional).

### 3. Add the dependency
- Add the line inside the `dependencies { ... }` block.
- Examples:
  - `implementation 'com.google.code.gson:gson:2.10.1'`
  - `testImplementation 'junit:junit:4.13.2'`

### 4. Sync/Build
- Run `./gradlew assembleDebug` (or `gradlew.bat` on Windows) to verify the dependency resolves and the app builds.
- **Critical**: If the build fails, revert the change and inform the user or try a different version.
