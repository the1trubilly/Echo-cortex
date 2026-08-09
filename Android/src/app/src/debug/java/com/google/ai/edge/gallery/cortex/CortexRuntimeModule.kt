/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.cortex

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object CortexRuntimeModule {
  @Provides
  @Singleton
  fun provideCortexRuntime(): CortexRuntime = NoOpCortexRuntime
}
