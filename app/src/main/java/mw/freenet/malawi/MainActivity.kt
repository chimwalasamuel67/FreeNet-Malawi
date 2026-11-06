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
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

class MainActivity : Activity() {
    private lateinit var connectionStatus: TextView
    private lateinit var connectivityManager: ConnectivityManager
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

    private fun refreshConnectionStatus() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }

        if (capabilities == null) {
            connectionStatus.text = "Connection: No active network\nVPN status: Not detected\nUnderlying network: Not available\nInternet access: Not detected\n\nLive monitoring runs while this screen is open."
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
    }
}
