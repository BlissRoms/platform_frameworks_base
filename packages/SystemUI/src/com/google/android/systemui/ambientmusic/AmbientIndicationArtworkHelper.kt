package com.google.android.systemui.ambientmusic

import android.app.WallpaperColors
import android.content.Context
import android.content.theming.ThemeStyle
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Handler
import com.android.systemui.graphics.ImageLoader
import com.android.systemui.monet.ColorScheme
import com.android.systemui.res.R
import com.android.systemui.util.getColorWithAlpha
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object AmbientIndicationArtworkHelper {

    @JvmField @Volatile var lastArtworkResult: ArtworkResult? = null

    data class ArtworkResult(
        @JvmField val albumArtUri: Uri,
        @JvmField val artwork: Drawable,
        @JvmField val colorScheme: ColorScheme,
        @JvmField val smallIcon: Drawable,
    )

    private val memoryCache = android.util.LruCache<String, ArtworkResult>(20)

    fun processArtwork(
        context: Context,
        imageLoader: ImageLoader,
        albumArtUri: Uri?,
        targetWidth: Int,
        targetHeight: Int,
        mainHandler: Handler,
        callback:
            (
                artwork: LayerDrawable?,
                colorScheme: ColorScheme?,
                albumArtUri: Uri?,
                smallIcon: Drawable?,
            ) -> Unit,
    ) {
        if (albumArtUri == null) {
            mainHandler.post { callback(null, null, null, null) }
            return
        }

        val cacheKey = albumArtUri.toString()
        val cachedResult = memoryCache.get(cacheKey)
        if (cachedResult != null) {
            lastArtworkResult = cachedResult
            mainHandler.post {
                callback(
                    cachedResult.artwork as? LayerDrawable,
                    cachedResult.colorScheme,
                    cachedResult.albumArtUri,
                    cachedResult.smallIcon,
                )
            }
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val validWidth = if (targetWidth > 0) targetWidth else (200f * context.resources.displayMetrics.density).toInt()
            val validHeight = if (targetHeight > 0) targetHeight else (100f * context.resources.displayMetrics.density).toInt()

            val cacheDir = java.io.File(context.cacheDir, "ambient_art").apply { mkdirs() }
            val diskCacheFile = java.io.File(cacheDir, Integer.toHexString(cacheKey.hashCode()) + ".png")

            val bitmap = try {
                if (diskCacheFile.exists()) {
                    android.graphics.BitmapFactory.decodeFile(diskCacheFile.absolutePath)
                } else if (albumArtUri.scheme == "http" || albumArtUri.scheme == "https") {
                    val url = java.net.URL(cacheKey)
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.doInput = true
                    val bytes = connection.inputStream.use { it.readBytes() }
                    try {
                        diskCacheFile.writeBytes(bytes)
                    } catch (e: Exception) {
                        // Non-critical cache write error
                    }
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } else {
                    imageLoader.loadBitmapSync(
                        ImageLoader.Uri(albumArtUri),
                        validWidth,
                        validHeight,
                        android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE,
                    )
                }
            } catch (e: Exception) {
                null
            }

            if (bitmap == null) {
                mainHandler.post { callback(null, null, albumArtUri, null) }
                return@launch
            }

            processBitmap(
                context,
                bitmap,
                albumArtUri,
                cacheKey,
                validWidth,
                validHeight,
                mainHandler,
                callback,
            )
        }
    }

    private fun processBitmap(
        context: Context,
        bitmap: Bitmap,
        albumArtUri: Uri,
        cacheKey: String,
        targetWidth: Int,
        targetHeight: Int,
        mainHandler: Handler,
        callback:
            (
                artwork: LayerDrawable?,
                colorScheme: ColorScheme?,
                albumArtUri: Uri?,
                smallIcon: Drawable?,
            ) -> Unit,
    ) {
        val wallpaperColors = WallpaperColors.fromBitmap(bitmap)
        if (wallpaperColors == null) {
            mainHandler.post { callback(null, null, albumArtUri, null) }
            return
        }

        val colorScheme = ColorScheme(wallpaperColors, false, ThemeStyle.CONTENT)

        val iconSizePx = (48f * context.resources.displayMetrics.density).toInt()
        val smallIconBitmap = Bitmap.createScaledBitmap(bitmap, iconSizePx, iconSizePx, true)
        val smallIcon = BitmapDrawable(context.resources, smallIconBitmap)

        val scale =
            Math.max(targetWidth, targetHeight).toFloat() /
                Math.max(bitmap.width, bitmap.height).toFloat()
        val scaledWidth = (bitmap.width * scale).toInt()
        val scaledHeight = (bitmap.height * scale).toInt()

        val artworkBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(artworkBitmap)
        val matrix =
            Matrix().apply {
                postScale(scale, scale)
                postTranslate((targetWidth - scaledWidth) / 2f, (targetHeight - scaledHeight) / 2f)
            }
        canvas.drawBitmap(bitmap, matrix, null)

        val artworkDrawable = BitmapDrawable(context.resources, artworkBitmap)

        val drawable = context.getDrawable(R.drawable.qs_media_scrim)
        val scrimDrawable = (if (drawable != null) drawable.mutate() else null) as GradientDrawable
        val secondaryTone20 = colorScheme.materialScheme.secondaryPalette.tone(20)
        scrimDrawable.setColors(
            intArrayOf(
                getColorWithAlpha(secondaryTone20, 0.65f),
                getColorWithAlpha(secondaryTone20, 0.75f),
            )
        )

        val layeredArtwork = LayerDrawable(arrayOf(artworkDrawable, scrimDrawable))

        val result = ArtworkResult(albumArtUri, layeredArtwork, colorScheme, smallIcon)
        lastArtworkResult = result
        memoryCache.put(cacheKey, result)

        bitmap.recycle()

        mainHandler.post { callback(layeredArtwork, colorScheme, albumArtUri, smallIcon) }
    }
}
