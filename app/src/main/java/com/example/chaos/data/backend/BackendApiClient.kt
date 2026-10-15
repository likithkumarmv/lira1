package com.example.chaos.data.backend

import com.example.chaos.domain.models.CallEscalation
import com.example.chaos.domain.models.DemoEvent
import com.example.chaos.domain.models.DemoIncident
import com.example.chaos.domain.models.DemoSession
import com.example.chaos.domain.models.Incident
import com.example.chaos.networking.ConnectionManager
import org.json.JSONArray
import org.json.JSONObject

data class GeminiTriage(
    val severity: String = "MEDIUM",
    val briefing: String = "",
    val spokenAlert: String = "",
    val shouldEscalate: Boolean = true
)

class BackendApiClient(
    private val connectionManager: ConnectionManager,
    var baseUrl: String = "http://127.0.0.1:3000",
    var authToken: String? = null
) {

    private suspend fun executeApi(
        path: String,
        method: String = "GET",
        jsonBody: String? = null,
        connectTimeoutMs: Int = 3500,
        readTimeoutMs: Int = 6000
    ): Result<String> {
        val candidates = listOf(
            baseUrl,
            "http://127.0.0.1:3000",
            "http://10.148.112.68:3000"
        ).filter { it.isNotBlank() && !it.contains("10.0.2.2") }.distinct()

        var lastError: Throwable? = null
        for (candidate in candidates) {
            val fullUrl = "$candidate$path"
            val result = connectionManager.executeBackendHttps(
                urlString = fullUrl,
                method = method,
                jsonBody = jsonBody,
                authToken = authToken,
                connectTimeoutMs = connectTimeoutMs,
                readTimeoutMs = readTimeoutMs
            )
            if (result.isSuccess) {
                baseUrl = candidate
                return result
            } else {
                lastError = result.exceptionOrNull()
            }
        }
        return Result.failure(lastError ?: Exception("Unable to connect to LIRA backend on candidate endpoints"))
    }

    suspend fun checkHealth(): Result<Boolean> {
        val result = executeApi("/api/health", method = "GET")
        return result.map { jsonStr ->
            try {
                val obj = JSONObject(jsonStr)
                obj.optString("status", "").equals("ok", ignoreCase = true) ||
                        obj.optBoolean("ready", false) ||
                        obj.optJSONObject("services")?.optBoolean("geminiBrain") == true
            } catch (e: Exception) {
                false
            }
        }
    }

    suspend fun submitIncident(incident: Incident): Result<String> {
        val payload = JSONObject().apply {
            put("schemaVersion", 1)
            put("incidentId", incident.incidentId)
            put("deviceId", incident.deviceId)
            put("eventType", incident.type)
            put("startedAt", incident.startedAt ?: "")
            put("localState", incident.state.name)
            val evArray = JSONArray()
            incident.evidence.forEach { evArray.put(it) }
            put("sensorEvidence", evArray)
        }.toString()

        val result = executeApi("/api/v1/incidents", method = "POST", jsonBody = payload)
        return result.mapCatching { jsonStr ->
            val obj = JSONObject(jsonStr)
            obj.optString("incidentId", incident.incidentId)
        }
    }

    suspend fun requestGeminiTriage(
        incidentId: String,
        eventType: String,
        sensorEvidence: List<String>
    ): Result<GeminiTriage> {
        val payload = JSONObject().apply {
            put("incidentId", incidentId)
            put("eventType", eventType)
            val evArray = JSONArray()
            sensorEvidence.forEach { evArray.put(it) }
            put("sensorEvidence", evArray)
        }.toString()

        val result = executeApi("/api/agent/triage", method = "POST", jsonBody = payload)

        return result.mapCatching { jsonStr ->
            val root = JSONObject(jsonStr)
            val tObj = root.optJSONObject("triage") ?: root
            GeminiTriage(
                severity = tObj.optString("severity", "MEDIUM"),
                briefing = tObj.optString("briefing", "Physical alert observed by sensor nodes."),
                spokenAlert = tObj.optString("spokenAlert", "Perimeter alert observed on physical node."),
                shouldEscalate = tObj.optBoolean("shouldEscalate", true)
            )
        }
    }

    suspend fun synthesizeVoice(text: String): Result<String> {
        val payload = JSONObject().apply {
            put("text", text)
        }.toString()

        val result = executeApi("/api/voice/synthesize", method = "POST", jsonBody = payload)

        return result.mapCatching { jsonStr ->
            val obj = JSONObject(jsonStr)
            obj.getString("audioBase64")
        }
    }

    suspend fun createEscalation(
        incident: Incident,
        contactId: String,
        goal: String,
        idempotencyKey: String
    ): Result<CallEscalation> {
        val payload = JSONObject().apply {
            put("schemaVersion", 1)
            put("incidentId", incident.incidentId)
            put("deviceId", incident.deviceId)
            put("eventType", incident.type)
            put("startedAt", incident.startedAt ?: "")
            val evArray = JSONArray()
            incident.evidence.forEach { evArray.put(it) }
            put("sensorEvidence", evArray)
            put("localState", incident.state.name)
            put("contactId", contactId)
            put("goal", goal)
            put("userApproved", true)
            put("idempotencyKey", idempotencyKey)
        }.toString()

        val result = executeApi("/api/calls/start", method = "POST", jsonBody = payload)
        return result.mapCatching { jsonStr ->
            val obj = JSONObject(jsonStr)
            val jobId = obj.optString("jobId", obj.optString("callId", "call-001"))
            val status = obj.optString("status", "dialing")
            val attempt = obj.optInt("attempt", 1)
            val createdAt = obj.optString("createdAt", "")

            CallEscalation(
                jobId = jobId,
                incidentId = incident.incidentId,
                contactId = contactId,
                contactName = obj.optString("contactName", "Likith (Lead Supervisor)"),
                contactPhoneMasked = obj.optString("contactPhoneMasked", "+91 63645 40011"),
                goal = goal,
                status = status,
                attempt = attempt,
                createdAt = createdAt
            )
        }
    }

    suspend fun getEscalationStatus(jobId: String): Result<CallEscalation> {
        val result = executeApi("/api/calls/$jobId", method = "GET")
        return result.mapCatching { jsonStr ->
            val obj = JSONObject(jsonStr)
            CallEscalation(
                jobId = obj.optString("jobId", obj.optString("callId", jobId)),
                incidentId = obj.optString("incidentId", ""),
                contactId = obj.optString("contactId", ""),
                contactName = obj.optString("contactName", "Supervisor"),
                contactPhoneMasked = obj.optString("contactPhoneMasked", "***"),
                goal = obj.optString("goal", ""),
                status = obj.optString("status", "unknown"),
                attempt = obj.optInt("attempt", 1),
                createdAt = obj.optString("createdAt", ""),
                durationSeconds = if (obj.has("durationSeconds")) obj.getInt("durationSeconds") else null,
                transcriptSummary = if (obj.has("transcriptSummary")) obj.getString("transcriptSummary") else null,
                transcript = if (obj.has("transcript")) obj.getString("transcript") else null,
                error = if (obj.has("error")) obj.getString("error") else null
            )
        }
    }

    suspend fun getTranscript(jobId: String): Result<Pair<String?, String?>> {
        val result = executeApi("/api/calls/$jobId/transcript", method = "GET")
        return result.mapCatching { jsonStr ->
            val obj = JSONObject(jsonStr)
            val transcript = if (obj.has("transcript")) obj.getString("transcript") else null
            val summary = if (obj.has("summary")) obj.getString("summary") else null
            Pair(transcript, summary)
        }
    }

    // ----------------------------------------------------
    // Demo Mode API Endpoints
    // ----------------------------------------------------

    private fun parseDemoSession(obj: JSONObject): DemoSession {
        val sObj = if (obj.has("session")) obj.getJSONObject("session") else obj
        val eventsList = mutableListOf<DemoEvent>()
        val evArray = sObj.optJSONArray("events")
        if (evArray != null) {
            for (i in 0 until evArray.length()) {
                val e = evArray.getJSONObject(i)
                eventsList.add(
                    DemoEvent(
                        eventId = e.optString("eventId", ""),
                        sessionId = e.optString("sessionId", ""),
                        personLabel = e.optString("personLabel", ""),
                        authResult = e.optString("authResult", "Authorized"),
                        type = e.optString("type", "ENTRY"),
                        timestamp = e.optString("timestamp", ""),
                        timestampIst = e.optString("timestampIst", ""),
                        explanation = e.optString("explanation", ""),
                        responseStatus = e.optString("responseStatus", "RESOLVED_SAFE")
                    )
                )
            }
        }

        val peopleList = mutableListOf<String>()
        val pArray = sObj.optJSONArray("peopleInside")
        if (pArray != null) {
            for (i in 0 until pArray.length()) {
                peopleList.add(pArray.getString(i))
            }
        }

        val incObj = sObj.optJSONObject("activeIncident")
        val activeInc = if (incObj != null) {
            DemoIncident(
                incidentId = incObj.optString("incidentId", ""),
                eventId = incObj.optString("eventId", ""),
                personLabel = incObj.optString("personLabel", ""),
                startedAt = incObj.optString("startedAt", ""),
                state = incObj.optString("state", ""),
                reason = incObj.optString("reason", "")
            )
        } else null

        val callsList = mutableListOf<CallEscalation>()
        val cArray = sObj.optJSONArray("calls")
        if (cArray != null) {
            for (i in 0 until cArray.length()) {
                val c = cArray.getJSONObject(i)
                callsList.add(
                    CallEscalation(
                        jobId = c.optString("jobId", c.optString("callId", "")),
                        incidentId = c.optString("incidentId", ""),
                        contactId = c.optString("contactId", ""),
                        contactName = c.optString("contactName", "Likith (Lead Supervisor)"),
                        contactPhoneMasked = c.optString("contactPhoneMasked", "+91 63645 40011"),
                        goal = c.optString("goal", ""),
                        status = c.optString("status", "dialing"),
                        attempt = c.optInt("attempt", 1),
                        createdAt = c.optString("createdAt", ""),
                        durationSeconds = if (c.has("durationSeconds")) c.getInt("durationSeconds") else 22,
                        transcriptSummary = if (c.has("transcriptSummary")) c.getString("transcriptSummary") else null,
                        transcript = if (c.has("transcript")) c.getString("transcript") else null,
                        error = if (c.has("error")) c.getString("error") else null
                    )
                )
            }
        }

        return DemoSession(
            sessionId = sObj.optString("sessionId", ""),
            status = sObj.optString("status", "ACTIVE"),
            startTime = sObj.optString("startTime", ""),
            startTimeIst = sObj.optString("startTimeIst", ""),
            endTime = if (sObj.has("endTime") && !sObj.isNull("endTime")) sObj.getString("endTime") else null,
            endTimeIst = if (sObj.has("endTimeIst") && !sObj.isNull("endTimeIst")) sObj.getString("endTimeIst") else null,
            durationSeconds = sObj.optInt("durationSeconds", 0),
            totalEntries = sObj.optInt("totalEntries", 0),
            authorizedCount = sObj.optInt("authorizedCount", 0),
            unauthorizedCount = sObj.optInt("unauthorizedCount", 0),
            peopleInsideCount = sObj.optInt("peopleInsideCount", 0),
            peopleInside = peopleList,
            events = eventsList,
            activeIncident = activeInc,
            calls = callsList,
            reportSent = sObj.optBoolean("reportSent", false),
            reportSentAt = if (sObj.has("reportSentAt") && !sObj.isNull("reportSentAt")) sObj.getString("reportSentAt") else null,
            reportRecipient = sObj.optString("reportRecipient", "likithkumarmv6364@gmail.com"),
            reportMessageId = if (sObj.has("reportMessageId") && !sObj.isNull("reportMessageId")) sObj.getString("reportMessageId") else null,
            reportError = if (sObj.has("reportError") && !sObj.isNull("reportError")) sObj.getString("reportError") else null
        )
    }

    suspend fun startDemoSession(): Result<DemoSession> {
        val result = executeApi("/api/demo/start", method = "POST")
        return result.mapCatching { jsonStr ->
            parseDemoSession(JSONObject(jsonStr))
        }
    }

    suspend fun getDemoSession(): Result<DemoSession> {
        val result = executeApi("/api/demo/session", method = "GET")
        return result.mapCatching { jsonStr ->
            parseDemoSession(JSONObject(jsonStr))
        }
    }

    suspend fun triggerDemoShowcaseStep(): Result<Pair<DemoEvent?, DemoSession>> {
        val result = executeApi("/api/demo/showcase", method = "POST")
        return result.mapCatching { jsonStr ->
            val root = JSONObject(jsonStr)
            val session = parseDemoSession(root)
            val eObj = root.optJSONObject("event")
            val event = if (eObj != null) {
                DemoEvent(
                    eventId = eObj.optString("eventId", ""),
                    sessionId = eObj.optString("sessionId", ""),
                    personLabel = eObj.optString("personLabel", ""),
                    authResult = eObj.optString("authResult", "Authorized"),
                    type = eObj.optString("type", "ENTRY"),
                    timestamp = eObj.optString("timestamp", ""),
                    timestampIst = eObj.optString("timestampIst", ""),
                    explanation = eObj.optString("explanation", ""),
                    responseStatus = eObj.optString("responseStatus", "RESOLVED_SAFE")
                )
            } else null
            Pair(event, session)
        }
    }

    suspend fun triggerCustomDemoEvent(
        personLabel: String,
        authResult: String,
        type: String,
        explanation: String
    ): Result<DemoEvent> {
        val payload = JSONObject().apply {
            put("personLabel", personLabel)
            put("authResult", authResult)
            put("type", type)
            put("explanation", explanation)
        }.toString()

        val result = executeApi("/api/demo/event", method = "POST", jsonBody = payload)
        return result.mapCatching { jsonStr ->
            val root = JSONObject(jsonStr)
            val eObj = root.getJSONObject("event")
            DemoEvent(
                eventId = eObj.optString("eventId", ""),
                sessionId = eObj.optString("sessionId", ""),
                personLabel = eObj.optString("personLabel", ""),
                authResult = eObj.optString("authResult", "Authorized"),
                type = eObj.optString("type", "ENTRY"),
                timestamp = eObj.optString("timestamp", ""),
                timestampIst = eObj.optString("timestampIst", ""),
                explanation = eObj.optString("explanation", ""),
                responseStatus = eObj.optString("responseStatus", "RESOLVED_SAFE")
            )
        }
    }

    suspend fun pauseDemoSession(): Result<DemoSession> {
        val result = executeApi("/api/demo/pause", method = "POST")
        return result.mapCatching { parseDemoSession(JSONObject(it)) }
    }

    suspend fun resumeDemoSession(): Result<DemoSession> {
        val result = executeApi("/api/demo/resume", method = "POST")
        return result.mapCatching { parseDemoSession(JSONObject(it)) }
    }

    suspend fun resetDemoSession(): Result<DemoSession> {
        val result = executeApi("/api/demo/reset", method = "POST")
        return result.mapCatching { parseDemoSession(JSONObject(it)) }
    }

    suspend fun endDemoSession(): Result<Pair<DemoSession, Boolean>> {
        val result = executeApi("/api/demo/end", method = "POST")
        return result.mapCatching { jsonStr ->
            val root = JSONObject(jsonStr)
            val session = parseDemoSession(root)
            val emailSuccess = root.optJSONObject("emailResult")?.optBoolean("success", false) ?: false
            Pair(session, emailSuccess)
        }
    }

    suspend fun resendDemoReport(): Result<Boolean> {
        val result = executeApi("/api/demo/report/resend", method = "POST")
        return result.mapCatching { jsonStr ->
            val root = JSONObject(jsonStr)
            root.optBoolean("success", false)
        }
    }

    suspend fun exportDemoReport(format: String): Result<String> {
        return executeApi("/api/demo/export/$format", method = "GET")
    }
}
