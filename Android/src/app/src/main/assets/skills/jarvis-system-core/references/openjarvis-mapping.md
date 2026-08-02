# OpenJarvis-to-Android-Jarvis mapping

This skill is an original, minimal prototype informed by the public OpenJarvis architecture. It does not embed or run the OpenJarvis Python framework.

| OpenJarvis concept | Skill prototype | Native Android destination |
| --- | --- | --- |
| Intelligence | Provider and exact model fields in the typed plan | Existing provider-neutral model picker and runtime router |
| Engine | Runtime identity in the typed plan | `AgentRuntimeExecutor` and provider implementations |
| Agents | Simple/tool-aware strategy plus phased decomposition | A native orchestrator around the unified chat session |
| Tools & Memory | Available-tool inventory, capability checks, supplied memory summaries | Tool-call loop, native capability broker, ThreadKeeper migration, Markdown vault, derived retrieval indexes |
| Learning | Typed traces, feedback, and proposal-only reflection | Durable trace store, evaluations, approval-gated improvement pipeline |

## Deliberate boundaries

- The skill does not call other tools. Cross-tool orchestration belongs in the agent runtime.
- The skill is stateless. It never stores credentials, chats, traces, or memory in WebView `localStorage`.
- The Markdown export is returned to the caller rather than written to disk.
- A proposed capability is not a permission grant.
- A reflection is not an automatically applied self-modification.

## Planned sequence

1. Implement the OpenAI Responses tool-call loop so cloud and local models share one capability contract.
2. Add a native trace collector around model, retrieval, tool, and response events.
3. Lift ThreadKeeper's useful record/link/checkpoint concepts into a native, app-isolated memory service.
4. Make canonical memories Markdown files in an Obsidian-compatible vault; treat embeddings and indexes as rebuildable derivatives.
5. Add memory retrieval gates, link categories, activation/gravity scoring, summaries, and Matryoshka-vector levels of detail.
6. Add schedulers and proactive agents only after lifecycle, permission, notification, and battery behavior are explicit.
7. Add an approval-gated proposal/test/audit/rollback loop for skills and later app capabilities.
