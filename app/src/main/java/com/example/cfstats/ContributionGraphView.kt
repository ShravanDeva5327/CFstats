// ContributionGraphView.kt
package com.example.cfstats

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class ContributionGraphView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // This 2D array holds the contribution data:
    // rows: 7 days; columns: 25 weeks.
    // Each cell will hold a number of submissions made that day
    private var contributions: Array<Array<Int>>? = null

    // Paint for cells with 1 submission
    private val greenPaintLight = Paint().apply {
        color = Color.parseColor("#90EE90") // Light green (LightGreen)
        style = Paint.Style.FILL
    }
    // Paint for cells with 2 submissions
    private val greenPaintMedium = Paint().apply {
        color = Color.parseColor("#00FF00") // Standard green (Lime)
        style = Paint.Style.FILL
    }
    // Paint for cells with 3+ submissions
    private val greenPaintDark = Paint().apply {
        color = Color.parseColor("#008000") // Dark green (Green)
        style = Paint.Style.FILL
    }

    // Paint for white cells (no contribution)
    private val grayPaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.FILL
    }

    // Paint for black borders around cells
    private val blackPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    /**
     * Update the contributions data and redraw the view.
     *
     * @param contributions A 7x25 array representing the contributions data.
     */
    fun setContributions(contributions: Array<Array<Int>>) {
        this.contributions = contributions
        invalidate()  // Force the view to redraw with the new data
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val numCols = 25  // 25 weeks
        val numRows = 7   // 7 days in a week
        val gapSize = 4f  // Space between squares

        // Calculate box size (ensuring squares)
        val boxSize = minOf((width - (numCols + 1) * gapSize) / numCols,
            (height - (numRows + 1) * gapSize) / numRows)

        for (row in 0 until numRows) {
            for (col in 0 until numCols) {
                val left = col * (boxSize + gapSize) + gapSize
                val top = row * (boxSize + gapSize) + gapSize
                val right = left + boxSize
                val bottom = top + boxSize

                val contribution = contributions?.getOrNull(row)?.getOrNull(col) ?: 0
                val paint = when {
                    contribution >=3 -> greenPaintDark
                    contribution == 2 -> greenPaintMedium
                    contribution == 1 -> greenPaintLight
                    else -> grayPaint
                }

                val cornerRadius = 10f // Adjust as needed
                // Draw rounded square
                canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, paint)

                // Draw rounded border
                canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, blackPaint)
            }
        }
    }

}
