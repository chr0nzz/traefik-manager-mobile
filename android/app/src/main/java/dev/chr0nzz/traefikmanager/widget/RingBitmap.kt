package dev.chr0nzz.traefikmanager.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface

object RingBitmap {

    fun circle(color: Int, sizePx: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        val r = sizePx / 2f
        canvas.drawCircle(r, r, r, paint)
        return bmp
    }

    fun create(ok: Int, warn: Int, err: Int, sizePx: Int, textColor: Int, trackColor: Int): Bitmap {
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val stroke = sizePx * 0.14f
        val pad = stroke / 2f
        val rect = RectF(pad, pad, sizePx - pad, sizePx - pad)
        val total = (ok + warn + err).toFloat().coerceAtLeast(1f)

        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            color = trackColor
        }
        canvas.drawOval(rect, trackPaint)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.BUTT
        }

        data class Seg(val value: Int, val color: Int)
        val segs = listOf(
            Seg(ok,   0xFF22c55e.toInt()),
            Seg(warn, 0xFFf59e0b.toInt()),
            Seg(err,  0xFFef4444.toInt()),
        )

        var startAngle = -90f
        for (seg in segs) {
            if (seg.value <= 0) continue
            val sweep = seg.value / total * 360f
            paint.color = seg.color
            canvas.drawArc(rect, startAngle, sweep, false, paint)
            startAngle += sweep
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textAlign = Paint.Align.CENTER
            textSize = sizePx * 0.28f
            typeface = Typeface.DEFAULT_BOLD
        }
        val cy = sizePx / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(ok.toString(), sizePx / 2f, cy, textPaint)

        return bmp
    }
}
