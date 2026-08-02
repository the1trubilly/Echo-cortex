# Android Jarvis — Echo Handoff

## Current milestone: OpenAI cloud Skill/MCP execution

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
