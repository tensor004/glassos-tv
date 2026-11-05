#!/usr/bin/env python3
"""Restyles Control Center for the Night Scene home style.

Usage: python3 apply_cc.py <glassos source dir>
In the Night Scene style (Settings > Home style) Control Center becomes a centred panel on a blurred, darkened
copy of the wallpaper: dark pill tiles in two columns (icon, name, value) and a row of square icon buttons under
them; the focused tile turns white. With the Top Shelf style nothing changes. All actions stay the same.
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


# ---- CcButton: pill and dark square styles ----
rep("               private val square: Boolean = false, private val labelH: Int = ctx.dpi(30f)) : View(ctx) {",
    "               private val square: Boolean = false, private val labelH: Int = ctx.dpi(30f),\n"
    "               private val pill: Boolean = false, private val dark: Boolean = false) : View(ctx) {")

rep("    private val focus = FocusAnim(this, 1.1f) { invalidate() }\n    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)\n    private val gp = Paint()",
    "    private val focus = FocusAnim(this, if (pill) 1.035f else 1.1f) { invalidate() }\n    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)\n    private val gp = Paint()")

rep("    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(cellW, size + labelH)\n",
    "    private val pillText = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.medium; textSize = size * 0.30f }\n"
    "    private val pillValue = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.regular; textSize = size * 0.26f; textAlign = Paint.Align.RIGHT }\n"
    "\n"
    "    /** Night Scene tile: dark rounded rect, icon, name, and a value on the right; white with dark text when focused. */\n"
    "    private fun drawPill(c: Canvas, f: Float) {\n"
    "        val w = width.toFloat()\n"
    "        val h = size.toFloat()\n"
    "        rect.set(0f, 0f, w, h)\n"
    "        val rad = h * 0.42f\n"
    "        val rest = if (on) 0xCC4B4B50.toInt() else if (current) 0xB3303034.toInt() else 0xB31F1F22.toInt()\n"
    "        fill.color = lerpColor(rest, 0xF7FFFFFF.toInt(), f)\n"
    "        c.drawRoundRect(rect, rad, rad, fill)\n"
    "        gl.style = Paint.Style.STROKE\n"
    "        if (f < 0.99f) {\n"
    "            gl.strokeWidth = dp(0.8f)\n"
    "            gl.color = alphaOf(Color.WHITE, 0.14f * (1 - f))\n"
    "            c.drawRoundRect(rect, rad, rad, gl)\n"
    "        }\n"
    "        gl.style = Paint.Style.FILL\n"
    "        val col = lerpColor(Color.WHITE, 0xFF1C1C1E.toInt(), f)\n"
    "        if (label.isEmpty() && text == null) {\n"
    "            Glyphs.draw(c, glyph, w / 2f, h / 2f, h * 0.42f, col, gl)\n"
    "            return\n"
    "        }\n"
    "        Glyphs.draw(c, glyph, h * 0.52f, h / 2f, h * 0.40f, col, gl)\n"
    "        val x = h * 1.02f\n"
    "        var room = w - x - h * 0.4f\n"
    "        val value = text ?: if (on) \"On\" else null\n"
    "        if (value != null) {\n"
    "            pillValue.color = (col and 0xFFFFFF) or 0x99000000.toInt()\n"
    "            c.drawText(value, w - h * 0.4f, h / 2f - (pillValue.ascent() + pillValue.descent()) / 2, pillValue)\n"
    "            room -= pillValue.measureText(value) + h * 0.2f\n"
    "        }\n"
    "        pillText.color = col\n"
    "        val l = TextUtils.ellipsize(label, pillText, room.coerceAtLeast(h), TextUtils.TruncateAt.END).toString()\n"
    "        c.drawText(l, x, h / 2f - (pillText.ascent() + pillText.descent()) / 2, pillText)\n"
    "    }\n"
    "\n"
    "    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(cellW, size + labelH)\n")

rep("        val f = focus.amount\n        val s = size.toFloat()\n        val left = (width - s) / 2f\n",
    "        val f = focus.amount\n        if (pill) { drawPill(c, f); return }\n        val s = size.toFloat()\n        val left = (width - s) / 2f\n")

rep("            fill.color = alphaOf(Color.WHITE, rest + (0.97f - rest) * f)\n",
    "            fill.color = if (dark) lerpColor(if (on) 0xCC4B4B50.toInt() else 0xB31F1F22.toInt(), 0xF7FFFFFF.toInt(), f)\n"
    "                else alphaOf(Color.WHITE, rest + (0.97f - rest) * f)\n")

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
    "    private val nightW get() = Backdrop.screenW * 56 / 100\n"
    "    private val panelW get() = if (NightStyle.isOn(context)) nightW else dpi(372f)\n")

# the glass panel itself disappears in the Night Scene style: the tiles float on the blur
rep("            glass.dark = (0.36f + g.dark * 0.7f).coerceAtMost(0.66f)\n            super.onDraw(c)\n",
    "            glass.dark = (0.36f + g.dark * 0.7f).coerceAtMost(0.66f)\n            if (!NightStyle.isOn(context)) super.onDraw(c)\n")

rep("        isOpen = true\n        returnFocus = from\n        buildTabs()\n",
    "        isOpen = true\n        returnFocus = from\n        applyLayout()\n        buildTabs()\n")

rep("        panel.pivotX = panelW.toFloat()\n        panel.pivotY = 0f\n",
    "        val night = NightStyle.isOn(context)\n"
    "        panel.pivotX = if (night) panelW / 2f else panelW.toFloat()\n"
    "        panel.pivotY = if (night) Backdrop.screenH * 0.42f else 0f\n")

rep("        panel.scaleX = 0.35f; panel.scaleY = 0.2f\n",
    "        panel.scaleX = if (night) 0.92f else 0.35f; panel.scaleY = if (night) 0.92f else 0.2f\n")

rep("    // ---------- Open and close ----------\n",
    "    /** Corner panel for the Top Shelf style, centred and glassless for the Night Scene style. */\n"
    "    private fun applyLayout() {\n"
    "        val night = NightStyle.isOn(context)\n"
    "        val lp = panel.layoutParams as LayoutParams\n"
    "        lp.width = panelW\n"
    "        lp.gravity = if (night) Gravity.CENTER else Gravity.TOP or Gravity.END\n"
    "        lp.rightMargin = if (night) 0 else Backdrop.screenW * 48 / 1920\n"
    "        lp.topMargin = if (night) 0 else Backdrop.screenH * 36 / 1080\n"
    "        panel.layoutParams = lp\n"
    "        tabRow.gravity = if (night) Gravity.CENTER else Gravity.CENTER_VERTICAL\n"
    "        title.gravity = if (night) Gravity.CENTER_HORIZONTAL else Gravity.START\n"
    "        title.textSize = if (night) 19f else 15f\n"
    "        (title.layoutParams as LinearLayout.LayoutParams).width = if (night) -1 else -2\n"
    "    }\n\n"
    "    // ---------- Open and close ----------\n")

# ---- grids ----
rep("    private val cell get() = (panelW - dpi(44f)) / 4\n",
    "    private val cell get() = if (NightStyle.isOn(context)) dpi(96f) else (panelW - dpi(44f)) / 4\n"
    "    private val pillH get() = Backdrop.screenH * 88 / 1080\n"
    "    private val pillGap get() = Backdrop.screenH * 18 / 1080\n"
    "    private val pillW get() = (nightW - dpi(44f) - pillGap) / 2\n")

rep("    private fun button(g: Glyph, label: String, click: () -> Unit) =",
    "    private fun addCentered(v: View) {\n"
    "        body.addView(v, LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL))\n"
    "    }\n\n"
    "    /** A General tab button: a round one normally, a wide pill (or with [small] a square) in the Night Scene style. */\n"
    "    private fun tile(g: Glyph, label: String, small: Boolean = false, click: () -> Unit): CcButton {\n"
    "        if (!NightStyle.isOn(context)) return button(g, label, click)\n"
    "        val b = if (small) CcButton(context, pillH, pillH, g, \"\", square = true, labelH = 0, dark = true)\n"
    "            else CcButton(context, pillH, pillW, g, label, labelH = 0, pill = true)\n"
    "        b.id = View.generateViewId()\n"
    "        b.onClick = click\n"
    "        return b\n"
    "    }\n\n"
    "    /** Two columns of pills with a row of square buttons under them. */\n"
    "    private fun nightGrid(pills: List<CcButton>, smalls: List<CcButton>): LinearLayout {\n"
    "        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; clipChildren = false; clipToPadding = false }\n"
    "        gridTop = pills.take(2)\n"
    "        pills.chunked(2).forEachIndexed { r, row ->\n"
    "            val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }\n"
    "            row.forEachIndexed { i, b -> line.addView(b, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) leftMargin = pillGap }) }\n"
    "            col.addView(line, LinearLayout.LayoutParams(-2, -2).apply { if (r > 0) topMargin = pillGap })\n"
    "        }\n"
    "        val sq = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; clipChildren = false; clipToPadding = false }\n"
    "        smalls.forEachIndexed { i, b -> sq.addView(b, LinearLayout.LayoutParams(-2, -2).apply { if (i > 0) leftMargin = pillH / 2 }) }\n"
    "        col.addView(sq, LinearLayout.LayoutParams(-2, -2).apply { topMargin = pillGap * 2 })\n"
    "        return col\n"
    "    }\n\n"
    "    private fun button(g: Glyph, label: String, click: () -> Unit) =")

# ---- General tab ----
rep('        val power = button(Glyph.POWER, "Sleep") {', '        val power = tile(Glyph.POWER, "Sleep", small = true) {')
rep("        val wifi = button(Glyph.WIFI, Net.networkName(ctx)) {", "        val wifi = tile(Glyph.WIFI, Net.networkName(ctx)) {")
rep('        val dnd = button(Glyph.MOON, "Do Not Disturb") { toggleDnd() }', '        val dnd = tile(Glyph.MOON, "Do Not Disturb") { toggleDnd() }')
rep('        val audio = button(Glyph.SPEAKER, "Audio") {', '        val audio = tile(Glyph.SPEAKER, "Audio") {')
rep('        val timer = button(Glyph.TIMER, "Sleep Timer") { showTimerChoices() }', '        val timer = tile(Glyph.TIMER, "Sleep Timer") { showTimerChoices() }')
rep('        val pads = button(Glyph.GAMEPAD, "Controllers") {', '        val pads = tile(Glyph.GAMEPAD, "Controllers") {')
rep('        val acc = button(Glyph.ACCESS, "Accessibility") {', '        val acc = tile(Glyph.ACCESS, "Accessibility") {')
rep('            else button(Glyph.LOCK, "Parental") {', '            else tile(Glyph.LOCK, "Parental") {')
rep('        val fire = button(Glyph.OPEN, "TV Settings") {', '        val fire = tile(Glyph.OPEN, "TV Settings", small = true) {')
rep('        val gear = button(Glyph.GEAR, "GlassOS") {', '        val gear = tile(Glyph.GEAR, "GlassOS", small = true) {')
rep("        body.addView(grid(listOfNotNull(power, wifi, dnd, audio, timer, pads, acc, pc, fire, gear)))\n",
    "        if (NightStyle.isOn(ctx)) addCentered(nightGrid(listOfNotNull(wifi, dnd, audio, timer, pads, acc, pc), listOf(power, fire, gear)))\n"
    "        else addCentered(grid(listOfNotNull(power, wifi, dnd, audio, timer, pads, acc, pc, fire, gear)))\n")
rep("        body.addView(grid(items))\n", "        addCentered(grid(items))\n")

open(path, "w", encoding="utf-8").write(s)
print("Control Center restyle: all edits applied")
