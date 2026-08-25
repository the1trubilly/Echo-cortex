# Android Jarvis — Echo Brief

## Current milestone

Jarvis Alpha has a working native Cortex memory path. A fresh chat can recall verified facts from
older conversations and can assemble a bounded, multi-memory synthesis packet. The user-visible
acceptance tests passed on the connected phone on 2026-08-24.

This is the first working skeleton of ThreadKeeper 4.0.1's normal hot path, not the complete
schema-13 cognitive engine.

## Permanent architecture decisions

- Canonical memory is Markdown in an Obsidian-compatible vault chosen in Settings.
- Billy's exact messages and Jarvis's completed replies are separate artifacts with explicit
  provenance. Jarvis wording is never silently promoted into evidence about Billy.
- SQLite is a disposable, rebuildable local index. It is never the canonical source of truth.
- Every stored document is hash-verified before it can enter model context.
- Retrieval is app-level infrastructure shared by model executors, not a prompt-only skill.
- Alpha is the development organism; Main remains independently installable.
- Normal recall is automatic. Billy should not need to issue save/recall commands.
- Retrieval stays bounded: at most 64 local candidates, 8 surfaced artifacts, and 5,200 rendered
  context characters.
- Exact source remains epistemically authoritative. Search score, recency, geometry, activation,
  and future memory physics may route attention but may not manufacture truth.
- Legacy-index maintenance is incremental and non-destructive. It verifies old Markdown and
  rebuilds derived metadata in the background; it does not rewrite canonical memories.

## Native recall skeleton now present

1. Exact Markdown capture for both sides of each completed exchange.
2. Verified receipts for capture, retrieval, and schema-11 import copies.
3. Whole-vault FTS index with a versioned, background rebuild path.
4. Three explicit recall intents:
   - focused lookup;
   - broad personal recall;
   - cross-memory synthesis.
5. Cue-by-cue candidate gathering so a common term cannot crowd rarer concepts out of the local
   field.
6. Associative expansion through neighboring artifacts from matched sessions.
7. Provenance-aware ranking, durable-personal gravity, recency, session diversity, and
   near-duplicate suppression.
8. Bounded detail rendering with `EXACT` and `EXACT_EXCERPT` levels. Excerpts contain only exact
   source text plus an explicit omission marker; no summary is fabricated.
9. A model-facing memory packet that labels source, detail level, reason surfaced, and the boundary
   between remembered evidence and inference.

## Non-negotiable product requirements

- Jarvis must remember without Billy manually telling it when to save or recall.
- It must support rich synthesis across many memories, not only isolated fact lookup.
- Memories remain human-readable Markdown and usable as an Obsidian vault.
- System Instructions and Personality Prompt remain separate, editable, and persistent.
- The unified chat remains the main interface for text, tools, images, speech, and future agents.
- Self-ADB and Code on the Go are autonomy foundations; future app-editing capabilities should use
  natural language and explicit approval modes.
- Testing must include what the user actually sees on the phone. Database state and passing tests
  are supporting evidence, not acceptance by themselves.

## Relevant repository and runtime discoveries

- Git branch: `jarvis-alpha-native-cortex`.
- Alpha package: `com.google.aiedge.gallery.alpha`.
- Connected test device during this milestone: `10.0.0.33:41733`.
- The live selected vault was `Sync/Billy Cortex/Jarvis Alpha Cortex` and contained older exact
  interview memories that were healthy but previously unreachable due bounded recency-first
  candidate selection.
- The real interview artifact was present and verified. The failure was retrieval routing, not
  missing storage.
- ThreadKeeper 4.0.1 reference runtime passed 56/56 tests before native work began.
- ThreadKeeper 4.0.1's important invariants remain the guide: store generously, retrieve
  ruthlessly; keep library, keep map, move flashlight; direct retrieval, spread, bounded candidate
  field, memory physics, then DMR.

## Next architectural milestone

Turn the working recall skeleton into explicit schema-13 native modules:

1. Versioned Markdown sidecars and idempotent migration receipts for old/unstructured material.
2. Typed concepts and links with provenance, temporal validity, confidence, and contradiction
   handling.
3. Bounded associative spread and memory-physics ticks over the local candidate field.
4. Durable multi-resolution summaries/embeddings while retaining exact Markdown as truth.
5. DMR packet assembly with auditable detail-level selection.
6. Lifecycle, reconsolidation, open loops, policies, routines, outcomes, and health/recovery UI.

Do not attempt all of these in one unverified rewrite. Preserve the now-working user-visible recall
path after every stage.
