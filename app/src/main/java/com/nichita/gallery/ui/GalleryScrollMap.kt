package com.nichita.gallery.ui

import kotlin.math.ceil

/** Prefix geometry for full-span headers and variable-sized months, not equal-month estimates. */
internal class GalleryScrollMap(
    counts: List<Int>,
    val columns: Int,
    val cellHeight: Float,
    val headerHeight: Float,
    val headingHeight: Float,
    val gap: Float,
    bottomPadding: Float,
    private val albumTitleHeight: Float = 0f
) {
    val headerIndexes = IntArray(counts.size)
    private val starts = FloatArray(counts.size)
    private val rowCounts = IntArray(counts.size)
    val totalHeight: Float
    init {
        var index = if (albumTitleHeight > 0) 2 else 1
        var y = headingHeight + gap + if (albumTitleHeight > 0) albumTitleHeight + gap else 0f
        counts.forEachIndexed { section, count ->
            headerIndexes[section] = index
            starts[section] = y
            index += count + 1
            rowCounts[section] = ceil(count.toDouble() / columns).toInt()
            y += headerHeight + gap + rowCounts[section] * (cellHeight + gap)
        }
        totalHeight = (y - gap + bottomPadding).coerceAtLeast(0f)
    }

    private fun lastAtOrBefore(values: FloatArray, value: Float): Int {
        var low = 0
        var high = values.lastIndex
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (values[mid] <= value) low = mid + 1 else high = mid - 1
        }
        return high.coerceAtLeast(0)
    }

    fun monthAt(offset: Float): Int = if (starts.isEmpty()) 0 else lastAtOrBefore(starts, offset)

    fun offsetFor(index: Int): Float {
        if (index <= 0 || headerIndexes.isEmpty()) return 0f
        if (albumTitleHeight > 0 && index == 1) return headingHeight + gap
        val result = headerIndexes.binarySearch(index)
        val section = if (result >= 0) result else (-result - 2).coerceAtLeast(0)
        val relative = index - headerIndexes[section]
        return starts[section] + if (relative <= 0) 0f else headerHeight + gap + (relative - 1) / columns * (cellHeight + gap)
    }

    fun target(offset: Float): Pair<Int, Int> {
        if (starts.isEmpty() || offset < starts[0]) {
            return if (albumTitleHeight > 0 && offset >= headingHeight + gap) 1 to (offset - headingHeight - gap).toInt()
            else 0 to offset.coerceAtLeast(0f).toInt()
        }
        val section = monthAt(offset)
        val local = (offset - starts[section]).coerceAtLeast(0f)
        if (local < headerHeight + gap) return headerIndexes[section] to local.toInt()
        val rows = rowCounts[section]
        val rowOffset = local - headerHeight - gap
        val row = (rowOffset / (cellHeight + gap)).toInt().coerceIn(0, (rows - 1).coerceAtLeast(0))
        return headerIndexes[section] + 1 + row * columns to (rowOffset - row * (cellHeight + gap)).toInt()
    }
}
