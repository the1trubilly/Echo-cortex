# Android Jarvis — Echo Handoff

## Current milestone: isolated Jarvis Alpha native Cortex

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

### Not yet verified or implemented

- A live cloud-model exchange was not run in Alpha because its correctly isolated data contains no
  OpenAI API key. The post-turn hook compiles and the native persistence path is device-tested, but
  end-to-end capture from a real provider reply remains to be confirmed after Billy enters an Alpha key.
- A user-selected Storage Access Framework folder was not chosen on Billy's behalf. The chooser is
  visible and the provider write path is implemented, but only Alpha-private atomic writes were
  exercised by the automated device test.
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

Enter an OpenAI key in Alpha only, send one harmless labeled test exchange, verify the two resulting
Markdown files and receipt in the private vault, force-stop/relaunch Alpha, and verify the counts and
files persist. Then implement the provider-neutral pre-turn retrieval gate over the native Markdown
and SQLite boundary before adding vector or graph-derived indexes.

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
