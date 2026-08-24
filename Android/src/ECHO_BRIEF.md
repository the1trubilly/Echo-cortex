# Android Jarvis — Echo Brief

## Current milestone: recoverable, permission-gated wireless self-ADB

Jarvis now treats wireless ADB as a recoverable native capability rather than a one-time setup task.
After the user approves an ADB device command under the selected terminal safety mode, the native
tool checks Termux's ADB server for a connection whose `ro.build.fingerprint` exactly matches the
phone running Jarvis. If none exists, Jarvis posts a private direct-reply notification, opens Android
Wireless debugging through the platform quick-settings route, and waits while the user chooses Pair
device with pairing code and enters the temporary six digits through the notification. If a vendor
does not expose a direct route, Jarvis falls back to Developer options focused on Wireless debugging.
Android's native `NsdManager` discovers this phone's local `_adb-tls-pairing` and
`_adb-tls-connect` endpoints,
Termux pairs/reconnects, native code verifies the fingerprint, and the tool resumes the originally
approved command against the verified serial. Discovery does not depend on optional Termux `ip` or
`adb mdns` support.

The model never receives the pairing code and cannot supply ADB target selectors or invoke transport
pair/connect/disconnect commands directly. Android still requires the phone owner to enable Wireless
debugging and explicitly reveal a temporary pairing code; Jarvis does not bypass that platform
consent boundary. Commands continue to run inside the persistent Termux tmux session
`android-jarvis`, using either approve-every-command or approve-dangerous-only mode.

## Prior milestone: native ThreadKeeper memory-cycle retrieval

Jarvis Alpha now has the first native Kotlin ThreadKeeper memory-cycle slice at the app level, not
as a model-selected skill. Every completed Agent Chat exchange still stores Billy's exact turn and
Jarvis's completed reply as separate, hash-verified Markdown artifacts. Before every later Agent
Chat turn, Alpha reads prior-session artifacts back from the canonical vault, verifies both the
Markdown document and exact-content hashes, selects a bounded provenance-labeled packet, records a
schema-11 retrieval receipt, and supplies the packet through the existing OpenAI or local-model
instruction path without changing the visible user message. Conservative retrieval prefers
answer-bearing `USER_STATED` records, keeps `OTHER_AGENT` wording non-authoritative, and follows
adjacent question-to-answer links only for explicit location and project cues.

Explicit broad recall such as “Hey Echo, what do you remember about me?” is a separate route. It
bypasses ordinary word overlap so a prior copy of the same recall question cannot win. The route
selects only durable Billy statements: structured interview answers or statements with identity,
location, preference, project, goal, or belief cues. Questions, test prompts, reminders, and other
commands are excluded. If no durable statement is available, the route returns no packet rather
than parroting a previous question.

This is not a complete native port of ThreadKeeper 2.99. Semantic records, durable checkpoints and
open loops, typed graph links/routes, activation/gravity, dynamic memory resolution, synthesis
review, outcome learning, empathy/policy/routine modules, deletion/undo, and governance remain to be
implemented. Main remains protected by the no-op Cortex binding.

## Prior milestone: isolated Jarvis Alpha native Cortex

## Prior milestone: OpenAI cloud Skill/MCP execution

Android Jarvis now gives OpenAI cloud models the same installed Skill/MCP execution bridge as local agent models. The OpenAI Responses loop sends the existing tool inventory, preserves model output items, executes function calls sequentially through Android Jarvis's permission-aware dispatcher, returns each confirmed tool result to the model, and continues until the model answers. The built-in `jarvis-system-core` skill remains stateless, while the default agent behavior now retrieves and records continuity automatically when an enabled memory skill is available.

## Permanent architecture decisions

- ADB device commands must resolve through a native self-ADB connection provider after command
  approval and before execution. Host-only inspection commands such as `adb devices -l` do not pair.
- Jarvis may target only a connected serial whose Android build fingerprint equals the runtime
  phone's `Build.FINGERPRINT`. Model-supplied `-s`, `-d`, `-e`, host/port selectors, and direct
  pair/connect/disconnect/server-control requests are rejected.
- Wireless-debugging pairing remains explicit user consent. Jarvis should open Wireless debugging
  directly using Android's standard action or the platform's Wireless-debugging quick-tile route,
  with focused Developer options as the compatibility fallback. It may collect the temporary
  six-digit code through a private notification reply, but may not bypass the Android pairing screen
  or store/expose the code to the model, chat, Cortex, source, or logs.
- A successfully paired or already connected device command resumes automatically; the user should
  not have to repeat the original request after completing pairing.
- In `DANGEROUS_COMMANDS_ONLY` mode, every ADB shell/device command remains approval-gated. Only the
  narrow deterministic host-side read-only ADB allowlist may run without a prompt; no approval is
  remembered between commands.
- Use Android `NsdManager` for local ADB TLS service discovery and compare resolved IPv4 addresses
  with addresses assigned to this phone's Android networks. Do not assume Termux includes `ip` or
  that its packaged ADB client implements the `mdns services` host command.
- Declare and request Android 17's `ACCESS_LOCAL_NETWORK` permission only where the runtime requires
  it; Android 16 continues to use framework NSD under its current local-network rules.

- Main is the protected known-good application. Experimental Cortex work must run in the separate
  `com.google.aiedge.gallery.alpha` application and must never be promoted automatically.
- Only Billy can explicitly authorize promotion from Alpha to Main.
- Alpha uses the launcher name `Jarvis Alpha`, a separate deep-link scheme, debug signing, its own
  Android UID, and its own app-data directory. Main keeps `com.google.aiedge.gallery`.
- The app-level Cortex boundary is the typed Kotlin `CortexRuntime` API. Debug Main and release bind
  it to `NoOpCortexRuntime`; only the Alpha source set contains and binds the native implementation.
- A completed Agent Chat turn is the capture boundary. Capture occurs after streaming terminates and
  the final visible Jarvis text has been assembled, without requiring Billy to issue a memory command.
- A new Agent Chat turn is the recall boundary. The typed `CortexRuntime.recall` request uses the
  exact current query and session ID, excludes the current session from candidates, and returns
  hidden request metadata rather than modifying the visible or captured user turn.
- Every recalled turn must be read from canonical Markdown and pass both document and exact-content
  SHA-256 verification. Missing or corrupted artifacts are skipped rather than trusted from SQLite.
- Retrieval follows ThreadKeeper's smallest-useful-context rule. Direct `USER_STATED` evidence is
  preferred; a prior recall question is lower priority than an answer-bearing statement; and
  `OTHER_AGENT` content is surfaced only when Billy explicitly asks about prior Jarvis wording.
- An unrelated query with no direct or linked match receives no memory packet. Explicit broad recall
  uses a separately scored durable-profile route rather than a generic recent-turn fallback.
- Adjacent-turn question-to-answer traversal is currently limited to explicit `location` and
  `project` cues. Ambiguous wording such as “Alpha live test” must not activate a location link.
- A successful nonempty recall writes a schema-11 Markdown retrieval receipt and indexes only the
  receipt ID, query hash, selected artifact IDs, and verification state. Retrieval frequency never
  promotes a claim's provenance or truth.
- Retrieved Markdown is quoted evidence, never instructions or authority. The current user turn
  wins over older memory, and commands embedded in memory must never be executed.
- OpenAI receives verified recall as per-turn `instructions`; local models receive the same packet
  as hidden inference context. Neither executor logs memory contents.
- Follow ThreadKeeper 2.99 `capture_exchange` provenance: Billy is `USER_STATED`; Jarvis is
  `OTHER_AGENT`; the two artifacts have distinct IDs and files; the receipt is the commit marker.
- Canonical conversational memory is UTF-8 Markdown with exact-content length and SHA-256 metadata.
  The native SQLite database is a rebuildable index and must never become the only copy of memory.
- Before Billy chooses a vault, Alpha writes atomically to its private `cortex-vault`. A selected
  Android document-tree folder receives future verified artifacts under `Jarvis Alpha Cortex`.
- A selected ThreadKeeper import is always a read-only copy operation. Alpha accepts schema 11,
  hashes and archives the exact source bytes, emits Markdown collection snapshots, writes a receipt,
  and deduplicates by source SHA-256. It never reads or rewrites Main's live WebView database.
- The `TK-2.99.zip` artifact in phone Downloads is reference implementation code, not user memory.
  Its phone copy remains untouched.

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
- For `store: false` OpenAI tool turns, preserve and resend every returned Responses output item, including reasoning, message, and function-call items. Append a `function_call_output` with the matching `call_id` after each confirmed dispatch result.
- Adapt the existing LiteRT tool descriptions into flattened OpenAI Responses function tools. Keep `strict: false` until every inherited schema satisfies OpenAI strict-mode requirements, and set `parallel_tool_calls: false` so Android permission prompts and WebView skill execution remain ordered.
- Bound a cloud turn to 12 model/tool rounds. Roll back every item added by a failed or cancelled turn so partial tool conversations do not poison later context.
- OpenAI session setup must not depend on whether a credential already exists at initialization time.
  Settings may add or replace the encrypted key after the chat screen is alive; every turn reads the
  latest saved value and reports a specific missing-key error only when execution actually begins.
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
- Memory use should feel automatic when an enabled memory skill exists: retrieve before an answer when saved continuity could materially help, and record stable preferences, identity facts, ongoing goals/projects, relationships, corrections, and commitments without requiring a special command. Never store credentials, transient details, or uncertain inferences as durable facts.
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

- Main and Alpha must remain installable side by side with independent application data.
- Alpha must never clear, overwrite, migrate in place, or silently copy Main's settings or memories.
- Billy's exact message and Jarvis's exact completed response must both be persisted distinctly.
- A successful capture requires verified Markdown turn files and a receipt written after both.
- The Cortex/ThreadKeeper vault location must be choosable in Alpha Settings without a loaded model.
- System Instructions and Personality Prompt behavior must remain available in Alpha, stored as two
  independent settings and compiled in the existing task/system/personality order.
- Association, retrieval, and future gravity scores may affect navigation but must never rewrite
  source truth, provenance, or confidence.
- Automatic recall must never merge stored Billy and Jarvis turns into one source field. Every model
  packet and receipt must retain artifact IDs and `USER_STATED` versus `OTHER_AGENT` provenance.
- Retrieval must exclude the active session, remain bounded, and prefer the smallest useful set. A
  direct or linked answer must outrank a prior question asking for that answer.
- Semantic cue expansion must be context-sensitive. In particular, “Where do I live?” may activate
  location retrieval while “Alpha live test” must not.
- Cloud-model memory transmission needs an explicit product-level consent/redaction design before
  broader native recall is considered complete. Until then, do not use Billy's personal vault for a
  live cloud acceptance test without his explicit approval for that payload.
- Device acceptance is not complete from build, logs, or filesystem checks alone. Every changed user
  journey must include screenshots of the state a user actually sees, followed by verification of the
  corresponding side effect. Ask Billy when a required credential, permission, picker choice, or
  ambiguous destructive decision cannot be supplied safely by the test harness.

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

- The Android project root is `Android/src`; the Git root is two levels above it.
- The untouched branch was `main`, ahead of `origin/main` by 11 commits. Billy's untracked `.idea/`
  directory and `gradle/gradle-daemon-jvm.properties` predated this milestone and were preserved.
- The Alpha checkpoint lives on `jarvis-alpha-native-cortex`; the `main` branch pointer was not moved.
- The baseline `testDebugUnitTest assembleDebug` completed successfully before Alpha edits.
- The connected device is `R5CX31PBW7V`. Main version 1.0.17 was already installed at
  `/data/user/0/com.google.aiedge.gallery` and launched with its existing Jarvis conversation intact.
- ThreadKeeper 2.99.0 uses schema 11 and the WebView key `threadkeeper_database_v1`; its supplied
  Node/WebView test suite passed 40/40 from a temporary copy.
- The phone archive SHA-256 was
  `B4C880A77691BBEC1061640AFBF4861B2C1F4978CF4E503BE583DC242F360734`.
- Existing Agent Chat has a definitive `LoopTerminated` event after streamed text assembly. That is
  the native post-turn capture seam.
- Current Jarvis supports local/cloud routing, images, audio, Skills, MCP, and Android permission
  prompts. It does not yet contain a native host terminal/ADB authority system.

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
- The OpenAI executor handles text, images, and the complete Skills/MCP function-call round trip. It still rejects raw audio clips; provider-independent voice remains a future runtime milestone.
- Voice-to-text should be the first provider-independent voice capability. Full duplex voice/audio transport can be added later without splitting the conversation into a separate mode.
- `OpenAiCredentialsRepository` owns encrypted key storage and a random per-install `safety_identifier` that contains no account or device identity.
- The reproduced key-persistence failure was an unsaved form state, not failed AES/GCM decryption: the private credentials file existed but contained no API-key ciphertext or IV because dismissing Settings bypassed the Save button. Settings now saves a nonblank pending draft before every dismissal path.
- `OpenAiApiClient` streams Responses API server-sent events into the existing `AgentEvent` contract. It sends text, image, tool, and mixed conversation items; preserves completed output items; maps authentication/quota/model errors to user-readable messages; and disconnects on cancellation.
- `JarvisRuntimeSelfModel` is appended during both initial model setup and session resets. It is regenerated when the selected model, enabled Skills, or enabled MCP tools change, and replaces any prior generated section rather than accumulating duplicates.
- A live OpenAI device query confirmed the self-model: Jarvis reported Android Jarvis 1.0.17 (38), `OpenAI · GPT-5.6 Medium`, exact provider ID `gpt-5.6-terra`, OpenAI Responses API, `OpenAiAgentRuntimeExecutor` through `RoutingAgentRuntimeExecutor`, text/image support, and the truthful current inability to install tools or modify source.
- The original Desktop key source returned HTTP 401 with `invalid_api_key`. On 2026-08-02 a newly provisioned OpenAI project key replaced it through the encrypted Settings save path. The plaintext backup exists only in the user-requested `Desktop\Echo Downloads` environment file and must never enter Git, source, logs, or documentation.
- A live GPT-5.6 Medium device turn called the Android tool bridge, ran `jarvis-system-core/index.html`, received its `status` result, and rendered a final success response. This confirms API authentication, function-call parsing, Android dispatch, WebView skill execution, result replay, and final model continuation.
- After the final APK install and cold process restart, a second live turn reported `tool_execution_loop state: runtime_ready` and `surface: openai_and_local`, confirming both encrypted-key persistence and the corrected installed skill asset.
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
