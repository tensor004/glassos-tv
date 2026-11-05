#!/usr/bin/env python3
"""Adds the Night Scene home style to the GlassOS source tree.

Usage: python3 apply_night.py <glassos source dir>   (the folder that has app/src/main/...)
Every edit looks for one exact piece of text and stops with an error if it is not found exactly once, so a
changed source can never be patched half way without anyone noticing.
"""
import sys, os

root = sys.argv[1]
D = os.path.join(root, "app/src/main/java/io/github/abhishekkaisar/glassos")


def patch(name, edits):
    path = os.path.join(D, name)
    s = open(path, encoding="utf-8").read()
    for i, (old, new) in enumerate(edits):
        n = s.count(old)
        if n != 1:
            sys.exit("PATCH FAILED in %s, edit %d: expected 1 match, found %d:\n%s" % (name, i + 1, n, old))
        s = s.replace(old, new)
    open(path, "w", encoding="utf-8").write(s)
    print("patched", name, "(%d edits)" % len(edits))


patch("HomeScreen.kt", [
    # 1. the new view
    ("    lateinit var appMenu: AppMenu\n        private set\n",
     "    lateinit var appMenu: AppMenu\n        private set\n"
     "    /** The Night Scene home style (Settings > Home style); hidden while the Top Shelf home is in use. */\n"
     "    lateinit var night: NightHome\n        private set\n"),
    # 2. redraw its glass with the backdrop
    ("            gear.invalidate()\n            chevron.invalidate()\n",
     "            gear.invalidate()\n            if (::night.isInitialized) night.invalidateGlass()\n            chevron.invalidate()\n"),
    # 3. build it
    ("        appMenu = AppMenu(this)\n        root.addView(appMenu, FrameLayout.LayoutParams(-1, -1))\n",
     "        appMenu = AppMenu(this)\n        root.addView(appMenu, FrameLayout.LayoutParams(-1, -1))\n"
     "        night = NightHome(this)\n"
     "        home.addView(night, FrameLayout.LayoutParams(-1, -1))\n"
     "        root.addView(night.prompt, FrameLayout.LayoutParams(-1, -1))\n"
     "        showNightStyle(NightStyle.isOn(ctx))\n"),
    # 4. the new functions, before focusDock
    ("    fun focusDock() {\n        if (dockTiles.isNotEmpty())",
     "    private fun showNightStyle(on: Boolean) {\n"
     "        night.visibility = if (on) View.VISIBLE else View.GONE\n"
     "        header.visibility = if (on) View.GONE else View.VISIBLE\n"
     "        content.visibility = if (on) View.GONE else View.VISIBLE\n"
     "        chevron.visibility = if (on) View.GONE else View.VISIBLE\n"
     "        hero.isFocusable = !on\n"
     "    }\n\n"
     "    /** Settings > Home style: switches between the Top Shelf home and the Night Scene home. */\n"
     "    fun setNightStyle(on: Boolean) {\n"
     "        NightStyle.setOn(ctx, on)\n"
     "        if (on && prefs.scene == Prefs.DEFAULT_SCENE) prefs.scene = \"scene:p-night\"\n"
     "        showNightStyle(on)\n"
     "        setWallpaper(prefs.scene, save = false)\n"
     "        updateTime()\n"
     "        if (on) night.refreshWeather(false)\n"
     "    }\n\n"
     "    /** Settings > Weather city: the keyboard for the city name. */\n"
     "    fun openCityPrompt() {\n"
     "        if (night.prompt.isOpen) return\n"
     "        blockHome(true)\n"
     "        night.prompt.open {\n"
     "            blockHome(false)\n"
     "            focusDock()\n"
     "            updateHeroRunning()\n"
     "        }\n"
     "    }\n\n"
     "    fun focusDock() {\n"
     "        if (::night.isInitialized && night.active) { night.focusFirst(); return }\n"
     "        if (dockTiles.isNotEmpty())"),
    # 5. the Home key must not make the still hero focusable again
    ("        hero.isFocusable = !block\n",
     "        hero.isFocusable = !block && !NightStyle.isOn(ctx)\n"),
    # 6. back from Settings
    ("        if (!editMode) gear.requestFocus()\n",
     "        if (!editMode) { if (night.active) night.focusFirst() else gear.requestFocus() }\n"),
    # 7. clock
    ("        if (!::gear.isInitialized) return\n        val fmt =",
     "        if (!::gear.isInitialized) return\n        if (::night.isInitialized) night.updateTime()\n        val fmt ="),
    # 8. a still wallpaper and no Top Shelf network calls in this style
    ("    private fun slidesFor(id: String): List<Slide> = HeroRepo.cachedTitles(ctx, prefs.topShelf).ifEmpty { fallbackSlides(id) }",
     "    private fun slidesFor(id: String): List<Slide> =\n"
     "        if (NightStyle.isOn(ctx)) fallbackSlides(id) else HeroRepo.cachedTitles(ctx, prefs.topShelf).ifEmpty { fallbackSlides(id) }"),
    ("    private fun refreshTitles(id: String, token: Int) {\n",
     "    private fun refreshTitles(id: String, token: Int) {\n        if (NightStyle.isOn(ctx)) return\n"),
    # 9. apps for the dock
    ("    private fun applyApps(list: List<AppEntry>) {\n        apps = list\n",
     "    private fun applyApps(list: List<AppEntry>) {\n        apps = list\n        if (::night.isInitialized) night.setApps(list)\n"),
    # 10. the city keyboard: Back, Menu and keys
    ("    fun onBack() {\n        when {\n            setup.isOpen -> setup.back()\n",
     "    fun onBack() {\n        when {\n            night.prompt.isOpen -> night.prompt.close()\n            setup.isOpen -> setup.back()\n"),
    ("        when {\n            cc.isOpen -> cc.close()\n            settings.isOpen -> settings.close()\n",
     "        when {\n            night.prompt.isOpen -> night.prompt.close()\n            cc.isOpen -> cc.close()\n            settings.isOpen -> settings.close()\n"),
    ("        if (settings.isOpen || cc.isOpen || appMenu.isOpen) return false\n",
     "        if (settings.isOpen || cc.isOpen || appMenu.isOpen || night.prompt.isOpen) return false\n"),
    # 11. the old top row editor and the drawer do not exist in this style
    ("    fun enterEdit() {\n        if (dockTiles.isEmpty()) return\n",
     "    fun enterEdit() {\n        if (dockTiles.isEmpty() || night.active) return\n"),
    ("    fun focusAllApps() {\n",
     "    fun focusAllApps() {\n        if (night.active) { focusDock(); return }\n"),
])

patch("Overlays.kt", [
    ('    private val homeAppRow = RowView(context, "Home screen")\n',
     '    private val homeAppRow = RowView(context, "Home screen")\n'
     '    private val homeStyleRow = RowView(context, "Home style")\n'
     '    private val cityRow = RowView(context, "Weather city")\n'),
    ("        list.addView(soundRow, rowLp())\n",
     "        list.addView(homeStyleRow, rowLp())\n        list.addView(cityRow, rowLp())\n        list.addView(soundRow, rowLp())\n"),
    ("        replaceRow.onClick = {\n",
     "        homeStyleRow.onClick = {\n            act.setNightStyle(!NightStyle.isOn(context))\n            updateValues()\n        }\n"
     "        cityRow.onClick = { close { act.openCityPrompt() } }\n"
     "        replaceRow.onClick = {\n"),
    ('        soundRow.valueView.text = if (act.prefs.sound) "On" else "Off"\n',
     '        homeStyleRow.valueView.text = if (NightStyle.isOn(context)) "Night Scene" else "Top Shelf"\n'
     '        cityRow.valueView.text = NightStyle.city(context).ifEmpty { "Not set" }\n'
     '        soundRow.valueView.text = if (act.prefs.sound) "On" else "Off"\n'),
])

patch("WallpaperStore.kt", [
    ('        "scene:dark" to "Dark"\n    )',
     '        "scene:dark" to "Dark"\n    ) + NightPhotos.ALL'),
    ("        val b = WallpaperGen.render(name, w, h)\n",
     "        val b = NightPhotos.render(ctx, name, w, h)\n"),
    ("        val b = WallpaperGen.render(name, w * 2, h * 2)\n",
     "        val b = NightPhotos.render(ctx, name, w * 2, h * 2)\n"),
])

print("Night Scene home: all edits applied")
