/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.agent

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.gallery.customtasks.agentchat.AgentToolsImpl
import com.google.ai.edge.gallery.data.OpenAiCredentialsRepository
import com.google.ai.edge.gallery.data.createOpenAiChatModels
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OpenAiInitializationDeviceTest {
  @Test
  fun initializesCloudSessionBeforeAKeyIsAddedInSettings() = runBlocking {
    val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
    val context = IsolatedPreferencesContext(targetContext)
    val model = createOpenAiChatModels().first()
    val executor =
      OpenAiAgentRuntimeExecutor(
        credentialsRepository = OpenAiCredentialsRepository(context),
        apiClient = OpenAiApiClient(),
        agentTools = AgentToolsImpl(),
      )
    var initializationResult: String? = null

    executor.initialize(
      context = context,
      config =
        AgentRuntimeConfig(
          model = model,
          taskId = "openai-initialization-device-test",
          systemInstruction = "Device-test instruction",
        ),
      onDone = { result -> initializationResult = result },
    )

    assertEquals("", initializationResult)
    assertNotNull(model.instance)
    executor.cleanUp {}
  }

  private class IsolatedPreferencesContext(base: Context) : ContextWrapper(base) {
    private val identifier = UUID.randomUUID().toString()

    override fun getApplicationContext(): Context = this

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
      baseContext.getSharedPreferences("openai_device_test_${identifier}_$name", mode)
  }
}
