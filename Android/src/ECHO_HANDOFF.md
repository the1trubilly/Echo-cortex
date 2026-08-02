# Android Jarvis — Echo Handoff

## Current milestone: OpenJarvis-inspired system core skill

### Scope completed

- Added a built-in `jarvis-system-core` skill as the first working orchestration slice.
- Modeled OpenJarvis's five primitives: Intelligence, Engine, Agents, Tools & Memory, and Learning.
- Added deterministic `status`, `plan`, `observe`, `reflect`, and `export` actions.
- Added phased workflow DAGs, capability classification, explicit approval flags, typed trace steps, bounded observations, credential-shaped value redaction, proposal-only reflection, and Markdown/Obsidian export.
- Kept the prototype stateless and honest: it cannot call another tool, grant a capability, persist memory, schedule work, or modify Android Jarvis.
- Added an architecture mapping that separates skill-level prototypes from the native runtime, memory, scheduler, and self-improvement work still required.
- Made the built-in skill opt-in so upgrading the app does not silently change the active agent prompt or exceed the existing enabled-skill set.

### Exact files changed

- `app/src/main/assets/skills/jarvis-system-core/SKILL.md` — defines when and how the agent may use each orchestration action and states the safety boundaries.
- `app/src/main/assets/skills/jarvis-system-core/scripts/index.html` — provides the existing `run_js` WebView entry point.
- `app/src/main/assets/skills/jarvis-system-core/scripts/index.js` — implements typed planning, capability review, evidence traces, reflection, redaction, and Markdown export.
- `app/src/main/assets/skills/jarvis-system-core/references/openjarvis-mapping.md` — maps the OpenJarvis primitives and future module sequence onto Android Jarvis.
- `app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/SkillManagerViewModel.kt` — adds `jarvis-system-core` to the built-in skills that default to off.
- `ECHO_BRIEF.md` — records the durable OpenJarvis, memory-vault, evidence, and self-improvement architecture decisions.
- `ECHO_HANDOFF.md` — records this milestone and its verification evidence.

### Commands and evidence

- The JavaScript syntax check completed successfully.
- A direct contract test exercised all five actions. It reported 9 module statuses, a 5-node workflow, explicit approval for `memory:write` and an unknown capability, `[redacted]` for an API-key field, two reflection proposals for a failed trace, and a Markdown trace document.
- `gradlew.bat testDebugUnitTest assembleDebug` returned `BUILD SUCCESSFUL` in 15 seconds; 64 tasks ran or were up to date.
- Installing `app/build/outputs/apk/debug/app-debug.apk` on USB device `R5CX31PBW7V` returned `Success`.
- A cold launch resumed `com.google.ai.edge.gallery/.MainActivity`; the filtered `AndroidRuntime` startup-crash log was empty.
- Agent Skills initialized the downloaded `Gemma-4-E2B-it` model and enabled its Skills control.
- The installed Skills manager reported 19 total skills. The chat badge reported 14 enabled skills, confirming the new opt-in built-in was discovered without silently adding it to the enabled set.

### Unresolved problems

- The current OpenAI executor still lacks the Responses API tool-call round trip, so GPT-5.6 cannot invoke this or other Skills yet.
- The skill plans tool use but cannot orchestrate tool calls. A native provider-neutral agent loop must own execution and feed confirmed results into traces.
- Durable traces, sessions, scheduling, background work, and capability enforcement are not implemented by this skill.
- ThreadKeeper remains WebView/localStorage based and does not yet provide isolated native storage, semantic retrieval, or canonical Markdown files.
- The memory-nanite design still needs an explicit Markdown schema, file layout, provenance/receipt rules, typed link ontology, retrieval-gate contract, and derived-index lifecycle before native implementation.
- No persistent learning or self-modification is active. Reflection is intentionally proposal-only.

### Questions requiring Echo's architectural judgment

- Choose the user-visible name for the native memory organ and its Obsidian vault (for example Echo Cortex, Jarvis Memory, or another name).
- Decide whether each memory item should be one Markdown file, whether files should group by topic/time, or whether both should coexist through canonical atomic notes plus generated maps.
- Decide which write effects always require confirmation and which low-risk memory writes may later use a standing, revocable grant.

### Recommended next step

Implement the provider-neutral native tool-call loop, starting with OpenAI Responses function calls mapped into the existing Skills/MCP tool executor. Wrap every route, retrieval, generation, tool call, and response in a trace event. That makes `jarvis-system-core` usable from GPT-5.6 and creates the evidence spine needed before native memory or self-improvement.
