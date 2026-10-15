package com.example.chaos.data.esp32

import com.example.chaos.domain.models.DeviceStatus
import com.example.chaos.domain.models.Incident
import com.example.chaos.domain.models.IncidentState
import com.example.chaos.domain.models.IncidentSummary
import com.example.chaos.domain.models.SensorStatus
import com.example.chaos.networking.ConnectionManager
import org.json.JSONArray
import org.json.JSONObject

class Esp32ApiClient(
    private val connectionManager: ConnectionManager,
    var baseUrl: String = "http://192.168.4.1"
) {

    suspend fun getHealth(): Result<Boolean> {
        val result = connectionManager.executeEsp32Http(
            urlString = "$baseUrl/api/v1/health",
            method = "GET",
            connectTimeoutMs = 2500,
            readTimeoutMs = 2500
        )
        return result.map { jsonStr ->
            try {
                val json = JSONObject(jsonStr)
                json.optString("deviceId", "").isNotEmpty()
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun getStatus(): Result<DeviceStatus> {
        val result = connectionManager.executeEsp32Http(
            urlString = "$baseUrl/api/v1/status",
            method = "GET",
            connectTimeoutMs = 2500,
            readTimeoutMs = 2500
        )
        return result.mapCatching { jsonStr ->
            val root = JSONObject(jsonStr)
            val schemaVersion = root.optInt("schemaVersion", 1)
            val deviceId = root.optString("deviceId", "lira-esp32-01")
            val firmwareVersion = root.optString("firmwareVersion", "0.1.0")
            val uptimeSeconds = root.optLong("timestampUptimeMs", 0) / 1000
            val armed = root.optBoolean("armed", false)
            val state = IncidentState.fromString(root.optString("state", "DISARMED"))

            val sensorsMap = mutableMapOf<String, SensorStatus>()
            val sensorsObj = root.optJSONObject("sensors")
            if (sensorsObj != null) {
                for (key in sensorsObj.keys()) {
                    val sObj = sensorsObj.getJSONObject(key)
                    sensorsMap[key] = SensorStatus(
                        configured = sObj.optBoolean("configured", false),
                        active = if (sObj.has("active")) sObj.optBoolean("active") else null,
                        ready = if (sObj.has("ready")) sObj.optBoolean("ready") else null,
                        lastChangedUptimeMs = if (sObj.has("lastChangedUptimeMs")) sObj.optLong("lastChangedUptimeMs") else null,
                        errorCode = if (sObj.has("errorCode")) sObj.optString("errorCode") else null
                    )
                }
            }

            var incidentSummary: IncidentSummary? = null
            val incObj = root.optJSONObject("currentIncident")
            if (incObj != null && incObj.has("incidentId")) {
                incidentSummary = IncidentSummary(
                    incidentId = incObj.getString("incidentId"),
                    type = incObj.optString("type", "motion"),
                    state = IncidentState.fromString(incObj.optString("state", "AWAITING_ACK")),
                    startedUptimeMs = if (incObj.has("startedUptimeMs")) incObj.optLong("startedUptimeMs") else null,
                    ackDeadlineUptimeMs = if (incObj.has("ackDeadlineUptimeMs")) incObj.optLong("ackDeadlineUptimeMs") else null
                )
            }

            val lastEventId = if (root.has("lastEventId")) root.getString("lastEventId") else null

            DeviceStatus(
                schemaVersion = schemaVersion,
                deviceId = deviceId,
                firmwareVersion = firmwareVersion,
                uptimeSeconds = uptimeSeconds,
                armed = armed,
                state = state,
                sensors = sensorsMap,
                currentIncident = incidentSummary,
                lastEventId = lastEventId
            )
        }
    }

    suspend fun getEvents(afterEventId: String? = null): Result<List<Incident>> {
        val url = if (afterEventId != null) {
            "$baseUrl/api/v1/events?after=$afterEventId"
        } else {
            "$baseUrl/api/v1/events"
        }
        val result = connectionManager.executeEsp32Http(urlString = url, method = "GET")
        return result.mapCatching { jsonStr ->
            val list = mutableListOf<Incident>()
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val evidenceList = mutableListOf<String>()
                val evArray = item.optJSONArray("evidence")
                if (evArray != null) {
                    for (j in 0 until evArray.length()) {
                        evidenceList.add(evArray.getString(j))
                    }
                }
                list.add(
                    Incident(
                        incidentId = item.getString("incidentId"),
                        deviceId = item.optString("deviceId", "lira-esp32-01"),
                        type = item.optString("type", "vibration"),
                        state = IncidentState.fromString(item.optString("state", "RESOLVED")),
                        startedAt = if (item.has("startedAt")) item.getString("startedAt") else null,
                        startedUptimeMs = if (item.has("startedUptimeMs")) item.optLong("startedUptimeMs") else null,
                        ackDeadlineUptimeMs = if (item.has("ackDeadlineUptimeMs")) item.optLong("ackDeadlineUptimeMs") else null,
                        evidence = evidenceList,
                        acknowledgedBy = if (item.has("acknowledgedBy")) item.getString("acknowledgedBy") else null,
                        escalationJobId = if (item.has("escalationJobId")) item.getString("escalationJobId") else null,
                        resolutionNote = if (item.has("resolutionNote")) item.getString("resolutionNote") else null
                    )
                )
            }
            list
        }
    }

    suspend fun setArmState(armed: Boolean): Result<Pair<Boolean, IncidentState>> {
        val payload = JSONObject().apply {
            put("armed", armed)
        }.toString()

        val result = connectionManager.executeEsp32Http(
            urlString = "$baseUrl/api/v1/arm",
            method = "POST",
            jsonBody = payload
        )
        return result.mapCatching { jsonStr ->
            val obj = JSONObject(jsonStr)
            val isArmed = obj.optBoolean("armed", armed)
            val state = IncidentState.fromString(obj.optString("state", if (armed) "ARMED_IDLE" else "DISARMED"))
            Pair(isArmed, state)
        }
    }

    suspend fun acknowledgeIncident(incidentId: String, method: String): Result<Boolean> {
        val payload = JSONObject().apply {
            put("incidentId", incidentId)
            put("method", method)
        }.toString()

        val result = connectionManager.executeEsp32Http(
            urlString = "$baseUrl/api/v1/acknowledge",
            method = "POST",
            jsonBody = payload
        )
        return result.mapCatching { jsonStr ->
            val obj = JSONObject(jsonStr)
            obj.optBoolean("success", true)
        }
    }

    suspend fun setRelay(channel: Int, state: String): Result<Boolean> {
        val payload = JSONObject().apply {
            put("channel", channel)
            put("state", state)
        }.toString()

        val result = connectionManager.executeEsp32Http(
            urlString = "$baseUrl/api/v1/relay",
            method = "POST",
            jsonBody = payload
        )
        return result.mapCatching { jsonStr ->
            val obj = JSONObject(jsonStr)
            obj.optBoolean("success", true)
        }
    }

    suspend fun updateTftDisplay(message: String): Result<Boolean> {
        val payload = JSONObject().apply {
            put("message", message)
        }.toString()

        val result = connectionManager.executeEsp32Http(
            urlString = "$baseUrl/api/v1/display",
            method = "POST",
            jsonBody = payload,
            connectTimeoutMs = 2000,
            readTimeoutMs = 2000
        )
        return result.mapCatching { jsonStr ->
            val obj = JSONObject(jsonStr)
            obj.optBoolean("success", true)
        }
    }
}
