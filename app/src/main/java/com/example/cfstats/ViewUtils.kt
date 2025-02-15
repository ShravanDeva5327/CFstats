package com.example.cfstats

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.View.MeasureSpec

fun createContributionBitmap(context: Context, contributions: Array<Array<Int>>, width: Int, height: Int): Bitmap {
    // Instantiate your custom view
    val view = ContributionGraphView(context)
    view.setContributions(contributions)

    // Measure and layout the view offscreen
    view.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    view.layout(0, 0, view.measuredWidth, view.measuredHeight)

    // Create the bitmap and draw the view onto its canvas
    val bitmap = Bitmap.createBitmap(view.measuredWidth, view.measuredHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    view.draw(canvas)
    return bitmap
}
