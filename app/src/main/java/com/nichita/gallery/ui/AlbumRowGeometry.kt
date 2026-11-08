package com.nichita.gallery.ui

/** Unknown rows use the mean of actual measured album rows, never visible-item counts. */
internal class AlbumRowGeometry(private val albums: Int, private val columns: Int, private val gap: Float, heading: Float) {
    private val heights = FloatArray(1 + (albums + columns - 1) / columns)
    private var prefix = FloatArray(heights.size + 1)
    init { heights[0] = heading; rebuild() }
    fun measure(row: Int, height: Float): Boolean {
        if (row !in heights.indices || height <= 0 || heights[row] == height) return false
        heights[row] = height
        return true
    }
    fun rebuild() {
        var sum = 0f
        var count = 0
        for (row in 1 until heights.size) if (heights[row] > 0) { sum += heights[row]; count++ }
        val average = if (count == 0) 300f else sum / count
        for (row in heights.indices) prefix[row + 1] = prefix[row] + (heights[row].takeIf { it > 0 } ?: average) + gap
    }
    fun snapshot(): AlbumRowSnapshot = AlbumRowSnapshot(prefix.copyOf(), columns, albums, gap)
}

internal class AlbumRowSnapshot(private val prefix: FloatArray, private val columns: Int, private val albums: Int, private val gap: Float) {
    val totalHeight: Float get() = ((prefix.lastOrNull() ?: 0f) - gap).coerceAtLeast(0f)
    fun offset(row: Int): Float = prefix[row.coerceIn(0, prefix.lastIndex)]
    fun target(offset: Float): Pair<Int, Int> {
        val search = prefix.binarySearch(offset.coerceAtLeast(0f))
        val row = (if (search >= 0) search else -search - 2).coerceIn(0, (prefix.size - 2).coerceAtLeast(0))
        val index = if (row == 0) 0 else (1 + (row - 1) * columns).coerceAtMost(albums)
        return index to (offset - prefix[row]).coerceAtLeast(0f).toInt()
    }
}
