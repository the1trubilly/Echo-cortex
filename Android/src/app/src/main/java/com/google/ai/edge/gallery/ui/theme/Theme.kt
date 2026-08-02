/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ai.edge.gallery.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val lightScheme =
  lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
  )

private val darkScheme =
  darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
  )

@Immutable
data class CustomColors(
  val appTitleGradientColors: List<Color> = listOf(),
  val tabHeaderBgColor: Color = Color.Transparent,
  val taskCardBgColor: Color = Color.Transparent,
  val taskBgColors: List<Color> = listOf(),
  val taskBgGradientColors: List<List<Color>> = listOf(),
  val taskIconColors: List<Color> = listOf(),
  val taskIconShapeBgColor: Color = Color.Transparent,
  val homeBottomGradient: List<Color> = listOf(),
  val userBubbleBgColor: Color = Color.Transparent,
  val agentBubbleBgColor: Color = Color.Transparent,
  val linkColor: Color = Color.Transparent,
  val successColor: Color = Color.Transparent,
  val positiveStrokeColor: Color = Color.Transparent,
  val negativeStrokeColor: Color = Color.Transparent,
  val lassoStrokeColor: Color = Color.Transparent,
  val recordButtonBgColor: Color = Color.Transparent,
  val waveFormBgColor: Color = Color.Transparent,
  val modelInfoIconColor: Color = Color.Transparent,
  val warningContainerColor: Color = Color.Transparent,
  val warningTextColor: Color = Color.Transparent,
  val errorContainerColor: Color = Color.Transparent,
  val errorTextColor: Color = Color.Transparent,
  val newFeatureContainerColor: Color = Color.Transparent,
  val newFeatureTextColor: Color = Color.Transparent,
  val bgStarColor: Color = Color.Transparent,
  val promoBannerBgBrush: Brush = Brush.verticalGradient(listOf(Color.Transparent)),
  val promoBannerIconBgBrush: Brush = Brush.verticalGradient(listOf(Color.Transparent)),
)

val LocalCustomColors = staticCompositionLocalOf { CustomColors() }

val lightCustomColors =
  CustomColors(
    appTitleGradientColors = listOf(Color(0xFFC2FF55), Color(0xFF63FF00)),
    tabHeaderBgColor = Color(0xFF1D4D00),
    taskCardBgColor = surfaceContainerLowestLight,
    taskBgColors =
      listOf(
        Color(0xFF071007),
        Color(0xFF081508),
        Color(0xFF061410),
        Color(0xFF041216),
      ),
    taskBgGradientColors =
      listOf(
        listOf(Color(0xFFB2FF3D), Color(0xFF5CFF00)),
        listOf(Color(0xFF72FF1F), Color(0xFF25C400)),
        listOf(Color(0xFF43FFC9), Color(0xFF00B97C)),
        listOf(Color(0xFF00E5FF), Color(0xFF008CA3)),
      ),
    taskIconColors =
      listOf(
        Color(0xFF8CFF00),
        Color(0xFF61E800),
        Color(0xFF4DFF88),
        Color(0xFF00E5FF),
      ),
    taskIconShapeBgColor = Color.Black,
    homeBottomGradient = listOf(Color.Transparent, Color(0x331D4D00)),
    agentBubbleBgColor = Color(0xFF061006),
    userBubbleBgColor = Color(0xFF183800),
    linkColor = Color(0xFF00E5FF),
    successColor = Color(0xFF8CFF00),
    positiveStrokeColor = Color(0xFF63FF00),
    negativeStrokeColor = Color(0xFFFF4D5A),
    lassoStrokeColor = Color(0xFF00E5FF),
    recordButtonBgColor = Color(0xFFFF3347),
    waveFormBgColor = Color(0xFF1D4D00),
    modelInfoIconColor = Color(0xFF8CFF00),
    warningContainerColor = Color(0xFF3D3000),
    warningTextColor = Color(0xFFFFD54A),
    errorContainerColor = Color(0xFF57000A),
    errorTextColor = Color(0xFFFF8A91),
    newFeatureContainerColor = Color(0xFF183800),
    newFeatureTextColor = Color(0xFFBDFF7A),
    bgStarColor = Color(0x3363FF00),
    promoBannerBgBrush =
      Brush.linearGradient(
        colorStops =
          arrayOf(
            0.0f to Color(0x66205A00),
            0.6154f to Color(0x55102E00),
            1.0f to Color(0x3300A7B8),
          ),
        start = Offset(0f, 0f),
        end = Offset(0f, Float.POSITIVE_INFINITY),
      ),
    promoBannerIconBgBrush =
      Brush.linearGradient(
        colorStops =
          arrayOf(
            0.2442f to Color(0x8863FF00),
            0.4296f to Color(0x6650D900),
            0.6651f to Color(0x6600E5FF),
          ),
        start = Offset(0f, 1f),
        end = Offset(1f, 0f),
      ),
  )

val darkCustomColors =
  CustomColors(
    appTitleGradientColors = listOf(Color(0xFFC2FF55), Color(0xFF63FF00)),
    tabHeaderBgColor = Color(0xFF1D4D00),
    taskCardBgColor = surfaceContainerHighDark,
    taskBgColors =
      listOf(
        Color(0xFF071007),
        Color(0xFF081508),
        Color(0xFF061410),
        Color(0xFF041216),
      ),
    taskBgGradientColors =
      listOf(
        listOf(Color(0xFFB2FF3D), Color(0xFF5CFF00)),
        listOf(Color(0xFF72FF1F), Color(0xFF25C400)),
        listOf(Color(0xFF43FFC9), Color(0xFF00B97C)),
        listOf(Color(0xFF00E5FF), Color(0xFF008CA3)),
      ),
    taskIconColors =
      listOf(
        Color(0xFF8CFF00),
        Color(0xFF61E800),
        Color(0xFF4DFF88),
        Color(0xFF00E5FF),
      ),
    taskIconShapeBgColor = Color.Black,
    homeBottomGradient = listOf(Color.Transparent, Color(0x331D4D00)),
    agentBubbleBgColor = Color(0xFF061006),
    userBubbleBgColor = Color(0xFF183800),
    linkColor = Color(0xFF00E5FF),
    successColor = Color(0xFF8CFF00),
    positiveStrokeColor = Color(0xFF63FF00),
    negativeStrokeColor = Color(0xFFFF4D5A),
    lassoStrokeColor = Color(0xFF00E5FF),
    recordButtonBgColor = Color(0xFFFF3347),
    waveFormBgColor = Color(0xFF1D4D00),
    modelInfoIconColor = Color(0xFF8CFF00),
    warningContainerColor = Color(0xFF3D3000),
    warningTextColor = Color(0xFFFFD54A),
    errorContainerColor = Color(0xFF57000A),
    errorTextColor = Color(0xFFFF8A91),
    newFeatureContainerColor = Color(0xFF183800),
    newFeatureTextColor = Color(0xFFBDFF7A),
    bgStarColor = Color(0x3363FF00),
    promoBannerBgBrush =
      Brush.linearGradient(
        colorStops = arrayOf(0.0f to Color(0x88205A00), 0.8077f to Color(0x66020A02)),
        start = Offset(0f, 0f),
        end = Offset(0f, Float.POSITIVE_INFINITY),
      ),
    promoBannerIconBgBrush =
      Brush.linearGradient(
        colorStops =
          arrayOf(
            0.2442f to Color(0x9963FF00),
            0.4296f to Color(0x7750D900),
            0.6651f to Color(0x7700E5FF),
          ),
        start = Offset(0f, 1f),
        end = Offset(1f, 0f),
      ),
  )

val MaterialTheme.customColors: CustomColors
  @Composable @ReadOnlyComposable get() = LocalCustomColors.current

/**
 * Controls the color of the phone's status bar icons based on whether the app is using a dark
 * theme.
 */
@Composable
fun StatusBarColorController(useDarkTheme: Boolean) {
  val view = LocalView.current
  val currentWindow = (view.context as? Activity)?.window

  if (currentWindow != null) {
    SideEffect {
      WindowCompat.setDecorFitsSystemWindows(currentWindow, false)
      val controller = WindowCompat.getInsetsController(currentWindow, view)
      controller.isAppearanceLightStatusBars = !useDarkTheme
      controller.isAppearanceLightNavigationBars = !useDarkTheme
    }
  }
}

@Composable
fun GalleryTheme(content: @Composable () -> Unit) {
  val view = LocalView.current

  StatusBarColorController(useDarkTheme = true)

  CompositionLocalProvider(LocalCustomColors provides darkCustomColors) {
    MaterialTheme(colorScheme = darkScheme, typography = AppTypography, content = content)
  }

  // Keep system navigation visually continuous with the black Jarvis canvas.
  LaunchedEffect(Unit) {
    val window = (view.context as Activity).window

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      window.isNavigationBarContrastEnforced = false
    }
  }
}
