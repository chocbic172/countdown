package me.ethanhoward.countdown.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.HardwareRenderer
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.net.Uri
import android.util.Log
import androidx.core.graphics.scale
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.ethanhoward.countdown.domain.ColorMath

/** Adjustments applied to a photo background. */
data class PhotoAdjustments(val blur: Float, val contrast: Float, val opacity: Float)

/**
 * Turns a user-picked photo into a widget background: downscale → blur → contrast/opacity.
 * The result is pre-rendered because RemoteViews can't apply effects themselves.
 */
class WidgetBackgroundProcessor(private val context: Context) {

    /** Decodes [uri] (from the photo picker) into a mutable-safe software bitmap of at most [SOURCE_MAX_SIDE]. */
    suspend fun decode(uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
        android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longSide = max(info.size.width, info.size.height)
            if (longSide > SOURCE_MAX_SIDE) {
                val f = SOURCE_MAX_SIDE.toFloat() / longSide
                decoder.setTargetSize((info.size.width * f).roundToInt(), (info.size.height * f).roundToInt())
            }
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    suspend fun decode(file: File): Bitmap? = withContext(Dispatchers.IO) {
        if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
    }

    /** Produces the adjusted background, scaled so its long side is at most [maxSide] px. */
    suspend fun render(source: Bitmap, adjustments: PhotoAdjustments, maxSide: Int = OUTPUT_MAX_SIDE): Bitmap =
        withContext(Dispatchers.Default) {
            val scaled = scaleDown(source, maxSide)
            // Blur is defined relative to a OUTPUT_MAX_SIDE image so previews match the final output.
            val radius = adjustments.blur * max(scaled.width, scaled.height) / OUTPUT_MAX_SIDE
            val blurred = if (radius >= 0.5f) blur(scaled, radius) else scaled
            applyColor(blurred, adjustments.contrast, adjustments.opacity)
        }

    suspend fun saveSource(bitmap: Bitmap, file: File) = write(bitmap, file, Bitmap.CompressFormat.JPEG, 90)

    suspend fun saveProcessed(bitmap: Bitmap, file: File) = write(bitmap, file, Bitmap.CompressFormat.WEBP_LOSSY, 90)

    /** Average luminance of [bitmap]'s colors (alpha ignored), used to pick a readable text color. */
    fun averageLuminance(bitmap: Bitmap): Float {
        val small = bitmap.scale(16, 16)
        val pixels = IntArray(16 * 16)
        small.getPixels(pixels, 0, 16, 0, 0, 16, 16)
        return pixels.map(ColorMath::luminance).average().toFloat()
    }

    private suspend fun write(bitmap: Bitmap, file: File, format: Bitmap.CompressFormat, quality: Int) =
        withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.outputStream().use { bitmap.compress(format, quality, it) }
            if (!tmp.renameTo(file)) error("Could not write $file")
        }

    private fun scaleDown(bitmap: Bitmap, maxSide: Int): Bitmap {
        val longSide = max(bitmap.width, bitmap.height)
        if (longSide <= maxSide) return bitmap
        val f = maxSide.toFloat() / longSide
        return bitmap.scale((bitmap.width * f).roundToInt().coerceAtLeast(1), (bitmap.height * f).roundToInt().coerceAtLeast(1))
    }

    private fun applyColor(bitmap: Bitmap, contrast: Float, opacity: Float): Bitmap {
        val t = 128f * (1f - contrast)
        val matrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, t,
                0f, contrast, 0f, 0f, t,
                0f, 0f, contrast, 0f, t,
                0f, 0f, 0f, opacity, 0f,
            ),
        )
        val out = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(bitmap, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(matrix) })
        return out
    }

    private fun blur(bitmap: Bitmap, radius: Float): Bitmap =
        try {
            gpuBlur(bitmap, radius)
        } catch (e: Exception) {
            Log.w(TAG, "GPU blur failed, using fallback", e)
            cheapBlur(bitmap, radius)
        }

    /** Gaussian blur via RenderEffect rendered offscreen through HardwareRenderer. */
    private fun gpuBlur(bitmap: Bitmap, radius: Float): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val reader = ImageReader.newInstance(
            w, h, PixelFormat.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        val renderer = HardwareRenderer()
        val node = RenderNode("blur")
        try {
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
            node.setPosition(0, 0, w, h)
            node.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.MIRROR))
            val canvas = node.beginRecording()
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            node.endRecording()
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            reader.acquireNextImage().use { image ->
                val buffer = checkNotNull(image.hardwareBuffer)
                buffer.use {
                    val hw = checkNotNull(Bitmap.wrapHardwareBuffer(it, null))
                    return hw.copy(Bitmap.Config.ARGB_8888, false)
                }
            }
        } finally {
            node.discardDisplayList()
            renderer.destroy()
            reader.close()
        }
    }

    /** Approximate blur by downscaling and bilinear upscaling. */
    private fun cheapBlur(bitmap: Bitmap, radius: Float): Bitmap {
        val factor = (1f + radius / 2f)
        val small = bitmap.scale((bitmap.width / factor).roundToInt().coerceAtLeast(1), (bitmap.height / factor).roundToInt().coerceAtLeast(1))
        return small.scale(bitmap.width, bitmap.height)
    }

    companion object {
        private const val TAG = "WidgetBgProcessor"
        /** Size of the stored original, so adjustments can be re-applied later without re-picking. */
        const val SOURCE_MAX_SIDE = 1600
        /** Final widget image size; comfortably under the RemoteViews bitmap memory limit. */
        const val OUTPUT_MAX_SIDE = 1024
        const val PREVIEW_MAX_SIDE = 512
    }
}
