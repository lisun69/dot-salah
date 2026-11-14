package com.abidur.dotsalah

/** Widget themes. Colours are ARGB. */
enum class WidgetStyle(
    val label: String,
    val bgRes: Int,
    val bg: Long,
    val stroke: Long,
    val primary: Long,
    val secondary: Long,
    val ghost: Long,
    val divider: Long,
    val light: Boolean
) {
    AMOLED("AMOLED", R.drawable.wbg_amoled, 0xFF000000, 0xFF262626, 0xFFFFFFFF, 0xFF8A8A8A, 0x26FFFFFF, 0xFF262626, false),
    LIGHT("LIGHT", R.drawable.wbg_light, 0xFFFFFFFF, 0xFFE2E2E2, 0xFF000000, 0xFF6E6E6E, 0x24000000, 0xFFE2E2E2, true),
    CLEAN("CLEAN", R.drawable.wbg_clean, 0xFF1C1C1E, 0x00000000, 0xFFF2F2F2, 0xFF9A9A9A, 0x22FFFFFF, 0xFF2E2E30, false);

    val primaryInt get() = primary.toInt()
    val secondaryInt get() = secondary.toInt()
    val ghostInt get() = ghost.toInt()
    val dividerInt get() = divider.toInt()
}
