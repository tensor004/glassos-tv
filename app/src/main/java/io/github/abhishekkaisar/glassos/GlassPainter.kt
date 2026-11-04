package io.github.abhishekkaisar.glassos

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/**
 * Draws a Liquid Glass style rounded rect. Compared with frosted glass it is clearer and has depth:
 *  - the pre-blurred wallpaper behind the shape, lightly blurred so colours and shapes still read through;
 *  - a "lens" band along the edge that shows the wallpaper magnified, so light seems to bend at the rim;
 *  - a faint tint, almost no grain, a soft top sheen;
 *  - a rim lit at opposite corners (bright top-left and bottom-right, dim between), with a soft inner glow.
 * No per-frame blur: the blur was done once. Each painter owns its own shaders, because on API 25
 * shader matrices are shared by reference.
 */
class GlassPainter {
    var tint = 0.14f
    var tintColor = Color.WHITE
    var dark = 0f
    var border = 0.26f
    var highlight = 0.42f
    var sheen = 0.09f
    var grain = 0.55f
    var stroke = 1.5f
    /** Strength of the edge lens (0 turns it off). */
    var lens = 1f
    /**
     * Draw the parts that do not follow the backdrop (dark, tint, grain, sheen, rim) from one cached bitmap: one
     * textured pass instead of five full-size passes per frame. For large, static glass (the shelf).
     */
    var cacheStatic = false

    /** A paint plus the bitmap its shader currently wraps. */
    private class Sampler {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        var bmp: Bitmap? = null
    }

    private val backA = Sampler()
    private val backB = Sampler()
    private val lensA = Sampler()
    private val lensB = Sampler()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = Grain.newShader() }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val m = Matrix()
    private val inv = Matrix()
    private val lm = Matrix()
    private val inset = RectF()
    private val ringPath = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val ringOuter = RectF()
    private val ringInner = RectF()
    private var ringW = -1f
    private var ringH = -1f
    private var ringRadius = -1f
    private var kL = -1f
    private var kT = -1f
    private var kW = -1f
    private var kH = -1f
    private var kSheen = -1f
    private var kHigh = -1f
    private var kBorder = -1f
    private var staticBmp: Bitmap? = null
    private var staticKey = ""
    private val staticPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private fun layer(c: Canvas, r: RectF, radius: Float, l: BackLayer, s: Sampler, zoom: Float, path: Path?) {
        val b = l.bmp ?: return
        if (b !== s.bmp) { s.bmp = b; s.paint.shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        lm.set(inv)
        lm.preConcat(l.m)
        if (zoom != 1f) lm.postScale(zoom, zoom, r.centerX(), r.centerY())
        s.paint.shader.setLocalMatrix(lm)
        s.paint.alpha = (l.alpha.coerceIn(0f, 1f) * 255).toInt()
        if (path != null) c.drawPath(path, s.paint) else c.drawRoundRect(r, radius, radius, s.paint)
    }

    /** The band along the inside of the edge where the lens shows the magnified wallpaper. */
    private fun updateRing(r: RectF, radius: Float) {
        if (r.width() == ringW && r.height() == ringH && radius == ringRadius) return
        ringW = r.width(); ringH = r.height(); ringRadius = radius
        val band = (minOf(r.width(), r.height()) * 0.16f).coerceAtMost(radius).coerceAtLeast(2f)
        ringPath.rewind()
        ringOuter.set(r)
        ringInner.set(r.left + band, r.top + band, r.right - band, r.bottom - band)
        ringPath.addRoundRect(ringOuter, radius, radius, Path.Direction.CW)
        if (ringInner.width() > 1f && ringInner.height() > 1f) {
            val ir = (radius - band).coerceAtLeast(0f)
            ringPath.addRoundRect(ringInner, ir, ir, Path.Direction.CW)
        }
    }

    fun draw(c: Canvas, v: View, r: RectF, radius: Float) {
        val a = Backdrop.a
        if (a.bmp != null) {
            viewToWindow(v, m)
            m.invert(inv)
            layer(c, r, radius, a, backA, 1f, null)
            val b = Backdrop.b
            val hasB = b.bmp != null && b.alpha > 0.004f
            if (hasB) layer(c, r, radius, b, backB, 1f, null)
            if (lens > 0f) {
                updateRing(r, radius)
                layer(c, r, radius, a, lensA, LENS_ZOOM, ringPath)
                if (hasB) layer(c, r, radius, b, lensB, LENS_ZOOM, ringPath)
            }
        } else {
            fill.color = 0xFF141A30.toInt()
            c.drawRoundRect(r, radius, radius, fill)
        }
        if (cacheStatic) { drawStatic(c, r, radius); return }
        drawOverlay(c, r, radius)
    }

    private fun drawStatic(c: Canvas, r: RectF, radius: Float) {
        val sk = "${r.left},${r.top},${r.width()},${r.height()},$radius,$dark,$tint,$tintColor,$grain,$sheen,$highlight,$border,$stroke"
        if (sk != staticKey || staticBmp == null) {
            staticKey = sk
            // Not recycled: the last recorded frame may still draw the old one.
            val bw = Math.ceil(r.width().toDouble()).toInt().coerceAtLeast(1)
            val bh = Math.ceil(r.height().toDouble()).toInt().coerceAtLeast(1)
            val b = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            val bc = Canvas(b)
            // Same coordinates as on screen, so the grain tile and gradients land exactly where they did.
            bc.translate(-r.left, -r.top)
            drawOverlay(bc, r, radius)
            staticBmp = b
        }
        c.drawBitmap(staticBmp!!, r.left, r.top, staticPaint)
    }

    private fun drawOverlay(c: Canvas, r: RectF, radius: Float) {
        if (dark > 0f) {
            // Liquid glass is clearer than frosted: keep only part of the darkening.
            fill.color = Color.argb((dark * 0.7f * 255).toInt(), 4, 8, 20)
            c.drawRoundRect(r, radius, radius, fill)
        }
        if (tint > 0f) {
            fill.color = alphaOf(tintColor, tint)
            c.drawRoundRect(r, radius, radius, fill)
        }
        if (grain > 0f) {
            // Grain is what makes glass look frosted, so it is kept very faint.
            grainPaint.alpha = (grain * 0.2f * 255).toInt()
            c.drawRoundRect(r, radius, radius, grainPaint)
        }
        if (r.left != kL || r.top != kT || r.width() != kW || r.height() != kH ||
            sheen != kSheen || highlight != kHigh || border != kBorder) {
            kL = r.left; kT = r.top; kW = r.width(); kH = r.height()
            kSheen = sheen; kHigh = highlight; kBorder = border
            sheenPaint.shader = if (sheen > 0f) LinearGradient(0f, r.top, 0f, r.top + r.height() * 0.6f,
                intArrayOf(alphaOf(Color.WHITE, sheen), alphaOf(Color.WHITE, sheen * 0.25f), 0), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP) else null
            // Lit at two opposite corners, dim in between: the look of a curved glass edge catching light.
            edge.shader = if (highlight + border > 0f) LinearGradient(r.left, r.top, r.right, r.bottom,
                intArrayOf(
                    alphaOf(Color.WHITE, highlight),
                    alphaOf(Color.WHITE, border * 0.35f),
                    alphaOf(Color.WHITE, border * 0.35f),
                    alphaOf(Color.WHITE, highlight * 0.7f)
                ),
                floatArrayOf(0f, 0.3f, 0.7f, 1f), Shader.TileMode.CLAMP) else null
        }
        if (sheenPaint.shader != null) c.drawRoundRect(r, radius, radius, sheenPaint)
        if (edge.shader != null) {
            // Soft inner glow just inside the rim, then the crisp rim itself.
            val gw = stroke * 4f
            val gh = gw / 2f
            inset.set(r.left + gh, r.top + gh, r.right - gh, r.bottom - gh)
            if (inset.width() > 0f && inset.height() > 0f) {
                edge.strokeWidth = gw
                edge.alpha = 46
                val gr = (radius - gh).coerceAtLeast(0f)
                c.drawRoundRect(inset, gr, gr, edge)
            }
            edge.strokeWidth = stroke
            edge.alpha = 255
            val h = stroke / 2f
            inset.set(r.left + h, r.top + h, r.right - h, r.bottom - h)
            c.drawRoundRect(inset, radius - h, radius - h, edge)
        }
    }

    private companion object {
        /** How much the lens band magnifies the wallpaper. */
        const val LENS_ZOOM = 1.12f
    }
}
