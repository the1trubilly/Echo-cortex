# Android Jarvis — Echo Brief

## Current milestone

Android Jarvis now includes a built-in, opt-in `jarvis-system-core` skill: the first executable OpenJarvis-inspired orchestration slice. It produces a typed five-primitive system manifest, phased workflow DAG, capability/approval report, evidence trace, proposal-only reflection, and Obsidian-ready Markdown export. It is deliberately stateless and does not pretend to execute another tool, persist memory, schedule work, or modify the app. Those behaviors remain native runtime/service milestones.

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
- Treat a nonblank API-key draft as pending credential input: Save/Replace, keyboard Done, Close, and system Back must all use the same verified save path. If encryption or persistence fails, keep Settings open and show an error instead of reporting success.
- Never print, log, commit, or place an OpenAI API key in a Gradle property, resource, manifest, or source file.
- Append an app-generated Runtime Self-Model after task instructions, saved System Instructions, and saved Personality Prompt. Runtime facts are authoritative session metadata; personality may shape their presentation but must not erase or invent them.
- Runtime self-knowledge must identify the user-facing model tier, exact configured provider model ID, inference provider, executor path, accepted modalities, enabled Skill/MCP names, memory status, and current tool/self-extension limits.
- Treat self-extension as an app-level capability workflow, not unrestricted prompt-driven source mutation. The required lifecycle is propose, explain requested capabilities, user review, install/update, test, audit, and rollback.
- Jarvis must never claim that it installed a tool, changed code, wrote memory, granted a permission, or modified itself unless an app/tool result confirms the action.
- Planning, reflection, and learning proposals must never be presented as executed changes. Self-improvement requires propose, capability review, user approval, installation/update, testing, audit, and rollback.
- Every canonical memory must be exportable as Markdown suitable for an Obsidian vault; no opaque vector database may become the only copy of user memory.
- Treat direct client-side OpenAI credentials as a personal-device implementation. A future multi-user or distributed release should move provider credentials behind a trusted backend.
- Present cloud models alongside local models in Jarvis while preserving local-model functionality. Cloud model cards are ready immediately and must not expose download controls or a user-facing context-size control.
- Keep OpenAI provider IDs internal. Present `gpt-5.6-sol` as `GPT-5.6 High`, `gpt-5.6-terra` as `GPT-5.6 Medium`, and `gpt-5.6-luna` as `GPT-5.6 Instant`. Do not expose Sol/Terra/Luna branding in the model picker.
- Do not show the separate `chat-latest` alias while GPT-5.6 Instant is present; two user-facing Instant choices would be ambiguous.
- Jarvis should answer normally when a capability is unnecessary. Skills and MCP tools are optional capabilities, and the absence of a matching tool must never reduce ordinary chat to a `No skills or tools found` response.
- Jarvis is the only registered general chat task. Keep the former AI Chat implementation available as dormant source while its shared components are reused, but do not register a competing task or duplicate its OpenAI models.
- Treat `Model.name` as the stable cross-task model identity. Global model-management aggregation must deduplicate by `name`, not by full mutable `Model` equality.
- Back from the primary Jarvis chat must open the Jarvis control center over the active session. It must not exit the app, unload the active model, enter the retired Gallery home, or strand the user in a model selector.
- Treat the Jarvis control center as the secondary-navigation hub for Settings, Skills, MCP servers, and model management. Closing a nested Settings/Skills/MCP surface returns to this hub.
- Render both Edge Gallery skill catalogs inside the app: the repository's featured folders and the broader Community Skills GitHub Discussions category. A visible `+` beside each entry invokes the existing validation and persistence path; users must not need to copy URLs manually.
- Map OpenJarvis's typed system model onto five stable Android Jarvis primitives: Intelligence, Engine, Agents, Tools & Memory, and Learning. Keep the model/provider picker under Intelligence, provider executors under Engine, orchestration strategies under Agents, permission-gated capabilities and the memory service under Tools & Memory, and traces/evaluations/reviewed improvements under Learning.
- Use a skill for the first orchestration prototype, but keep cross-tool execution, permission enforcement, durable traces, scheduling, and self-modification in native app/runtime layers. A JavaScript skill must never impersonate capabilities the current executor cannot supply.
- Keep `jarvis-system-core` stateless and disabled by default until the user chooses it. Do not persist its plans or traces in shared WebView `localStorage`; its export action returns Markdown without claiming to write a vault file.
- Use the OpenJarvis trace vocabulary for the initial evidence pipeline: `route`, `retrieve`, `generate`, `tool_call`, and `respond`. Planned steps and supplied observations must remain distinguishable from confirmed executions.
- Treat canonical long-term memory as human-readable Markdown in an Obsidian-compatible vault. Embeddings, graph projections, summaries, caches, and Matryoshka-vector levels of detail are derived indexes that must be rebuildable from canonical notes and receipts.
- Design the future memory service as an external cognitive organ: a persistent world state and conceptual lattice with typed links, memory gates, category-sensitive link strength, activation/gravity, provenance, confidence, temporal context, and bounded active rendering over a deeper archive.
- Incorporate useful ThreadKeeper concepts (records, links, checkpoints, receipts, readback, activation, and synthesis review) in the native memory service while replacing its shared-origin WebView storage and lexical-only retrieval.
- Expose the install bridge only while the WebView is on an exact official Edge Gallery featured-catalog, Skills-category, or numbered discussion page. Featured requests remain limited to exact featured folders. Community requests may use the post author's declared HTTPS `Skill Webhost Path`, but must still pass native source checks, `SKILL.md` fetching/parsing, duplicate-name checks, and local persistence. Never give arbitrary navigated pages bridge authority.
- Show the existing third-party skill disclaimer before opening the community catalog. Community listings are discovery, not an endorsement or allowlist.
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
- When runtime metadata is available, Jarvis must answer model/provider/runtime questions from it instead of saying it lacks visibility into the underlying model.
- App backgrounds and system bars must remain black. Primary text, interactive controls, focus/selection states, and borders must remain in the neon-green family with readable contrast.
- The selected Jarvis artwork must remain recognizable and unclipped under adaptive launcher masks. Do not stretch the poster or use its wordmark at tiny icon sizes.
- Branding must be visible without removing upstream model/chat functionality or renaming internal source identifiers unnecessarily.
- Jarvis chat is the primary application surface. Model management, configuration, and other retained Edge Gallery screens are secondary destinations rather than competing chat modes.
- Android Back from Jarvis must expose Settings and skill installation without crashing or destroying the current chat session.
- One-tap featured and community installations must be validated, saved locally, enabled on success, and remain present after process death. Keep manual URL and local-directory import as fallback paths.
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
- The reproduced key-persistence failure was an unsaved form state, not failed AES/GCM decryption: the private credentials file existed but contained no API-key ciphertext or IV because dismissing Settings bypassed the Save button. Settings now saves a nonblank pending draft before every dismissal path.
- `OpenAiApiClient` streams Responses API server-sent events into the existing `AgentEvent` contract. It sends text and image inputs, maps authentication/quota/model errors to user-readable messages, and disconnects on cancellation.
- `JarvisRuntimeSelfModel` is appended during both initial model setup and session resets. It is regenerated when the selected model, enabled Skills, or enabled MCP tools change, and replaces any prior generated section rather than accumulating duplicates.
- A live OpenAI device query confirmed the self-model: Jarvis reported Android Jarvis 1.0.17 (38), `OpenAI · GPT-5.6 Medium`, exact provider ID `gpt-5.6-terra`, OpenAI Responses API, `OpenAiAgentRuntimeExecutor` through `RoutingAgentRuntimeExecutor`, text/image support, and the truthful current inability to install tools or modify source.
- The Desktop key source selected on 2026-08-02 was structurally recognized but OpenAI returned HTTP 401 with `invalid_api_key`. The app's request path and error UI were verified, but successful OpenAI inference remains blocked until the key is replaced with a valid API-platform key.
- The reproduced Back crash was a `NullPointerException` at `HomeScreen.kt:901`: the legacy highlighted-task list force-unwrapped the now-unregistered AI Chat task. Jarvis no longer uses Home as its Back destination.
- Upstream's `SKILL_ALLOWLIST_URL` is blank, so its native featured-skill list contains no remote entries. The working in-app browser now exposes the live `skills/featured` directory and the official `discussions/categories/skills` community catalog as separate tabs.
- Community posts conventionally declare `Skill Webhost Path` and `Skill Source Repository`. The injected community `+` fetches the selected numbered discussion, prefers the declared webhost path, and hands that HTTPS base to the native installer. If a post lacks an installable declaration, the app opens the discussion rather than inventing a source.
- GitHub's mobile page intercepts custom navigation links, so the per-row install buttons use a narrow JavaScript bridge. Native code grants that bridge authority only on official Edge Gallery catalog/discussion pages and still revalidates every install source before installation.
- Device verification installed `mood-music` from its rendered `+` button, converted the normal GitHub tree URL to the raw `SKILL.md` base, saved it selected, and found it still enabled after a cold app restart.
- `C:\Users\Billy\Desktop\Echo Downloads` is the user's preferred drop location for future project inputs. Files explicitly named elsewhere on the Desktop remain in scope when the user points to them.
- `Desktop\bluetooth_content_share.html` contains one project-key-shaped candidate, but a direct redacted authentication check returned HTTP 401 `invalid_api_key`; do not reinstall or claim that credential is valid.
- Compose `painterResource` can load raster resources directly but cannot load an Android `<bitmap>` wrapper. Compact Compose surfaces must reference `R.drawable.jarvis_brand_icon` directly; the XML wrappers remain suitable for platform launcher/splash consumers.
- The Niagara launcher hides non-favorite apps until an alphabet section is selected. Use its `A` section to visually inspect the installed `Android Jarvis` icon.
- The current OpenJarvis repository is a broader system framework rather than a single agent loop. Its public architecture is a typed specification across Intelligence, Engine, Agents, Tools & Memory, and Learning, with additional workflow, trace, security/capability, session, scheduler, channel, connector, and speech modules.
- OpenJarvis traces use `route`, `retrieve`, `generate`, `tool_call`, and `respond` steps; its workflow graph models agent, tool, condition, parallel, loop, and transform nodes; and its capability labels include file, network, code, memory, channel, tool, schedule, and system scopes.
- The `jarvis-system-core` asset exposes `status`, `plan`, `observe`, `reflect`, and `export` actions through the existing `run_js` bridge. Direct JavaScript checks confirmed the five actions, approval defaults, credential-shaped value redaction, reflection proposals, and Markdown output.
- The installed device's Skills manager reported 19 total skills after this APK was installed, while the Agent Skills chat badge remained at 14 enabled skills because `jarvis-system-core` is opt-in.
