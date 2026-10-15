package com.example.chaos.networking

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

enum class WifiStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    TIMED_OUT,
    ERROR
}

enum class CellularStatus {
    AVAILABLE,
    UNAVAILABLE,
    REQUESTING
}

enum class BackendStatus {
    ONLINE,
    OFFLINE,
    AUTH_ERROR,
    TESTING
}

class ConnectionManager(private val context: Context) {

    private val tag = "LiraConnMgr"
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _esp32Network = MutableStateFlow<Network?>(null)
    val esp32Network: StateFlow<Network?> = _esp32Network.asStateFlow()

    private val _cellularNetwork = MutableStateFlow<Network?>(null)
    val cellularNetwork: StateFlow<Network?> = _cellularNetwork.asStateFlow()

    private val _esp32WifiStatus = MutableStateFlow(WifiStatus.DISCONNECTED)
    val esp32WifiStatus: StateFlow<WifiStatus> = _esp32WifiStatus.asStateFlow()

    private val _cellularStatus = MutableStateFlow(CellularStatus.UNAVAILABLE)
    val cellularStatus: StateFlow<CellularStatus> = _cellularStatus.asStateFlow()

    private val _backendStatus = MutableStateFlow(BackendStatus.OFFLINE)
    val backendStatus: StateFlow<BackendStatus> = _backendStatus.asStateFlow()

    private var wifiNetworkCallback: ConnectivityManager.NetworkCallback? = null
    private var cellularNetworkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        registerCellularNetworkCallback()
    }

    /**
     * Cellular internet path: registers a request for TRANSPORT_CELLULAR with NET_CAPABILITY_INTERNET
     */
    fun registerCellularNetworkCallback() {
        if (cellularNetworkCallback != null) return

        _cellularStatus.value = CellularStatus.REQUESTING
        val cellularRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        cellularNetworkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(tag, "Cellular network available: $network")
                _cellularNetwork.value = network
                _cellularStatus.value = CellularStatus.AVAILABLE
            }

            override fun onLost(network: Network) {
                Log.d(tag, "Cellular network lost: $network")
                if (_cellularNetwork.value == network) {
                    _cellularNetwork.value = null
                    _cellularStatus.value = CellularStatus.UNAVAILABLE
                    _backendStatus.value = BackendStatus.OFFLINE
                }
            }

            override fun onUnavailable() {
                Log.w(tag, "Cellular network unavailable")
                _cellularNetwork.value = null
                _cellularStatus.value = CellularStatus.UNAVAILABLE
            }
        }

        try {
            connectivityManager.requestNetwork(cellularRequest, cellularNetworkCallback!!)
        } catch (e: Exception) {
            Log.e(tag, "Failed to request cellular network", e)
            _cellularStatus.value = CellularStatus.UNAVAILABLE
        }
    }

    /**
     * ESP32 local Wi-Fi path: Connects using WifiNetworkSpecifier for Android 10+ (API 29+)
     * Does NOT request NET_CAPABILITY_INTERNET because ESP32 SoftAP has no internet.
     */
    fun connectEsp32(ssid: String, password: String) {
        disconnectEsp32()
        _esp32WifiStatus.value = WifiStatus.CONNECTING

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val specifier = WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                    .setWpa2Passphrase(password)
                    .build()

                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(specifier)
                    .build()

                wifiNetworkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        Log.d(tag, "ESP32 Wi-Fi connected: $network")
                        _esp32Network.value = network
                        _esp32WifiStatus.value = WifiStatus.CONNECTED
                    }

                    override fun onLost(network: Network) {
                        Log.d(tag, "ESP32 Wi-Fi lost: $network")
                        if (_esp32Network.value == network) {
                            _esp32Network.value = null
                            _esp32WifiStatus.value = WifiStatus.DISCONNECTED
                        }
                    }

                    override fun onUnavailable() {
                        Log.w(tag, "ESP32 Wi-Fi connection timed out or rejected")
                        _esp32Network.value = null
                        _esp32WifiStatus.value = WifiStatus.TIMED_OUT
                    }
                }

                connectivityManager.requestNetwork(request, wifiNetworkCallback!!)
            } catch (e: Exception) {
                Log.e(tag, "Error initiating WifiNetworkSpecifier connection", e)
                _esp32WifiStatus.value = WifiStatus.ERROR
            }
        } else {
            Log.w(tag, "Legacy Android Wi-Fi binding fallback (pre-Android 10)")
            _esp32WifiStatus.value = WifiStatus.ERROR
        }
    }

    fun disconnectEsp32() {
        wifiNetworkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.w(tag, "Error unregistering Wi-Fi callback", e)
            }
            wifiNetworkCallback = null
        }
        _esp32Network.value = null
        _esp32WifiStatus.value = WifiStatus.DISCONNECTED
    }

    fun setBackendStatus(status: BackendStatus) {
        _backendStatus.value = status
    }

    /**
     * Executes HTTP requests bound to the ESP32 Wi-Fi Network specifically.
     * Never leaks to cellular or default network.
     */
    suspend fun executeEsp32Http(
        urlString: String,
        method: String = "GET",
        jsonBody: String? = null,
        connectTimeoutMs: Int = 3000,
        readTimeoutMs: Int = 3000
    ): Result<String> = withContext(Dispatchers.IO) {
        val network = _esp32Network.value
            ?: return@withContext Result.failure(IllegalStateException("ESP32 Wi-Fi network not connected"))

        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = network.openConnection(url) as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/json")

            if (jsonBody != null && (method == "POST" || method == "PUT")) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                    writer.write(jsonBody)
                    writer.flush()
                }
            }

            val responseCode = connection.responseCode
            val inputStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val responseText = BufferedReader(InputStreamReader(inputStream, "UTF-8")).use {
                it.readText()
            }

            if (responseCode in 200..299) {
                Result.success(responseText)
            } else {
                Result.failure(Exception("HTTP $responseCode: $responseText"))
            }
        } catch (e: Exception) {
            Log.e(tag, "executeEsp32Http failed for $urlString", e)
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Executes HTTPS requests bound to the Cellular Network when contacting cloud endpoints,
     * or standard default network/loopback for local dev/reverse-tethering backend servers.
     */
    suspend fun executeBackendHttps(
        urlString: String,
        method: String = "GET",
        jsonBody: String? = null,
        authToken: String? = null,
        connectTimeoutMs: Int = 4000,
        readTimeoutMs: Int = 6000
    ): Result<String> = withContext(Dispatchers.IO) {
        val url = URL(urlString)
        val host = url.host ?: ""
        val isLocalHost = host == "127.0.0.1" || host == "localhost" || host == "10.0.2.2" ||
                host.startsWith("192.168.") || host.startsWith("10.")
        val cellularNet = _cellularNetwork.value

        var connection: HttpURLConnection? = null
        try {
            connection = if (!isLocalHost && cellularNet != null) {
                cellularNet.openConnection(url) as HttpURLConnection
            } else {
                url.openConnection() as HttpURLConnection
            }
            connection.requestMethod = method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/json")
            if (!authToken.isNullOrBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $authToken")
            }

            if (jsonBody != null && (method == "POST" || method == "PUT")) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                    writer.write(jsonBody)
                    writer.flush()
                }
            }

            val responseCode = connection.responseCode
            val inputStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }

            val responseText = BufferedReader(InputStreamReader(inputStream, "UTF-8")).use {
                it.readText()
            }

            if (responseCode in 200..299) {
                Result.success(responseText)
            } else {
                Result.failure(Exception("HTTP $responseCode: $responseText"))
            }
        } catch (e: Exception) {
            Log.e(tag, "executeBackendHttps failed for $urlString", e)
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    fun cleanUp() {
        disconnectEsp32()
        cellularNetworkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.w(tag, "Error unregistering cellular callback", e)
            }
            cellularNetworkCallback = null
        }
    }
}
