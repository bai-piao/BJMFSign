package com.bjmf.sign.android.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.util.LruCache
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.bjmf.sign.android.ui.glass.LocalGlassDark
import java.net.URL
import java.util.Collections
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/** 高德瓦片地图选点（GCJ-02 坐标），深色模式下底图自动转暗。 */
@Composable
internal fun NativeMapPicker(
    initialLat: Double,
    initialLng: Double,
    hasSelection: Boolean,
    onCoordinateSelected: (Double, Double) -> Unit,
) {
    val latestOnSelected by rememberUpdatedState(onCoordinateSelected)
    val dark = LocalGlassDark.current

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            AmapTileView(context).apply {
                onPointSelected = { lat, lng -> latestOnSelected(lat, lng) }
                setDarkMode(dark)
                setPoint(initialLat, initialLng, hasSelection)
            }
        },
        update = { mapView ->
            mapView.onPointSelected = { lat, lng -> latestOnSelected(lat, lng) }
            mapView.setDarkMode(dark)
            mapView.setPoint(initialLat, initialLng, hasSelection)
        },
    )
}

private class AmapTileView(context: Context) : View(context) {
    var onPointSelected: ((Double, Double) -> Unit)? = null

    private var centerLat = DEFAULT_MAP_POINT.first
    private var centerLng = DEFAULT_MAP_POINT.second
    private var markerLat = DEFAULT_MAP_POINT.first
    private var markerLng = DEFAULT_MAP_POINT.second
    private var hasMarker = false
    private var zoom = 12
    private var downX = 0f
    private var downY = 0f
    private var moved = false
    private var startCenterX = 0.0
    private var startCenterY = 0.0
    private var scaleAccum = 1f

    private val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(232, 236, 240)
        style = Paint.Style.FILL
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(70, 160, 168, 176)
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.rgb(255, 59, 48)
        style = Paint.Style.FILL
    }
    private val markerInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
    }
    private var darkMode = false

    /** 深色模式：反色 + 色相旋转 180°，得到保留原色相的暗色底图。 */
    fun setDarkMode(dark: Boolean) {
        if (dark == darkMode) return
        darkMode = dark
        tilePaint.colorFilter = if (dark) ColorMatrixColorFilter(NIGHT_MATRIX) else null
        placeholderPaint.color = if (dark) AndroidColor.rgb(36, 38, 42) else AndroidColor.rgb(232, 236, 240)
        gridPaint.color = if (dark) AndroidColor.argb(70, 90, 96, 104) else AndroidColor.argb(70, 160, 168, 176)
        invalidate()
    }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                scaleAccum = 1f
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleAccum *= detector.scaleFactor
                when {
                    scaleAccum > 1.22f -> {
                        zoom = (zoom + 1).coerceAtMost(MAX_ZOOM)
                        scaleAccum = 1f
                        invalidate()
                    }

                    scaleAccum < 0.82f -> {
                        zoom = (zoom - 1).coerceAtLeast(MIN_ZOOM)
                        scaleAccum = 1f
                        invalidate()
                    }
                }
                return true
            }
        },
    )

    fun setPoint(lat: Double, lng: Double, selected: Boolean) {
        if (abs(centerLat - lat) < 0.000001 && abs(centerLng - lng) < 0.000001 && hasMarker == selected) {
            return
        }
        centerLat = lat.coerceIn(-85.0, 85.0)
        centerLng = lng.coerceIn(-180.0, 180.0)
        markerLat = centerLat
        markerLng = centerLng
        hasMarker = selected
        zoom = if (selected) max(zoom, 16) else zoom
        invalidate()
    }

    override fun onDraw(canvas: AndroidCanvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return
        drawTiles(canvas)
        drawMarker(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        if (event.pointerCount > 1) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                moved = false
                val center = project(centerLat, centerLng, zoom)
                startCenterX = center.first
                startCenterY = center.second
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                if (abs(dx) > 4f || abs(dy) > 4f) moved = true
                val next = unproject(startCenterX - dx, startCenterY - dy, zoom)
                centerLat = next.first
                centerLng = next.second
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!moved) {
                    val point = screenToLatLng(event.x, event.y)
                    markerLat = point.first
                    markerLng = point.second
                    hasMarker = true
                    onPointSelected?.invoke(markerLat, markerLng)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    private fun drawTiles(canvas: AndroidCanvas) {
        val center = project(centerLat, centerLng, zoom)
        val topLeftX = center.first - width / 2.0
        val topLeftY = center.second - height / 2.0
        val minX = floor(topLeftX / TILE_SIZE).toInt() - 1
        val maxX = floor((topLeftX + width) / TILE_SIZE).toInt() + 1
        val minY = floor(topLeftY / TILE_SIZE).toInt() - 1
        val maxY = floor((topLeftY + height) / TILE_SIZE).toInt() + 1
        val tileCount = 1 shl zoom

        for (x in minX..maxX) {
            val wrappedX = ((x % tileCount) + tileCount) % tileCount
            for (y in minY..maxY) {
                if (y !in 0 until tileCount) continue
                val left = (x * TILE_SIZE - topLeftX).toFloat()
                val top = (y * TILE_SIZE - topLeftY).toFloat()
                val bitmap = AmapTileCache.get(zoom, wrappedX, y) { postInvalidateOnAnimation() }
                if (bitmap == null) {
                    canvas.drawRect(left, top, left + TILE_SIZE, top + TILE_SIZE, placeholderPaint)
                    canvas.drawRect(left, top, left + TILE_SIZE, top + TILE_SIZE, gridPaint)
                } else {
                    canvas.drawBitmap(bitmap, left, top, tilePaint)
                }
            }
        }
    }

    private fun drawMarker(canvas: AndroidCanvas) {
        if (!hasMarker) return
        val center = project(centerLat, centerLng, zoom)
        val marker = project(markerLat, markerLng, zoom)
        val x = (marker.first - (center.first - width / 2.0)).toFloat()
        val y = (marker.second - (center.second - height / 2.0)).toFloat()
        val radius = 14f
        val path = AndroidPath().apply {
            moveTo(x, y)
            cubicTo(x - radius, y - radius * 0.7f, x - radius, y - radius * 2f, x, y - radius * 2.45f)
            cubicTo(x + radius, y - radius * 2f, x + radius, y - radius * 0.7f, x, y)
            close()
        }
        canvas.drawPath(path, markerPaint)
        canvas.drawOval(RectF(x - 4.8f, y - radius * 1.58f, x + 4.8f, y - radius * 0.9f), markerInnerPaint)
    }

    private fun screenToLatLng(x: Float, y: Float): Pair<Double, Double> {
        val center = project(centerLat, centerLng, zoom)
        return unproject(center.first + x - width / 2.0, center.second + y - height / 2.0, zoom)
    }

    companion object {
        private val NIGHT_MATRIX = ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ).apply {
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        -0.574f, 1.430f, 0.144f, 0f, 0f,
                        0.426f, 0.430f, 0.144f, 0f, 0f,
                        0.426f, 1.430f, -0.856f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
            postConcat(ColorMatrix().apply { setScale(0.85f, 0.85f, 0.9f, 1f) })
        }
        private const val TILE_SIZE = 256
        private const val MIN_ZOOM = 4
        private const val MAX_ZOOM = 18

        fun project(lat: Double, lng: Double, zoom: Int): Pair<Double, Double> {
            val scale = TILE_SIZE * 2.0.pow(zoom)
            val sinLat = sin(lat.coerceIn(-85.0, 85.0) * Math.PI / 180.0)
            val x = (lng + 180.0) / 360.0 * scale
            val y = (0.5 - ln((1 + sinLat) / (1 - sinLat)) / (4 * Math.PI)) * scale
            return x to y
        }

        fun unproject(x: Double, y: Double, zoom: Int): Pair<Double, Double> {
            val scale = TILE_SIZE * 2.0.pow(zoom)
            val lng = x / scale * 360.0 - 180.0
            val n = Math.PI - 2.0 * Math.PI * y / scale
            val lat = 180.0 / Math.PI * atan(0.5 * (exp(n) - exp(-n)))
            return lat.coerceIn(-85.0, 85.0) to lng.coerceIn(-180.0, 180.0)
        }
    }
}

private object AmapTileCache {
    private const val MAX_CACHE_SIZE = 96
    private val cache = object : LruCache<String, Bitmap>(MAX_CACHE_SIZE) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }
    private val loading = Collections.synchronizedSet(mutableSetOf<String>())
    private val executor = Executors.newFixedThreadPool(4)

    fun get(zoom: Int, x: Int, y: Int, onLoaded: () -> Unit): Bitmap? {
        val key = "$zoom/$x/$y"
        cache.get(key)?.let { return it }
        if (loading.add(key)) {
            executor.execute {
                runCatching {
                    val url = tileUrl(zoom, x, y)
                    val connection = URL(url).openConnection()
                    connection.connectTimeout = 6000
                    connection.readTimeout = 8000
                    connection.setRequestProperty("User-Agent", "BJMFSign/1.0 Android")
                    connection.getInputStream().use { stream ->
                        BitmapFactory.decodeStream(stream)?.let { bitmap -> cache.put(key, bitmap) }
                    }
                }
                loading.remove(key)
                onLoaded()
            }
        }
        return null
    }

    private fun tileUrl(zoom: Int, x: Int, y: Int): String {
        val subdomain = (abs(x + y) % 4) + 1
        return "https://webrd0$subdomain.is.autonavi.com/appmaptile?lang=zh_cn&size=1&scale=1&style=8&x=$x&y=$y&z=$zoom"
    }
}

internal val DEFAULT_MAP_POINT = 39.904200 to 116.407400

internal fun String.toLatLngOrNull(): Pair<Double, Double>? {
    val parts = replace("，", " ")
        .replace(",", " ")
        .replace("|", " ")
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
    if (parts.size < 2) return null
    val first = parts[0].toDoubleOrNull() ?: return null
    val second = parts[1].toDoubleOrNull() ?: return null
    val (lat, lng) = normalizeCoordPair(first, second) ?: return null
    if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
    return lat to lng
}

internal fun Double.toCoordText(): String = "%.6f".format(Locale.US, this)


internal fun android.content.Context.hasLocationPermission(): Boolean {
    return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

internal fun normalizeCoordPair(first: Double, second: Double): Pair<Double, Double>? {
    return when {
        first in -180.0..180.0 && second in -90.0..90.0 && first !in -90.0..90.0 -> second to first
        first in -90.0..90.0 && second in -180.0..180.0 && second !in -90.0..90.0 -> first to second
        first in -180.0..180.0 && second in -90.0..90.0 -> second to first
        first in -90.0..90.0 && second in -180.0..180.0 -> first to second
        else -> null
    }
}
