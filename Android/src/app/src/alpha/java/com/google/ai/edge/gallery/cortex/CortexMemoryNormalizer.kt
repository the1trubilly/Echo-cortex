/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.cortex

internal data class CortexNormalizationMetadata(
  val version: Int,
  val title: String,
  val cavemanMvnSummary: String,
  val statementKind: String,
  val temporalStatus: String,
  val modality: String,
  val correctionCue: Boolean,
  val conceptTerms: List<String>,
  val projectionHash: String,
)

/**
 * Deterministic, rebuildable normalization only. It classifies routing hooks and creates a bounded
 * navigation preview; it never rewrites, confirms, or supersedes the exact Markdown source.
 */
internal object CortexMemoryNormalizer {
  const val VERSION = 2

  private val correctionPattern =
    Regex(
      "\\b(actually|correction|i was wrong|no longer|not anymore|instead|used to|" +
        "changed my mind|update:|to clarify)\\b",
      RegexOption.IGNORE_CASE,
    )
  private val currentPattern =
    Regex(
      "\\b(now|currently|today|these days|as of|no longer|not anymore|changed my mind)\\b",
      RegexOption.IGNORE_CASE,
    )
  private val pastPattern =
    Regex(
      "\\b(used to|previously|formerly|back then|at the time|before that)\\b",
      RegexOption.IGNORE_CASE,
    )
  private val futurePattern =
    Regex(
      "\\b(i plan|we plan|i want|we want|going to|eventually|in the future|will)\\b",
      RegexOption.IGNORE_CASE,
    )
  private val hypotheticalPattern =
    Regex(
      "\\b(if|maybe|perhaps|might|could|hypothetical|imagine|suppose)\\b",
      RegexOption.IGNORE_CASE,
    )
  private val requestPattern =
    Regex(
      "^(hey\\s+)?(echo|jarvis)[,!?:;\\s]+|" +
        "^(ask|reply|set|schedule|create|write|explain|tell|show|give|run|test|" +
        "remember|forget|delete|find|search)\\b",
      RegexOption.IGNORE_CASE,
    )

  fun normalize(
    content: String,
    sourceKind: CortexSourceKind,
    recallMetadata: CortexRecallIndexMetadata,
  ): CortexNormalizationMetadata {
    val trimmed = content.trim()
    val correctionCue = sourceKind == CortexSourceKind.USER_STATED && correctionPattern.containsMatchIn(trimmed)
    val statementKind =
      when {
        sourceKind != CortexSourceKind.USER_STATED -> "assistant_output"
        correctionCue -> "correction_candidate"
        trimmed.endsWith('?') -> "question"
        requestPattern.containsMatchIn(trimmed) -> "request"
        else -> "declaration"
      }
    val temporalStatus =
      when {
        currentPattern.containsMatchIn(trimmed) -> "current_time_cue"
        pastPattern.containsMatchIn(trimmed) -> "past_time_cue"
        futurePattern.containsMatchIn(trimmed) -> "future_time_cue"
        else -> "unspecified"
      }
    val modality =
      when {
        sourceKind != CortexSourceKind.USER_STATED -> "derived_agent_output"
        statementKind == "question" || statementKind == "request" -> "non_claim"
        hypotheticalPattern.containsMatchIn(trimmed) -> "hypothetical_or_uncertain"
        else -> "user_asserted"
      }
    val title = representativeTitle(trimmed, recallMetadata.conceptTerms)
    val cavemanMvnSummary = cavemanMvnSummary(trimmed)
    val projectionHash =
      CortexHashing.sha256(
        listOf(
            VERSION.toString(),
            sourceKind.name,
            statementKind,
            temporalStatus,
            modality,
            correctionCue.toString(),
            recallMetadata.conceptTerms.joinToString("|"),
            title,
            cavemanMvnSummary,
          )
          .joinToString("\n")
      )
    return CortexNormalizationMetadata(
      version = VERSION,
      title = title,
      cavemanMvnSummary = cavemanMvnSummary,
      statementKind = statementKind,
      temporalStatus = temporalStatus,
      modality = modality,
      correctionCue = correctionCue,
      conceptTerms = recallMetadata.conceptTerms,
      projectionHash = projectionHash,
    )
  }

  /** A deterministic LOD0 topic label. It is navigation metadata, never source truth. */
  fun representativeTitle(content: String, conceptTerms: Collection<String>): String {
    val concepts =
      conceptTerms
        .asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()
        .take(MAX_TITLE_CONCEPTS)
        .map(::displayConcept)
        .toList()
    if (concepts.isNotEmpty()) return concepts.joinToString(" · ").take(MAX_TITLE_CHARS)

    return content
      .lineSequence()
      .map(String::trim)
      .firstOrNull(String::isNotBlank)
      ?.replace(Regex("^([#>*-]|\\d+[.)])\\s*"), "")
      ?.replace(Regex("\\s+"), " ")
      ?.trim()
      ?.take(MAX_TITLE_CHARS)
      .orEmpty()
      .ifBlank { "Untitled memory" }
  }

  /**
   * Minimum Viable Nuance means bounded but not de-qualified: negation, uncertainty, correction,
   * and the ending are retained. This is a deterministic preview, not an adjudicated summary.
   */
  fun cavemanMvnSummary(content: String): String {
    val compact =
      content
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (compact.isBlank()) return "No readable memory content."
    if (compact.length <= MAX_MVN_CHARS) return compact

    val marker = " … "
    val available = MAX_MVN_CHARS - marker.length
    val headLength = (available * 2) / 3
    return compact.take(headLength).trimEnd() +
      marker +
      compact.takeLast(available - headLength).trimStart()
  }

  private fun displayConcept(concept: String): String =
    concept
      .replace('_', ' ')
      .split(' ')
      .filter(String::isNotBlank)
      .joinToString(" ") { word ->
        when (word.lowercase()) {
          "adb", "ai", "lod0", "mvn" -> word.uppercase()
          else -> word.lowercase().replaceFirstChar(Char::titlecase)
        }
      }

  private const val MAX_TITLE_CONCEPTS = 5
  private const val MAX_TITLE_CHARS = 120
  private const val MAX_MVN_CHARS = 320
}
