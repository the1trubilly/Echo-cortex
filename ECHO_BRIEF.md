# Android Jarvis — Echo Brief

## Current milestone

Jarvis Alpha has a working native Cortex memory path. A fresh chat can recall verified facts from
older conversations and can assemble a bounded, multi-memory synthesis packet. The user-visible
acceptance tests passed on the connected phone on 2026-08-24.

This now includes a native schema-13 cognitive-field skeleton from ThreadKeeper 4.0.1: persistent
concept/link navigation, bounded associative expansion, memory physics, auditable memory handles,
safe exact/excerpt DMR, versioned normalization sidecars, and provisional temporal/correction
routes. It is not yet the complete cognitive engine.

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
  and memory physics may route attention but may not manufacture truth.
- Derived concepts and links have an `inform_only` authority ceiling. They remain unconfirmed,
  rebuildable index data and never become independent corroboration of a memory.
- Transformation cannot increase authority. Normalization may classify routing hooks but may not
  rewrite, summarize, confirm, or supersede exact source Markdown.
- Corrections preserve history. A newer explicit correction creates a `POSSIBLY_CORRECTS` route;
  both exact artifacts remain active until a separate evidence-backed adjudication is authorized.
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

## Native schema-13 cognitive field now present

1. SQLite index version 6 stores explicit origin, trust, authority, privacy, disclosure,
   quarantine, eligibility, memory-state, observed-time, and recorded-time metadata.
2. Captures and verified legacy Markdown are indexed into bounded lexical/phrase concepts.
3. Concept co-occurrence links form a persistent, rebuildable lattice. Links are marked
   `MODEL_INFERRED`, `UNCONFIRMED`, and `inform_only`.
4. Direct candidates expand through the persistent concept lattice before exact Markdown is read
   and hash-verified.
5. A 36-memory local field performs two-hop fanout-3 spread and three deterministic physics ticks
   using gravity, affinity, cohesion, repulsion, resonance, inertia, and decay.
6. Each surfaced memory includes activation, mass, force summary, navigation basis, nearest
   related memories, available detail levels, truth source, and epistemic boundary.
7. Schema-13 retrieval receipts record the intent, bounds, direct/spread counts, tick trace,
   degraded state, timing, selected LODs, and inform-only invariant.
8. The hot path reuses precomputed derived terms while still reading and hash-verifying exact
   Markdown before model injection. On Billy's live vault the verified synthesis cycle completed
   all three ticks in 40.56 ms without degradation.

## Native normalization and correction layer now present

1. Every new exact artifact receives a deterministic schema-13 Markdown normalization sidecar in
   the Obsidian vault. Existing verified turns migrate incrementally in bounded background batches.
2. Sidecars record the exact source IDs/hashes/location, normalizer version, observed/recorded
   time, statement type, temporal cue, modality, correction cue, concept hooks, projection hash,
   and the `inform_only`/`UNCONFIRMED` boundary.
3. Deterministic filenames plus replace-in-place vault writes and SQLite normalization receipts
   make migration idempotent without changing the canonical source.
4. Typed artifact relations include `SAME_EXCHANGE_CONTEXT`, `SAME_SESSION_CONTEXT`,
   `SEMANTIC_OVERLAP`, and `POSSIBLY_CORRECTS`, each with strength, confidence, exact evidence
   basis, independence state, confirmation status, and authority ceiling.
5. Correction pairs influence associative routing and repulsion but never adjudicate truth. The
   selector keeps both exact USER_STATED artifacts together so the model can explain what changed.
6. Background migration releases the mutation gate between eight-artifact batches; foreground
   recall repairs at most two artifacts, preventing maintenance from blocking the chat UI.
7. The live fresh-chat Blue Lantern to Green Lantern acceptance test retrieved both statements,
   identified the newer scoped correction, separated memory from inference, ran all three physics
   ticks in 51.51 ms, and did not degrade.

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

Build upward from the verified schema-13 hot path:

1. Add explicit conflict sets and preview/apply adjudication receipts so Billy can confirm,
   dispute, or supersede a provisional correction without deleting history.
2. Extend temporal metadata from wording cues to separately sourced event, observed, recorded,
   valid-from, and valid-to times; never infer causality from ordering alone.
3. Add richer evidence-backed semantic and causal link types plus worldline/exposure genealogy.
4. Durable multi-resolution summaries and Matryoshka embeddings while retaining exact Markdown as
   truth and preventing lossy summaries from becoming canonical.
5. Lifecycle and reconsolidation machinery: supersession, contradiction review, open loops,
   policies, routines, outcomes, and worldline/genealogy boundaries.
6. Health, recovery, graph inspection, and receipt inspection UI that stays understandable to a
   non-technical user.

Do not attempt all of these in one unverified rewrite. Preserve the now-working user-visible recall
path after every stage.
