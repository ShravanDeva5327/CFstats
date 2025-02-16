package com.example.cfstats

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import android.widget.RemoteViews
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class UpdateWidgetWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val context = applicationContext

        // Retrieve the stored handle
        val sharedPrefs = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val handle = sharedPrefs.getString("handle", null)
        if (handle.isNullOrEmpty()) {
            // No handle stored; nothing to update.
            return Result.success()
        }

        // Create an empty contributions array.
        var contributions = Array(7) { Array(25) { 0 } }
        var fetchSucceeded = false

        // Attempt to fetch contribution data from Codeforces.
        try {
            val apiUrl = "https://codeforces.com/api/user.status?handle=$handle"
            val url = URL(apiUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                val jsonResponse = JSONObject(response)
                if (jsonResponse.getString("status") == "OK") {
                    fetchSucceeded = true
                    val resultArray = jsonResponse.getJSONArray("result")
                    val zoneId = ZoneId.of("GMT+05:30")
                    val today = LocalDate.now(zoneId)
                    val startOfCurrentWeek = today.minusDays(today.dayOfWeek.value % 7L)
                    val startDate = startOfCurrentWeek.minusWeeks(24)

                    for (i in 0 until resultArray.length()) {
                        val submission = resultArray.getJSONObject(i)
                        if (submission.has("verdict") && submission.getString("verdict") == "OK") {
                            val creationTime = submission.getLong("creationTimeSeconds")
                            val submissionDate = Instant.ofEpochSecond(creationTime)
                                .atZone(zoneId)
                                .toLocalDate()
                            if (!submissionDate.isBefore(startDate) && !submissionDate.isAfter(today)) {
                                val daysDiff = ChronoUnit.DAYS.between(startDate, submissionDate).toInt()
                                val weekIndex = daysDiff / 7
                                val dayIndex = daysDiff % 7
                                if (weekIndex in 0..24 && dayIndex in 0..6) {
                                    contributions[dayIndex][weekIndex] += 1
                                }
                            }
                        }
                    }
                }
            } else {
                Log.e("UpdateWidgetWorker", "HTTP error code: ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e("UpdateWidgetWorker", "Exception: ${e.message}")
            e.printStackTrace()
        }

        // If fetching failed, attempt to load cached contributions.
        if (!fetchSucceeded) {
            val cachedContrib = loadCachedContributions(context)
            if (cachedContrib != null) {
                contributions = cachedContrib
                Log.i("UpdateWidgetWorker", "Using cached contributions")
            } else {
                Log.i("UpdateWidgetWorker", "No cached contributions available")
            }
        } else {
            // If fetch succeeded, cache the new contributions.
            cacheContributions(context, contributions)
        }

        // Define desired bitmap dimensions (adjust these as needed)
        val bitmapWidth = 693
        val bitmapHeight = 198
        val bitmap = createContributionBitmap(context, contributions, bitmapWidth, bitmapHeight)

        // Update the widget using RemoteViews
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_contribution)
        remoteViews.setImageViewBitmap(R.id.widgetImage, bitmap)

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, ContributionWidgetProvider::class.java)
        )
        appWidgetManager.updateAppWidget(widgetIds, remoteViews)

        return Result.success()
    }

    /**
     * Caches the contributions array into SharedPreferences as a JSON string.
     */
    private fun cacheContributions(context: Context, contributions: Array<Array<Int>>) {
        val jsonArray = JSONArray()
        for (row in contributions) {
            val rowArray = JSONArray()
            for (value in row) {
                rowArray.put(value)
            }
            jsonArray.put(rowArray)
        }
        val sharedPrefs = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("cached_contributions", jsonArray.toString()).apply()
    }

    /**
     * Loads the cached contributions array from SharedPreferences.
     * Returns null if no cached data is available or if an error occurs.
     */
    private fun loadCachedContributions(context: Context): Array<Array<Int>>? {
        val sharedPrefs = context.getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val jsonStr = sharedPrefs.getString("cached_contributions", null) ?: return null
        return try {
            val jsonArray = JSONArray(jsonStr)
            val contributions = Array(7) { Array(25) { 0 } }
            for (i in 0 until jsonArray.length()) {
                val rowArray = jsonArray.getJSONArray(i)
                for (j in 0 until rowArray.length()) {
                    contributions[i][j] = rowArray.getInt(j)
                }
            }
            contributions
        } catch (e: Exception) {
            Log.e("UpdateWidgetWorker", "Error loading cached contributions: ${e.message}")
            null
        }
    }

    companion object {
        // Enqueue a unique periodic work request to update the widget every 15 minutes.
        fun enqueueWork(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<UpdateWidgetWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "update_widget_work",
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
        }
    }
}
