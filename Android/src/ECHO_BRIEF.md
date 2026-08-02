# Android Jarvis — Echo Brief

## Current milestone

Android Jarvis now has a working upstream model catalog, complete user-visible Android Jarvis branding, independent global prompt settings, and a direct OpenAI cloud provider in AI Chat. OpenAI models can be selected and used without downloading or loading a local model; a valid API key is required.

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
- Keep agent/chat execution provider-neutral through `AgentRuntimeExecutor`. Route `RuntimeType.OPENAI` to the OpenAI executor and retain the existing local executor for LiteRT models and Agent Skills.
- Use OpenAI's Responses API for new cloud chat sessions. Pass the already-compiled task, System Instructions, and Personality Prompt through the API `instructions` field in the same established order.
- Keep OpenAI conversation history in Android Jarvis and send it explicitly on each request with `store: false`. This preserves current local chat-history ownership and avoids relying on provider-side response storage.
- Store the OpenAI API key separately from protobuf settings. Encrypt it with an Android Keystore AES/GCM key, keep only ciphertext and IV in private preferences, and exclude those preferences from both cloud backup and device transfer.
- Never print, log, commit, or place an OpenAI API key in a Gradle property, resource, manifest, or source file.
- Treat direct client-side OpenAI credentials as a personal-device implementation. A future multi-user or distributed release should move provider credentials behind a trusted backend.
- Present cloud models alongside local AI Chat models while preserving local-model functionality. Cloud model cards are ready immediately and must not expose download controls or a user-facing context-size control.
- The initial curated OpenAI choices are `chat-latest` (ChatGPT Instant), `gpt-5.6-sol`, `gpt-5.6-terra`, and `gpt-5.6-luna`. Revalidate this list against current official OpenAI documentation before future releases.

## Non-negotiable requirements

- System Instructions and Personality Prompt must remain independently editable and independently stored.
- Both settings must persist across app process death and reopening.
- Prompt editing must not require a downloaded or initialized model.
- Both settings must feed new chat and agent model initialization through the existing system-instruction path.
- Do not add a user-facing context-size setting for cloud models.
- The OpenAI key must be editable without a model download, masked in the UI, encrypted at rest, excluded from backups, and removable by the user.
- OpenAI API usage and ChatGPT subscriptions must be described as separate billing products in the UI.
- Do not claim successful cloud inference unless a real API response confirms it. Build, install, model-selection, persistence, and error-path evidence are not substitutes for a successful response.
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
- AI Chat's model list is assembled from curated OpenAI cloud entries followed by the upstream allowlist entries. The installed build exposed 11 total AI Chat models: 4 OpenAI cloud choices and 7 locally compatible choices on the connected phone.
- `AgentExecutorModule` now injects a routing executor for AI Chat. Agent Skills deliberately retains the local executor and is not silently moved to the cloud.
- `OpenAiCredentialsRepository` owns encrypted key storage and a random per-install `safety_identifier` that contains no account or device identity.
- `OpenAiApiClient` streams Responses API server-sent events into the existing `AgentEvent` contract. It sends text and image inputs, maps authentication/quota/model errors to user-readable messages, and disconnects on cancellation.
- The Desktop key source selected on 2026-08-02 was structurally recognized but OpenAI returned HTTP 401 with `invalid_api_key`. The app's request path and error UI were verified, but successful OpenAI inference remains blocked until the key is replaced with a valid API-platform key.
