# Android Jarvis — Echo Handoff

## Scope completed

- Added OpenAI as a first-class AI Chat provider without changing the existing LiteRT model path.
- Added selectable ChatGPT Instant, GPT-5.6 Sol, GPT-5.6 Terra, and GPT-5.6 Luna cloud model cards. They are available without a model download.
- Added an explained, masked OpenAI API-key setting that can be saved, replaced, or removed independently of model state.
- Encrypted the key with Android Keystore AES/GCM and excluded its private preferences from cloud backup and device transfer.
- Added a Responses API streaming client with text/image input, locally managed history, `store: false`, cancellation, and readable API-error mapping.
- Routed OpenAI AI Chat sessions through the cloud executor while preserving the existing local executor for local chat and Agent Skills.
- Reused the existing compiled system-instruction path, so task instructions are followed by saved System Instructions and then saved Personality Prompt before being sent as OpenAI `instructions`.
- Added request-contract and streaming-parser unit coverage.

## Exact files changed

- `app/src/main/java/com/google/ai/edge/gallery/data/Model.kt` — added the `OPENAI` runtime type.
- `app/src/main/java/com/google/ai/edge/gallery/data/OpenAiModels.kt` — defines the four curated OpenAI model choices and cloud-ready model metadata.
- `app/src/main/java/com/google/ai/edge/gallery/data/OpenAiCredentialsRepository.kt` — saves, reads, and removes the API key using Android Keystore AES/GCM; also owns the anonymous per-install safety identifier.
- `app/src/main/java/com/google/ai/edge/gallery/agent/OpenAiApiClient.kt` — implements Responses API requests, SSE streaming, images, cancellation, and user-readable failure mapping.
- `app/src/main/java/com/google/ai/edge/gallery/agent/OpenAiAgentRuntimeExecutor.kt` — adapts OpenAI streaming and local conversation history to the existing `AgentEvent` interface.
- `app/src/main/java/com/google/ai/edge/gallery/agent/RoutingAgentRuntimeExecutor.kt` — delegates each session to the OpenAI or existing local executor based on runtime type.
- `app/src/main/java/com/google/ai/edge/gallery/agent/AgentRuntimeExecutor.kt` — adds provider-neutral conversation restoration support.
- `app/src/main/java/com/google/ai/edge/gallery/agent/AgentExecutorModule.kt` — injects the routing executor for AI Chat while retaining the local executor for Agent Skills.
- `app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatViewModel.kt` — restores OpenAI sessions through the provider-neutral history path and preserves actionable cloud API errors.
- `app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatScreen.kt` — converts saved text history to provider-neutral user/assistant turns when starting a cloud session.
- `app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatTaskModule.kt` — adds the OpenAI choices to AI Chat and supplies cloud-specific empty-state copy.
- `app/src/main/java/com/google/ai/edge/gallery/ui/modelmanager/ModelManagerViewModel.kt` — exposes API-key presence/save/remove operations to Settings.
- `app/src/main/java/com/google/ai/edge/gallery/ui/home/SettingsDialog.kt` — adds the masked OpenAI credential editor, billing explanation, persisted-state indicator, and replace/remove actions.
- `app/src/main/java/com/google/ai/edge/gallery/ui/common/modelitem/ModelNameAndStatus.kt` — identifies OpenAI models as cloud models with no download required.
- `app/src/main/res/values/strings.xml` — adds OpenAI model, credential, security, billing, and cloud-chat copy.
- `app/src/main/res/xml/backup_rules.xml` — excludes OpenAI credentials from legacy Android backup.
- `app/src/main/res/xml/data_extraction_rules.xml` — excludes OpenAI credentials from cloud backup and device transfer.
- `app/src/test/java/com/google/ai/edge/gallery/common/OpenAiApiClientTest.kt` — verifies request composition, `chat-latest` compatibility, image/history encoding, SSE deltas, completion, and failure events.
- `ECHO_BRIEF.md` — records durable provider, credential, prompt, and cloud-session architecture decisions.
- `ECHO_HANDOFF.md` — records this implementation and its verified limits.

## Commands and evidence

- Inspected the existing model, chat, executor, prompt-composition, settings, storage, backup, and dependency-injection paths before changing them.
- Verified current OpenAI model/API guidance against official documentation on 2026-08-02. The model resolver selected `gpt-5.6-sol`; the curated UI also exposes ChatGPT Instant, Terra, and Luna for user choice.
- `gradlew.bat testDebugUnitTest assembleDebug` returned `BUILD SUCCESSFUL` after implementation.
- A later focused `gradlew.bat testDebugUnitTest` returned `BUILD SUCCESSFUL`: 3 suites, 8 tests, 0 failures, 0 errors.
- The final `gradlew.bat assembleDebug` returned `BUILD SUCCESSFUL` in 10 seconds.
- The final APK was `app/build/outputs/apk/debug/app-debug.apk`.
- `adb -s R5CX31PBW7V install -r app/build/outputs/apk/debug/app-debug.apk` returned `Success`.
- The launcher activity started and remained focused. The Android crash buffer contained no startup crash.
- The installed home screen showed `AI Chat task with 11 models`.
- The AI Chat model list showed all four entries: `OpenAI · ChatGPT Instant`, `OpenAI · GPT-5.6 Sol`, `OpenAI · GPT-5.6 Terra`, and `OpenAI · GPT-5.6 Luna`, each labeled `Cloud · No download required`.
- Settings showed the OpenAI field before any local model was downloaded. After secure entry and save, it showed `API key saved`, `Replace key`, and `Remove key`.
- After force-stop and relaunch, the saved-key UI state was restored. The app-private encrypted-preferences file existed and was 379 bytes; its contents were not printed.
- A GPT-5.6 Luna session opened without a download, accepted `Reply with OK`, reached the OpenAI request path, and displayed `OpenAI rejected the API key. Replace it in Settings and try again.` The crash buffer remained empty.
- An independent no-generation `GET /v1/models` check returned HTTP 401, error type `invalid_request_error`, code `invalid_api_key`, confirming authentication failure rather than network, quota, or model-selection failure.
- Secret-safety checks found no API-key pattern in the Git diff. The key was never printed or written to the repository.

## Unresolved problems

- The selected Desktop key is invalid or has been revoked. A successful model response could not be verified and must not be claimed.
- Replace it with a valid OpenAI API-platform key in Settings, then rerun a real chat. A ChatGPT subscription does not itself provide API billing or credits.
- The direct-on-device credential design is appropriate for this personal-device milestone but should be replaced by a trusted backend before any multi-user/distributed release.
- Pre-existing build warnings remain: Android Gradle Plugin versus compile SDK 37, context-receiver migration, and Gradle/Moshi deprecations. None blocked this feature and none were broadened into unrelated refactors.
- Pre-existing untracked `.idea/` and `gradle/gradle-daemon-jvm.properties` paths remain untouched and uncommitted.

## Questions for Echo

- When a valid API key is available, should the next provider milestone add OpenAI voice/realtime and image-generation routes, or first extract a general provider interface for additional cloud services?
- Should the curated OpenAI model list remain release-pinned, or should a later settings screen fetch and filter the account's live `/v1/models` list?
- Before public distribution, what trusted-backend boundary should own cloud-provider credentials, quotas, and user identity?

## Recommended next step

Replace the rejected key with a valid OpenAI API-platform key, send one GPT-5.6 Luna prompt and one image prompt, and capture the completed streamed response plus restart/history restoration evidence. After that, add voice/realtime behind the same provider-neutral execution boundary rather than coupling it directly to the current chat UI.
