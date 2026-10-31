package com.jimmy0933.myplayer

/** Maps the right-edge vertical control: high volume at the top, mute at the bottom. */
internal object VerticalVolumeGeometry {
    fun fractionAt(y: Float, height: Float, inset: Float): Float {
        if (!y.isFinite() || !height.isFinite() || !inset.isFinite()) return 0f
        val usable = height - 2f * inset.coerceAtLeast(0f)
        if (usable <= 0f) return 0f
        return (1f - (y - inset.coerceAtLeast(0f)) / usable).coerceIn(0f, 1f)
    }

    fun yAt(fraction: Float, height: Float, inset: Float): Float {
        if (!height.isFinite() || !inset.isFinite()) return 0f
        val start = inset.coerceAtLeast(0f)
        val usable = (height - 2f * start).coerceAtLeast(0f)
        val level = if (fraction.isFinite()) fraction.coerceIn(0f, 1f) else 0f
        return start + usable * (1f - level)
    }
}
