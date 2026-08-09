/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.cortex

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal data class Schema11Collection(
  val name: String,
  val entryCount: Int,
  val element: JsonElement,
)

internal data class Schema11Inspection(
  val database: JsonObject,
  val collections: List<Schema11Collection>,
) {
  val totalEntries: Int = collections.sumOf { it.entryCount }
}

/** Strict, read-only inspection of a selected ThreadKeeper database copy. */
internal object Schema11Importer {
  private val json = Json { ignoreUnknownKeys = false }
  private val sensitiveKey =
    Regex(
      "^(api[_-]?key|password|secret|access[_-]?token|refresh[_-]?token|authorization)$",
      RegexOption.IGNORE_CASE,
    )

  fun inspect(sourceBytes: ByteArray): Schema11Inspection {
    require(sourceBytes.isNotEmpty()) { "The selected ThreadKeeper copy is empty." }
    require(sourceBytes.size <= MAX_IMPORT_BYTES) {
      "The selected ThreadKeeper copy exceeds the 32 MB Alpha import limit."
    }
    val parsed = json.parseToJsonElement(sourceBytes.toString(Charsets.UTF_8)).unwrapString()
    val root = parsed as? JsonObject ?: error("ThreadKeeper import must be a JSON object.")
    val database = findDatabase(root)
    val schemaVersion =
      database["schemaVersion"]?.jsonPrimitive?.intOrNull
        ?: database["schema_version"]?.jsonPrimitive?.intOrNull
        ?: error("ThreadKeeper schemaVersion is missing.")
    require(schemaVersion == 11) {
      "Jarvis Alpha accepts only a ThreadKeeper schema-11 copy; found schema $schemaVersion."
    }
    rejectEmbeddedSecrets(database)

    val collections =
      database.entries
        .filter { (name, value) ->
          name != "schemaVersion" &&
            name != "schema_version" &&
            (value is JsonArray || value is JsonObject)
        }
        .sortedBy { it.key }
        .map { (name, value) ->
          Schema11Collection(
            name = name,
            entryCount =
              when (value) {
                is JsonArray -> value.size
                is JsonObject -> value.size
                else -> 0
              },
            element = value,
          )
        }
    require(collections.isNotEmpty()) { "The schema-11 copy contains no collections." }
    return Schema11Inspection(database = database, collections = collections)
  }

  private fun findDatabase(root: JsonObject): JsonObject {
    if (root.containsSchemaVersion()) return root
    val candidates = listOf("database", "threadkeeper_database_v1", "data")
    candidates.forEach { name ->
      val candidate = root[name]?.unwrapString()
      if (candidate is JsonObject && candidate.containsSchemaVersion()) return candidate
    }
    error("Could not find a ThreadKeeper database object in the selected copy.")
  }

  private fun JsonObject.containsSchemaVersion(): Boolean =
    containsKey("schemaVersion") || containsKey("schema_version")

  private fun JsonElement.unwrapString(): JsonElement {
    if (this !is JsonPrimitive || !isString) return this
    return json.parseToJsonElement(content)
  }

  private fun rejectEmbeddedSecrets(element: JsonElement, path: String = "root") {
    when (element) {
      is JsonObject ->
        element.forEach { (key, value) ->
          if (
            sensitiveKey.matches(key) &&
              value is JsonPrimitive &&
              value.content.isNotBlank()
          ) {
            error("Import rejected because a possible secret was found at $path.$key.")
          }
          rejectEmbeddedSecrets(value, "$path.$key")
        }
      is JsonArray -> element.forEachIndexed { index, value ->
        rejectEmbeddedSecrets(value, "$path[$index]")
      }
      else -> Unit
    }
  }

  private const val MAX_IMPORT_BYTES = 32 * 1024 * 1024
}
