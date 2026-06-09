<p align="center">
  <img src="graphics/app_icon.svg" width="128" height="128" alt="SaveTo App Icon" />
</p>

<h1 align="center">SaveTo</h1>

<p align="center">
  <a href="https://github.com/sudo-py-dev/SaveTo/actions/workflows/ci.yml"><img src="https://github.com/sudo-py-dev/SaveTo/actions/workflows/ci.yml/badge.svg" alt="Android CI" /></a>
  <a href="https://github.com/sudo-py-dev/SaveTo/releases"><img src="https://img.shields.io/badge/release-v1.1-blue.svg?logo=github" alt="GitHub Release" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache--2.0-orange.svg" alt="License" /></a>
  <a href="https://android-arsenal.com/api?level=23"><img src="https://img.shields.io/badge/API-23%2B-brightgreen.svg?style=flat&logo=android" alt="API Level" /></a>
</p>

<p align="center">
  SaveTo is a lightweight, single-purpose Android utility that integrates into the system-wide sharing sheet to save shared files, multiple documents, or text directly to any destination chosen via the Storage Access Framework (SAF).
</p>

---

## Visual Preview

<p align="center">
  <img src="graphics/feature_graphic.png" width="800" alt="SaveTo Feature Graphic" />
</p>

| 1. Share Sheet Integration | 2. Save Location Selection |
| :-: | :-: |
| <img src="graphics/phone_screenshot_1_share_sheet.png" width="350" alt="Share Sheet" /> | <img src="graphics/phone_screenshot_2_save_picker.png" width="350" alt="Save Picker" /> |

---

---

## Key Features

- **Seamless Share Target**: Integrates directly with Android's system share sheet, registering for `android.intent.action.SEND` and `android.intent.action.SEND_MULTIPLE` across all MIME types (`*/*`).
- **No Launcher Clutter**: The application does not declare a launcher activity, keeping the launcher interface clean. It is active only when files are shared.
- **Privacy & Security First**: Zero dangerous storage permissions (such as `READ_EXTERNAL_STORAGE` or `WRITE_EXTERNAL_STORAGE`) are required. It relies entirely on temporary URI permissions granted dynamically via incoming Intents.
- **Bulk Save support**: When multiple files are shared, it lets you select a destination directory and saves all files in one operation with a progress tracking indicator.
- **Text-to-File Conversion**: Automatically detects shared text content, converts it into a `.txt` file, and prompts to save it.
- **Robust Error Handling**: Safely reports partial failures when bulk-saving multiple files, handles I/O exceptions, and cleans up temporary cache files.

---

## Technical Architecture

The codebase is structured following clean Android architecture principles, utilizing Kotlin, Coroutines, and Android Jetpack Architecture Components.

```mermaid
graph TD
    Intent[Incoming Share Intent] --> |SEND / SEND_MULTIPLE| Activity[ShareActivity]
    Activity --> |Resolve Metadata| VM[SaveViewModel]
    Activity --> |Launch Picker| SAF[Storage Access Framework]
    SAF --> |Dest URI| VM
    VM --> |Coroutines IO Dispatcher| Save[Buffered Stream Write]
```

### Components

- **[AndroidManifest.xml](app/src/main/AndroidManifest.xml)**: Configures the intent filters, theme settings, and application metadata. `ShareActivity` is configured with a translucent theme (`Theme.Translucent.NoTitleBar`) so that it overlays seamlessly onto the system share dialog.
- **[ShareActivity.kt](app/src/main/kotlin/com/save/to/ShareActivity.kt)**: Manages UI, checks URI read access, interacts with the Android Storage Access Framework (using `ActivityResultContracts.CreateDocument` and `OpenDocumentTree`), and shows standard system alerts and progress dialogs.
- **[SaveViewModel.kt](app/src/main/kotlin/com/save/to/SaveViewModel.kt)**: Houses the business logic for resolving source file details, performing high-performance I/O operations using a buffered stream (`8KB` buffer size), managing state, and cleaning up cache files.
- **[strings.xml](app/src/main/res/values/strings.xml)**: Declares all UI string resources and error messages for user-facing localization.

---

## Development & Build Requirements

- **SDK Targets**: Min SDK `23` (Android 6.0), Target SDK `35` (Android 15)
- **Java Compatibility**: JDK `17` toolchain
- **Build Tool**: Gradle Kotlin DSL

### Code Styling and Design

This application strictly follows modern development standards:
- **Coroutines for Asynchronous I/O**: Offloads stream writing tasks to `Dispatchers.IO` and uses `ensureActive()` inside loops to respect lifecycle cancellation.
- **No Force Unwraps**: Handles null values gracefully and relies on defensive typing contracts to prevent runtime crashes.
- **Resource Cleanup**: Employs `use { ... }` blocks for Kotlin `Closeable` objects, ensuring input and output streams are safely closed under all circumstances.

### Building the Project

Ensure you have a valid `keystore.properties` file configured in the root directory prior to building. Run the following command from the project root to compile a release build:

```bash
./gradlew assembleRelease
```

