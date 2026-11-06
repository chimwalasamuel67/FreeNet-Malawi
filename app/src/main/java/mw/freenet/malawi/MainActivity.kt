package mw.freenet.malawi

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Process
import android.os.SystemClock
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : Activity() {
    private lateinit var connectionStatus: TextView
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var historyStatus: TextView
    private var lastSnapshot: String? = null
    private val connectionHistory = mutableListOf<String>()
    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private var callbackRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshOnUiThread()
        override fun onLost(network: Network) = refreshOnUiThread()
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = refreshOnUiThread()
        override fun onUnavailable() = refreshOnUiThread()
    }

    private fun refreshOnUiThread() {
        runOnUiThread {
            if (::connectionStatus.isInitialized) refreshConnectionStatus()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        val padding = (20 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(padding, padding * 2, padding, padding)
            setBackgroundColor(0xFFF4F7FB.toInt())
        }

        val title = TextView(this).apply {
            text = "FreeNet Malawi"
            textSize = 28f
            setTextColor(0xFF1557A5.toInt())
            typeface = Typeface.DEFAULT_BOLD
        }
        root.addView(title)

        val subtitle = TextView(this).apply {
            text = "Learning and research into affordable, lawful connectivity"
            textSize = 16f
            setTextColor(0xFF303943.toInt())
            setPadding(0, padding / 2, 0, padding)
        }
        root.addView(subtitle)

        val vpnTitle = TextView(this).apply {
            text = "VPN provider"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF1557A5.toInt())
        }
        root.addView(vpnTitle)

        val vpnDescription = TextView(this).apply {
            text = "FreeNet Malawi currently uses Proton VPN as an external provider. Install Proton VPN and sign in before opening it here."
            textSize = 14f
            setTextColor(0xFF303943.toInt())
            setPadding(0, padding / 3, 0, padding / 2)
        }
        root.addView(vpnDescription)

        val openVpnButton = Button(this).apply {
            text = "Open Proton VPN"
            setOnClickListener {
                val launchIntent = packageManager.getLaunchIntentForPackage("ch.protonvpn.android")
                if (launchIntent != null) startActivity(launchIntent)
                else Toast.makeText(this@MainActivity, "Proton VPN is not installed. Install it when you have internet access.", Toast.LENGTH_LONG).show()
            }
        }
        root.addView(openVpnButton)

        val label = TextView(this).apply {
            text = "Choose a network to learn about:"
            textSize = 16f
            setTextColor(0xFF202830.toInt())
            setPadding(0, padding, 0, 0)
        }
        root.addView(label)

        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Wi-Fi", "Airtel Malawi", "TNM"))
        }
        root.addView(spinner, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val explanation = TextView(this).apply {
            textSize = 15f
            setTextColor(0xFF303943.toInt())
            setPadding(0, padding, 0, padding)
        }
        root.addView(explanation)

        fun updateExplanation(selection: String) {
            explanation.text = when (selection) {
                "Airtel Malawi" -> "Airtel is a mobile network operator. Internet access through its mobile network may use your active data bundle or applicable plan."
                "TNM" -> "TNM is a mobile network operator. Internet access through its mobile network may use your active data bundle or applicable plan."
                else -> "Wi-Fi access depends on the network owner and the internet service available at that location."
            } + "\n\nThis prototype does not create free data or bypass network charges."
        }

        spinner.setSelection(0)
        updateExplanation("Wi-Fi")
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                updateExplanation(spinner.selectedItem.toString())
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

        val connectionTitle = TextView(this).apply {
            text = "Your phone's connection"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF1557A5.toInt())
            setPadding(0, padding, 0, padding / 2)
        }
        root.addView(connectionTitle)

        connectionStatus = TextView(this).apply {
            textSize = 15f
            setTextColor(0xFF303943.toInt())
        }
        root.addView(connectionStatus)

        val refreshButton = Button(this).apply {
            text = "Check connection"
            setOnClickListener { refreshConnectionStatus() }
        }
        root.addView(refreshButton)

        val note = TextView(this).apply {
            text = "Live monitoring updates when Android reports a network change. Android may not expose the VPN's underlying network. This app cannot identify the mobile operator, measure data balance, or determine charges."
            textSize = 13f
            setTextColor(0xFF526171.toInt())
            setPadding(0, padding / 2, 0, 0)
        }
        root.addView(note)

        val historyTitle = TextView(this).apply {
            text = "Connection activity history"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF1557A5.toInt())
            setPadding(0, padding, 0, padding / 2)
        }
        root.addView(historyTitle)

        historyStatus = TextView(this).apply {
            text = "No connection changes recorded yet."
            textSize = 14f
            setTextColor(0xFF303943.toInt())
            setPadding(0, 0, 0, padding)
        }
        root.addView(historyStatus)
        loadConnectionHistory()

        val clearHistoryButton = Button(this).apply {
            text = "Clear history"
            setOnClickListener {
                connectionHistory.clear()
                saveConnectionHistory()
                lastSnapshot = null
                updateHistoryView()
                Toast.makeText(this@MainActivity, "Connection history cleared.", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(clearHistoryButton)

        val usageTitle = TextView(this).apply {
            text = "Network usage counters"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF1557A5.toInt())
            setPadding(0, padding, 0, padding / 2)
        }
        root.addView(usageTitle)

        val usageStatus = TextView(this).apply {
            textSize = 14f
            setTextColor(0xFF303943.toInt())
        }
        root.addView(usageStatus)

        val refreshUsageButton = Button(this).apply {
            text = "Refresh usage counters"
            setOnClickListener { usageStatus.text = buildUsageSummary() }
        }
        root.addView(refreshUsageButton)
        usageStatus.text = buildUsageSummary()

        val qualityTitle = TextView(this).apply {
            text = "Connection quality monitor"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(0xFF1557A5.toInt())
            setPadding(0, padding, 0, padding / 2)
        }
        root.addView(qualityTitle)

        val qualityStatus = TextView(this).apply {
            text = "Response time: Not tested\\nQuality: Not measured"
            textSize = 14f
            setTextColor(0xFF303943.toInt())
        }
        root.addView(qualityStatus)

        val testQualityButton = Button(this).apply {
            text = "Test connection quality"
            setOnClickListener {
                isEnabled = false
                text = "Testing..."
                qualityStatus.text = "Testing internet response time..."
                Thread {
                    var result: String
                    var connection: HttpURLConnection? = null
                    try {
                        val started = SystemClock.elapsedRealtime()
                        connection = (URL("https://connectivitycheck.gstatic.com/generate_204").openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 7000
                            readTimeout = 7000
                            instanceFollowRedirects = true
                        }
                        val responseCode = connection.responseCode
                        val elapsed = SystemClock.elapsedRealtime() - started
                        result = if (responseCode == 204 || responseCode == 200) {
                            val rating = when {
                                elapsed < 150 -> "Good"
                                elapsed < 400 -> "Fair"
                                else -> "Slow"
                            }
                            "Response time: ${elapsed} ms\\nQuality: $rating\\nTest endpoint: Google connectivity check"
                        } else {
                            "Test could not be completed (server response $responseCode). Try again."
                        }
                    } catch (_: Exception) {
                        result = "Test unavailable. Check your internet connection and try again."
                    } finally {
                        connection?.disconnect()
                    }
                    runOnUiThread {
                        qualityStatus.text = result
                        testQualityButton.isEnabled = true
                        testQualityButton.text = "Test connection quality"
                    }
                }.start()
            }
        }
        root.addView(testQualityButton)

        val qualityNote = TextView(this).apply {
            text = "This test measures the time for one HTTPS request to a test server. Results depend on the route, server and VPN, and are not a full speed test or a guarantee of network stability. A small amount of data is used."
            textSize = 13f
            setTextColor(0xFF526171.toInt())
            setPadding(0, padding / 3, 0, padding)
        }
        root.addView(qualityNote)

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            isVerticalScrollBarEnabled = true
            addView(root, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        setContentView(scrollView)
        refreshConnectionStatus()
    }

    override fun onStart() {
        super.onStart()
        if (!callbackRegistered) {
            try {
                connectivityManager.registerDefaultNetworkCallback(networkCallback)
                callbackRegistered = true
            } catch (_: RuntimeException) {
                // Manual refresh remains available if live monitoring cannot start.
            }
        }
        refreshConnectionStatus()
    }

    override fun onStop() {
        if (callbackRegistered) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (_: IllegalArgumentException) {
                // Already unregistered.
            }
            callbackRegistered = false
        }
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (::connectionStatus.isInitialized) refreshConnectionStatus()
    }

    private fun transportName(capabilities: NetworkCapabilities): String? = when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile network"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
        else -> null
    }

    private fun saveConnectionHistory() {
        getSharedPreferences("freenet_settings", Context.MODE_PRIVATE)
            .edit()
            .putString("connection_history", connectionHistory.joinToString("\n"))
            .apply()
    }

    private fun loadConnectionHistory() {
        val saved = getSharedPreferences("freenet_settings", Context.MODE_PRIVATE)
            .getString("connection_history", "") ?: ""
        connectionHistory.clear()
        if (saved.isNotBlank()) connectionHistory.addAll(saved.lines().takeLast(20))
        updateHistoryView()
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 0L) return "Not reported by Android"
        val mb = bytes / (1024.0 * 1024.0)
        return String.format(Locale.getDefault(), "%.2f MB", mb)
    }

    private fun buildUsageSummary(): String {
        val totalReceived = TrafficStats.getTotalRxBytes()
        val totalSent = TrafficStats.getTotalTxBytes()
        val appReceived = TrafficStats.getUidRxBytes(Process.myUid())
        val appSent = TrafficStats.getUidTxBytes(Process.myUid())
        return "Device-wide received: ${formatBytes(totalReceived)}\n" +
            "Device-wide sent: ${formatBytes(totalSent)}\n\n" +
            "FreeNet Malawi received: ${formatBytes(appReceived)}\n" +
            "FreeNet Malawi sent: ${formatBytes(appSent)}\n\n" +
            "These are Android traffic counters since the phone booted. Device-wide totals can include Wi-Fi and mobile traffic. FreeNet figures cover this app only, not Proton VPN or other apps. They are not TNM/Airtel bundle balances or billing records."
    }

    private fun updateHistoryView() {
        historyStatus.text = if (connectionHistory.isEmpty()) "No connection changes recorded yet." else connectionHistory.asReversed().joinToString("\n")
    }

    private fun recordConnectionChange(snapshot: String) {
        if (snapshot == lastSnapshot) return
        lastSnapshot = snapshot
        val time = timeFormatter.format(Date())
        connectionHistory.add("$time  " + snapshot.replace("\n", " | "))
        if (connectionHistory.size > 20) connectionHistory.removeAt(0)
        saveConnectionHistory()
        updateHistoryView()
    }

    private fun refreshConnectionStatus() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }

        if (capabilities == null) {
            connectionStatus.text = "Connection: No active network\nVPN status: Not detected\nUnderlying network: Not available\nInternet access: Not detected\n\nLive monitoring runs while this screen is open."
            recordConnectionChange("No active network | VPN not detected | Internet not detected")
            return
        }

        val vpnActive = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        val activeTransport = transportName(capabilities)
        val internetStatus = when {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> "Internet access: Available"
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> "Internet access: Not yet confirmed"
            else -> "Internet access: Not detected"
        }

        // Best-effort hint only: Android may hide a VPN's physical underlying network.
        var underlyingHint: String? = null
        if (vpnActive) {
            for (candidate in connectivityManager.allNetworks) {
                if (candidate == activeNetwork) continue
                val candidateCapabilities = connectivityManager.getNetworkCapabilities(candidate) ?: continue
                if (candidateCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
                underlyingHint = transportName(candidateCapabilities)
                if (underlyingHint != null) break
            }
        } else {
            underlyingHint = activeTransport
        }

        val connectionLine = if (vpnActive) "Connection: VPN" else "Connection: " + (activeTransport ?: "Other network")
        val vpnLine = if (vpnActive) "VPN status: Active" else "VPN status: Not detected"
        val underlyingLine = when {
            vpnActive && underlyingHint != null -> "Underlying network detected: " + underlyingHint
            vpnActive -> "Underlying network: Not exposed by Android"
            underlyingHint != null -> "Network type: " + underlyingHint
            else -> "Network type: Not identified"
        }

        connectionStatus.text = connectionLine + "\n" + vpnLine + "\n" + underlyingLine + "\n" + internetStatus + "\n\nA VPN does not remove mobile-data charges."
        recordConnectionChange(connectionLine + "\n" + vpnLine + "\n" + underlyingLine + "\n" + internetStatus)
    }
}
