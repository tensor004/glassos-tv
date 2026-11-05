package io.github.abhishekkaisar.glassos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import android.graphics.Outline
import android.view.ViewOutlineProvider

/**
 * The Night Scene home style: a still wallpaper, a greeting with the date and weather, a very large clock and
 * one glass dock with every app at the bottom. It replaces the Top Shelf and the app drawer while it is on
 * (Settings > Home style). Written for GlassOS; the look is inspired by tvOS 26 and by other open-source TV
 * launchers, but no code is copied from them.
 */
object NightStyle {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("glass_night", Context.MODE_PRIVATE)

    fun isOn(ctx: Context): Boolean = sp(ctx).getBoolean("on", false)
    fun setOn(ctx: Context, v: Boolean) { sp(ctx).edit().putBoolean("on", v).apply() }

    /** The city typed for the weather line; empty means no weather and no network request at all. */
    fun city(ctx: Context): String = sp(ctx).getString("city", "") ?: ""
    fun setCity(ctx: Context, v: String) {
        sp(ctx).edit().putString("city", v.trim()).remove("wx").remove("wxAt").apply()
    }
}

/** Weather for the typed city from wttr.in, kept for 30 minutes. Sends the city name and nothing else. */
object NightWeather {
    private val io = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private const val FRESH_MS = 30 * 60 * 1000L

    /** [done] gets the text ("8° Foggy") or null, on the main thread. A cached value is delivered first. */
    fun refresh(ctx: Context, force: Boolean, done: (String?) -> Unit) {
        val app = ctx.applicationContext
        val city = NightStyle.city(app)
        if (city.isEmpty()) { done(null); return }
        val sp = app.getSharedPreferences("glass_night", Context.MODE_PRIVATE)
        val cached = sp.getString("wx", null)
        val at = sp.getLong("wxAt", 0L)
        val fresh = cached != null && System.currentTimeMillis() - at < FRESH_MS
        if (cached != null) done(cached)
        if (fresh && !force) return
        io.execute {
            val text = try { fetch(city) } catch (e: Throwable) { null }
            if (text != null) sp.edit().putString("wx", text).putLong("wxAt", System.currentTimeMillis()).apply()
            ui.post { if (text != null) done(text) }
        }
    }

    private fun fetch(city: String): String? {
        val q = URLEncoder.encode(city, "UTF-8").replace("+", "%20")
        val c = URL("https://wttr.in/$q?format=j1").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 8000
            c.readTimeout = 10000
            c.setRequestProperty("User-Agent", "GlassOS")
            if (c.responseCode != 200) return null
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val cur = JSONObject(body).getJSONArray("current_condition").getJSONObject(0)
            val temp = cur.getString("temp_C")
            val desc = cur.getJSONArray("weatherDesc").getJSONObject(0).getString("value").trim()
            return "$temp° $desc"
        } finally {
            c.disconnect()
        }
    }
}

/** The large clock: thin digits with a soft white-to-lilac fade, like the tvOS 26 lock screen. */
class BigClock(ctx: Context) : View(ctx) {
    var text = ""
        set(v) { if (v != field) { field = v; invalidate() } }
    private val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Fonts.light
        textAlign = Paint.Align.CENTER
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
    p.shader = LinearGradient(
        0f, 0f, 0f, h.toFloat(),
        0xF8FFFFFF.toInt(),
        0xBFE6D2F4.toInt(),
        Shader.TileMode.CLAMP
    )
    p.textSize = h * 0.70f
}

 override fun onDraw(c: Canvas) {
    c.drawText(text, width / 2f, height * 0.76f, p)
 }
}

/** A square app icon in the dock: rounded, a white ring and a small spring when focused. */
class DockIcon(ctx: Context, private val size: Int, val label: String, val pkg: String?, private val gear: Boolean = false) : View(ctx) {
    var onClick: (() -> Unit)? = null
    var onFocus: ((Boolean) -> Unit)? = null
    var bitmap: Bitmap? = null
        set(v) {
            field = v
            buildShader()
            invalidate()
        }

    private val focus = FocusAnim(this, 1.14f) { invalidate() }
    private val rect = RectF()
    private val art = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val glyph = Paint(Paint.ANTI_ALIAS_FLAG)
    private val m = Matrix()

    init {
        isFocusable = true
        isClickable = true
        setOnClickListener { onClick?.invoke() }
        if (gear) fill.shader = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), 0xFF5C5C61.toInt(), 0xFF2E2E31.toInt(), Shader.TileMode.CLAMP)
        else fill.color = 0x33FFFFFF
    }

    private fun buildShader() {
        val b = bitmap
        if (b == null) { art.shader = null; return }
        val sh = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val sc = maxOf(size / b.width.toFloat(), size / b.height.toFloat())
        m.setScale(sc, sc)
        m.postTranslate((size - b.width * sc) / 2f, (size - b.height * sc) / 2f)
        sh.setLocalMatrix(m)
        art.shader = sh
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(size, size)

    override fun onFocusChanged(gained: Boolean, direction: Int, previous: Rect?) {
        super.onFocusChanged(gained, direction, previous)
        focus.set(gained)
        onFocus?.invoke(gained)
    }

    override fun onDraw(c: Canvas) {
        val r = size * 0.23f
        rect.set(0f, 0f, size.toFloat(), size.toFloat())
        if (gear || art.shader == null) c.drawRoundRect(rect, r, r, fill)
        if (gear) Glyphs.draw(c, Glyph.GEAR, size / 2f, size / 2f, size * 0.46f, Color.WHITE, glyph)
        else if (art.shader != null) c.drawRoundRect(rect, r, r, art)
        val f = focus.amount
        if (f > 0f) {
            ring.strokeWidth = dp(3.5f)
            ring.color = alphaOf(Color.WHITE, f)
            val h = ring.strokeWidth / 2f
            rect.set(h, h, size - h, size - h)
            c.drawRoundRect(rect, r - h, r - h, ring)
        }
    }
}

/** The round glass button at the top right (opens Control Center). */
class NightButton(ctx: Context, private val dia: Int, private val glyphType: Glyph) : GlassBox(ctx, dia / 2f) {
    var onClick: (() -> Unit)? = null
    private val focus = FocusAnim(this, 1.12f) { invalidate() }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val gp = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isFocusable = true
        isClickable = true
        setOnClickListener { onClick?.invoke() }
        glass.tint = 0.06f
        glass.dark = 0.26f
        glass.sheen = 0.08f
        glass.highlight = 0.55f
        glass.border = 0.12f
        shadowAlpha = 0.2f
    }

    override fun onFocusChanged(gained: Boolean, direction: Int, previous: Rect?) {
        super.onFocusChanged(gained, direction, previous)
        focus.set(gained)
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val f = focus.amount
        if (f > 0f) {
            ring.strokeWidth = dp(3f)
            ring.color = alphaOf(Color.WHITE, f)
            c.drawCircle(width / 2f, height / 2f, width / 2f - ring.strokeWidth / 2f, ring)
        }
        Glyphs.draw(c, glyphType, width / 2f, height / 2f, width * 0.40f, Color.WHITE, gp)
    }
}

internal class DockScroll(ctx: Context) : HorizontalScrollView(ctx) {
    var onScrolled: (() -> Unit)? = null
    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrolled?.invoke()
    }
}

class NightHome(private val home: HomeScreen) : FrameLayout(home.ctx) {
    val active: Boolean get() = NightStyle.isOn(context)
    val prompt = CityPrompt(home)

    private val sw = Backdrop.screenW
    private val sh = Backdrop.screenH
    private val main = Handler(Looper.getMainLooper())

    private val hello = TextView(context).apply {
        textSize = 21f; typeface = Fonts.medium; setTextColor(0xCCFFFFFF.toInt()); gravity = Gravity.CENTER
        setShadowLayer(dp(8f), 0f, dp(1f), 0x66000000)
    }
    private val dateLine = TextView(context).apply {
        textSize = 25f; typeface = Fonts.medium; setTextColor(0xFFFFFFFF.toInt()); gravity = Gravity.CENTER
        setShadowLayer(dp(8f), 0f, dp(1f), 0x66000000)
    }
    private val clock = BigClock(context)
    private val iconPx = (sh * 108 / 1080)
    private val gapPx = (sh * 20 / 1080)
    private val padH = (sh * 60 / 1080)
    private val barH = (sh * 144 / 1080)
    private val barMaxW = (sw * 75 / 100)

    private val bar = GlassBox(context, sh * 44f / 1080f).apply {
    glass.tint = 0.04f
    glass.dark = 0.30f
    glass.sheen = 0.06f
    glass.highlight = 0.55f
    glass.border = 0.12f
    glass.cacheStatic = true
    shadowAlpha = 0.22f

    clipChildren = true
    clipToPadding = true
    clipToOutline = true
    outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(
                0, 0, view.width, view.height,
                sh * 44f / 1080f
            )
        }
    }
}
    private val scroll = DockScroll(context).apply {
        isHorizontalScrollBarEnabled = false
        isHorizontalFadingEdgeEnabled = false
        setFadingEdgeLength(0)
        overScrollMode = View.OVER_SCROLL_NEVER
        isFillViewport = true
        clipChildren = true
        clipToPadding = true
    }
    private val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        clipChildren = true
        clipToPadding = true
    }
    private val tooltip = TextView(context).apply {
        textSize = 15f
        typeface = Fonts.medium
        setTextColor(Color.WHITE)
        setPadding(dpi(16f), dpi(8f), dpi(16f), dpi(9f))
        background = GradientDrawable().apply { setColor(0xF01E1E20.toInt()); cornerRadius = dp(12f) }
        visibility = View.INVISIBLE
        maxLines = 1
    }
    private val button = NightButton(context, sh * 82 / 1080, Glyph.TOGGLES)

    private var shownPkgs: List<String> = emptyList()
    private var weather: String? = null
    private var lastFocus: DockIcon? = null
    private var focusedIcon: DockIcon? = null

    init {
        clipChildren = false
        clipToPadding = false
        val greeting = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        greeting.addView(hello, LinearLayout.LayoutParams(-2, -2))
        greeting.addView(dateLine, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(2f) })
        addView(greeting, LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = sh * 50 / 1080 })
        addView(clock, LayoutParams(sw / 2, sh * 300 / 1080, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
    topMargin = sh * 220 / 1080
})

        row.setPadding(padH, 0, padH, 0)
        scroll.addView(row, FrameLayout.LayoutParams(-2, -1))
        scroll.onScrolled = { focusedIcon?.let { placeTooltip(it) } }
        bar.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        addView(bar, LayoutParams(barMaxW, barH, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = sh * 28 / 1080 })

        button.onClick = { home.openControlCenter() }
        addView(button, LayoutParams(sh * 82 / 1080, sh * 82 / 1080, Gravity.TOP or Gravity.END).apply {
            rightMargin = sw * 58 / 1920; topMargin = sh * 50 / 1080
        })
        addView(tooltip, LayoutParams(-2, -2, Gravity.TOP or Gravity.START))
        updateTime()
    }

    // ---------- Text ----------

    fun updateTime() {
        val now = Date()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val part = when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..20 -> "Good evening"
            else -> "Good night"
        }
        val name = home.prefs.name.trim()
        hello.text = if (name.isEmpty()) part else "$part, $name"
        val date = SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(now)
        val wx = weather
        dateLine.text = if (wx.isNullOrEmpty()) date else "$date  ·  $wx"
        val fmt = if (android.text.format.DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
        clock.text = android.text.format.DateFormat.format(fmt, now.time).toString()
    }

    fun refreshWeather(force: Boolean) {
        NightWeather.refresh(context, force) { text ->
            weather = text
            updateTime()
        }
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE && active) { updateTime(); refreshWeather(false) }
    }

    // ---------- Dock ----------

    /** Same order as the old home: the top row first, then every other app. Hidden apps stay hidden. */
    fun setApps(list: List<AppEntry>) {
        val prefs = home.prefs
        val hidden = prefs.hidden
        val visible = if (hidden.isEmpty()) list else list.filter { it.pkg !in hidden }
        val shown = visible.map { it.pkg }.toSet()
        val top = (prefs.dock ?: AppRepo.defaultDock(visible)).filter { it in shown }.distinct()
        val rest = AppRepo.arrange(visible.filter { it.pkg !in top }, prefs.order).map { it.pkg }
        val byPkg = visible.associateBy { it.pkg }
        val order = (top + rest).mapNotNull { byPkg[it] }
        val keys = order.map { it.pkg }
        if (keys == shownPkgs && row.childCount > 0) return
        shownPkgs = keys
        val hadFocus = scroll.hasFocus()
        val keep = focusedIcon?.pkg
        row.removeAllViews()
        focusedIcon = null
        lastFocus = null
        val missing = ArrayList<Pair<DockIcon, String>>()
        for (e in order) {
            val icon = DockIcon(context, iconPx, e.label, e.pkg)
            if (!e.isBanner && e.art != null) icon.bitmap = e.art else missing.add(icon to e.pkg)
            icon.onClick = { home.launch(e.pkg, icon) }
            wire(icon)
            addIcon(icon)
        }
        val gear = DockIcon(context, iconPx, "Settings", null, gear = true)
        gear.onClick = { home.openSettings() }
        wire(gear)
        addIcon(gear)
        val n = order.size + 1
        val content = n * iconPx + (n - 1) * gapPx + 2 * padH
        val lp = bar.layoutParams
        lp.width = minOf(content, barMaxW)
        bar.layoutParams = lp
        if (missing.isNotEmpty()) loadIcons(order, missing)
        if (hadFocus) {
            val again = (0 until row.childCount).map { row.getChildAt(it) as DockIcon }
            (again.firstOrNull { it.pkg != null && it.pkg == keep } ?: again.firstOrNull())?.requestFocus()
        }
        bar.invalidate()
    }

    private fun addIcon(icon: DockIcon) {
        val lp = LinearLayout.LayoutParams(iconPx, iconPx)
        if (row.childCount > 0) lp.leftMargin = gapPx
        row.addView(icon, lp)
    }

    private fun wire(icon: DockIcon) {
        icon.onFocus = { gained ->
            if (gained) {
                focusedIcon = icon
                lastFocus = icon
                showTooltip(icon)
            } else if (focusedIcon === icon) {
                focusedIcon = null
                tooltip.visibility = View.INVISIBLE
            }
        }
    }

    /** Apps whose art is a wide banner get their launcher icon instead, loaded off the main thread. */
    private fun loadIcons(order: List<AppEntry>, missing: List<Pair<DockIcon, String>>) {
        val byPkg = order.associateBy { it.pkg }
        val pm = context.packageManager
        val px = iconPx
        Thread {
            for ((icon, pkg) in missing) {
                val b: Bitmap? = try {
                    val d = pm.getApplicationIcon(pkg)
                    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
                    d.setBounds(0, 0, px, px)
                    d.draw(Canvas(bmp))
                    bmp
                } catch (e: Throwable) { null }
                main.post { icon.bitmap = b ?: byPkg[pkg]?.art }
            }
        }.apply { priority = Thread.MIN_PRIORITY; isDaemon = true }.start()
    }

    private fun showTooltip(icon: DockIcon) {
        tooltip.text = icon.label
        tooltip.visibility = View.VISIBLE
        placeTooltip(icon)
        main.postDelayed({ if (focusedIcon === icon) placeTooltip(icon) }, 380)
    }

    private fun placeTooltip(icon: DockIcon) {
        tooltip.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val a = IntArray(2)
        val b = IntArray(2)
        icon.getLocationInWindow(a)
        getLocationInWindow(b)
        val cx = a[0] - b[0] + icon.width * icon.scaleX / 2f
        val top = a[1] - b[1]
        val w = tooltip.measuredWidth
        tooltip.translationX = (cx - w / 2f).coerceIn(dp(8f), (width - w - dp(8f)).coerceAtLeast(dp(8f)))
        tooltip.translationY = top - tooltip.measuredHeight - dp(10f)
    }

    fun invalidateGlass() {
        bar.invalidate()
        button.invalidate()
    }

    /** Focus goes to the icon that had it last, or the first one. */
    fun focusFirst() {
        val target = lastFocus?.takeIf { it.parent != null } ?: (if (row.childCount > 0) row.getChildAt(0) else null)
        (target ?: button).requestFocus()
    }
}

/**
 * Asks for the weather city on the D-pad keyboard (Settings > Weather city). Typing stays on the TV: the city name
 * is only sent to wttr.in when the weather is fetched.
 */
class CityPrompt(private val home: HomeScreen) : FrameLayout(home.ctx) {
    var isOpen = false
        private set
    private var text = ""
    private var onClosed: (() -> Unit)? = null

    private val dim = View(context).apply { setBackgroundColor(0xB3000000.toInt()) }
    private val panel = GlassBox(context, dp(30f)).apply {
        glass.tint = 0.10f
        glass.dark = 0.30f
        glass.sheen = 0.10f
        shadowAlpha = 0.5f
    }
    private var firstKey: () -> View? = { null }
    private val field = TextView(context).apply {
        textSize = 22f
        typeface = Fonts.regular
        setTextColor(Color.WHITE)
        hint = "City name"
        setHintTextColor(0x80FFFFFF.toInt())
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dpi(22f), 0, dpi(22f), 0)
        maxLines = 1
        background = GradientDrawable().apply { setColor(0x26FFFFFF); cornerRadius = dp(23f) }
    }

    init {
        visibility = View.GONE
        clipChildren = false
        addView(dim, LayoutParams(-1, -1))
        val col = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(dpi(28f), dpi(24f), dpi(28f), dpi(22f))
        }
        col.addView(TextView(context).apply {
            this.text = "Weather city"
            textSize = 26f; typeface = Fonts.medium; setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(-2, -2))
        col.addView(TextView(context).apply {
            this.text = "Shown next to the date on the Night Scene home."
            textSize = 16f; typeface = Fonts.regular; setTextColor(0x99FFFFFF.toInt())
        }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(4f) })
        col.addView(field, LinearLayout.LayoutParams(dpi(420f), dpi(46f)).apply { topMargin = dpi(14f) })
        val kb = Keyboard(context, digits = false) { k ->
            text = when (k) {
                Keyboard.DEL -> text.dropLast(1)
                Keyboard.CLEAR -> ""
                else -> if (text.length < 40) text + k else text
            }
            field.text = text
        }
        col.addView(kb, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(12f) })
        val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; clipChildren = false }
        fun button(label: String, click: () -> Unit): RowView {
            val b = RowView(context, label).apply {
                id = View.generateViewId()
                titleView.gravity = Gravity.CENTER
                titleView.textSize = 18f
                onClick = click
            }
            buttons.addView(b, LinearLayout.LayoutParams(dpi(164f), dpi(46f)).apply { leftMargin = dpi(8f); rightMargin = dpi(8f) })
            return b
        }
        button("Cancel") { close() }
        button("No weather") {
            NightStyle.setCity(context, "")
            close()
            home.night.refreshWeather(true)
        }
        val save = button("Save") {
            NightStyle.setCity(context, text)
            close()
            home.night.refreshWeather(true)
        }
        col.addView(buttons, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dpi(14f) })
        kb.downTo(save)
        panel.addView(col, LayoutParams(-2, -2))
        addView(panel, LayoutParams(-2, -2, Gravity.CENTER))
        firstKey = { kb.firstKey() }
    }

    fun open(closed: () -> Unit) {
        if (isOpen) return
        isOpen = true
        onClosed = closed
        text = NightStyle.city(context)
        field.text = text
        visibility = View.VISIBLE
        firstKey()?.requestFocus()
        invalidateDeep(panel)
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        visibility = View.GONE
        val c = onClosed
        onClosed = null
        c?.invoke()
    }
}
