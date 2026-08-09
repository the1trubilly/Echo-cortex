/* Copyright 2026 Google LLC. Licensed under the Apache License, Version 2.0. */
package com.google.ai.edge.gallery.cortex

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object CortexRuntimeModule {
  @Provides
  @Singleton
  fun provideCortexRuntime(@ApplicationContext context: Context): CortexRuntime =
    AlphaCortexRuntime.get(context)
}
