package com.wendigo.ayhalkasi

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import androidx.core.content.ContextCompat

object Palette {

    data class Entry(val key: String, val label: Int, val overlay: Int, val swatch: Int)

    val entries = listOf(
        Entry("pink", R.string.palette_pink, 0, R.color.pal_pink_primary),
        Entry("rose", R.string.palette_rose, R.style.ThemeOverlay_AyHalkasi_Rose, R.color.pal_rose_primary),
        Entry("lila", R.string.palette_lila, R.style.ThemeOverlay_AyHalkasi_Lila, R.color.pal_lila_primary),
        Entry("bordo", R.string.palette_bordo, R.style.ThemeOverlay_AyHalkasi_Bordo, R.color.pal_bordo_primary),
        Entry("coral", R.string.palette_coral, R.style.ThemeOverlay_AyHalkasi_Coral, R.color.pal_coral_primary),
        Entry("ocean", R.string.palette_ocean, R.style.ThemeOverlay_AyHalkasi_Ocean, R.color.pal_ocean_primary)
    )

    fun current(): Entry = entries.firstOrNull { it.key == Store.palette } ?: entries.first()

    fun overlayFor(key: String): Int = entries.firstOrNull { it.key == key }?.overlay ?: 0

    fun primaryColor(ctx: Context): Int {
        Store.init(ctx)
        return ContextCompat.getColor(ctx, current().swatch)
    }

    fun accent(ctx: Context): Int = attr(ctx, com.google.android.material.R.attr.colorPrimary)
    fun accentTint(ctx: Context): Int = attr(ctx, com.google.android.material.R.attr.colorPrimaryContainer)
    fun secondary(ctx: Context): Int = attr(ctx, com.google.android.material.R.attr.colorSecondary)
    fun onPrimary(ctx: Context): Int = attr(ctx, com.google.android.material.R.attr.colorOnPrimary)
    fun onPrimaryContainer(ctx: Context): Int = attr(ctx, com.google.android.material.R.attr.colorOnPrimaryContainer)

    private fun attr(ctx: Context, attr: Int): Int {
        val tv = TypedValue()
        return if (ctx.theme.resolveAttribute(attr, tv, true)) {
            if (tv.resourceId != 0) ContextCompat.getColor(ctx, tv.resourceId) else tv.data
        } else {
            Color.MAGENTA
        }
    }
}
