# Android Jarvis — Echo Handoff

## Scope completed

- Repaired the model list by decoupling its filename version from app version `1.0.17` and using the confirmed upstream `1_0_15` catalog.
- Renamed all user-visible app branding found in Android resources, Compose UI, notifications, Terms UI, and a skill-generated image footer to Android Jarvis.
- Replaced the visible Gallery mark in the existing `icon.xml`, `logo.xml`, and skill SVG filenames with an Android Jarvis “J” mark; internal resource names were retained.
- Added independent, locally persisted System Instructions and Personality Prompt settings to the home Settings dialog.
- Wired both saved prompts into AI Chat, Agent Skills, Ask Image, and Audio Scribe model initialization after the existing effective task instructions, without altering specialized task protocols.
- Added unit coverage for prompt ordering, empty-section behavior, and Agent Chat default prompt selection with saved sections.

## Exact files changed

- `settings.gradle.kts` — developer-visible Gradle project name changed to Android Jarvis.
- `app/src/main/AndroidManifest.xml` — launcher/activity label now uses the Android Jarvis app-name resource.
- `app/src/main/proto/settings.proto` — added separate `system_instructions` and `personality_prompt` fields.
- `app/src/main/java/com/google/ai/edge/gallery/data/SystemPromptRepository.kt` — reads/writes the two saved fields atomically while retaining per-task prompt storage.
- `app/src/main/java/com/google/ai/edge/gallery/common/SystemPromptHelper.kt` — separates task-prompt retrieval from compiled runtime prompt creation, scopes global prompts to conversational tasks, and defines the composition order.
- `app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatViewModel.kt` — keeps the task editor task-specific and recompiles global sections when applying a task prompt change.
- `app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/AgentChatTaskModule.kt` — preserves Agent Chat's MCP/skills default selection when saved sections are appended.
- `app/src/main/java/com/google/ai/edge/gallery/ui/modelmanager/ModelManagerViewModel.kt` — exposes saved-prompt persistence to Settings and uses allowlist version `1_0_15`.
- `app/src/main/java/com/google/ai/edge/gallery/ui/home/SettingsDialog.kt` — adds two explained editors and an explicit Save prompts action independent of model state.
- `app/src/main/res/values/strings.xml` — Android Jarvis branding and saved-prompt labels/descriptions.
- `app/src/main/java/com/google/ai/edge/gallery/ui/home/HomeScreen.kt` — Android Jarvis animated home title.
- `app/src/main/java/com/google/ai/edge/gallery/ui/common/tos/GemmaTermsOfUseDialog.kt` — Android Jarvis name in visible Gemma terms copy.
- `app/src/main/java/com/google/ai/edge/gallery/data/DownloadRepository.kt` — Android Jarvis download notification channel name.
- `app/src/main/java/com/google/ai/edge/gallery/notifications/NotificationReceiver.kt` — Android Jarvis default notification channel name.
- `app/src/main/res/drawable/icon.xml` — Android Jarvis launcher mark, retaining the internal filename.
- `app/src/main/res/drawable/logo.xml` — Android Jarvis top-bar mark, retaining the internal filename.
- `app/src/main/res/values/themes.xml` — light splash screen uses the Android Jarvis icon.
- `app/src/main/res/values-night/themes.xml` — dark splash screen uses the Android Jarvis icon.
- `app/src/main/assets/skills/learn-something-new/assets/galleryLogo.svg` — Android Jarvis mark for skill-generated images, retaining the upstream filename.
- `app/src/main/assets/skills/learn-something-new/scripts/index.html` — Android Jarvis name in generated image footer.
- `app/src/test/java/com/google/ai/edge/gallery/common/SystemPromptHelperTest.kt` — prompt composition tests.
- `app/src/test/java/com/google/ai/edge/gallery/customtasks/agentchat/AgentChatTaskModuleTest.kt` — Agent Chat prompt-selection regression tests.
- `ECHO_BRIEF.md` — durable architecture and milestone context.
- `ECHO_HANDOFF.md` — this implementation record.

## Commands and evidence

- Git inspection used `git -c safe.directory=C:/Users/Billy/StudioProjects/Echo-cortex ...` because repository ownership differs from the automation account; global Git configuration was not changed.
- Untouched baseline: `gradlew.bat assembleDebug` initially stopped because `JAVA_HOME` was unset. Re-running with Android Studio's bundled JBR produced `BUILD SUCCESSFUL`.
- Baseline device install: `adb -s R5CX31PBW7V install -r app/build/outputs/apk/debug/app-debug.apk` returned `Success`.
- Baseline launch returned `Status: ok`, `LaunchState: COLD`, and exposed the failed version-derived allowlist fetch in `AGModelManagerViewModel` logs.
- Official GitHub API inspection confirmed `1_0_15.json` is the latest Android allowlist file and `1_0_17.json` is absent.
- Changed build/tests: `gradlew.bat testDebugUnitTest assembleDebug` returned `BUILD SUCCESSFUL`; both new unit-test classes passed.
- Changed device install returned `Success`; launch returned `Status: ok`, `LaunchState: COLD`.
- Device UI hierarchy showed `Android Jarvis`, `AI Chat task with 7 models`, and `Agent Skills task with 2 models`.
- App logs showed `Done: loading model allowlist from internet`, parsed 9 models from `1_0_15.json`, completed task processing, and contained no fatal startup exception.
- Device Settings UI showed both prompt editors without a downloaded model. Distinct temporary values were saved, found separately in private settings data, restored after force-stop/relaunch, then cleared and confirmed absent from private settings data.

## Unresolved problems

- No feature-blocking problem is known.
- No local model is currently downloaded on the connected phone, so live inference with the composed prompt was not exercised. The shared initialization path and Agent Chat behavior are covered by code inspection and focused unit tests.
- Pre-existing build warnings remain: Android Gradle Plugin 8.13.0 versus compile SDK 37.0, the legacy manifest `package` attribute, deprecated Gradle features, and Kotlin context-receiver migration notices. They were not changed because they do not block this milestone.

## Questions for Echo

- Should future builds ship non-empty default System Instructions or Personality Prompt, or should both remain opt-in and empty by default?
- Should a later milestone expose the existing per-task system prompt editor as an advanced setting distinct from these global prompts?

## Recommended next step

Download one small compatible local model and run one AI Chat and one Agent Skills session with unmistakable saved instructions/personality text. Confirm the observed responses, then add an instrumentation-level assertion or runtime debug trace for the final compiled system instruction if stronger end-to-end evidence is needed.
