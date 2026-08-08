package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.max

/**
 * Lightweight waveform renderer for audio previews.
 *
 * The ViewModel provides normalized peak values as a FloatArray. The renderer
 * draws one vertical bar per sample, scaled to the available canvas height.
 */
@Composable
fun WaveformVisualizer(
    waveform: FloatArray,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (waveform.isEmpty() || size.width <= 0f || size.height <= 0f) return@Canvas

        val centerY = size.height / 2f
        val halfHeight = size.height / 2f
        val stepX = size.width / max(1, waveform.size).toFloat()
        val barWidth = max(1f, stepX * 0.65f)

        waveform.forEachIndexed { index, sample ->
            val amplitude = abs(sample).coerceIn(0f, 1f)
            val barHeight = max(1f, amplitude * halfHeight)
            val x = index * stepX + stepX / 2f

            drawLine(
                color = color,
                start = Offset(x, centerY - barHeight),
                end = Offset(x, centerY + barHeight),
                strokeWidth = barWidth
            )
        }
    }
}
