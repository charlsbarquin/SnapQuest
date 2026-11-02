# SnapQuest

SnapQuest is an offline trivia and camera-challenge game for Android. Play a full game on your own or pass the device between friends, answer multiple-choice questions, and take turns completing **Find & Frame** prompts. It is designed for curious players and for students and developers who want to explore a Kotlin and Jetpack Compose application.

## Download SnapQuest

> **Release pending — no public APK is available yet.** This project checkout has no configured Git remote, verified GitHub repository URL, published GitHub Release, or signed release APK. Do not install the local debug APK as a production release.

Once the project owner publishes a signed APK, visitors can download it from the official repository's **Releases** page. The repository owner must provide or configure the GitHub `owner/repository` URL before this page can link to the repository, release, APK, or issue tracker. See [Publishing a release](#publishing-a-release) for the remaining steps.

The current app configuration supports devices running **Android 8.0 (API 26) or later**. A camera is needed to capture a Find & Frame submission; the app does not require a camera to be present just to install.

### Install a published APK

When an official release is available:

1. Open the project's verified GitHub repository and select **Releases**.
2. Open the latest release and download its `.apk` asset. Download only from the official repository or another source you trust.
3. Open the downloaded APK on the Android device and review the system installation prompt. Android may ask you to allow app installation from the browser or file manager used to open it.
4. After installation, open **SnapQuest** from the app launcher. Grant camera access only when you choose to start a camera round.

If Android says the package is incompatible, check that the device runs Android 8.0 or later and that the APK is a supported release. To update an existing installation, install a release signed with the same signing key and with a higher version code. Do not uninstall first unless you are prepared to lose the app's locally stored history and settings.

## Screenshots

These screenshots are existing project captures. The Home capture is omitted because it predates the current card-icon correction.

| Game setup | Trivia round |
| --- | --- |
| ![SnapQuest solo game setup screen](design-review/system/game-setup.png) | ![SnapQuest trivia question screen](design-review/system/trivia-round.png) |

| Find & Frame prompt | Scores and profiles |
| --- | --- |
| ![SnapQuest Find and Frame prompt screen](design-review/system/camera-prompt.png) | ![SnapQuest scores screen](design-review/system/scores.png) |

## Features

- Full eight-round game in Solo or pass-and-play Multiplayer mode.
- A bundled bank of 144 multiple-choice questions across eight categories, with 18 questions per category and six at each internal difficulty level. Difficulty is handled internally; there is no difficulty selector.
- Twenty-four bundled Find & Frame prompts, with two camera rounds per Full Game. The camera flow includes on-device photo preview, retake, accept, or reject. Players judge whether a photo matches the prompt; the app does not automatically analyze images.
- Unscored Practice, plus short Quick Trivia and Category Practice sessions. Short-session results are kept separate from Full Game history and records.
- Completed-game history, incorrect-answer review, local statistics, and optional player profiles.
- Local question reports, rules and help, and an interactive tutorial.
- System, Light, and Dark appearance settings.
- Bundled trivia and camera prompts; no account or network connection is needed for gameplay.

## How to play

A Full Game has **eight rounds**:

| Round | Type |
| --- | --- |
| 1–3 | Trivia |
| 4 | Find & Frame |
| 5–7 | Trivia |
| 8 | Find & Frame |

Choose Solo or Multiplayer and configure the participants and trivia categories. In a trivia round, the assigned participant chooses one of four answers. In a camera round, the assigned player follows a safety prompt, captures and reviews a photo, and the judge (another participant in Multiplayer) accepts or rejects it. Camera prompts are selected independently of the chosen trivia category set. Camera rounds can also be skipped.

- A correct trivia answer or accepted camera submission earns **10 points**.
- An incorrect answer, rejected submission, or skipped round earns **0 points**.
- The maximum Full Game score is **80 points**. There is no timer, and tied scores remain ties.
- Trivia and camera turns use separate rotations in Multiplayer.

Practice is unscored. Quick Trivia and Category Practice are short sessions and do not contribute to Full Game records or achievements.

## Requirements and privacy

- **Minimum Android:** API 26 (Android 8.0 Oreo), as configured by `minSdk`.
- **Camera:** The app declares camera permission and requests it for camera use. Camera hardware is optional for installation; players can skip camera rounds if they cannot or do not wish to capture a photo.
- **Internet:** The app manifest does not request `INTERNET` permission. Trivia and camera prompts are bundled in the app. Gameplay, history, preferences, and reports use local app storage; no account or backend is configured.
- **Storage:** Room stores game history and related local records; Preferences DataStore stores app preferences. Captured photos are held temporarily in app-private cache and are deleted as the camera round ends or is canceled. Photos are not saved in game history.
- **Backup:** The configured Android backup and device-transfer rules exclude the Room database and settings file. Uninstalling the app removes its locally stored app data.

No formal privacy policy file is present in this repository. If one is required for public distribution, it must be prepared and published separately based on the app's actual data practices.

## Technology

- **Kotlin** for application and game logic.
- **Jetpack Compose** and **Material 3** for the interface and theming.
- **CameraX** for camera preview and photo capture.
- **Room** for local game history and related records.
- **Preferences DataStore** for local settings.
- **Gson** for loading and validating bundled JSON content.
- **Gradle Kotlin DSL**, the Gradle wrapper, and **KSP** for build configuration and Room code generation.

The checked-in build configuration currently specifies compile SDK 37, target SDK 37, minimum SDK 26, Kotlin 2.2.10, Android Gradle Plugin 9.3.3, Gradle 9.5.0, and Java source/target compatibility 11. Java source/target compatibility is not the same setting as the JDK used to run Gradle. The project uses the Gradle wrapper and requires an Android Studio/Gradle JDK setup compatible with the checked-in Android Gradle Plugin; this README does not prescribe a separate JDK version.

## Build from source

Use Android Studio with Android SDK platform 37 installed. Open the project directory, allow Gradle sync to finish, and select a connected Android device or emulator running API 26 or later.

From PowerShell at the project root:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

To install and run the debug build on a connected device from Android Studio, select the `app` run configuration and press **Run**. The debug APK output path is:

```text
app/build/outputs/apk/debug/app-debug.apk
```

This is a development build. It is not the signed production APK promised by a public release. A connected instrumentation run also requires an online, authorized device or a configured emulator; for example:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## Project structure

```text
app/
  schemas/                         Room schema exports
  src/main/assets/content/         Bundled trivia and camera-prompt JSON
  src/main/java/.../domain/game/   Game models, rules, selection, and sessions
  src/main/java/.../data/content/  Content loading and validation
  src/main/java/.../data/history/  Local history, reports, profiles, and statistics
  src/main/java/.../data/local/    Room entities, DAO, database, and migrations
  src/main/java/.../data/settings/ Preferences DataStore settings
  src/main/java/.../feature/       Compose screens and feature-specific UI/state
  src/main/java/.../ui/            Shared components, navigation, and theme
  src/test/                        Local JVM unit tests
  src/androidTest/                 Android instrumentation tests
design-review/                     Existing UI and device screenshots
gradle/
  libs.versions.toml               Dependency and plugin version catalog
  wrapper/                          Gradle wrapper configuration
```

## Testing and verification

The project contains JVM unit tests and Android instrumentation tests for game rules, content, history and migrations, settings, Compose screens, navigation recreation, and camera-related behavior.

The latest recorded verification for the current project build (November 2, 2026) was:

- **116 unit tests passed**; no failures, errors, or skips.
- **43 connected instrumentation tests passed**; no failures or skips, on a Samsung SM-A536B running Android API 36.
- Debug APK assembly succeeded.

These are recorded results from that tested build, not a guarantee for every device or Android release. Run the commands above against the current checkout to verify it locally. A connected-device result depends on ADB authorization and device availability.

## Troubleshooting

- **APK will not install:** Confirm the device runs API 26 or later, re-download the APK from the verified official release, and check Android's install-from-this-source setting. If updating, use an APK signed with the same key as the installed app.
- **Camera does not open:** Grant camera permission in Android settings, then reopen the camera round. If the device has no camera or you prefer not to use it, skip the round.
- **History or settings are missing after reinstall:** Local app data is removed when the app is uninstalled, and this app excludes its database and settings from configured backup and device transfer.
- **Gradle sync/build fails:** Confirm Android SDK platform 37 is installed, Android Studio is using a Gradle JDK compatible with the project's Android Gradle Plugin, and Gradle can access its configured distribution and dependencies. Review the first error in the Build output.
- **Camera photo is not accepted:** Camera rounds are judged by players against the prompt; the app does not use automatic image recognition. Retake the photo if needed and avoid capturing people or private information.

## Feedback and bug reports

No verified GitHub repository URL or issue tracker is configured in this checkout, so an Issues link cannot be supplied yet. Once the owner publishes the repository and enables Issues, include the device model, Android version, steps to reproduce, and expected versus actual behavior in a report. Do not attach photos, private documents, credentials, addresses, or other sensitive information.

## License and credits

There is no license file in this repository. Until the owner selects and adds a license, do not assume that others have permission to reuse, modify, or redistribute this source code. The project uses the third-party libraries identified in its Gradle version catalog and dependency declarations; consult each library's own license before redistribution. No separate external-asset credits file was found during this documentation pass.

## Publishing a release

There is no verified GitHub remote, public repository URL, release page, or published APK available from this checkout. The release build has no signing configuration, and no signing credentials were found in the project; no release APK was generated. The local debug APK is not a substitute for a signed release.

To make SnapQuest downloadable:

1. Provide or configure the intended GitHub repository URL (`owner/repository`) and publish the source repository.
2. Choose a release-signing approach and configure it outside source control. Keep the keystore and passwords private; never commit them or put them in this README. Preserve the same signing key for future updates.
3. Generate and verify a signed release APK after signing is configured.
4. Create a GitHub Release in the verified repository and attach the signed APK. Then add verified repository, latest-release, APK, and Issues links near the top of this README.

Until those steps are complete, visitors can inspect and build the source but cannot download an official release APK from GitHub.
