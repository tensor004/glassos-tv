#!/usr/bin/env python3
"""Restyles Control Center for the Night Scene home style (the tvOS 26 look).

Usage: python3 apply_cc.py <glassos source dir>
In the Night Scene style (Settings > Home style) Control Center is a corner panel over a blurred, darkened copy of
the wallpaper: eight dark pill tiles in two columns (icon, name, value; Wi-Fi, Bluetooth, Memory, Do Not Disturb,
Audio, Sleep Timer, Ambient Mode, Power Off) and a row of four square buttons (TV Settings, Controllers, GlassOS,
Accessibility). The focused tile turns white. With the Top Shelf style nothing changes. All actions stay the same.
Every edit must match exactly once, so a changed source stops the build instead of being patched half way.
"""
import sys, os

root = sys.argv[1]
path = os.path.join(root, "app/src/main/java/io/github/abhishekkaisar/glassos/ControlCenter.kt")
s = open(path, encoding="utf-8").read()


def rep(old, new):
    global s
    n = s.count(old)
    if n != 1:
        sys.exit("PATCH FAILED in ControlCenter.kt: expected 1 match, found %d:\n%s" % (n, old))
    s = s.replace(old, new)


# ---- three new glyphs ----
rep("NOTE, CHECK, CLOSE, OPEN }", "NOTE, CHECK, CLOSE, OPEN, BLUETOOTH, MEMORY, SPARKLE }")
rep("            Glyph.NONE -> {}\n",
    "            Glyph.NONE -> {}\n"
    "            Glyph.BLUETOOTH -> {\n"
    "                path.reset()\n"
    "                path.moveTo(cx - h * 0.55f, cy - h * 0.5f)\n"
    "                path.lineTo(cx + h * 0.5f, cy + h * 0.45f)\n"
    "                path.lineTo(cx, cy + h * 0.95f)\n"
    "                path.lineTo(cx, cy - h * 0.95f)\n"
    "                path.lineTo(cx + h * 0.5f, cy - h * 0.45f)\n"
    "                path.lineTo(cx - h * 0.55f, cy + h * 0.5f)\n"
    "                c.drawPath(path, p)\n"
    "            }\n"
    "            Glyph.MEMORY -> {\n"
    "                r.set(cx - h * 0.62f, cy - h * 0.62f, cx + h * 0.62f, cy + h * 0.62f)\n"
    "                c.drawRoundRect(r, h * 0.16f, h * 0.16f, p)\n"
    "                for (k in -1..1) {\n"
    "                    val x = cx + k * h * 0.36f\n"
    "                    c.drawLine(x, cy - h * 0.62f, x, cy - h * 0.95f, p)\n"
    "                    c.drawLine(x, cy + h * 0.62f, x, cy + h * 0.95f, p)\n"
    "                }\n"
    "                p.style = Paint.Style.FILL\n"
    "                r.set(cx - h * 0.26f, cy - h * 0.26f, cx + h * 0.26f, cy + h * 0.26f)\n"
    "                c.drawRoundRect(r, h * 0.06f, h * 0.06f, p)\n"
    "            }\n"
    "            Glyph.SPARKLE -> {\n"
    "                p.style = Paint.Style.FILL\n"
    "                for (st in 0..1) {\n"
    "                    val x = if (st == 0) cx - h * 0.18f else cx + h * 0.62f\n"
    "                    val y = if (st == 0) cy + h * 0.12f else cy - h * 0.62f\n"
    "                    val a = if (st == 0) h * 0.82f else h * 0.34f\n"
    "                    path.reset()\n"
    "                    path.moveTo(x, y - a)\n"
    "                    path.quadTo(x, y, x + a, y)\n"
    "                    path.quadTo(x, y, x, y + a)\n"
    "                    path.quadTo(x, y, x - a, y)\n"
    "                    path.quadTo(x, y, x, y - a)\n"
    "                    path.close()\n"
    "                    c.drawPath(path, p)\n"
    "                }\n"
    "            }\n")

# ---- CcButton: pill and dark square styles ----
rep("               private val square: Boolean = false, private val labelH: Int = ctx.dpi(30f)) : View(ctx) {",
    "               private val square: Boolean = false, private val labelH: Int = ctx.dpi(30f),\n"
    "               private val pill: Boolean = false, private val dark: Boolean = false) : View(ctx) {")

rep("    var onClick: (() -> Unit)? = null\n    var onFocus: (() -> Unit)? = null\n\n    private val focus = FocusAnim(this, 1.1f) { invalidate() }",
    "    var onClick: (() -> Unit)? = null\n    var onFocus: (() -> Unit)? = null\n"
    "    /** Night Scene pill: the second line when [text] is empty, the colour of the icon, and an optional bar (0..1). */\n"
    "    var sub: String? = null\n        set(v) { if (field != v) { field = v; invalidate() } }\n"
    "    var tint = 0\n        set(v) { if (field != v) { field = v; invalidate() } }\n"
    "    var progress = -1f\n        set(v) { if (field != v) { field = v; invalidate() } }\n\n"
    "    private val focus = FocusAnim(this, if (pill) 1.035f else 1.1f) { invalidate() }")

rep("    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(cellW, size + labelH)\n",
    "    private val pillText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.medium; textSize = size * 0.25f }\n"
    "    private val pillValue = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.regular; textSize = size * 0.205f }\n"
    "\n"
    "    /** Night Scene tile: a dark stadium, icon, name over a value, optional bar; white with dark text when focused. */\n"
    "    private fun drawPill(c: Canvas, f: Float) {\n"
    "        val w = width.toFloat()\n"
    "        val h = size.toFloat()\n"
    "        rect.set(0f, 0f, w, h)\n"
    "        val rad = h / 2f\n"
    "        val rest = if (on) 0xB0707078.toInt() else if (current) 0xB3303034.toInt() else 0x99121316.toInt()\n"
    "        fill.color = lerpColor(rest, 0xFAFFFFFF.toInt(), f)\n"
    "        c.drawRoundRect(rect, rad, rad, fill)\n"
    "        gl.style = Paint.Style.STROKE\n"
    "        if (f < 0.99f) {\n"
    "            gl.strokeWidth = dp(1.3f)\n"
    "            gl.color = alphaOf(Color.WHITE, 0.30f * (1 - f))\n"
    "            val hs = gl.strokeWidth / 2\n"
    "            rect.inset(hs, hs)\n"
    "            c.drawRoundRect(rect, rad - hs, rad - hs, gl)\n"
    "        }\n"
    "        gl.style = Paint.Style.FILL\n"
    "        val col = lerpColor(Color.WHITE, 0xFF1C1C1E.toInt(), f)\n"
    "        Glyphs.draw(c, glyph, h * 0.50f, h / 2f, h * 0.40f, if (tint != 0) tint else col, gl)\n"
    "        gl.style = Paint.Style.FILL\n"
    "        val x = h * 0.88f\n"
    "        val room = (w - x - h * 0.34f).coerceAtLeast(h)\n"
    "        val value = text ?: if (on) \"On\" else sub\n"
    "        pillText.color = col\n"
    "        val title = TextUtils.ellipsize(label, pillText, room, TextUtils.TruncateAt.END).toString()\n"
    "        if (value == null) {\n"
    "            c.drawText(title, x, h / 2f - (pillText.ascent() + pillText.descent()) / 2, pillText)\n"
    "            return\n"
    "        }\n"
    "        pillValue.color = (col and 0xFFFFFF) or 0xB3000000.toInt()\n"
    "        val th = pillText.descent() - pillText.ascent()\n"
    "        val vh = pillValue.descent() - pillValue.ascent()\n"
    "        val gap = h * 0.03f\n"
    "        val bar = if (progress >= 0f) h * 0.11f else 0f\n"
    "        val top = (h - (th + gap + vh + bar)) / 2f\n"
    "        c.drawText(title, x, top - pillText.ascent(), pillText)\n"
    "        val v = TextUtils.ellipsize(value, pillValue, room, TextUtils.TruncateAt.END).toString()\n"
    "        c.drawText(v, x, top + th + gap - pillValue.ascent(), pillValue)\n"
    "        if (progress >= 0f) {\n"
    "            val by = top + th + gap + vh + h * 0.05f\n"
    "            rect.set(x, by, x + room, by + h * 0.055f)\n"
    "            fill.color = (col and 0xFFFFFF) or 0x40000000\n"
    "            c.drawRoundRect(rect, h * 0.03f, h * 0.03f, fill)\n"
    "            rect.set(x, by, x + room * progress.coerceIn(0f, 1f), by + h * 0.055f)\n"
    "            fill.color = col\n"
    "            c.drawRoundRect(rect, h * 0.03f, h * 0.03f, fill)\n"
    "        }\n"
    "    }\n"
    "\n"
    "    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(cellW, size + labelH)\n")

rep("        val f = focus.amount\n        val s = size.toFloat()\n        val left = (width - s) / 2f\n",
    "        val f = focus.amount\n        if (pill) { drawPill(c, f); return }\n        val s = size.toFloat()\n        val left = (width - s) / 2f\n")

rep("            fill.color = alphaOf(Color.WHITE, rest + (0.97f - rest) * f)\n",
    "            fill.color = if (dark) lerpColor(0x99121316.toInt(), 0xFAFFFFFF.toInt(), f)\n"
    "                else alphaOf(Color.WHITE, rest + (0.97f - rest) * f)\n")

rep("                gl.strokeWidth = dp(0.6f)\n                gl.color = alphaOf(Color.WHITE, 0.22f * (1 - f))\n",
    "                gl.strokeWidth = if (dark) dp(1.3f) else dp(0.6f)\n                gl.color = alphaOf(Color.WHITE, (if (dark) 0.30f else 0.22f) * (1 - f))\n")

# ---- ControlCenter: blurred dark background ----
rep("    private val dim = View(context).apply { setBackgroundColor(0x47000000) }\n",
    "    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)\n"
    "    /** Behind the panel: a light dim, or in the Night Scene style the blurred wallpaper under a dark veil. */\n"
    "    private val dim = object : View(context) {\n"
    "        override fun onDraw(c: Canvas) {\n"
    "            val bmp = Backdrop.a.bmp\n"
    "            if (NightStyle.isOn(context) && bmp != null) {\n"
    "                c.drawBitmap(bmp, Backdrop.a.m, dimPaint)\n"
    "                c.drawColor(0xA8000000.toInt())\n"
    "            } else c.drawColor(0x47000000)\n"
    "        }\n"
    "    }\n")

rep("    private val panelW = dpi(372f)\n",
    "    private val nightW get() = Backdrop.screenW * 586 / 1920\n"
    "    private val panelW get() = if (NightStyle.isOn(context)) nightW else dpi(372f)\n")

# the glass panel itself disappears in the Night Scene style: the tiles float on the blur
rep("            glass.dark = (0.36f + g.dark * 0.7f).coerceAtMost(0.66f)\n            super.onDraw(c)\n",
    "            glass.dark = (0.36f + g.dark * 0.7f).coerceAtMost(0.66f)\n            if (!NightStyle.isOn(context)) super.onDraw(c)\n")

rep("        isOpen = true\n        returnFocus = from\n        buildTabs()\n",
    "        isOpen = true\n        returnFocus = from\n        applyLayout()\n        buildTabs()\n")

rep("    // ---------- Open and close ----------\n",
    "    /** Top right corner either way; the Night Scene style has no glass panel, tab row or title around the tiles. */\n"
    "    private fun applyLayout() {\n"
    "        val night = NightStyle.isOn(context)\n"
    "        val sw = Backdrop.screenW\n"
    "        val sh = Backdrop.screenH\n"
    "        val lp = panel.layoutParams as LayoutParams\n"
    "        lp.width = panelW\n"
    "        lp.rightMargin = if (night) sw * 36 / 1920 else sw * 48 / 1920\n"
    "        lp.topMargin = if (night) sh * 112 / 1080 else sh * 36 / 1080\n"
    "        panel.layoutParams = lp\n"
    "        val pad = dpi(22f)\n"
    "        if (night) column.setPadding(0, 0, 0, 0) else column.setPadding(pad, dpi(18f), pad, dpi(14f))\n"
    "        tabRow.visibility = if (night) View.GONE else View.VISIBLE\n"
    "        title.visibility = if (night) View.GONE else View.VISIBLE\n"
    "        (body.layoutParams as LinearLayout.LayoutParams).topMargin = if (night) 0 else dpi(10f)\n"
    "    }\n\n"
    "    // ---------- Open and close ----------\n")

# ---- grids ----
rep("    private val cell get() = (panelW - dpi(44f)) / 4\n",
    "    private val cell get() = if (NightStyle.isOn(context)) nightW / 4 else (panelW - dpi(44f)) / 4\n"
    "    private val pillH get() = Backdrop.screenH * 112 / 1080\n"
    "    private val pillGap get() = Backdrop.screenW * 22 / 1920\n"
    "    private val pillW get() = (nightW - pillGap) / 2\n"
    "    private val sqS get() = Backdrop.screenW * 130 / 1920\n")

rep("    private fun button(g: Glyph, label: String, click: () -> Unit) =",
    "    private fun addCentered(v: View) {\n"
    "        body.addView(v, LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL))\n"
    "    }\n\n"
    "    /** A General tab button: a round one normally, a wide pill (or with [small] a square) in the Night Scene style. */\n"
    "    private fun tile(g: Glyph, label: String, small: Boolean = false, click: () -> Unit): CcButton {\n"
    "        if (!NightStyle.isOn(context)) return button(g, label, click)\n"
    "        val b = if (small) CcButton(context, sqS, sqS, g, \"\", square = true, labelH = 0, dark = true)\n"
    "            else CcButton(context, pillH, pillW, g, label, labelH = 0, pill = true)\n"
    "        b.id = View.generateViewId()\n"
    "        b.onClick = click\n"
    "        return b\n"
    "    }\n\n"
    "    /** Two columns of pills with a row of four square buttons under them. */\n"
    "    private fun nightGrid(pills: List<CcButton>, smalls: List<CcButton>): LinearLayout {\n"
    "        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }\n"
    "        gridTop = pills.take(2)\n"
    "        pills.chunked(2).forEachIndexed { r, row ->\n"
    "            val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }\n"
    "            row.forEachIndexed { i, b -> line.addView(b, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) leftMargin = pillGap }) }\n"
    "            col.addView(line, LinearLayout.LayoutParams(-2, -2).apply { if (r > 0) topMargin = pillGap })\n"
    "        }\n"
    "        val sq = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }\n"
    "        val between = if (smalls.size > 1) (nightW - smalls.size * sqS) / (smalls.size - 1) else 0\n"
    "        smalls.forEachIndexed { i, b -> sq.addView(b, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) leftMargin = between }) }\n"
    "        col.addView(sq, LinearLayout.LayoutParams(-2, -2).apply { topMargin = pillGap })\n"
    "        return col\n"
    "    }\n\n"
    "    private var memButton: CcButton? = null\n\n"
    "    private fun memInfo(b: CcButton) {\n"
    "        try {\n"
    "            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager\n"
    "            val mi = android.app.ActivityManager.MemoryInfo()\n"
    "            am.getMemoryInfo(mi)\n"
    "            val used = 1f - mi.availMem / mi.totalMem.toFloat()\n"
    "            b.sub = \"${(used * 100).toInt()}% used\"\n"
    "            b.progress = used\n"
    "        } catch (e: Throwable) { b.sub = null; b.progress = -1f }\n"
    "    }\n\n"
    "    private fun btState(): String? = try {\n"
    "        when (android.provider.Settings.Global.getInt(context.contentResolver, \"bluetooth_on\", -1)) { 1 -> \"On\"; 0 -> \"Off\"; else -> null }\n"
    "    } catch (e: Throwable) { null }\n\n"
    "    private fun button(g: Glyph, label: String, click: () -> Unit) =")

# ---- General tab ----
rep('        val power = button(Glyph.POWER, "Sleep") {', '        val power = tile(Glyph.POWER, "Sleep") {')
rep("        val wifi = button(Glyph.WIFI, Net.networkName(ctx)) {", "        val wifi = tile(Glyph.WIFI, Net.networkName(ctx)) {")
rep('        val dnd = button(Glyph.MOON, "Do Not Disturb") { toggleDnd() }', '        val dnd = tile(Glyph.MOON, "Do Not Disturb") { toggleDnd() }')
rep('        val audio = button(Glyph.SPEAKER, "Audio") {', '        val audio = tile(Glyph.SPEAKER, "Audio") {')
rep('        val timer = button(Glyph.TIMER, "Sleep Timer") { showTimerChoices() }', '        val timer = tile(Glyph.TIMER, "Sleep Timer") { showTimerChoices() }')
rep('        val pads = button(Glyph.GAMEPAD, "Controllers") {', '        val pads = tile(Glyph.GAMEPAD, "Controllers", small = true) {')
rep('        val acc = button(Glyph.ACCESS, "Accessibility") {', '        val acc = tile(Glyph.ACCESS, "Accessibility", small = true) {')
rep('            else button(Glyph.LOCK, "Parental") {', '            else tile(Glyph.LOCK, "Parental") {')
rep('        val fire = button(Glyph.OPEN, "TV Settings") {', '        val fire = tile(Glyph.OPEN, "TV Settings", small = true) {')
rep('        val gear = button(Glyph.GEAR, "GlassOS") {', '        val gear = tile(Glyph.GEAR, "GlassOS", small = true) {')
rep("        body.addView(grid(listOfNotNull(power, wifi, dnd, audio, timer, pads, acc, pc, fire, gear)))\n",
    "        if (NightStyle.isOn(ctx)) {\n"
    "            val blue = 0xFF0A84FF.toInt()\n"
    "            wifi.label = \"Wi-Fi\"; wifi.sub = Net.networkName(ctx); wifi.tint = blue\n"
    "            dnd.sub = \"Off\"\n"
    "            audio.sub = \"Sound & output\"\n"
    "            timer.sub = \"Off\"\n"
    "            power.label = \"Power Off\"; power.sub = \"Screen Off\"; power.tint = 0xFFFF453A.toInt()\n"
    "            val bt = tile(Glyph.BLUETOOTH, \"Bluetooth\") { openStd(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS) }\n"
    "            bt.tint = blue; bt.sub = btState()\n"
    "            val mem = tile(Glyph.MEMORY, \"Memory\") { memButton?.let { memInfo(it) } }\n"
    "            memButton = mem\n"
    "            memInfo(mem)\n"
    "            val ambient = tile(Glyph.SPARKLE, \"Ambient Mode\") { openStd(android.provider.Settings.ACTION_DREAM_SETTINGS) }\n"
    "            ambient.sub = \"Screensaver\"; ambient.tint = 0xFFFFD60A.toInt()\n"
    "            addCentered(nightGrid(listOf(wifi, bt, mem, dnd, audio, timer, ambient, power), listOf(fire, pads, gear, acc)))\n"
    "        } else addCentered(grid(listOfNotNull(power, wifi, dnd, audio, timer, pads, acc, pc, fire, gear)))\n")
rep("        body.addView(grid(items))\n", "        addCentered(grid(items))\n")

open(path, "w", encoding="utf-8").write(s)
print("Control Center restyle: all edits applied")
