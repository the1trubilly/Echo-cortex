# Android Jarvis — Echo Handoff

## Current milestone: native Cortex cross-session recall

### Follow-up: real broad-recall acceptance failure and fix

- Billy tested the actual chat prompt `Hey Echo what do you remember about me`. Jarvis visibly
  replied that the only verified memory was Billy previously asking that same question.
- Receipt `757e064e-628c-4ac9-a62a-37b72637db3f` proved the selector chose one `USER_STATED`
  artifact only because it overlapped on `hey` and `echo`. The broad-recall fallback never ran
  because ordinary lexical selection took precedence.
- Explicit broad recall now bypasses lexical selection and ranks durable Billy statements only.
  Structured interview answers and explicit identity/location/preference/project/goal/belief
  statements qualify; questions, recall prompts, test commands, reminders, and other requests do not.
- The exact production failure is a permanent regression fixture: newer copies of the `Hey Echo`
  question plus newer validation/reminder commands must lose to the older numbered interview.
- A temporary read-only instrumentation audit ran the final selector against Billy's real vault with
  the exact prompt. It selected exactly one artifact, the structured interview containing Greenwood.
  It did not call OpenAI, write a receipt, or remain in the repository.
- User-visible cloud-response acceptance is still pending explicit authorization to transmit the
  selected interview packet to OpenAI. The installed final code, actual-vault selection, disposable
  phone runtime, builds, and screenshot of the failure are verified; do not claim the corrected
  cloud answer is verified yet.

### Scope completed

- Reproduced Billy's real failure: the selected vault contained the exact interview answer with
  `Greenwood Delaware`, but a later session answered that it had no reliable personal history.
  Capture was working; no native retrieval path existed.
- Added typed app-level recall requests and packets to `CortexRuntime`. Main/release remain no-op;
  Alpha performs automatic recall before every Agent Chat turn.
- Added a hidden request-metadata hook so retrieval does not alter the displayed user message or the
  exact Markdown later captured for that turn.
- Added canonical Markdown readback with document and exact-content SHA-256 verification. SQLite is
  used only to locate prior-session candidates.
- Added conservative bounded selection with strict `USER_STATED`/`OTHER_AGENT` handling,
  answer-bearing-statement preference, simple normalization, and adjacent question-to-answer links
  limited to explicit location and project cues.
- Added provider-neutral injection: OpenAI receives the verified packet through per-turn
  `instructions`; local models receive it as hidden inference context.
- Added schema-11 Markdown retrieval receipts plus a version-2 SQLite migration and visible recall
  counts in Alpha Settings.
- Updated Jarvis's runtime self-model to truthfully report this first native memory-cycle slice and
  explicitly name the ThreadKeeper modules that are still missing.

### Exact files changed

- `app/build.gradle.kts` - exposes an Alpha-only native-Cortex BuildConfig fact.
- `app/src/main/java/com/google/ai/edge/gallery/cortex/CortexRuntime.kt` - typed recall request/packet
  boundary and Main-safe no-op behavior.
- `app/src/main/java/com/google/ai/edge/gallery/agent/AgentRequest.kt` - hidden Cortex recall metadata key.
- `app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatViewModel.kt` - task-specific hidden
  request-metadata hook.
- `app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/AgentChatViewModel.kt` - automatic
  pre-turn recall and existing post-turn exact capture.
- `app/src/main/java/com/google/ai/edge/gallery/agent/OpenAiAgentRuntimeExecutor.kt` - appends verified
  recall to per-turn OpenAI instructions.
- `app/src/main/java/com/google/ai/edge/gallery/agent/DefaultAgentRuntimeExecutor.kt` - prepends the
  same verified packet to local inference without changing the visible query.
- `app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/JarvisRuntimeSelfModel.kt` -
  truthful Alpha memory capability and remaining-limit description.
- `app/src/alpha/java/com/google/ai/edge/gallery/cortex/CortexRecallEngine.kt` - bounded selection,
  provenance rules, question/statement typing, explicit semantic links, and inert model packet.
- `app/src/alpha/java/com/google/ai/edge/gallery/cortex/AlphaCortexRuntime.kt` - verified readback,
  selection, receipt commit, indexing, status, and failure handling.
- `app/src/alpha/java/com/google/ai/edge/gallery/cortex/CortexVault.kt` - verified vault reads.
- `app/src/alpha/java/com/google/ai/edge/gallery/cortex/CortexIndexDatabase.kt` - prior-session query,
  retrieval receipts, counts, and non-destructive version-1-to-2 migration.
- `app/src/alpha/java/com/google/ai/edge/gallery/cortex/CortexMarkdownCodec.kt` - deterministic
  schema-11 retrieval receipts.
- `app/src/alpha/java/com/google/ai/edge/gallery/ui/home/CortexSettingsSection.kt` - automatic-recall
  explanation and verified recall count.
- `app/src/testAlpha/java/com/google/ai/edge/gallery/cortex/CortexRecallEngineTest.kt` - 11 retrieval
  regressions, including buried Greenwood linkage, provenance, bounds, question typing, and the
  `live` location-polysemy privacy case.
- `app/src/androidTestAlpha/java/com/google/ai/edge/gallery/cortex/AlphaCortexDeviceTest.kt` - local-only
  real-device capture, cross-session Greenwood recall, Markdown receipt, and reopened-index test.
- `app/src/test/java/com/google/ai/edge/gallery/customtasks/agentchat/JarvisRuntimeSelfModelTest.kt` -
  Alpha-enabled and Main-disabled memory truth.
- `docs/test-evidence/2026-08-09-alpha-recall-failure-before.png` - visible reproduced failure.
- `docs/test-evidence/2026-08-09-alpha-native-cortex-settings-final.png` - final installed Alpha
  Settings with selected vault, Markdown counts, and recall receipts.
- `docs/test-evidence/2026-08-09-alpha-broad-recall-failure.png` - exact user-visible failure for the
  production broad-recall prompt.
- `ECHO_BRIEF.md` and `ECHO_HANDOFF.md` - corrected architecture, evidence, limitations, and next work.

### Commands and evidence

- `gradlew.bat --no-daemon testAlphaUnitTest assembleAlpha assembleAlphaAndroidTest` passed after
  the broad-recall changes: 30 Alpha unit tests, zero failures.
- `gradlew.bat --no-daemon testDebugUnitTest assembleDebug` passed: shared Main chat code and the
  protected no-op Cortex binding still compile and pass their existing tests.
- Final `app-alpha.apk` update-install returned `Success` without clearing app data. The encrypted
  OpenAI credential preference and selected-vault preference remained present.
- Manual instrumentation on phone `R5CX31PBW7V` passed `AlphaCortexDeviceTest` 1/1. It used a
  disposable cache-rooted context, captured exact Billy/Jarvis Markdown, recalled Greenwood from a
  different session through both direct location and exact `Hey Echo` broad recall, wrote verified
  retrieval receipts, and reopened the migrated index. Only the test-runner package was uninstalled.
- The final installed app cold-launched with no `AndroidRuntime` crash. The user-visible Settings
  screenshot shows `Sync/Billy Cortex`, 11 verified exchanges, 22 turn files, and 3 recall receipts.
- The first pre-final cloud selector was too broad: receipt
  `7ef91c1f-8ac6-4cb7-ae99-673de5518701` selected 7 artifacts, including Billy's interview. A later
  pre-final semantic-link selector incorrectly treated “Alpha live” as location and again included
  the interview. Those two OpenAI requests occurred; later code added smallest-context, explicit-cue,
  polysemy, and question-versus-statement regressions. This privacy incident is recorded explicitly.
- A final live cloud recall was not run. The safety gate requires Billy's explicit approval before
  transmitting any further recalled vault packet to OpenAI. Do not claim the final cloud response is
  verified; local selection/injection tests, device receipt tests, builds, launch, and Settings are verified.
- Repository credential scan found no OpenAI key or known key fragment in source/docs outside ignored
  build output. No plaintext key was printed or added.

### Not yet verified or implemented

- The final selector has not been accepted against Billy's real interview through OpenAI because
  explicit authorization to transmit that recalled personal packet is still required.
- This is not the complete ThreadKeeper 2.99 system. Native semantic records, durable checkpoints
  and open loops, general typed links/routes, activation/gravity, dynamic memory resolution,
  synthesis review, outcome learning, empathy/policy/routine modules, deletion/undo, and governance
  remain unimplemented.
- Current semantic question-to-answer links cover explicit location and project intents only. Other
  concepts use exact lexical evidence; explicit broad recall uses deterministic durable-statement
  typing until the native semantic-record layer exists.
- No embeddings, HipoRAG/LiteRAG/RAPTOR layers, Matryoshka vectors, summarization hierarchy, or
  second-model encoder exists yet.
- No user-facing cloud-memory consent/preview control exists yet.

### Questions requiring Echo's architectural judgment

- Should automatic cloud recall use a standing per-provider consent, a per-turn preview, or a local
  redaction/sensitivity gate before any vault content leaves the phone?
- Should the next native record layer extract one atomic Markdown fact per claim while retaining an
  exact pointer to the source turn, or introduce checkpoint/open-loop records first?
- Which additional semantic link categories are safe to add deterministically before the planned
  on-device encoder is available?

### Recommended next step

Add native atomic semantic records and checkpoint/open-loop notes derived from exact source turns,
with source pointers and reviewable Markdown. Then add a visible cloud-memory consent/redaction
boundary before expanding retrieval beyond the current conservative location/project links.

## Prior milestone: isolated Jarvis Alpha native Cortex

### Scope completed

- Added a separately installable `Jarvis Alpha` build with application ID
  `com.google.aiedge.gallery.alpha`, version suffix `-alpha`, separate deep-link/auth schemes, debug
  signing, separate UID, and independent app data.
- Kept the implementation checkpoint on branch `jarvis-alpha-native-cortex`; `main` was not advanced.
- Preserved Main's `com.google.aiedge.gallery` identity and bound Main/release to an inert Cortex
  implementation. No Main APK was reinstalled and no Main app data was copied, cleared, or migrated.
- Added a typed app-level `CortexRuntime` boundary and an Agent Chat completion hook that runs only
  after Jarvis's final streamed response is assembled.
- Implemented Alpha-only Kotlin storage with two separate exact Markdown turn artifacts per exchange,
  ThreadKeeper provenance, UTF-8 byte counts, content/document SHA-256 verification, a receipt written
  last, and a rebuildable SQLite index.
- Added an Alpha Settings section that shows verified counts, lets Billy choose an Android document
  tree as the Obsidian-compatible vault, can return to the private vault, and imports a selected
  ThreadKeeper schema-11 copy.
- Implemented read-only, SHA-256-idempotent schema-11 copy import. It rejects likely embedded
  credentials, archives exact source bytes, creates Markdown snapshots for every top-level collection,
  writes a verified receipt, and never reads Main's WebView database automatically.
- Preserved the existing separate System Instructions and Personality Prompt editors and their
  task/system/personality composition order in Alpha.
- Fixed the OpenAI lifecycle gap exposed by the first real Alpha walkthrough: cloud-session
  configuration no longer returns early when Settings has no key yet. A key saved later is read on
  the next turn without requiring the user to restart or reselect the model.
- Added screenshot-based acceptance evidence for the user-visible key state, successful cold-launch
  OpenAI reply, and selected-vault memory counts.

### Exact files changed

- `app/build.gradle.kts` - defines the Alpha identity and Alpha instrumentation test target.
- `app/src/main/AndroidManifest.xml` - makes the existing deep-link scheme variant-specific.
- `app/src/main/java/com/google/ai/edge/gallery/cortex/CortexRuntime.kt` - typed shared boundary,
  capture request/receipt types, provenance enum, and Main-safe no-op implementation.
- `app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatViewModel.kt` - protected post-response
  completion seam after the terminal stream event.
- `app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/AgentChatViewModel.kt` - captures
  Billy's last exact user turn and Jarvis's completed agent response through `CortexRuntime`.
- `app/src/main/java/com/google/ai/edge/gallery/ui/home/SettingsDialog.kt` - invokes the
  build-specific Cortex settings section.
- `app/src/debug/.../CortexRuntimeModule.kt` and `app/src/release/.../CortexRuntimeModule.kt` - bind
  Main/release to `NoOpCortexRuntime`.
- `app/src/debug/.../CortexSettingsSection.kt` and `app/src/release/.../CortexSettingsSection.kt` -
  omit experimental controls outside Alpha.
- `app/src/alpha/res/values/strings.xml` - gives Alpha its distinct launcher name.
- `app/src/alpha/.../AlphaCortexRuntime.kt` - coordinates exact exchange capture, receipts, status,
  vault selection, and schema-11 copy import.
- `app/src/alpha/.../CortexMarkdownCodec.kt` - deterministic Markdown envelopes and SHA-256 helpers.
- `app/src/alpha/.../CortexVault.kt` - verified private atomic writes and selected-folder readback writes.
- `app/src/alpha/.../CortexIndexDatabase.kt` - native rebuildable SQLite exchange/import index.
- `app/src/alpha/.../Schema11Importer.kt` - strict schema validation, wrapper handling, size limit,
  collection inventory, and likely-secret rejection.
- `app/src/alpha/.../CortexRuntimeModule.kt` - Alpha-only Hilt binding to the native runtime.
- `app/src/alpha/.../CortexSettingsSection.kt` - Alpha vault picker, status, and import UI.
- `app/src/testAlpha/.../CortexMarkdownCodecTest.kt` - exact UTF-8 and schema-11 validation tests.
- `app/src/androidTestAlpha/.../AlphaCortexDeviceTest.kt` - real-device exact pair, receipt, and SQLite
  reopen verification in disposable cache rather than Billy's vault.
- `app/src/main/java/com/google/ai/edge/gallery/agent/OpenAiAgentRuntimeExecutor.kt` - always prepares
  the cloud session before a key exists while continuing to read the latest encrypted key per turn.
- `app/src/androidTestAlpha/.../OpenAiInitializationDeviceTest.kt` - regression coverage for entering
  Settings without a key and adding the credential afterward.
- `docs/test-evidence/2026-08-09-alpha-live-openai.png` - visible successful live provider reply.
- `docs/test-evidence/2026-08-09-alpha-settings-key.png` - visible `API key saved` state without key
  material.
- `docs/test-evidence/2026-08-09-alpha-cortex-vault.png` - visible selected vault and verified counts.
- `ECHO_BRIEF.md` and `ECHO_HANDOFF.md` - record the boundary, decisions, evidence, and remaining work.

### Commands and evidence

- Untouched baseline: `gradlew.bat testDebugUnitTest assembleDebug` exited 0.
- Reference artifact: phone `/sdcard/Download/TK-2.99.zip` was pulled read-only to a temporary folder;
  SHA-256 matched the value in `ECHO_BRIEF.md`; its runtime test passed 40/40.
- Alpha shell before Cortex: `gradlew.bat assembleAlpha` returned `BUILD SUCCESSFUL`.
- Full variants: `gradlew.bat testDebugUnitTest assembleDebug testAlphaUnitTest assembleAlpha`
  returned `BUILD SUCCESSFUL`.
- Alpha device test: `gradlew.bat connectedAlphaAndroidTest` finished 1/1 tests and returned
  `BUILD SUCCESSFUL`. It verified two distinct exact Markdown turn artifacts, provenance, receipt,
  counts, and a reopened SQLite handle in a disposable cache-rooted context.
- Installing `app/build/outputs/apk/alpha/app-alpha.apk` on device `R5CX31PBW7V` returned `Success`.
- Device package checks showed both `com.google.aiedge.gallery` and
  `com.google.aiedge.gallery.alpha` installed at distinct APK/data paths.
- A clean Alpha launch returned `Status: ok`, `LaunchState: COLD`, PID 4984, and
  `topResumedActivity` for the Alpha package with data directory
  `/data/user/0/com.google.aiedge.gallery.alpha`. The crash buffer was empty.
- After the connected test removed its target APK, the final verified APK was reinstalled. Its final
  cold launch returned `Status: ok` with PID 13684; both Main and Alpha packages were listed, and
  Alpha's real `files/cortex-vault` did not exist yet, confirming no fake test memory was left behind.
- Visible Alpha Settings showed `App version: 1.0.17-alpha (38)`, Personality Prompt,
  `Cortex / ThreadKeeper vault (Alpha)`, the private vault label, zero real-vault counts, folder
  selection, and schema-11 import controls.
- Reproduced the real failure in a screenshot: a saved Alpha key followed by a chat turn displayed
  `OpenAI is not initialized.` Logs confirmed this occurred before an OpenAI HTTP response.
- Final clean command `gradlew.bat testDebugUnitTest testAlphaUnitTest assembleAlpha
  assembleAlphaAndroidTest` returned `BUILD SUCCESSFUL`; the two intended device tests then passed
  2/2 through a manual instrumentation install that preserved Alpha app data.
- A final cold launch visibly showed `API key saved`; the live prompt `Reply with exactly Alpha live
  test passed` received `Alpha live test passed` in 2.3 seconds.
- Alpha wrote the exact prompt and exact Jarvis reply as separate Markdown documents plus a verified
  receipt under the selected `Sync/Billy Cortex/Jarvis Alpha Cortex` tree. Settings visibly reported
  `1 verified exchanges · 2 turn files · 0 imports` after restart.
- One automated coordinate initially selected the `Schedule Reminder` sample because screenshot and
  device-coordinate resolutions differed. The exact accidental 9:00 AM reminder, alarm, three vault
  documents, and index row were identified and removed. The cleanup refused to act until artifact
  contents matched; the selected vault now contains only the labeled successful test exchange.

### Not yet verified or implemented

- No actual ThreadKeeper database export was supplied; only the 2.99 implementation ZIP was supplied.
  Import validation is unit-tested, but no user memory was imported.
- Retrieval, linking, summarization, graph gravity, embeddings, Matryoshka vector levels of detail,
  pre-turn memory gates, and automatic synthesis are not yet implemented.
- Native terminal/ADB capability tokens, screenshots as next observations, scheduling, rollback UI,
  and Alpha-to-Main promotion tooling are not implemented.

### Questions requiring Echo's architectural judgment

- Should the next slice prioritize pre-turn retrieval over decomposing imported collection snapshots
  into one Markdown note per semantic memory?
- Which memory types may be recorded automatically under a revocable standing grant, and which need
  per-write confirmation?
- When Billy explicitly authorizes a future promotion, should Main adopt Alpha's package identity or
  receive the tested features through a controlled source merge while retaining its existing data UID?

### Recommended next step

Implement the provider-neutral pre-turn retrieval gate over the native Markdown and SQLite boundary,
with screenshot-based user-flow acceptance and exact-vault verification before adding vector or
graph-derived indexes. Import an actual ThreadKeeper schema-11 database copy only when Billy supplies
one and explicitly selects it in Alpha.

## Prior milestone: OpenAI cloud Skill/MCP execution

### Scope completed

- Connected OpenAI Responses function calls to the existing Android Jarvis Skill/MCP dispatcher used by local agent models.
- Converted LiteRT tool descriptions into the flattened Responses function-tool format without duplicating tool definitions.
- Preserved response output items and matched each executed result to its `call_id` through `function_call_output` before the next model step.
- Forced sequential tool calls for stable Android permission and WebView interaction, and bounded each turn to 12 model/tool rounds.
- Rolled back all conversation items added by a failed or cancelled turn.
- Updated Jarvis's authoritative runtime self-model so GPT-5.6 truthfully knows it can execute enabled Skills and MCP tools.
- Added default automatic-memory behavior: use an enabled memory skill for useful pre-answer continuity and durable post-turn facts without waiting for a “remember” command, while excluding secrets, transient details, and uncertain inference.
- Replaced the invalid on-device OpenAI credential through the encrypted Settings flow. No key material was added to the repository or logs.
- Updated `jarvis-system-core` status and architecture mapping so they no longer describe the cloud tool loop as future work.

### Exact files changed

- `app/src/main/java/com/google/ai/edge/gallery/agent/OpenAiApiClient.kt` — adds mixed Responses input items, tool-schema adaptation, sequential-tool request settings, output-item streaming, and structured response results.
- `app/src/main/java/com/google/ai/edge/gallery/agent/OpenAiAgentRuntimeExecutor.kt` — adds the bounded model/tool loop, Android dispatcher integration, tool-result replay, full output-item conversation state, and failed-turn rollback.
- `app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/AgentChatTaskModule.kt` — instructs Jarvis to retrieve and record memory automatically when an enabled memory skill exists, with privacy boundaries.
- `app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/JarvisRuntimeSelfModel.kt` — reports the real OpenAI Skill/MCP execution capability and current memory boundary.
- `app/src/main/assets/skills/jarvis-system-core/scripts/index.js` — marks the shared tool execution loop as runtime-ready.
- `app/src/main/assets/skills/jarvis-system-core/references/openjarvis-mapping.md` — records the tool-loop milestone as complete.
- `app/src/test/java/com/google/ai/edge/gallery/common/OpenAiApiClientTest.kt` — verifies request privacy, mixed inputs, flattened tools, sequential execution, function outputs, and streamed function-call items.
- `app/src/test/java/com/google/ai/edge/gallery/customtasks/agentchat/JarvisRuntimeSelfModelTest.kt` — verifies truthful OpenAI execution metadata.
- `app/src/test/java/com/google/ai/edge/gallery/customtasks/agentchat/AgentChatTaskModuleTest.kt` — verifies automatic-memory and secret-exclusion instructions.
- `ECHO_BRIEF.md` — records the permanent protocol, safety, and automatic-memory decisions plus device evidence.
- `ECHO_HANDOFF.md` — records this implementation and verification.

### Commands and evidence

- Focused OpenAI request/parser, self-model, and prompt tests returned `BUILD SUCCESSFUL`.
- The required `gradlew.bat assembleDebug` returned `BUILD SUCCESSFUL`.
- Installing `app/build/outputs/apk/debug/app-debug.apk` on USB device `R5CX31PBW7V` returned `Success`.
- Launching `com.google.aiedge.gallery/com.google.ai.edge.gallery.MainActivity` produced a live process and no `FATAL EXCEPTION`; only device/driver warnings for deprecated ashmem and unavailable vendor libraries appeared.
- The visible Jarvis chat loaded with `OpenAI · GPT-5.6 Medium`, the Back button opened the Android Jarvis control center, and Settings reported the encrypted API key saved.
- After the final APK installation, a force-stop and cold relaunch kept the key saved, confirming encrypted credential persistence across process death.
- The Skills manager showed `jarvis-system-core` installed and enabled.
- A live GPT-5.6 Medium request returned `Called JS script "jarvis-system-core/index.html"`, followed by `Jarvis System Core status executed successfully` and the skill's module report. This confirms the new key, API model access, function calls, Android tool dispatch, JavaScript skill execution, tool-result replay, and final response.
- A second live call after the final cold restart again executed the skill and reported `tool_execution_loop state: runtime_ready` with surface `openai_and_local`, confirming the replacement key persisted and the corrected built-in asset was installed.

### Unresolved problems

- Automatic memory currently depends on the model selecting an enabled memory skill. A native pre-turn retrieval hook and post-turn durable extraction hook do not exist yet.
- ThreadKeeper/persona memory remains skill/WebView based and is not yet the canonical Markdown/Obsidian vault.
- Durable native traces, background indexing, semantic retrieval, scheduling, proactive agents, and reviewed self-improvement are not implemented.
- OpenAI raw audio input is still unsupported; voice-to-text and duplex voice remain future provider-neutral capabilities.
- Tool failures are returned to the model as a deliberately generic error to avoid leaking sensitive exception data; richer safe receipts still need a typed error contract.

### Questions requiring Echo's architectural judgment

- Choose the user-visible name for the native memory organ and its Obsidian vault.
- Decide whether canonical memory should use one atomic Markdown file per item, time/topic groupings, or atomic notes plus generated maps.
- Decide which low-risk memory writes may use a standing revocable grant and which always require confirmation.

### Recommended next step

Add a provider-neutral native memory lifecycle around every model turn: pre-turn retrieval gates, post-turn durable-fact extraction, provenance receipts, Markdown canonical storage, and rebuildable graph/vector indexes. Start with a small atomic-note schema and import useful ThreadKeeper records before adding semantic lattice gravity or Matryoshka-vector levels of detail.
