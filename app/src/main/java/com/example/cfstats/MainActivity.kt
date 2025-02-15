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
    private lateinit var loginButton: Button
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        handleEditText = findViewById(R.id.handleEditText)
        contributionGraphView = findViewById(R.id.contributionGraphView)
        loginButton = findViewById(R.id.loginButton)

        // Retrieve saved handle from SharedPreferences, if available.
        val sharedPrefs = getSharedPreferences("MyAppPrefs", Context.MODE_PRIVATE)
        val savedHandle = sharedPrefs.getString("handle", null)
        if (savedHandle != null) {
            handleEditText.setText(savedHandle)
            fetchUserSubmissions(savedHandle)
        }

        // Set up click listener on the login button.
        loginButton.setOnClickListener {
            val handle = handleEditText.text.toString().trim()
            if (handle.isNotEmpty()) {
                // Save the handle persistently
                sharedPrefs.edit().putString("handle", handle).apply()
                // Fetch user submissions using the entered handle
                fetchUserSubmissions(handle)
            }
        }
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
