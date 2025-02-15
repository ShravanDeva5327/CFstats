package com.example.cfstats

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class MainActivity : AppCompatActivity() {

    private lateinit var handleEditText: EditText
    private lateinit var contributionGraphView: ContributionGraphView
    private lateinit var showContributionsButton: Button
    private val handler = Handler(Looper.getMainLooper())

    // Set polling interval (e.g., 15 min = 15 * 60 * 1000)
    private val pollingInterval = 900000L

    // Runnable that periodically calls the API
    private val pollingRunnable = object : Runnable {
        override fun run() {
            // Retrieve stored handle and call API if available
            val handle = getStoredHandle()
            if (!handle.isNullOrEmpty()) {
                fetchUserSubmissions(handle)
            }
            handler.postDelayed(this, pollingInterval)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        handleEditText = findViewById(R.id.handleEditText)
        contributionGraphView = findViewById(R.id.contributionGraphView)
        showContributionsButton = findViewById(R.id.loginButton)  // Renaming button for clarity

        // Retrieve saved handle from SharedPreferences, if available.
        val savedHandle = getStoredHandle()
        if (!savedHandle.isNullOrEmpty()) {
            handleEditText.setText(savedHandle)
            fetchUserSubmissions(savedHandle)
            // Start polling for periodic updates
            handler.postDelayed(pollingRunnable, pollingInterval)
        }

        // Set up click listener on the button.
        showContributionsButton.setOnClickListener {
            val handle = handleEditText.text.toString().trim()
            if (handle.isNotEmpty()) {
                // Save the handle persistently
                saveHandle(handle)
                // Immediately fetch user submissions for the entered handle
                fetchUserSubmissions(handle)
                // Start polling for periodic updates
                handler.removeCallbacks(pollingRunnable)
                handler.postDelayed(pollingRunnable, pollingInterval)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove callbacks to stop polling when the activity is destroyed
        handler.removeCallbacks(pollingRunnable)
    }

    // Helper functions for SharedPreferences
    private fun getStoredHandle(): String? {
        val sharedPrefs = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        return sharedPrefs.getString("handle", null)
    }

    private fun saveHandle(handle: String) {
        val sharedPrefs = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("handle", handle).apply()
    }

    private fun fetchUserSubmissions(handle: String) {
        Thread {
            try {
                val apiUrl = "https://codeforces.com/api/user.status?handle=$handle"
                val url = URL(apiUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                val responseCode = connection.responseCode
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                    val jsonResponse = JSONObject(response)

                    if (jsonResponse.getString("status") == "OK") {
                        val resultArray = jsonResponse.getJSONArray("result")
                        val contributions = Array(7) { Array(25) { 0 } }

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

                        runOnUiThread {
                            contributionGraphView.setContributions(contributions)
                        }
                    } else {
                        Log.e("MainActivity", "API returned error status")
                    }
                } else {
                    Log.e("MainActivity", "HTTP error code: $responseCode")
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Exception: ${e.message}")
                e.printStackTrace()
            }
        }.start()
    }
}
