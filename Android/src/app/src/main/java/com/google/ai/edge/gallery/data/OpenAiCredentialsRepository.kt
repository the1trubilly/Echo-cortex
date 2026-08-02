package com.google.ai.edge.gallery.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

const val OPENAI_CREDENTIALS_FILE = "openai_credentials"
private const val API_KEY_ENTRY = "api_key"
private const val API_KEY_IV_ENTRY = "api_key_iv"
private const val SAFETY_IDENTIFIER_ENTRY = "safety_identifier"
private const val KEYSTORE_ALIAS = "android_jarvis_openai_credentials"
private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"

/** Keeps the OpenAI credential encrypted at rest with a key backed by Android Keystore. */
@Singleton
class OpenAiCredentialsRepository
@Inject
constructor(@param:ApplicationContext private val context: Context) {
  private val preferences by lazy {
    context.getSharedPreferences(OPENAI_CREDENTIALS_FILE, Context.MODE_PRIVATE)
  }

  fun hasApiKey(): Boolean = readApiKey() != null

  fun readApiKey(): String? {
    val encryptedValue = preferences.getString(API_KEY_ENTRY, null) ?: return null
    val encodedIv = preferences.getString(API_KEY_IV_ENTRY, null) ?: return null
    return try {
      val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
      cipher.init(
        Cipher.DECRYPT_MODE,
        getOrCreateSecretKey(),
        GCMParameterSpec(128, Base64.getDecoder().decode(encodedIv)),
      )
      String(
          cipher.doFinal(Base64.getDecoder().decode(encryptedValue)),
          Charsets.UTF_8,
        )
        .trim()
        .ifEmpty { null }
    } catch (_: Exception) {
      null
    }
  }

  fun saveApiKey(apiKey: String) {
    val normalizedKey = apiKey.trim()
    require(normalizedKey.isNotEmpty()) { "The OpenAI API key cannot be empty." }
    val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
    val encryptedValue = cipher.doFinal(normalizedKey.toByteArray(Charsets.UTF_8))
    preferences
      .edit()
      .putString(API_KEY_ENTRY, Base64.getEncoder().encodeToString(encryptedValue))
      .putString(API_KEY_IV_ENTRY, Base64.getEncoder().encodeToString(cipher.iv))
      .apply()
  }

  fun clearApiKey() {
    preferences.edit().remove(API_KEY_ENTRY).remove(API_KEY_IV_ENTRY).apply()
  }

  /** A random per-install identifier; it contains no account or device information. */
  @Synchronized
  fun getOrCreateSafetyIdentifier(): String {
    val existing = preferences.getString(SAFETY_IDENTIFIER_ENTRY, null)
    if (!existing.isNullOrEmpty()) return existing

    val identifier = UUID.randomUUID().toString()
    preferences.edit().putString(SAFETY_IDENTIFIER_ENTRY, identifier).commit()
    return identifier
  }

  private fun getOrCreateSecretKey(): SecretKey {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val existingKey = keyStore.getKey(KEYSTORE_ALIAS, null) as? SecretKey
    if (existingKey != null) return existingKey

    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
      .apply {
        init(
          KeyGenParameterSpec.Builder(
              KEYSTORE_ALIAS,
              KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        )
      }
      .generateKey()
  }
}
