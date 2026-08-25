/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.cortex

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.round
import kotlin.math.sqrt

internal data class CortexMemoryNeighbor(
  val artifactId: String,
  val title: String,
  val basis: String,
  val strength: Double,
)

internal data class CortexMemoryPhysicsProfile(
  val candidatePool: Int,
  val spreadDepth: Int,
  val fanout: Int,
  val ticks: Int,
  val outputMemories: Int,
  val modelContextChars: Int,
  val softBudgetMs: Long,
  val gravityGain: Double = 0.18,
  val affinityGain: Double = 0.28,
  val cohesionGain: Double = 0.20,
  val repulsionGain: Double = 0.34,
  val resonanceGain: Double = 0.26,
  val inertiaGain: Double = 0.10,
  val decay: Double = 0.72,
)

internal data class CortexMemoryPhysicsTick(
  val tick: Int,
  val top: List<Pair<String, Double>>,
)

internal data class CortexCognitiveFieldResult(
  val candidates: List<ReadableRecallCandidate>,
  val profile: CortexMemoryPhysicsProfile,
  val trace: List<CortexMemoryPhysicsTick>,
  val degraded: Boolean,
  val operationMs: Double,
  val directSeedCount: Int,
  val spreadCandidateCount: Int,
)

/**
 * ThreadKeeper 4.0's bounded hot path, adapted to verified Markdown artifacts.
 *
 * This is navigation math only. It cannot change source text, truth, provenance, authority,
 * privacy, permission, or confirmation status.
 */
internal class CortexCognitiveField {
  private var liveActivation: Map<String, Double> = emptyMap()

  fun activate(
    query: String,
    candidates: List<ReadableRecallCandidate>,
    intent: CortexRecallIntent,
    requestedArtifacts: Int,
    requestedContextChars: Int,
  ): CortexCognitiveFieldResult {
    val profile =
      CortexMemoryPhysicsProfile(
        candidatePool = DEFAULT_CANDIDATE_POOL.coerceAtMost(MAX_CANDIDATE_POOL),
        spreadDepth = DEFAULT_SPREAD_DEPTH,
        fanout = DEFAULT_FANOUT,
        ticks = DEFAULT_TICKS.coerceAtMost(MAX_TICKS),
        outputMemories = requestedArtifacts.coerceIn(1, MAX_OUTPUT_MEMORIES).coerceAtMost(5),
        modelContextChars =
          requestedContextChars
            .coerceIn(1_000, MAX_MODEL_CONTEXT_CHARS)
            .coerceAtMost(
              if (intent == CortexRecallIntent.SYNTHESIS) MAX_MODEL_CONTEXT_CHARS
              else DEFAULT_MODEL_CONTEXT_CHARS
            ),
        softBudgetMs = DEFAULT_SOFT_BUDGET_MS,
      )
    if (candidates.isEmpty()) {
      return CortexCognitiveFieldResult(
        candidates = emptyList(),
        profile = profile,
        trace = emptyList(),
        degraded = false,
        operationMs = 0.0,
        directSeedCount = 0,
        spreadCandidateCount = 0,
      )
    }

    val started = System.nanoTime()
    val queryTokens = CortexRecallEngine.normalizedQueryTerms(query)
    val nodes =
      candidates.distinctBy(ReadableRecallCandidate::artifactId).take(profile.candidatePool).map {
        candidate ->
        val tokens =
          candidate.indexedTerms.ifEmpty {
            CortexRecallEngine.normalizedQueryTerms(candidate.exactContent.navigationSlice())
          }
        val durable =
          if (candidate.sourceKind == CortexSourceKind.USER_STATED) {
            candidate.indexedDurablePersonalScore.takeIf { it >= 0 }
              ?: CortexRecallEngine.navigationMassScore(candidate.exactContent, candidate.sourceKind)
          } else {
            0
          }
        val resonance = overlapScore(queryTokens, tokens)
        val durableSeed =
          if (
            intent != CortexRecallIntent.FOCUSED &&
              candidate.sourceKind == CortexSourceKind.USER_STATED
          ) {
            (durable.toDouble() / 800.0).coerceIn(0.0, 1.0) * 0.70
          } else {
            0.0
          }
        Node(
          candidate = candidate,
          tokens = tokens,
          durableScore = durable,
          queryResonance = resonance,
          seed = max(resonance * 0.88, durableSeed).coerceIn(0.0, 1.0),
          correctionCue =
            candidate.correctionCue || CORRECTION_CUE.containsMatchIn(candidate.exactContent),
        )
      }
    val byId = nodes.associateBy { it.candidate.artifactId }
    val adjacency = buildAdjacency(nodes)
    nodes.forEach { node ->
      val degree = adjacency[node.candidate.artifactId].orEmpty().size.coerceAtMost(12)
      val durableMass = (node.durableScore.toDouble() / 800.0).coerceIn(0.0, 1.0)
      val sourceMass = if (node.candidate.sourceKind == CortexSourceKind.USER_STATED) 0.12 else 0.04
      node.mass =
        (0.30 + durableMass * 0.34 + sourceMass + sqrt(degree / 12.0) * 0.14)
          .coerceIn(0.0, 1.0)
      node.depth = if (node.durableScore > 0) 0.70 else 0.45
    }

    val activation =
      nodes
        .filter { node -> node.seed >= MIN_ACTIVATION }
        .associate { node -> node.candidate.artifactId to node.seed }
        .toMutableMap()
    val directSeedCount = activation.size
    val pathBasis = activation.keys.associateWith { "direct resonance" }.toMutableMap()
    var frontier = activation.toMap()
    for (hop in 1..profile.spreadDepth) {
      val next = mutableMapOf<String, Double>()
      frontier.forEach { (sourceId, sourceActivation) ->
        adjacency[sourceId]
          .orEmpty()
          .sortedByDescending(Relation::strength)
          .take(profile.fanout)
          .forEach { relation ->
            val decay = if (hop == 1) 1.0 else 0.68
            val propagated =
              sourceActivation * relation.strength * 0.72 * decay * DEFAULT_SPREAD_GAIN
            if (propagated >= MIN_ACTIVATION && propagated > (next[relation.targetId] ?: 0.0)) {
              next[relation.targetId] = propagated
              pathBasis[relation.targetId] =
                "associative spread hop $hop via ${relation.basis} from $sourceId"
            }
          }
      }
      val boundedNext = next.entries.sortedByDescending(Map.Entry<String, Double>::value).take(48)
      boundedNext.forEach { (id, value) -> activation[id] = max(activation[id] ?: 0.0, value) }
      frontier = boundedNext.associate { it.key to it.value }
    }

    val pool =
      nodes
        .sortedWith(
          compareByDescending<Node> { activation[it.candidate.artifactId] ?: 0.0 }
            .thenByDescending(Node::mass)
            .thenByDescending { it.candidate.capturedAtEpochMs }
        )
        .take(profile.candidatePool)
    val poolById = pool.associateBy { it.candidate.artifactId }
    var fieldActivation =
      pool.associate { node ->
        node.candidate.artifactId to (activation[node.candidate.artifactId] ?: 0.0)
      }
    val trace = mutableListOf<CortexMemoryPhysicsTick>()
    var lastForces = emptyMap<String, Forces>()
    var degraded = false
    for (tickIndex in 0 until profile.ticks) {
      // Always run one useful force-integration tick. Later ticks honor the soft latency budget.
      if (tickIndex > 0 && elapsedMs(started) > profile.softBudgetMs) {
        degraded = true
        break
      }
      val next = mutableMapOf<String, Double>()
      val forcesById = mutableMapOf<String, Forces>()
      pool.forEach { node ->
        val id = node.candidate.artifactId
        val old = fieldActivation[id] ?: 0.0
        var gravity = 0.0
        var affinity = 0.0
        var cohesion = 0.0
        var repulsion = 0.0
        adjacency[id].orEmpty().forEach { relation ->
          val otherNode = poolById[relation.targetId] ?: return@forEach
          val other = fieldActivation[relation.targetId] ?: 0.0
          if (other < MIN_ACTIVATION) return@forEach
          gravity +=
            other * otherNode.mass * relation.strength * profile.gravityGain * 0.22
          if (relation.signedStrength >= 0.0) {
            affinity += other * relation.signedStrength * profile.affinityGain * 0.30
          } else {
            repulsion += other * abs(relation.signedStrength) * profile.repulsionGain * 0.36
          }
          cohesion += other * profile.cohesionGain * relation.cohesion
        }
        val resonance = node.queryResonance * profile.resonanceGain
        val inertia =
          (liveActivation[id] ?: 0.0) * profile.inertiaGain *
            (0.30 + (liveActivation[id] ?: 0.0) * 0.30)
        val depthHold = node.depth * old * 0.10
        val seed = activation[id] ?: 0.0
        val value =
          (old * profile.decay +
              seed * 0.26 +
              gravity +
              affinity +
              cohesion +
              resonance +
              inertia +
              depthHold -
              repulsion)
            .coerceIn(0.0, 1.0)
        next[id] = value
        forcesById[id] = Forces(gravity, affinity, cohesion, repulsion, resonance, inertia)
      }
      fieldActivation = next
      lastForces = forcesById
      trace +=
        CortexMemoryPhysicsTick(
          tick = tickIndex + 1,
          top =
            fieldActivation.entries
              .sortedByDescending(Map.Entry<String, Double>::value)
              .take(6)
              .map { entry -> entry.key to entry.value.rounded() },
        )
    }

    val rankedNodes =
      pool.sortedWith(
        compareByDescending<Node> { fieldActivation[it.candidate.artifactId] ?: 0.0 }
          .thenByDescending(Node::mass)
      )
    val rankedCandidates =
      rankedNodes.map { node ->
        val id = node.candidate.artifactId
        val forces = lastForces[id] ?: Forces()
        val neighbors =
          adjacency[id]
            .orEmpty()
            .mapNotNull { relation ->
              val other = byId[relation.targetId] ?: return@mapNotNull null
              val strength =
                relation.strength * (fieldActivation[relation.targetId] ?: 0.0).coerceAtLeast(0.1)
              CortexMemoryNeighbor(
                artifactId = relation.targetId,
                title = other.candidate.exactContent.lineSequence().firstOrNull().orEmpty().take(120),
                basis = relation.basis,
                strength = strength.rounded(),
              )
            }
            .sortedByDescending(CortexMemoryNeighbor::strength)
            .take(3)
        node.candidate.copy(
          physicsActivation = (fieldActivation[id] ?: 0.0).rounded(),
          physicsMass = node.mass.rounded(),
          navigationBasis = pathBasis[id] ?: "local memory-physics field",
          forceSummary = forces.summary(),
          nearestRelated = neighbors,
        )
      }
    liveActivation = fieldActivation
    return CortexCognitiveFieldResult(
      candidates = rankedCandidates,
      profile = profile,
      trace = trace,
      degraded = degraded,
      operationMs = elapsedMs(started).rounded(2),
      directSeedCount = directSeedCount,
      spreadCandidateCount = activation.size,
    )
  }

  private fun buildAdjacency(nodes: List<Node>): Map<String, List<Relation>> {
    val adjacency = nodes.associate { it.candidate.artifactId to mutableListOf<Relation>() }
    for (leftIndex in nodes.indices) {
      for (rightIndex in leftIndex + 1 until nodes.size) {
        val left = nodes[leftIndex]
        val right = nodes[rightIndex]
        val semantic = overlapScore(left.tokens, right.tokens)
        val sharedExchange = left.candidate.exchangeId == right.candidate.exchangeId
        val sharedSession = left.candidate.sessionId == right.candidate.sessionId
        val days =
          abs(left.candidate.capturedAtEpochMs - right.candidate.capturedAtEpochMs) / 86_400_000.0
        val temporal = when {
          days <= 3.0 -> 0.55
          days <= 14.0 -> 0.38
          days <= 45.0 -> 0.20
          else -> 0.0
        }
        val typedRelation =
          left.candidate.typedRelations
            .filter { relation -> relation.otherArtifactId == right.candidate.artifactId }
            .maxByOrNull(CortexTypedRelation::strength)
            ?: right.candidate.typedRelations
              .filter { relation -> relation.otherArtifactId == left.candidate.artifactId }
              .maxByOrNull(CortexTypedRelation::strength)
        val typedCorrection = typedRelation?.relationType == "POSSIBLY_CORRECTS"
        val strength =
          maxOf(
              semantic * 0.55,
              if (sharedExchange) 0.62 else 0.0,
              if (sharedSession) 0.42 else 0.0,
              typedRelation?.strength ?: 0.0,
            )
            .coerceIn(0.0, 1.0)
        if (strength < MIN_RELATION_STRENGTH) continue
        val looksConflicted =
          typedCorrection || (left.correctionCue.xor(right.correctionCue) && semantic >= 0.25)
        val signed = if (looksConflicted) -strength * 0.60 else strength
        val cohesion =
          ((if (sharedSession) 0.09 else 0.0) +
              (if (sharedExchange) 0.13 else 0.0) +
              temporal * 0.07 +
              semantic * 0.06)
            .coerceIn(0.0, 1.0)
        val basis =
          when {
            typedCorrection -> "typed POSSIBLY_CORRECTS route; preserve both exact sources"
            looksConflicted -> "possible correction/conflict"
            typedRelation != null -> "typed ${typedRelation.relationType.lowercase()} route"
            sharedExchange -> "shared exchange"
            sharedSession -> "shared conversation"
            else -> "shared concepts"
          }
        adjacency.getValue(left.candidate.artifactId) +=
          Relation(right.candidate.artifactId, strength, signed, cohesion, basis)
        adjacency.getValue(right.candidate.artifactId) +=
          Relation(left.candidate.artifactId, strength, signed, cohesion, basis)
      }
    }
    return adjacency
  }

  private fun overlapScore(left: Set<String>, right: Set<String>): Double {
    if (left.isEmpty() || right.isEmpty()) return 0.0
    return (left intersect right).size.toDouble() / minOf(left.size, right.size)
  }

  private fun elapsedMs(started: Long): Double = (System.nanoTime() - started) / 1_000_000.0

  private fun Double.rounded(decimals: Int = 3): Double {
    val factor = if (decimals == 2) 100.0 else 1_000.0
    return round(this * factor) / factor
  }

  private fun String.navigationSlice(): String =
    if (length <= MAX_NAVIGATION_CHARS) this
    else take(MAX_NAVIGATION_HEAD_CHARS) + takeLast(MAX_NAVIGATION_TAIL_CHARS)

  private data class Node(
    val candidate: ReadableRecallCandidate,
    val tokens: Set<String>,
    val durableScore: Int,
    val queryResonance: Double,
    val seed: Double,
    val correctionCue: Boolean,
    var mass: Double = 0.5,
    var depth: Double = 0.5,
  )

  private data class Relation(
    val targetId: String,
    val strength: Double,
    val signedStrength: Double,
    val cohesion: Double,
    val basis: String,
  )

  private data class Forces(
    val gravity: Double = 0.0,
    val affinity: Double = 0.0,
    val cohesion: Double = 0.0,
    val repulsion: Double = 0.0,
    val resonance: Double = 0.0,
    val inertia: Double = 0.0,
  ) {
    fun summary(): String =
      "gravity=${gravity.roundedLocal()} affinity=${affinity.roundedLocal()} " +
        "cohesion=${cohesion.roundedLocal()} repulsion=${repulsion.roundedLocal()} " +
        "resonance=${resonance.roundedLocal()} inertia=${inertia.roundedLocal()}"

    private fun Double.roundedLocal(): String = (round(this * 1_000.0) / 1_000.0).toString()
  }

  private companion object {
    const val DEFAULT_CANDIDATE_POOL = 36
    const val MAX_CANDIDATE_POOL = 64
    const val DEFAULT_SPREAD_DEPTH = 2
    const val DEFAULT_FANOUT = 3
    const val DEFAULT_SPREAD_GAIN = 1.0
    const val DEFAULT_TICKS = 3
    const val MAX_TICKS = 5
    const val MAX_OUTPUT_MEMORIES = 8
    const val DEFAULT_MODEL_CONTEXT_CHARS = 3_600
    const val MAX_MODEL_CONTEXT_CHARS = 5_200
    const val DEFAULT_SOFT_BUDGET_MS = 140L
    const val MAX_NAVIGATION_CHARS = 4_000
    const val MAX_NAVIGATION_HEAD_CHARS = 2_800
    const val MAX_NAVIGATION_TAIL_CHARS = 1_200
    const val MIN_ACTIVATION = 0.015
    const val MIN_RELATION_STRENGTH = 0.08
    val CORRECTION_CUE =
      Regex(
        "\\b(actually|correction|incorrect|no longer|not true|wrong|used to|superseded)\\b",
        RegexOption.IGNORE_CASE,
      )
  }
}
