---
name: create-activity
description: Use this skill when the user asks to create a new Android Activity or screen. It ensures consistency across Java code, XML layouts, and Manifest registration.
---

# Create Android Activity

## When to use this skill
- When the user asks to create a new screen, page, or activity.
- When creating a new `Activity` class.

## Instructions
Follow these steps to ensure a complete and compiling implementation.

### 1. Plan the Activity
- Determine the Activity name (e.g., `SettingsActivity`).
- Determine the layout name (e.g., `activity_settings.xml`).
- Identify the package path (usually `top.yztz.msggo.activities` or similar).

### 2. Create the Layout XML
- Create the file in `app/src/main/res/layout/`.
- Use a root layout (e.g., `LinearLayout`, `ConstraintLayout`) appropriate for the content.
- Add necessary UI elements with unique IDs.

### 3. Create the Java Class
- Create the file in the appropriate package directory.
- Extend `AppCompatActivity` or `Activity`.
- Override `onCreate`.
- Call `setContentView(R.layout.your_layout_name)`.
- Initialize views using `findViewById`.

### 4. Register in AndroidManifest.xml
- Open `app/src/main/AndroidManifest.xml`.
- Add an `<activity>` tag inside the `<application>` tag.
- Specify the `android:name` (using relative path like `.activities.NewActivity`).
- Add any necessary intent filters or attributes (e.g., `android:label`).

### 5. Verification
- Ensure the project builds.
- Check that the new Activity can be launched via Intent from elsewhere in the app (if applicable).
