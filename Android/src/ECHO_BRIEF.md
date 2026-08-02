# Android Jarvis — Echo Brief

## Current milestone

Android Jarvis now opens directly into one multimodal Jarvis agent chat instead of the Edge Gallery task grid. That surface can select local or OpenAI models, accepts supported media, exposes Skills and MCP controls, and answers normal questions without forcing tool use. Tiny Garden and the standalone Mobile Actions demo are no longer registered as user-facing tasks; their source remains temporarily available for safe capability extraction.

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
- Keep agent/chat execution provider-neutral through `AgentRuntimeExecutor`. Jarvis uses `RoutingAgentRuntimeExecutor` so `RuntimeType.OPENAI` selects the cloud executor while local models retain the LiteRT executor with Skills and MCP tools.
- Use OpenAI's Responses API for new cloud chat sessions. Pass the already-compiled task, System Instructions, and Personality Prompt through the API `instructions` field in the same established order.
- Keep OpenAI conversation history in Android Jarvis and send it explicitly on each request with `store: false`. This preserves current local chat-history ownership and avoids relying on provider-side response storage.
- Store the OpenAI API key separately from protobuf settings. Encrypt it with an Android Keystore AES/GCM key, keep only ciphertext and IV in private preferences, and exclude those preferences from both cloud backup and device transfer.
- Never print, log, commit, or place an OpenAI API key in a Gradle property, resource, manifest, or source file.
- Treat direct client-side OpenAI credentials as a personal-device implementation. A future multi-user or distributed release should move provider credentials behind a trusted backend.
- Present cloud models alongside local models in Jarvis while preserving local-model functionality. Cloud model cards are ready immediately and must not expose download controls or a user-facing context-size control.
- Keep OpenAI provider IDs internal. Present `gpt-5.6-sol` as `GPT-5.6 High`, `gpt-5.6-terra` as `GPT-5.6 Medium`, and `gpt-5.6-luna` as `GPT-5.6 Instant`. Do not expose Sol/Terra/Luna branding in the model picker.
- Do not show the separate `chat-latest` alias while GPT-5.6 Instant is present; two user-facing Instant choices would be ambiguous.
- Jarvis should answer normally when a capability is unnecessary. Skills and MCP tools are optional capabilities, and the absence of a matching tool must never reduce ordinary chat to a `No skills or tools found` response.
- Jarvis is the only registered general chat task. Keep the former AI Chat implementation available as dormant source while its shared components are reused, but do not register a competing task or duplicate its OpenAI models.
- Treat `Model.name` as the stable cross-task model identity. Global model-management aggregation must deduplicate by `name`, not by full mutable `Model` equality.
- Leaving the primary Jarvis chat must return to Home, where the navigation drawer and Settings remain reachable; it must not strand the user in the global model selector.
- Keep Tiny Garden unregistered. Keep its code dormant only until any reusable tool patterns have been extracted and the remaining special cases can be deleted safely.
- Keep Mobile Actions unregistered as a standalone mode. Later expose device actions as provider-neutral, permission-gated tools available to any compatible agent, with explicit confirmation for sensitive or destructive operations.
- The canonical visual source is `res/drawable-nodpi/jarvis_brand_poster.jpg`, copied without modification from the user-selected Desktop artwork.
- Use `res/drawable-nodpi/jarvis_brand_icon.png` for compact brand surfaces. It is a square launcher-safe derivative of the same robot artwork, retaining the green Android body, circular cyan reactor head, and red center light while omitting the wordmark at icon scale.
- Use the full poster on the home screen and the compact robot mark for launcher, splash, top bar, and generated learning-card footers.
- Keep upstream internal filenames and identifiers where practical. Existing `icon.xml`, `logo.xml`, launcher XML, and skill structure remain in place as integration points even though their visible artwork is Jarvis-branded.
- Jarvis Neon is a single intentional theme rather than a light/dark preference. Both legacy color-token sets resolve to a black/neon palette, and `GalleryTheme` always selects the Jarvis dark scheme so saved/system light-mode settings cannot undo the identity.
- Core visual tokens: black `#000000` background, neon lime `#8CFF00` primary, pale lime `#D5FFB6` primary text, cyan `#00E5FF` secondary/link accent, and near-black green surface containers.

## Non-negotiable requirements

- System Instructions and Personality Prompt must remain independently editable and independently stored.
- Both settings must persist across app process death and reopening.
- Prompt editing must not require a downloaded or initialized model.
- Both settings must feed new chat and agent model initialization through the existing system-instruction path.
- Do not add a user-facing context-size setting for cloud models.
- The OpenAI key must be editable without a model download, masked in the UI, encrypted at rest, excluded from backups, and removable by the user.
- OpenAI API usage and ChatGPT subscriptions must be described as separate billing products in the UI.
- App backgrounds and system bars must remain black. Primary text, interactive controls, focus/selection states, and borders must remain in the neon-green family with readable contrast.
- The selected Jarvis artwork must remain recognizable and unclipped under adaptive launcher masks. Do not stretch the poster or use its wordmark at tiny icon sizes.
- Branding must be visible without removing upstream model/chat functionality or renaming internal source identifiers unnecessarily.
- Jarvis chat is the primary application surface. Model management, configuration, and other retained Edge Gallery screens are secondary destinations rather than competing chat modes.
- Text, supported images/audio, Skills, MCP tools, and future memory/device capabilities must converge on the same session transcript and model selector.
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
- Jarvis initializes with `gpt-5.6-terra` (`GPT-5.6 Medium`) when no valid Jarvis model is already selected. Its picker is assembled from 3 curated OpenAI entries followed by locally compatible models.
- Agent Skills already used the shared `LlmChatScreen` with image/audio pickers, Skills, MCP controls, tool progress, generated images, and WebViews. The unified milestone reuses that screen as Jarvis rather than creating another chat UI.
- The OpenAI executor currently handles text and images but does not yet implement the Skills/MCP function-call round trip and rejects raw audio clips. Cloud tool calling and provider-independent voice are the next runtime milestones; do not claim them as complete.
- Voice-to-text should be the first provider-independent voice capability. Full duplex voice/audio transport can be added later without splitting the conversation into a separate mode.
- `OpenAiCredentialsRepository` owns encrypted key storage and a random per-install `safety_identifier` that contains no account or device identity.
- `OpenAiApiClient` streams Responses API server-sent events into the existing `AgentEvent` contract. It sends text and image inputs, maps authentication/quota/model errors to user-readable messages, and disconnects on cancellation.
- The Desktop key source selected on 2026-08-02 was structurally recognized but OpenAI returned HTTP 401 with `invalid_api_key`. The app's request path and error UI were verified, but successful OpenAI inference remains blocked until the key is replaced with a valid API-platform key.
- `C:\Users\Billy\Desktop\Echo Downloads` is the user's preferred drop location for future project inputs. Files explicitly named elsewhere on the Desktop remain in scope when the user points to them.
- `Desktop\bluetooth_content_share.html` contains one project-key-shaped candidate, but a direct redacted authentication check returned HTTP 401 `invalid_api_key`; do not reinstall or claim that credential is valid.
- Compose `painterResource` can load raster resources directly but cannot load an Android `<bitmap>` wrapper. Compact Compose surfaces must reference `R.drawable.jarvis_brand_icon` directly; the XML wrappers remain suitable for platform launcher/splash consumers.
- The Niagara launcher hides non-favorite apps until an alphabet section is selected. Use its `A` section to visually inspect the installed `Android Jarvis` icon.
