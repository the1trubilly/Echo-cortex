---
name: jarvis-system-core
description: Plan and inspect multi-step Jarvis work using an OpenJarvis-inspired typed workflow, capability gates, traces, and reflection. This skill plans work but does not execute other tools.
---

# Jarvis System Core

Use this skill when a request benefits from deliberate planning, capability checks, trace inspection, or a reusable Markdown handoff.

This is the first skill-level prototype of Android Jarvis's orchestration layer. It is deliberately stateless and cannot invoke another skill, use a device capability, write long-term memory, or modify the app by itself. Never report a planned step as completed.

## Actions

Call the `run_js` tool with script name `index.html` and a JSON string for `data`.

### 1. Inspect module status

```json
{"action":"status"}
```

### 2. Create a typed plan

```json
{
  "action": "plan",
  "goal": "The user's goal",
  "provider": "openai or local",
  "model": "Exact model identifier when known",
  "runtime": "Runtime/executor name when known",
  "available_tools": ["enabled-tool-name"],
  "requested_capabilities": ["memory:read", "network:fetch"],
  "constraints": ["Relevant constraint"],
  "memory_context": ["Short, non-sensitive retrieved-memory summary"]
}
```

Only list tools that the current runtime actually exposes. If no tools are available, pass an empty array. The result is a plan, not evidence of execution.

### 3. Add an observed step to a trace

```json
{
  "action": "observe",
  "trace": {"trace_id":"trace-id","query":"Short goal","steps":[]},
  "step": {
    "step_type": "tool_call",
    "success": true,
    "input": {"tool":"tool-name"},
    "output": {"summary":"Short result backed by the tool response"},
    "metadata": {"duration_ms":120}
  }
}
```

Allowed step types are `route`, `retrieve`, `generate`, `tool_call`, and `respond`. Add a step only after a real model, memory, or tool result exists. Do not pass credentials, authorization headers, private files, or full conversation transcripts.

### 4. Reflect on a trace

```json
{
  "action": "reflect",
  "trace": {"trace_id":"trace-id","query":"Short goal","steps":[]},
  "outcome": "success, partial, or failure",
  "feedback": 0.8
}
```

Reflection may recommend a later improvement, but it cannot install, grant, or apply one. Present proposals as proposals requiring normal user review.

### 5. Export a Markdown handoff

```json
{
  "action": "export",
  "plan": {"Use the exact plan object returned by the plan action":"here"},
  "trace": {"Use the current trace object":"here"},
  "reflection": {"Use the reflection object":"here"}
}
```

The result contains a `markdown` field suitable for an Obsidian note. The skill only returns the text; it does not write a file.

## Safety rules

- Treat `file:write`, `code:execute`, `memory:write`, `channel:send`, `schedule:create`, `system:admin`, and unknown capabilities as requiring explicit approval.
- Treat capability approval as scoped to one proposed operation, never as a blanket permission.
- Do not put secrets or raw sensitive content into a trace or export.
- Do not claim that another tool ran unless its result was observed and recorded.
- Do not claim durable memory, learning, scheduling, or self-modification; those remain app-level work.
