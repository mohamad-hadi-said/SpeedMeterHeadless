package com.speedmeter.headless

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.util.Locale
import kotlin.math.roundToInt

object SpeedBitmapGenerator {

    private val condensedBoldTypeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)

    // Ultra-bold paint with FILL_AND_STROKE for maximum contrast and solid line weight in status bar
    private val valPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = condensedBoldTypeface
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 2.8f
    }

    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = condensedBoldTypeface
        style = Paint.Style.FILL_AND_STROKE
        strokeWidth = 2.5f
    }

    fun formatSpeed(bytesPerSec: Double): Pair<String, String> {
        if (bytesPerSec <= 0.0) {
            return Pair("0", "K")
        }
        val kb = bytesPerSec / 1024.0
        return when {
            kb < 1000.0 -> {
                Pair(kb.roundToInt().toString(), "K")
            }
            kb < 1000.0 * 1024.0 -> {
                val mb = kb / 1024.0
                if (mb < 10.0) {
                    Pair(String.format(Locale.US, "%.1f", mb), "M")
                } else {
                    Pair(mb.roundToInt().toString(), "M")
                }
            }
            else -> {
                val gb = kb / (1024.0 * 1024.0)
                Pair(String.format(Locale.US, "%.1f", gb), "G")
            }
        }
    }

    fun formatFullSpeed(bytesPerSec: Double): String {
        if (bytesPerSec <= 0.0) return "0.0 KB/s"
        val kb = bytesPerSec / 1024.0
        return when {
            kb < 1000.0 -> String.format(Locale.US, "%.1f KB/s", kb)
            kb < 1000.0 * 1024.0 -> String.format(Locale.US, "%.2f MB/s", kb / 1024.0)
            else -> String.format(Locale.US, "%.2f GB/s", kb / (1024.0 * 1024.0))
        }
    }

    fun createSpeedBitmap(bytesPerSec: Double): Bitmap {
        val size = 96
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val (valStr, unitStr) = formatSpeed(bytesPerSec)

        // Dynamically scale font to occupy maximum height & width without clipping
        valPaint.textSize = when (valStr.length) {
            1 -> 62f
            2 -> 58f
            3 -> 54f
            else -> 46f
        }
        unitPaint.textSize = 46f

        // Baseline Y=48 for number, baseline Y=94 for unit
        canvas.drawText(valStr, 48f, 48f, valPaint)
        canvas.drawText(unitStr, 48f, 94f, unitPaint)

        return bitmap
    }
}
