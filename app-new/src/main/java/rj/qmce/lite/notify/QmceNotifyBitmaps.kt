package rj.qmce.lite.notify

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import kotlin.math.min

internal object QmceNotifyBitmaps {
    fun toCircle(src: Bitmap, sizePx: Int = 192): Bitmap {
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val scale = sizePx.toFloat() / min(src.width, src.height).coerceAtLeast(1)
        val dx = (sizePx - src.width * scale) / 2f
        val dy = (sizePx - src.height * scale) / 2f
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        paint.shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also {
            it.setLocalMatrix(matrix)
        }
        val r = sizePx / 2f
        canvas.drawCircle(r, r, r, paint)
        return out
    }
}
