# Android Jarvis — Echo Brief

## Current milestone

Android Jarvis now has a working upstream model catalog, complete user-visible Android Jarvis branding, and two independent global prompt settings that are editable before any model is downloaded or loaded.

## Permanent architecture decisions

- Preserve the upstream package, namespace, Kotlin class names, resource filenames, task IDs, JavaScript bridge names, and source links unless a later migration explicitly requires changing them.
- Keep the existing task-specific system prompt feature. It remains stored per task by `SystemPromptRepository` in `UserData`.
- Store the two Android Jarvis global prompts as distinct protobuf fields in `Settings`:
  - field 12: `system_instructions`
  - field 13: `personality_prompt`
- For AI Chat, Agent Skills, Ask Image, and Audio Scribe, compile model initialization instructions in this exact order:
  1. the existing effective task prompt (built-in default or per-task override)
  2. saved System Instructions
  3. saved Personality Prompt
- Omit empty saved-prompt sections so an upgrade with no saved prompts preserves the prior effective task prompt.
- Do not append the global prompts to specialized task protocols such as Tiny Garden, Mobile Actions, Prompt Lab, or Scrapbook.
- Continue using the existing single `systemInstruction` model/runtime input. Agent Chat still performs its MCP-versus-skills default prompt selection before retaining the appended saved sections.
- Treat the model allowlist release as independent from `BuildConfig.VERSION_NAME`. The current compatible upstream catalog is `model_allowlists/1_0_15.json`.

## Non-negotiable requirements

- System Instructions and Personality Prompt must remain independently editable and independently stored.
- Both settings must persist across app process death and reopening.
- Prompt editing must not require a downloaded or initialized model.
- Both settings must feed new chat and agent model initialization through the existing system-instruction path.
- Do not add a user-facing context-size setting for cloud models.
- Preserve upstream Edge Gallery features and visual patterns; avoid unrelated refactors.
- Do not claim a build, install, launch, device behavior, or persistence result without command/device evidence.

## Relevant repository discoveries

- Gradle project root: `Android/src`; single Android application module: `:app`.
- Git repository root: `Echo-cortex`; branch: `main` tracking `origin/main`.
- UI: Jetpack Compose. Dependency injection: Hilt. Persistent settings: protobuf DataStore.
- `ModelManagerViewModel.initializeModel` is the shared prompt injection point for normal chat and custom tasks, including Agent Chat.
- The task-specific chat prompt editor is driven by `LlmChatViewModel`; it must display and save only the task prompt, not the compiled global prompt.
- App version `1.0.17` previously requested nonexistent `1_0_17.json`. Google's upstream allowlist directory currently ends at `1_0_15.json`, which contains 9 models.
- Local command-line builds need Android Studio's bundled Java at `C:\Program Files\Android\Android Studio\jbr` because `JAVA_HOME` is not globally configured.
- ADB exposes the same Samsung device over USB and wireless debugging. Use USB serial `R5CX31PBW7V` to avoid ambiguity.
- Pre-existing untracked paths `Echo-cortex/.idea/` and `Android/src/gradle/gradle-daemon-jvm.properties` belong to the user and must remain untouched/uncommitted.
