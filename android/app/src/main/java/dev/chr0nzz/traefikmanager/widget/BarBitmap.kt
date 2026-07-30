package dev.chr0nzz.traefikmanager.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

object BarBitmap {

    fun create(
        widthPx: Int,
        heightPx: Int,
        ok: Int,
        warn: Int,
        err: Int,
        trackColor: Int,
        okColor: Int,
        warnColor: Int,
        errColor: Int,
    ): Bitmap {
        val w = widthPx.coerceAtLeast(2)
        val h = heightPx.coerceAtLeast(2)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val radius = h / 2f
        val clip = Path().apply {
            addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), radius, radius, Path.Direction.CW)
        }
        canvas.clipPath(clip)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = trackColor
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)

        val total = (ok + warn + err).coerceAtLeast(1)
        var x = 0f
        for ((count, color) in listOf(ok to okColor, warn to warnColor, err to errColor)) {
            if (count <= 0) continue
            val segW = w * count / total.toFloat()
            paint.color = color
            canvas.drawRect(x, 0f, x + segW, h.toFloat(), paint)
            x += segW
        }
        return bmp
    }
}
