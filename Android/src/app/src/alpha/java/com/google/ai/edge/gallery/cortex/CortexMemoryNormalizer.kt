/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.cortex

internal data class CortexNormalizationMetadata(
  val version: Int,
  val title: String,
  val statementKind: String,
  val temporalStatus: String,
  val modality: String,
  val correctionCue: Boolean,
  val conceptTerms: List<String>,
  val projectionHash: String,
)

/**
 * Deterministic, rebuildable normalization only. It classifies routing hooks and never rewrites,
 * summarizes, confirms, or supersedes the exact Markdown source.
 */
internal object CortexMemoryNormalizer {
  const val VERSION = 1

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
    val title =
      trimmed
        .lineSequence()
        .firstOrNull(String::isNotBlank)
        ?.replace(Regex("\\s+"), " ")
        ?.take(120)
        .orEmpty()
        .ifBlank { "Untitled memory" }
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
          )
          .joinToString("\n")
      )
    return CortexNormalizationMetadata(
      version = VERSION,
      title = title,
      statementKind = statementKind,
      temporalStatus = temporalStatus,
      modality = modality,
      correctionCue = correctionCue,
      conceptTerms = recallMetadata.conceptTerms,
      projectionHash = projectionHash,
    )
  }
}
